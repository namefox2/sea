package com.koretide.app.ui.tidewatch

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.koretide.app.R
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

class TideWatchRenderer(private val appContext: Context) : GLSurfaceView.Renderer {

    // ── State (written from UI thread via queueEvent, read on GL thread) ──────
    @Volatile var tidePercent  = 0.5f
    @Volatile var windAmp      = 0.25f     // 0..1
    @Volatile var windDirRad   = 3.93f     // 225° default
    @Volatile var isDark       = 0f

    @Volatile var deepColor    = floatArrayOf(0.04f, 0.22f, 0.58f)
    @Volatile var shallowColor = floatArrayOf(0.16f, 0.56f, 0.82f)
    @Volatile var skyHorizon   = floatArrayOf(0.72f, 0.90f, 1.00f)
    @Volatile var skyZenith    = floatArrayOf(0.28f, 0.58f, 0.92f)
    @Volatile var lightColor   = floatArrayOf(1.00f, 0.96f, 0.82f)
    @Volatile var sandDry      = floatArrayOf(0.92f, 0.86f, 0.68f)
    @Volatile var sandWet      = floatArrayOf(0.68f, 0.60f, 0.44f)

    // ── GL state ──────────────────────────────────────────────────────────────
    private var startMs = 0L
    private var aspect  = 1f

    private val proj  = FloatArray(16)
    private val view  = FloatArray(16)
    private val mvp   = FloatArray(16)

    private lateinit var sky:   SkyRenderer
    private lateinit var beach: BeachRenderer
    private lateinit var ocean: OceanMesh

    private var ocProg = 0
    private var oc_aPos    = -1
    private var oc_mvp     = -1
    private var oc_time    = -1
    private var oc_windAmp = -1
    private var oc_windDir = -1
    private var oc_tide    = -1
    private var oc_lightDir   = -1
    private var oc_lightColor = -1
    private var oc_deepColor  = -1
    private var oc_shallowColor = -1
    private var oc_camPos    = -1
    private var oc_roughness = -1

    // Camera
    private val eyePos = floatArrayOf(0f, 1.8f, 18f)
    private val center = floatArrayOf(0f, 0.2f, 0f)

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        startMs = System.currentTimeMillis()
        GLES20.glClearColor(0.05f, 0.1f, 0.2f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)

        try {
            val skyVert  = load(R.raw.sky_vert)
            val skyFrag  = load(R.raw.sky_frag)
            val ocVert   = load(R.raw.ocean_vert)
            val ocFrag   = load(R.raw.ocean_frag)
            val bchVert  = load(R.raw.beach_vert)
            val bchFrag  = load(R.raw.beach_frag)

            sky   = SkyRenderer(link(compile(GLES20.GL_VERTEX_SHADER, skyVert),
                                     compile(GLES20.GL_FRAGMENT_SHADER, skyFrag)))
            beach = BeachRenderer(link(compile(GLES20.GL_VERTEX_SHADER, bchVert),
                                       compile(GLES20.GL_FRAGMENT_SHADER, bchFrag)))
            ocProg = link(compile(GLES20.GL_VERTEX_SHADER, ocVert),
                          compile(GLES20.GL_FRAGMENT_SHADER, ocFrag))

            oc_aPos      = GLES20.glGetAttribLocation (ocProg, "a_Pos")
            oc_mvp       = GLES20.glGetUniformLocation(ocProg, "u_MVP")
            oc_time      = GLES20.glGetUniformLocation(ocProg, "u_Time")
            oc_windAmp   = GLES20.glGetUniformLocation(ocProg, "u_WindAmp")
            oc_windDir   = GLES20.glGetUniformLocation(ocProg, "u_WindDir")
            oc_tide      = GLES20.glGetUniformLocation(ocProg, "u_Tide")
            oc_lightDir  = GLES20.glGetUniformLocation(ocProg, "u_LightDir")
            oc_lightColor = GLES20.glGetUniformLocation(ocProg, "u_LightColor")
            oc_deepColor  = GLES20.glGetUniformLocation(ocProg, "u_DeepColor")
            oc_shallowColor = GLES20.glGetUniformLocation(ocProg, "u_ShallowColor")
            oc_camPos    = GLES20.glGetUniformLocation(ocProg, "u_CamPos")
            oc_roughness = GLES20.glGetUniformLocation(ocProg, "u_Roughness")

            ocean = OceanMesh()
            ocean.uploadToGPU()

        } catch (e: Exception) {
            Log.e("TideWatchRenderer", "onSurfaceCreated error", e)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = width.toFloat() / height.toFloat().coerceAtLeast(1f)
        Matrix.perspectiveM(proj, 0, 60f, aspect, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val t = (System.currentTimeMillis() - startMs) / 1000f
        val tide = tidePercent
        val wAmp = windAmp
        val wDir = windDirRad

        // View matrix
        Matrix.setLookAtM(view, 0,
            eyePos[0], eyePos[1], eyePos[2],
            center[0], center[1], center[2],
            0f, 1f, 0f)
        Matrix.multiplyMM(mvp, 0, proj, 0, view, 0)

        // Sun/moon direction from wind dir (opposite = light comes from wind direction)
        // Elevation: 45° up, slightly left of center
        val lightElev = 0.55f
        val lightAzim = wDir + 3.14f   // light opposite to wind
        val lightDir = floatArrayOf(
            cos(lightElev) * cos(lightAzim),
            sin(lightElev),
            cos(lightElev) * sin(lightAzim)
        )

        // Sky: map light direction to UV (approximate screen projection)
        val lightUV = floatArrayOf(
            (lightDir[0] * 0.4f + 0.5f).coerceIn(0.05f, 0.95f),
            (lightDir[1] * 0.4f + 0.72f).coerceIn(0.52f, 0.96f)
        )

        // Roughness: calm=0.04, storm=0.4
        val roughness = (wAmp * wAmp * 0.40f + 0.04f).coerceAtMost(0.40f)

        // ── Pass 1: Sky (no depth write) ────────────────────────────────────
        sky.draw(skyHorizon, skyZenith, lightColor, lightUV, isDark, t)

        // ── Pass 2: Beach ────────────────────────────────────────────────────
        beach.draw(mvp, tide, sandDry, sandWet, skyHorizon, t)

        // ── Pass 3: Ocean ────────────────────────────────────────────────────
        GLES20.glUseProgram(ocProg)
        GLES20.glUniformMatrix4fv(oc_mvp,   1, false, mvp,          0)
        GLES20.glUniform1f (oc_time,     t)
        GLES20.glUniform1f (oc_windAmp,  wAmp)
        GLES20.glUniform1f (oc_windDir,  wDir)
        GLES20.glUniform1f (oc_tide,     tide)
        GLES20.glUniform3fv(oc_lightDir,   1, lightDir,    0)
        GLES20.glUniform3fv(oc_lightColor, 1, lightColor,  0)
        GLES20.glUniform3fv(oc_deepColor,  1, deepColor,   0)
        GLES20.glUniform3fv(oc_shallowColor, 1, shallowColor, 0)
        GLES20.glUniform3fv(oc_camPos,     1, eyePos,      0)
        GLES20.glUniform1f (oc_roughness,  roughness)

        ocean.draw(oc_aPos)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun load(resId: Int): String =
        appContext.resources.openRawResource(resId).bufferedReader().use { it.readText() }

    private fun compile(type: Int, src: String): Int {
        val id = GLES20.glCreateShader(type)
        GLES20.glShaderSource(id, src)
        GLES20.glCompileShader(id)
        val status = IntArray(1)
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(id)
            GLES20.glDeleteShader(id)
            throw RuntimeException("Shader compile error: $log")
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
            val log = GLES20.glGetProgramInfoLog(prog)
            GLES20.glDeleteProgram(prog)
            throw RuntimeException("Shader link error: $log")
        }
        GLES20.glDeleteShader(vert)
        GLES20.glDeleteShader(frag)
        return prog
    }
}
