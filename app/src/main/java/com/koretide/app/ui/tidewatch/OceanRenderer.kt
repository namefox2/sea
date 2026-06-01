package com.koretide.app.ui.tidewatch

import android.content.Context
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.koretide.app.R
import com.koretide.app.theme.ThemeConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class OceanRenderer(private val appContext: Context) : GLSurfaceView.Renderer {

    // Per-frame volatile values — written from UI thread, read on GL thread
    @Volatile var tidePct  = 0.55f
    @Volatile var windBft  = 3.0f
    @Volatile var windDir  = 0.0f   // radians
    @Volatile var isDark   = 0.0f   // 0=day, 1=night
    @Volatile var aspect   = 1.0f

    // Theme color arrays — replaced atomically via queueEvent
    @Volatile private var skyTop  = floatArrayOf(0.51f, 0.73f, 0.87f)
    @Volatile private var skyBot  = floatArrayOf(0.74f, 0.88f, 0.96f)
    @Volatile private var seaTop  = floatArrayOf(0.12f, 0.43f, 0.71f)
    @Volatile private var seaBot  = floatArrayOf(0.05f, 0.18f, 0.40f)
    @Volatile private var sunCol  = floatArrayOf(1.0f,  0.96f, 0.78f)
    @Volatile private var mtnCol  = floatArrayOf(0.18f, 0.28f, 0.20f)
    @Volatile private var flatCol = floatArrayOf(0.47f, 0.42f, 0.33f)

    private var startTime = 0L
    private var program   = 0
    private var vboId     = 0

    // Cached uniform locations
    private var locTime    = -1; private var locTide   = -1; private var locWind   = -1
    private var locDir     = -1; private var locAspect = -1; private var locDark   = -1
    private var locSkyTop  = -1; private var locSkyBot = -1
    private var locSeaTop  = -1; private var locSeaBot = -1
    private var locSun     = -1; private var locMtn    = -1; private var locFlat   = -1

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        startTime = System.currentTimeMillis()
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)

        if (linkStatus[0] == 0) {
            Log.e(
                "OceanRenderer",
                GLES20.glGetProgramInfoLog(program)
            )
        }

        GLES20.glClearColor(0.05f, 0.15f, 0.30f, 1.0f)

        val vert = compile(GLES20.GL_VERTEX_SHADER,   load(R.raw.ocean_vert))
        val frag = compile(GLES20.GL_FRAGMENT_SHADER, load(R.raw.ocean_frag))
        if (vert == 0 || frag == 0) return

        program = GLES20.glCreateProgram().also { prog ->
            GLES20.glAttachShader(prog, vert)
            GLES20.glAttachShader(prog, frag)
            GLES20.glLinkProgram(prog)
        }

        locTime   = GLES20.glGetUniformLocation(program, "uTime")
        locTide   = GLES20.glGetUniformLocation(program, "uTide")
        locWind   = GLES20.glGetUniformLocation(program, "uWind")
        locDir    = GLES20.glGetUniformLocation(program, "uWindDir")
        locAspect = GLES20.glGetUniformLocation(program, "uAspect")
        locDark   = GLES20.glGetUniformLocation(program, "uDark")
        locSkyTop = GLES20.glGetUniformLocation(program, "uSkyTop")
        locSkyBot = GLES20.glGetUniformLocation(program, "uSkyBot")
        locSeaTop = GLES20.glGetUniformLocation(program, "uSeaTop")
        locSeaBot = GLES20.glGetUniformLocation(program, "uSeaBot")
        locSun    = GLES20.glGetUniformLocation(program, "uSun")
        locMtn    = GLES20.glGetUniformLocation(program, "uMtn")
        locFlat   = GLES20.glGetUniformLocation(program, "uFlat")

        // Full-screen quad: 2 triangles covering NDC [-1,1]
        val verts = floatArrayOf(-1f,-1f, 1f,-1f, -1f,1f, 1f,-1f, 1f,1f, -1f,1f)
        val buf = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().also { it.put(verts).position(0) }
        val vbo = IntArray(1)
        GLES20.glGenBuffers(1, vbo, 0)
        vboId = vbo[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.size * 4, buf, GLES20.GL_STATIC_DRAW)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = if (height > 0) width.toFloat() / height else 1f
    }

    override fun onDrawFrame(gl: GL10?) {
        if (program == 0) return
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        val t = (System.currentTimeMillis() - startTime) / 1000f
        GLES20.glUniform1f(locTime,   t)
        GLES20.glUniform1f(locTide,   tidePct)
        GLES20.glUniform1f(locWind,   windBft)
        GLES20.glUniform1f(locDir,    windDir)
        GLES20.glUniform1f(locAspect, aspect)
        GLES20.glUniform1f(locDark,   isDark)
        GLES20.glUniform3fv(locSkyTop, 1, skyTop,  0)
        GLES20.glUniform3fv(locSkyBot, 1, skyBot,  0)
        GLES20.glUniform3fv(locSeaTop, 1, seaTop,  0)
        GLES20.glUniform3fv(locSeaBot, 1, seaBot,  0)
        GLES20.glUniform3fv(locSun,    1, sunCol,   0)
        GLES20.glUniform3fv(locMtn,    1, mtnCol,   0)
        GLES20.glUniform3fv(locFlat,   1, flatCol,  0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
        val posLoc = GLES20.glGetAttribLocation(program, "aPosition")
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 8, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDisableVertexAttribArray(posLoc)
    }

    // ── Public API (called from UI thread via queueEvent) ─────────────────────

    fun applyTheme(cfg: ThemeConfig) {
        skyTop  = cfg.skyTopColor.toVec3()
        skyBot  = cfg.skyBottomColor.toVec3()
        seaTop  = cfg.seaTopColor.toVec3()
        seaBot  = cfg.seaBottomColor.toVec3()
        sunCol  = cfg.sunColor.toVec3()
        mtnCol  = cfg.mountainColor.toVec3()
        flatCol = cfg.tidalFlatColor.toVec3()
        isDark  = if (cfg.isDark) 1.0f else 0.0f
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
            Log.e("OceanRenderer", "Shader compile error: ${GLES20.glGetShaderInfoLog(id)}")
            GLES20.glDeleteShader(id)
            return 0
        }
        return id
    }
}
