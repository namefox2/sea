package com.koretide.app.ui.tidewatch

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.util.Log
import com.koretide.app.R
import com.koretide.app.theme.ThemeConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class OceanRenderer(private val appContext: Context) : GLSurfaceView.Renderer {

    // Per-frame volatile values — written from UI thread, read on GL thread
    @Volatile var tidePct = 0.55f
    @Volatile var windBft = 3.0f
    @Volatile var windDir = 0.0f   // radians
    @Volatile var aspect  = 1.0f

    // Theme colour arrays — replaced atomically from UI thread
    @Volatile private var skyTop     = floatArrayOf(0.51f, 0.73f, 0.87f)
    @Volatile private var skyBot     = floatArrayOf(0.74f, 0.88f, 0.96f)
    @Volatile private var seaShallow = floatArrayOf(0.12f, 0.43f, 0.71f)
    @Volatile private var seaDeep    = floatArrayOf(0.05f, 0.18f, 0.40f)
    @Volatile private var sunCol     = floatArrayOf(1.00f, 0.96f, 0.78f)
    @Volatile private var mtnCol     = floatArrayOf(0.18f, 0.28f, 0.20f)
    @Volatile private var flatCol    = floatArrayOf(0.47f, 0.42f, 0.33f)
    @Volatile private var isDark     = 0.0f

    private var startTime = 0L
    private val camera    = Camera()
    private lateinit var mesh: OceanMesh

    // GL object IDs
    private var skyProg  = 0
    private var ocProg   = 0
    private var skyVbo   = 0
    private var mtnTexId = 0

    // Cached attrib locations
    private var sk_aPos = -1
    private var oc_aXZ  = -1

    // Sky program uniform locations (sk_ prefix)
    private var sk_time   = -1; private var sk_dark   = -1; private var sk_aspect = -1
    private var sk_tide   = -1; private var sk_wind   = -1; private var sk_dir    = -1
    private var sk_skyTop = -1; private var sk_skyBot = -1
    private var sk_sun    = -1; private var sk_mtn    = -1; private var sk_flat   = -1
    private var sk_seaDp  = -1; private var sk_mtnTex = -1

    // Ocean program uniform locations (oc_ prefix)
    private var oc_vp     = -1; private var oc_camPos = -1; private var oc_sunDir = -1
    private var oc_time   = -1; private var oc_wind   = -1; private var oc_wDir   = -1
    private var oc_tide   = -1; private var oc_dark   = -1
    private var oc_skyTop = -1; private var oc_skyBot = -1
    private var oc_seaDp  = -1; private var oc_seaSh  = -1; private var oc_sunCol = -1

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        startTime = System.currentTimeMillis()
        GLES20.glClearColor(0.05f, 0.15f, 0.30f, 1.0f)

        // Sky program
        val sv = compile(GLES20.GL_VERTEX_SHADER,   load(R.raw.sky_vert))
        val sf = compile(GLES20.GL_FRAGMENT_SHADER, load(R.raw.sky_frag))
        if (sv != 0 && sf != 0) {
            skyProg = link(sv, sf)
            sk_aPos   = GLES20.glGetAttribLocation(skyProg, "aPos")
            sk_time   = uni(skyProg, "uTime");   sk_dark   = uni(skyProg, "uDark")
            sk_aspect = uni(skyProg, "uAspect"); sk_tide   = uni(skyProg, "uTide")
            sk_wind   = uni(skyProg, "uWind");   sk_dir    = uni(skyProg, "uWindDir")
            sk_skyTop = uni(skyProg, "uSkyTop"); sk_skyBot = uni(skyProg, "uSkyBot")
            sk_sun    = uni(skyProg, "uSun");    sk_mtn    = uni(skyProg, "uMtn")
            sk_flat   = uni(skyProg, "uFlat");   sk_seaDp  = uni(skyProg, "uSeaDeep")
            sk_mtnTex = uni(skyProg, "uMtnTex")
        }

        // Ocean program
        val ov = compile(GLES20.GL_VERTEX_SHADER,   load(R.raw.ocean_vert))
        val of = compile(GLES20.GL_FRAGMENT_SHADER, load(R.raw.ocean_frag))
        if (ov != 0 && of != 0) {
            ocProg  = link(ov, of)
            oc_aXZ  = GLES20.glGetAttribLocation(ocProg, "aXZ")
            oc_vp     = uni(ocProg, "uVP");       oc_camPos = uni(ocProg, "uCamPos")
            oc_sunDir = uni(ocProg, "uSunDir");   oc_time   = uni(ocProg, "uTime")
            oc_wind   = uni(ocProg, "uWind");     oc_wDir   = uni(ocProg, "uWindDir")
            oc_tide   = uni(ocProg, "uTide");     oc_dark   = uni(ocProg, "uDark")
            oc_skyTop = uni(ocProg, "uSkyTop");   oc_skyBot = uni(ocProg, "uSkyBot")
            oc_seaDp  = uni(ocProg, "uSeaDeep");  oc_seaSh  = uni(ocProg, "uSeaShallow")
            oc_sunCol = uni(ocProg, "uSunColor")
        }

        // Sky fullscreen quad VBO
        val quad = floatArrayOf(-1f,-1f, 1f,-1f, -1f,1f, 1f,-1f, 1f,1f, -1f,1f)
        val qBuf = ByteBuffer.allocateDirect(quad.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .also { it.put(quad).position(0) }
        val vbo = IntArray(1)
        GLES20.glGenBuffers(1, vbo, 0)
        skyVbo = vbo[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, skyVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, quad.size * 4, qBuf, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        // Mountain silhouette texture
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        mtnTexId = tex[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mtnTexId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val bmp = BitmapFactory.decodeResource(appContext.resources, R.drawable.mountain_silhouette)
        if (bmp != null) { GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0); bmp.recycle() }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        // Ocean grid mesh
        mesh = OceanMesh()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = if (height > 0) width.toFloat() / height else 1f
        camera.update(aspect)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val t = (System.currentTimeMillis() - startTime) / 1000f
        camera.update(aspect)

        // ── Pass 1: Sky background — no depth writes ───────────────────────────
        if (skyProg != 0) {
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glDepthMask(false)
            GLES20.glUseProgram(skyProg)

            GLES20.glUniform1f(sk_time,   t)
            GLES20.glUniform1f(sk_dark,   isDark)
            GLES20.glUniform1f(sk_aspect, aspect)
            GLES20.glUniform1f(sk_tide,   tidePct)
            GLES20.glUniform1f(sk_wind,   windBft)
            GLES20.glUniform1f(sk_dir,    windDir)
            GLES20.glUniform3fv(sk_skyTop, 1, skyTop,     0)
            GLES20.glUniform3fv(sk_skyBot, 1, skyBot,     0)
            GLES20.glUniform3fv(sk_sun,    1, sunCol,     0)
            GLES20.glUniform3fv(sk_mtn,    1, mtnCol,     0)
            GLES20.glUniform3fv(sk_flat,   1, flatCol,    0)
            GLES20.glUniform3fv(sk_seaDp,  1, seaDeep,   0)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mtnTexId)
            GLES20.glUniform1i(sk_mtnTex, 0)

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, skyVbo)
            GLES20.glEnableVertexAttribArray(sk_aPos)
            GLES20.glVertexAttribPointer(sk_aPos, 2, GLES20.GL_FLOAT, false, 8, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
            GLES20.glDisableVertexAttribArray(sk_aPos)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        }

        // ── Pass 2: Ocean mesh — depth test enabled ────────────────────────────
        if (ocProg != 0 && ::mesh.isInitialized) {
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glDepthMask(true)
            GLES20.glDepthFunc(GLES20.GL_LEQUAL)
            GLES20.glUseProgram(ocProg)

            // Sun/moon direction aligned with sky shader (MOON_X=0.72, MOON_Y=0.84)
            val sunDir = if (isDark > 0.5f)
                floatArrayOf(0.29f, 0.55f, 0.78f)   // moon: upper-right in world space
            else
                floatArrayOf(0.48f, 0.72f, 0.50f)   // sun: higher altitude

            GLES20.glUniformMatrix4fv(oc_vp,     1, false, camera.vp, 0)
            GLES20.glUniform3fv(oc_camPos, 1, camera.pos, 0)
            GLES20.glUniform3fv(oc_sunDir, 1, sunDir, 0)
            GLES20.glUniform1f(oc_time,   t)
            GLES20.glUniform1f(oc_wind,   windBft)
            GLES20.glUniform1f(oc_wDir,   windDir)
            GLES20.glUniform1f(oc_tide,   tidePct)
            GLES20.glUniform1f(oc_dark,   isDark)
            GLES20.glUniform3fv(oc_skyTop, 1, skyTop,     0)
            GLES20.glUniform3fv(oc_skyBot, 1, skyBot,     0)
            GLES20.glUniform3fv(oc_seaDp,  1, seaDeep,   0)
            GLES20.glUniform3fv(oc_seaSh,  1, seaShallow, 0)
            GLES20.glUniform3fv(oc_sunCol, 1, sunCol,     0)

            mesh.draw(oc_aXZ)
        }
    }

    // ── Public API (called from UI thread) ────────────────────────────────────

    fun applyTheme(cfg: ThemeConfig) {
        skyTop     = cfg.skyTopColor.toVec3()
        skyBot     = cfg.skyBottomColor.toVec3()
        seaShallow = cfg.seaTopColor.toVec3()
        seaDeep    = cfg.seaBottomColor.toVec3()
        sunCol     = cfg.sunColor.toVec3()
        mtnCol     = cfg.mountainColor.toVec3()
        flatCol    = cfg.tidalFlatColor.toVec3()
        isDark     = if (cfg.isDark) 1.0f else 0.0f
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun Int.toVec3() = floatArrayOf(
        Color.red(this)   / 255f,
        Color.green(this) / 255f,
        Color.blue(this)  / 255f
    )

    private fun load(resId: Int): String =
        appContext.resources.openRawResource(resId).bufferedReader().readText()

    private fun compile(type: Int, src: String): Int {
        val id = GLES20.glCreateShader(type)
        GLES20.glShaderSource(id, src)
        GLES20.glCompileShader(id)
        val status = IntArray(1)
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val tag = if (type == GLES20.GL_VERTEX_SHADER) "vert" else "frag"
            Log.e("OceanRenderer", "Shader($tag) error: ${GLES20.glGetShaderInfoLog(id)}")
            GLES20.glDeleteShader(id)
            return 0
        }
        return id
    }

    private fun link(vert: Int, frag: Int): Int {
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vert)
        GLES20.glAttachShader(prog, frag)
        GLES20.glLinkProgram(prog)
        val status = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e("OceanRenderer", "Program link error: ${GLES20.glGetProgramInfoLog(prog)}")
        }
        GLES20.glDeleteShader(vert)
        GLES20.glDeleteShader(frag)
        return prog
    }

    private fun uni(prog: Int, name: String) = GLES20.glGetUniformLocation(prog, name)
}
