package com.koretide.app.ui.tidewatch

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.koretide.app.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class TideWatchRenderer(private val appContext: Context) : GLSurfaceView.Renderer {

    // ── State (written from UI thread via queueEvent, read on GL thread) ──────
    @Volatile var tidePercent   = 0.5f
    @Volatile var windAmp       = 0.25f
    @Volatile var windDirRad    = 3.93f
    @Volatile var useDefaultSun  = true   // true → use defaultHour; false → actual local time
    @Volatile var defaultHour    = 12.0f  // hour used when useDefaultSun=true (set by theme)
    // 0..1 pre-computed in TideWatchFragment: tidePosition × rangeFactor × regionCap
    @Volatile var mudflatExposure = 0.40f
    // Ocean colors — theme-driven
    @Volatile var deepColor    = floatArrayOf(0.02f, 0.16f, 0.36f)  // deep navy (#0a3d6b area)
    @Volatile var shallowColor = floatArrayOf(0.10f, 0.68f, 0.72f) // bright turquoise (reference: #00CED1 area)
    @Volatile var sandDry      = floatArrayOf(0.92f, 0.86f, 0.68f)
    @Volatile var sandWet      = floatArrayOf(0.68f, 0.60f, 0.44f)
    // Sky/sun theme tints — blended into the time-of-day LUT each frame
    @Volatile var skyHorizonTheme = floatArrayOf(0.65f, 0.84f, 1.00f)
    @Volatile var skyZenithTheme  = floatArrayOf(0.14f, 0.40f, 0.82f)
    @Volatile var sunColorTheme   = floatArrayOf(1.00f, 0.95f, 0.85f)
    // When true: zero out lightDir X so sun/moon appears horizontally centred in view
    @Volatile var centerLightInView = false
    // When non-null: replaces lutLight after LUT+tint (use for themed 윤슬 color)
    @Volatile var lightColorOverride: FloatArray? = null

    // ── GL state ──────────────────────────────────────────────────────────────
    private var startMs = 0L
    private var aspect  = 1f

    private val proj  = FloatArray(16)
    private val view  = FloatArray(16)
    private val mvp   = FloatArray(16)

    private lateinit var sky:   SkyRenderer
    private lateinit var beach: BeachRenderer
    private lateinit var ocean: OceanMesh
    private lateinit var foam:  ShorelineFoam
    private lateinit var spray: SprayParticles

    // Ocean program + uniforms
    private var ocProg = 0
    private var oc_aPos         = -1
    private var oc_mvp          = -1
    private var oc_time         = -1
    private var oc_windAmp      = -1
    private var oc_windDir      = -1
    private var oc_tide         = -1
    private var oc_lightDir     = -1
    private var oc_lightColor   = -1
    private var oc_deepColor    = -1
    private var oc_shallowColor = -1
    private var oc_camPos       = -1
    private var oc_roughness    = -1
    private var oc_yunseulStr   = -1
    private var oc_waterlineZ   = -1
    private var oc_normalMap    = -1
    private var oc_horizonColor = -1
    private var oc_sandDryColor = -1
    private var oc_sandWetColor = -1
    private var oc_windSurge    = -1

    // Procedural normal map texture (128×128 RGBA, tiling ripple normals)
    private var normalMapTex = 0
    private var glReady = false
    @Volatile var initError: String? = null

    // Camera: standing on beach, ~6° downward pitch → horizon at ~40% from screen top
    private val eyePos = floatArrayOf(0f, 1.8f, 18f)
    private val center = floatArrayOf(0f, -0.3f, 0f)

    // Reused every frame to avoid per-frame Calendar allocation at 60 fps
    private val calendar = Calendar.getInstance()

    // ── Debug dump ────────────────────────────────────────────────────────────
    // Set from UI thread; consumed on GL thread in onDrawFrame.
    @Volatile var debugDumpRequested = false
    // Last-frame computed values written on GL thread, read by performDebugDump().
    private var dbgRawT          = 0f
    private var dbgT             = 0f
    private var dbgWaterlineZ    = 0f
    private var dbgWaterlineBase = 0f
    private var dbgShoreWave     = 0f
    private var dbgWindSurge     = 0f
    private val dbgLightColor   = FloatArray(3)
    private val dbgHorizonColor = FloatArray(3)

    // Pre-allocated — never replaced in onDrawFrame to avoid per-frame GC pressure
    private val lightDir   = FloatArray(3)
    private val lightUV    = FloatArray(2)
    private val lutHorizon = FloatArray(3)
    private val lutZenith  = FloatArray(3)
    private val lutLight   = FloatArray(3)
    private val lutAmbient = FloatArray(3)
    private var lutIsDark  = 0f

    // ── Sky LUT: keyframes [hr,hg,hb, zr,zg,zb, lr,lg,lb, isDark, ar,ag,ab] ──
    // Index 0 = hour; fields 1-3=horizon, 4-6=zenith, 7-9=light, 10=dark, 11-13=ambient(보색)
    private val SKY_LUT = arrayOf(
        // hr   horizon                 zenith                  light                   dark  ambient(보색)
        floatArrayOf( 0f, 0.02f,0.03f,0.10f, 0.01f,0.01f,0.06f, 0.15f,0.18f,0.30f, 1.0f, 0.04f,0.06f,0.15f), // midnight
        floatArrayOf( 5f, 0.18f,0.08f,0.12f, 0.04f,0.05f,0.15f, 0.40f,0.25f,0.20f, 0.6f, 0.08f,0.06f,0.12f), // pre-dawn
        floatArrayOf( 6f, 0.92f,0.42f,0.18f, 0.18f,0.28f,0.55f, 1.00f,0.65f,0.35f, 0.0f, 0.15f,0.22f,0.45f), // sunrise — cool shadow
        floatArrayOf( 9f, 0.72f,0.88f,1.00f, 0.25f,0.52f,0.88f, 1.00f,0.95f,0.82f, 0.0f, 0.12f,0.18f,0.40f), // morning — sky blue
        floatArrayOf(12f, 0.65f,0.84f,1.00f, 0.14f,0.40f,0.82f, 1.00f,0.98f,0.92f, 0.0f, 0.10f,0.15f,0.38f), // noon — sky blue
        floatArrayOf(15f, 0.72f,0.88f,1.00f, 0.18f,0.45f,0.86f, 1.00f,0.93f,0.78f, 0.0f, 0.12f,0.17f,0.38f), // afternoon — sky blue
        floatArrayOf(18f, 0.95f,0.40f,0.12f, 0.18f,0.22f,0.52f, 1.00f,0.58f,0.28f, 0.0f, 0.12f,0.15f,0.42f), // sunset — blue-purple shadow
        floatArrayOf(19f, 0.28f,0.12f,0.18f, 0.06f,0.06f,0.18f, 0.45f,0.22f,0.32f, 0.4f, 0.15f,0.10f,0.18f), // dusk — dim warm
        floatArrayOf(22f, 0.02f,0.03f,0.10f, 0.01f,0.01f,0.06f, 0.15f,0.18f,0.30f, 1.0f, 0.04f,0.06f,0.15f), // night — cool dark
        floatArrayOf(24f, 0.02f,0.03f,0.10f, 0.01f,0.01f,0.06f, 0.15f,0.18f,0.30f, 1.0f, 0.04f,0.06f,0.15f), // wrap
    )

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // If the EGL context was recreated (e.g. on repeated navigation) while the renderer
        // was still considered ready, free the old GL objects before rebuilding them.
        if (glReady) release()

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
            val fmVert   = load(R.raw.foam_vert)
            val fmFrag   = load(R.raw.foam_frag)
            val spVert   = load(R.raw.spray_vert)
            val spFrag   = load(R.raw.spray_frag)

            sky   = SkyRenderer(link(compile(GLES20.GL_VERTEX_SHADER, skyVert),
                                     compile(GLES20.GL_FRAGMENT_SHADER, skyFrag)))
            beach = BeachRenderer(link(compile(GLES20.GL_VERTEX_SHADER, bchVert),
                                       compile(GLES20.GL_FRAGMENT_SHADER, bchFrag)))
            ocProg   = link(compile(GLES20.GL_VERTEX_SHADER, ocVert),
                            compile(GLES20.GL_FRAGMENT_SHADER, ocFrag))
            foam     = ShorelineFoam(link(compile(GLES20.GL_VERTEX_SHADER, fmVert),
                                          compile(GLES20.GL_FRAGMENT_SHADER, fmFrag)))
            spray    = SprayParticles(link(compile(GLES20.GL_VERTEX_SHADER, spVert),
                                           compile(GLES20.GL_FRAGMENT_SHADER, spFrag)))

            oc_aPos         = GLES20.glGetAttribLocation (ocProg, "a_Pos")
            oc_mvp          = GLES20.glGetUniformLocation(ocProg, "u_MVP")
            oc_time         = GLES20.glGetUniformLocation(ocProg, "u_Time")
            oc_windAmp      = GLES20.glGetUniformLocation(ocProg, "u_WindAmp")
            oc_windDir      = GLES20.glGetUniformLocation(ocProg, "u_WindDir")
            oc_tide         = GLES20.glGetUniformLocation(ocProg, "u_Tide")
            oc_lightDir     = GLES20.glGetUniformLocation(ocProg, "u_LightDir")
            oc_lightColor   = GLES20.glGetUniformLocation(ocProg, "u_LightColor")
            oc_deepColor    = GLES20.glGetUniformLocation(ocProg, "u_DeepColor")
            oc_shallowColor = GLES20.glGetUniformLocation(ocProg, "u_ShallowColor")
            oc_camPos       = GLES20.glGetUniformLocation(ocProg, "u_CamPos")
            oc_roughness    = GLES20.glGetUniformLocation(ocProg, "u_Roughness")
            oc_yunseulStr   = GLES20.glGetUniformLocation(ocProg, "u_YunseulStr")
            oc_waterlineZ   = GLES20.glGetUniformLocation(ocProg, "u_WaterlineZ")
            oc_normalMap    = GLES20.glGetUniformLocation(ocProg, "u_NormalMap")
            oc_horizonColor = GLES20.glGetUniformLocation(ocProg, "u_HorizonColor")
            oc_sandDryColor = GLES20.glGetUniformLocation(ocProg, "u_SandDryColor")
            oc_sandWetColor = GLES20.glGetUniformLocation(ocProg, "u_SandWetColor")
            oc_windSurge    = GLES20.glGetUniformLocation(ocProg, "u_WindSurge")

            ocean        = OceanMesh()
            ocean.uploadToGPU()
            normalMapTex = buildNormalMap()
            glReady = true

        } catch (e: Exception) {
            initError = e.message ?: e.javaClass.simpleName
            Log.e("TideWatchRenderer", "onSurfaceCreated error", e)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = width.toFloat() / height.toFloat().coerceAtLeast(1f)
        // far=200 covers ocean to Z=-60; near=0.3 improves depth precision
        Matrix.perspectiveM(proj, 0, 63f, aspect, 0.3f, 200f)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (!glReady) {
            // Visible orange tint so init failure is distinguishable from dark night sky
            GLES20.glClearColor(0.25f, 0.08f, 0.02f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            return
        }
        // rawT grows without bound; t is wrapped to [0, 1000) so GPU float precision
        // never degrades. 1000 s is the LCM of all normal-map UV scroll periods
        // (0.011, 0.007, 0.006, 0.010, 0.028, 0.021, 0.060, 0.050 × 1000 = integers),
        // so the texture seam at the wrap is perfectly invisible.
        val rawT = (System.currentTimeMillis() - startMs) / 1000f
        val t    = rawT % 1000f
        val tide = tidePercent
        val wAmp = windAmp
        val wDir = windDirRad

        // ── Time of day: compute LUT first so clear color matches sky ─────────
        calendar.timeInMillis = System.currentTimeMillis()
        val hour = if (useDefaultSun) defaultHour
                   else calendar.get(Calendar.HOUR_OF_DAY) + calendar.get(Calendar.MINUTE) / 60f
        computeLightDir(hour)   // writes into lightDir member

        // Centre sun/moon horizontally in view for themed presets (밤바다, 노을해안)
        if (centerLightInView) {
            val elev = lightDir[1]
            val zComp = lightDir[2]
            val len = sqrt((elev * elev + zComp * zComp).toDouble()).toFloat().coerceAtLeast(0.001f)
            lightDir[0] = 0f
            lightDir[1] = elev / len
            lightDir[2] = zComp / len
        }

        sampleSkyLut(hour)      // writes into lutHorizon/Zenith/Light/Ambient/isDark members

        // Blend theme seasonal tint into LUT result (LUT drives time-of-day, theme adds seasonal flavor)
        val tint = 0.25f
        val tintInv = 1f - tint
        val sh = skyHorizonTheme; val sz = skyZenithTheme; val sc = sunColorTheme
        for (j in 0..2) {
            lutHorizon[j] = lutHorizon[j] * tintInv + sh[j] * tint
            lutZenith[j]  = lutZenith[j]  * tintInv + sz[j] * tint
            lutLight[j]   = lutLight[j]   * tintInv + sc[j] * tint
        }

        // Theme 윤슬 color override (e.g. silver moonlight, warm sunset gold)
        val lco = lightColorOverride
        if (lco != null) { lutLight[0] = lco[0]; lutLight[1] = lco[1]; lutLight[2] = lco[2] }

        lightUV[0] = (lightDir[0] * 0.4f + 0.5f).coerceIn(0.05f, 0.95f)
        lightUV[1] = (lightDir[1] * 0.4f + 0.72f).coerceIn(0.52f, 0.96f)

        // Clear with horizon-matched color → any rendering gap shows sky color, not black
        GLES20.glClearColor(lutHorizon[0] * 0.55f, lutHorizon[1] * 0.55f, lutHorizon[2] * 0.55f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // View matrix
        Matrix.setLookAtM(view, 0,
            eyePos[0], eyePos[1], eyePos[2],
            center[0], center[1], center[2],
            0f, 1f, 0f)
        Matrix.multiplyMM(mvp, 0, proj, 0, view, 0)

        // Roughness and 윤슬 strength
        val roughness  = (wAmp * wAmp * 0.40f + 0.04f).coerceAtMost(0.40f)
        val yunseulStr = (1f - wAmp * 0.9f).coerceIn(0f, 1f)

        // Waterline Z: where ocean meets beach/tidal-flat.
        // West-Sea tidal range: at low tide the ocean retreats ~36m to the horizon;
        // at high tide it fills nearly the whole foreground.
        //   tide=0.0 (간조) → waterlineZ=-18 → vast 갯벌 visible, ocean at far horizon
        //   tide=0.5 (중간) → waterlineZ= -1 → moderate beach strip
        //   tide=1.0 (만조) → waterlineZ=+16 → ocean fills view, thin beach near camera
        val waterlineBase = -18.0f + tide * 34.0f
        // Multi-frequency wave advance: two oscillations so no two waves are identical.
        // Primary ~5 s period, secondary ~8.6 s; amplitude ±1.5 m calm → ±3.5 m max wind.
        // Uses rawT (not wrapped t) so waterlineZ never jumps at the 1000-s boundary.
        val shoreWave =
            (sin(rawT * 1.25f) * 0.62f + sin(rawT * 0.73f + 1.4f) * 0.38f) *
                    (wAmp * 0.8f + 0.4f)
        // Wind surge: strong wind raises the mean water level slightly.
        // Coefficient kept small (0.07) so the Z-advance (×24.3 ratio) stays ≤ 1.7 m
        // at max wind — enough to be perceptible without mimicking a tide change.
        val windSurge  = wAmp * 0.07f
        val waterlineZ = waterlineBase + shoreWave + windSurge * (34f / 1.4f)

        // Track for debug dump (GL thread only — no sync needed)
        dbgRawT = rawT; dbgT = t
        dbgWaterlineBase = waterlineBase; dbgShoreWave = shoreWave
        dbgWindSurge = windSurge; dbgWaterlineZ = waterlineZ
        System.arraycopy(lutLight,   0, dbgLightColor,   0, 3)
        System.arraycopy(lutHorizon, 0, dbgHorizonColor, 0, 3)

        if (debugDumpRequested) {
            debugDumpRequested = false
            performDebugDump()
        }

        // ── Pass 1: Sky (no depth write) ─────────────────────────────────────
        sky.draw(lutHorizon, lutZenith, lutLight, lightUV, lutIsDark, t, aspect)

        // ── Pass 2: Beach (normal map on unit 0 for wave runup water texture) ──
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, normalMapTex)
        beach.draw(mvp, tide, waterlineZ, wAmp, mudflatExposure, sandDry, sandWet, lutHorizon, lightDir, eyePos, lutAmbient, t)

        // ── Pass 4: Ocean (normal map still on unit 0) ────────────────────────

        GLES20.glUseProgram(ocProg)
        GLES20.glUniformMatrix4fv(oc_mvp,           1, false, mvp,          0)
        GLES20.glUniform1f (oc_time,         t)
        GLES20.glUniform1f (oc_windAmp,      wAmp)
        GLES20.glUniform1f (oc_windDir,      wDir)
        GLES20.glUniform1f (oc_tide,         tide)
        GLES20.glUniform3fv(oc_lightDir,     1, lightDir,     0)
        GLES20.glUniform3fv(oc_lightColor,   1, lutLight,     0)
        GLES20.glUniform3fv(oc_deepColor,    1, deepColor,    0)
        GLES20.glUniform3fv(oc_shallowColor, 1, shallowColor, 0)
        GLES20.glUniform3fv(oc_camPos,       1, eyePos,       0)
        GLES20.glUniform1f (oc_roughness,    roughness)
        GLES20.glUniform1f (oc_yunseulStr,   yunseulStr)
        GLES20.glUniform1f (oc_waterlineZ,   waterlineZ)
        GLES20.glUniform1i (oc_normalMap,    0)
        GLES20.glUniform3fv(oc_horizonColor, 1, lutHorizon, 0)
        GLES20.glUniform3fv(oc_sandDryColor, 1, sandDry, 0)
        GLES20.glUniform3fv(oc_sandWetColor, 1, sandWet, 0)
        GLES20.glUniform1f (oc_windSurge,    windSurge)

        // Ocean is alpha-blended at the shore so the beach wave animation shows
        // through as the ocean fades out. Beach is rendered first (Pass 2).
        // GL_ALWAYS: bypass depth test so ocean always alpha-blends over beach
        // even where beach terrain is geometrically above the ocean Y plane.
        // glDepthMask(false): don't overwrite beach depth so spray (Pass 6) sees
        // correct depth values.
        GLES20.glDepthFunc(GLES20.GL_ALWAYS)
        GLES20.glDepthMask(false)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        ocean.draw(oc_aPos)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDepthMask(true)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)

        // ── Pass 6: Spray particles (GL_POINTS, alpha-blended) ────────────────
        spray.draw(mvp, t, wAmp, wDir, tide, waterlineZ, lutLight)
    }

    // ── Sky LUT helpers ───────────────────────────────────────────────────────

    // Interpolate sky LUT into pre-allocated member arrays (no heap allocation)
    private fun sampleSkyLut(hour: Float) {
        var i = 0
        while (i < SKY_LUT.size - 2 && SKY_LUT[i + 1][0] <= hour) i++
        val a = SKY_LUT[i]; val b = SKY_LUT[i + 1]
        val span = b[0] - a[0]
        val tf   = if (span < 0.001f) 0f else (hour - a[0]) / span
        for (j in 0..2) lutHorizon[j] = a[j + 1]  + (b[j + 1]  - a[j + 1])  * tf
        for (j in 0..2) lutZenith[j]  = a[j + 4]  + (b[j + 4]  - a[j + 4])  * tf
        for (j in 0..2) lutLight[j]   = a[j + 7]  + (b[j + 7]  - a[j + 7])  * tf
        lutIsDark                      = a[10]      + (b[10]      - a[10])      * tf
        for (j in 0..2) lutAmbient[j] = a[j + 11] + (b[j + 11] - a[j + 11]) * tf
    }

    // Write sun direction into pre-allocated lightDir member (no heap allocation)
    private fun computeLightDir(hour: Float) {
        val hourAngle = ((hour - 6f) / 12f) * PI.toFloat()
        val elevation = (sin(hourAngle.toDouble()).toFloat() * 0.8f + 0.1f).coerceAtLeast(0.05f)
        val azimuth   = cos(hourAngle.toDouble()).toFloat()
        val len = sqrt((azimuth * azimuth + elevation * elevation + 0.36f).toDouble()).toFloat()
        lightDir[0] = azimuth / len; lightDir[1] = elevation / len; lightDir[2] = -0.6f / len
    }

    // ── Normal map ────────────────────────────────────────────────────────────

    private fun heightAt(x: Float, y: Float): Float {
        val s = (2f * PI.toFloat()) / 128f
        var h = sin(x * s * 3f + y * s * 2f) * 0.50f
        h    += sin(x * s * 7f - y * s * 5f) * 0.25f
        h    += sin(y * s * 11f + x * s * 4f) * 0.15f
        h    += sin((x + y) * s * 17f)         * 0.10f
        return h
    }

    private fun buildNormalMap(): Int {
        val size   = 128
        val pixels = ByteArray(size * size * 4)
        var i = 0
        for (row in 0 until size) {
            for (col in 0 until size) {
                val h00 = heightAt(col.toFloat(), row.toFloat())
                val h10 = heightAt((col + 1).toFloat(), row.toFloat())
                val h01 = heightAt(col.toFloat(), (row + 1).toFloat())
                val dU  = h10 - h00
                val dV  = h01 - h00
                val nx  = -dU
                val ny  = 0.12f
                val nz  = -dV
                val len = sqrt((nx*nx + ny*ny + nz*nz).toDouble()).toFloat()
                pixels[i++] = ((nx/len * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255).toByte()
                pixels[i++] = ((ny/len * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255).toByte()
                pixels[i++] = ((nz/len * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255).toByte()
                pixels[i++] = 255.toByte()
            }
        }
        val buf = ByteBuffer.allocateDirect(pixels.size).order(ByteOrder.nativeOrder())
        buf.put(pixels)
        buf.position(0)
        val texId = IntArray(1)
        GLES20.glGenTextures(1, texId, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId[0])
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, size, size, 0,
                            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,     GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,     GLES20.GL_REPEAT)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return texId[0]
    }

    // ── Debug snapshot (called on GL thread when debugDumpRequested is set) ────

    @Suppress("LocalVariableName")
    private fun performDebugDump() {
        val wAmp  = windAmp
        val tide  = tidePercent
        val T     = dbgT
        val rawT  = dbgRawT
        val wzZ   = dbgWaterlineZ
        val TAG   = "OceanDebug"

        // --- Reproduce shader math on CPU ---
        val wind  = run { val w = wAmp.coerceIn(0f, 1f); w * w }   // smoothstep² like shader
        val amp   = 0.05f + (0.50f - 0.05f) * wind                  // mix(0.05, 0.50, wind)
        val tideY = tide * 1.4f - 0.7f + dbgWindSurge
        val minAmp = wAmp * wAmp * 0.62f + wAmp * 0.18f + 0.06f

        val L0   = 8f + (16f - 8f) * wAmp
        val spd0 = 0.85f + (1.65f - 0.85f) * wAmp
        val k0   = (2.0 * PI / L0).toFloat()
        val om0  = spd0 * k0

        // Beach wave cycle at current t (no per-column noise, centre column)
        val t1raw = (sin((k0 * wzZ - om0 * T).toDouble()) * 0.5 + 0.5).toFloat().coerceAtLeast(0f)
        val t1    = t1raw * t1raw
        val t2raw = (sin((k0 / 0.58f * wzZ - om0 * 1.25f * T).toDouble()) * 0.5 + 0.5).toFloat().coerceAtLeast(0f)
        val t2    = t2raw * t2raw
        val minReach  = 0.25f + wAmp * 0.8f
        val waveReach = maxOf(t1 * (1.8f + wAmp * 3.2f) + t2 * (0.7f + wAmp * 1.5f), minReach)

        // Approximate wave height at the waterline (beach side)
        val waveH_crest = 1.0f                    // crest is always waveH=+1
        val waveH_trough = -1.0f

        // Lighting geometry at the waterline (world pos ≈ (0, tideY, wzZ))
        val Lx = lightDir[0]; val Ly = lightDir[1]; val Lz = lightDir[2]
        val wx = 0f; val wy = tideY; val wz = wzZ
        val cx = eyePos[0]; val cy = eyePos[1]; val cz = eyePos[2]
        val vx = cx-wx; val vy = cy-wy; val vz = cz-wz
        val vLen = sqrt(vx*vx + vy*vy + vz*vz).coerceAtLeast(1e-6f)
        val Vx = vx/vLen; val Vy = vy/vLen; val Vz = vz/vLen

        // Half vector H = normalize(L + V) with flat normal (0,1,0)
        val hx = Lx+Vx; val hy = Ly+Vy; val hz = Lz+Vz
        val hLen = sqrt(hx*hx + hy*hy + hz*hz).coerceAtLeast(1e-6f)
        val Hx = hx/hLen; val Hy = hy/hLen; val Hz = hz/hLen

        val NdotL      = Ly.coerceAtLeast(0f)           // N=(0,1,0) dot L
        val NdotH      = Hy.coerceAtLeast(0f)           // N=(0,1,0) dot H
        val LdotNegV   = (-Lx*Vx - Ly*Vy - Lz*Vz).coerceAtLeast(0f)

        val sheenExp   = 20f - (20f-9f) * (wAmp*wAmp*0.40f+0.04f).coerceAtMost(0.40f)
        val sheen      = Math.pow(NdotH.toDouble(), sheenExp.toDouble()).toFloat()

        // SSS — backlit crest glow (only when waveH > 0 and L ≈ opposite of V)
        val sss_crest  = Math.pow(LdotNegV.toDouble(), 5.0).toFloat() * waveH_crest * 0.9f

        // Beach runup water specular (flat N=(0,1,0))
        val wSpec_flat = Math.pow(NdotH.toDouble(), 90.0).toFloat() * 0.38f

        // windS = smoothstep(wAmp) before squaring — gentler curve than wind²
        // foamBase uses windS (not wind²) to prevent over-amplification at high wind.
        // Old: 0.16 + wind² × 0.60 → foamBase up to 0.76 at wAmp=1.0
        // New: 0.16 + windS  × 0.42 → foamBase capped at 0.58 at wAmp=1.0
        val windS        = smoothstep(0f, 1f, wAmp)
        val foamBase     = 0.16f + windS * 0.42f
        val vFoam_low    = smoothstep(tideY + amp * 0.45f, tideY + amp * 1.05f, tideY + amp * 0.55f)
        val vFoam_mid    = smoothstep(tideY + amp * 0.45f, tideY + amp * 1.05f, tideY + amp * 0.75f)
        val vFoam_max    = smoothstep(tideY + amp * 0.45f, tideY + amp * 1.05f, tideY + amp * 1.50f)
        val waveMask_mid = smoothstep(0.42f, 0.85f, vFoam_mid)
        val waveMask_max = smoothstep(0.42f, 0.85f, vFoam_max)
        val foam_mid     = waveMask_mid * foamBase
        val foam_max     = waveMask_max * foamBase
        val foam_at_wl   = foam_max   // max-crest foam for pixel trace (shoreBand=1)

        // Crest colour boost — smoothstep(0,1,waveH) × 0.20  (reduced from 0.32 to avoid
        // double-whitening when foam also peaks at crest)
        val crestFac = smoothstep(0f, 1f, waveH_crest) * 0.20f

        // Roughness / yunseul
        val roughness  = (wAmp*wAmp*0.40f + 0.04f).coerceAtMost(0.40f)
        val yunseulStr = (1f - wAmp*0.9f).coerceIn(0f, 1f)

        // ── Pixel trace: CPU re-run of ocean_frag at distToWater=0, shoreNoise=0 ──
        val lc = dbgLightColor; val hc = dbgHorizonColor; val sc = shallowColor
        val distXZ_wl  = sqrt((cz - wz) * (cz - wz))      // cx=wx=0 → XZ only
        val dNorm_wl   = (distXZ_wl / 68f).coerceIn(0f, 1f)
        val NdotV_wl   = Vy                                  // flat N=(0,1,0)·V
        val fres_wl    = Math.pow((1f - NdotV_wl).toDouble(), 5.0).toFloat()
        // shoreProx=1 at distToWater=0 → blendInput=shoreBlend≈0 → depthBlend≈0 → water=shallowColor
        val depthBlend_wl = smoothstep(0.04f, 0.66f, 0f)
        // crest (waveH=+1):
        val wCr = floatArrayOf(
            sc[0] + crestFac * (sc[0] * 1.26f            - sc[0]),
            sc[1] + crestFac * (sc[1] * 1.26f + 0.04f   - sc[1]),
            sc[2] + crestFac * (sc[2] * 1.26f + 0.03f   - sc[2])
        )
        // troughDepth=0 at distToWater=0 → no trough effect
        val dFac = NdotL * 0.38f + 0.62f
        val cA   = floatArrayOf(wCr[0] * dFac, wCr[1] * dFac, wCr[2] * dFac)
        // foam: attenuated when crest is high (1-crestFac*0.5) to avoid double-whitening
        val fM   = foam_at_wl * 0.58f * (1f - crestFac * 0.5f)
        val cB   = floatArrayOf(cA[0] + fM*(0.96f-cA[0]), cA[1] + fM*(0.98f-cA[1]), cA[2] + fM*(1f-cA[2]))
        // Fresnel (deepZone=0 at waterline → yunseul=0; skyReflect≈0 at this dist):
        val fTgt = floatArrayOf(
            (sc[0] + 0.4f*(hc[0]-sc[0])) * 0.75f,
            (sc[1] + 0.4f*(hc[1]-sc[1])) * 0.75f,
            (sc[2] + 0.4f*(hc[2]-sc[2])) * 0.75f
        )
        val fMix = fres_wl * 0.14f * (1f - dNorm_wl) * (1f - fM)
        val cC   = floatArrayOf(cB[0]+fMix*(fTgt[0]-cB[0]), cB[1]+fMix*(fTgt[1]-cB[1]), cB[2]+fMix*(fTgt[2]-cB[2]))
        // shoreAlpha formula: fade only on beach side (edge0=-0.5, edge1=+3.0)
        val shoreE0 = -0.5f; val shoreE1 = 3.0f
        val aWl  = 1f - smoothstep(shoreE0, shoreE1, 0f)    // shoreAlpha at noise=0, distToWater=0
        val shoreT_wl = ((0f - shoreE0) / (shoreE1 - shoreE0)).coerceIn(0f, 1f)
        val comp = floatArrayOf(
            sandDry[0]*(1f-aWl) + cC[0]*aWl,
            sandDry[1]*(1f-aWl) + cC[1]*aWl,
            sandDry[2]*(1f-aWl) + cC[2]*aWl
        )

        val sep = "─────────────────────────────────"
        Log.d(TAG, "╔══ OCEAN DEBUG SNAPSHOT ══════════════")
        Log.d(TAG, "║ Logcat filter: tag:OceanDebug")
        Log.d(TAG, "╠$sep")
        Log.d(TAG, "║ [INPUT]")
        Log.d(TAG, "║  t(GPU)     = %.2f s  (rawT = %.1f s)".format(T, rawT))
        Log.d(TAG, "║  windAmp    = %.3f  (%.1f bft)".format(wAmp, wAmp*12f))
        Log.d(TAG, "║  windDir    = %.3f rad  (%.0f°)".format(windDirRad, Math.toDegrees(windDirRad.toDouble())))
        Log.d(TAG, "║  tide       = %.3f  (%.0f%%)".format(tide, tide*100f))
        Log.d(TAG, "║  mudflatExp = %.3f".format(mudflatExposure))
        Log.d(TAG, "╠$sep")
        Log.d(TAG, "║ [WATERLINE GEOMETRY]")
        Log.d(TAG, "║  waterlineBase = %.2f m".format(dbgWaterlineBase))
        Log.d(TAG, "║  shoreWave     = %.2f m   windSurge = %.3f m".format(dbgShoreWave, dbgWindSurge))
        Log.d(TAG, "║  waterlineZ    = %.2f m  ← ocean-beach boundary Z".format(wzZ))
        Log.d(TAG, "╠$sep")
        Log.d(TAG, "║ [WAVE PHYSICS]")
        Log.d(TAG, "║  wind²     = %.3f  amp = %.3f m  tideY = %.3f m".format(wind, amp, tideY))
        Log.d(TAG, "║  minAmp    = %.3f  foamStartY = %.3f m (tideY + amp*0.55)".format(minAmp, tideY + amp*0.55f))
        Log.d(TAG, "║  L0 = %.1f m  spd0 = %.3f rad/s  k0 = %.4f".format(L0, spd0, k0))
        Log.d(TAG, "╠$sep")
        Log.d(TAG, "║ [BEACH RUNUP CYCLE  (centre col, no noise)]")
        Log.d(TAG, "║  t1 = %.3f  t2 = %.3f  waveReach = %.2f m  minReach = %.2f m".format(t1, t2, waveReach, minReach))
        Log.d(TAG, "║  → wave covers %.2f m of beach from waterlineZ".format(waveReach))
        Log.d(TAG, "╠$sep")
        Log.d(TAG, "║ [LIGHTING AT WATERLINE  world=(0, %.2f, %.2f)]".format(tideY, wzZ))
        Log.d(TAG, "║  lightDir = (%.3f, %.3f, %.3f)  elev = %.1f°".format(
            Lx, Ly, Lz, Math.toDegrees(asin(Ly.toDouble()))))
        Log.d(TAG, "║  hour = %.1f  useDefaultSun = $useDefaultSun".format(defaultHour))
        Log.d(TAG, "║  NdotL (diffuse, flat N)  = %.3f".format(NdotL))
        Log.d(TAG, "║  NdotH (specular, flat N) = %.3f  → sheen = %.4f (exp %.0f)".format(NdotH, sheen, sheenExp))
        Log.d(TAG, "║  L·(-V)                   = %.3f  → SSS@crest = %.4f".format(LdotNegV, sss_crest))
        Log.d(TAG, "╠$sep")
        Log.d(TAG, "║ [CREST vs TROUGH  (ocean_frag contributions)]")
        Log.d(TAG, "║  crest brightening mix  = %.3f  (smoothstep(0,1,waveH) × 0.20)".format(crestFac))
        Log.d(TAG, "║  SSS at crest           = %.4f × lightColor".format(sss_crest))
        Log.d(TAG, "║  beach wSpec (flat N)   = %.4f  → +%.3f to runup color".format(wSpec_flat, wSpec_flat*0.32f))
        Log.d(TAG, "╠$sep")
        Log.d(TAG, "║ [FOAM WIND CURVE  foamBase = 0.16 + smoothstep(wAmp) × 0.42  (max 0.58)]")
        Log.d(TAG, "║  wAmp  | windS  | foamBase | foam×0.58 (ocean mix coeff)")
        for (wa in floatArrayOf(0.25f, 0.50f, 0.75f, 1.00f)) {
            val ws = smoothstep(0f, 1f, wa)
            val fb = 0.16f + ws * 0.42f
            val mark = if (wa == wAmp) "  ← current" else ""
            Log.d(TAG, "║  %.2f   | %.3f  | %.3f    | %.3f%s".format(wa, ws, fb, fb * 0.58f, mark))
        }
        Log.d(TAG, "╠$sep")
        Log.d(TAG, "║ [FOAM at waterlineZ  (onset tideY+amp×0.45 → full tideY+amp×1.05)]")
        Log.d(TAG, "║  windS=%.3f  foamBase=%.3f  (old formula would be %.3f)".format(windS, foamBase, 0.16f + wind * 0.60f))
        Log.d(TAG, "║  v_Foam  low=%.3f mid=%.3f max=%.3f  waveMask(mid/max)=%.3f/%.3f  foam(max)=%.3f".format(
            vFoam_low, vFoam_mid, vFoam_max, waveMask_mid, waveMask_max, foam_max))
        Log.d(TAG, "╠$sep")
        Log.d(TAG, "║ [SPECULAR / GLITTER]")
        Log.d(TAG, "║  roughness   = %.3f  yunseulStr = %.3f".format(roughness, yunseulStr))
        Log.d(TAG, "║  fineExp     = %.0f".format(280f - (280f-100f)*roughness))
        Log.d(TAG, "╠$sep")
        Log.d(TAG, "║ [COLORS & UNIFORMS]")
        Log.d(TAG, "║  lightColor   = (%.3f, %.3f, %.3f)  ← u_LightColor".format(lc[0], lc[1], lc[2]))
        Log.d(TAG, "║  horizonColor = (%.3f, %.3f, %.3f)  ← u_HorizonColor (sky at horizon)".format(hc[0], hc[1], hc[2]))
        Log.d(TAG, "║  shallowColor = (%.3f, %.3f, %.3f)".format(sc[0], sc[1], sc[2]))
        Log.d(TAG, "║  deepColor    = (%.3f, %.3f, %.3f)".format(deepColor[0], deepColor[1], deepColor[2]))
        Log.d(TAG, "║  sandDry      = (%.3f, %.3f, %.3f)".format(sandDry[0], sandDry[1], sandDry[2]))
        Log.d(TAG, "╠$sep")
        Log.d(TAG, "║ [PIXEL TRACE  distToWater=0, shoreNoise=0  (waterline centre, crest)]")
        Log.d(TAG, "║  dist(XZ)=%.1f m  dNorm=%.3f  depthBlend=%.3f".format(distXZ_wl, dNorm_wl, depthBlend_wl))
        Log.d(TAG, "║  NdotV=%.3f  Fresnel=%.3f  fresMix=%.3f".format(NdotV_wl, fres_wl, fMix))
        Log.d(TAG, "║  water(base)     = (%.3f, %.3f, %.3f)  ← shallowColor (depthBlend≈0)".format(sc[0], sc[1], sc[2]))
        Log.d(TAG, "║  after crest×%.3f = (%.3f, %.3f, %.3f)  troughDepth=0→억제됨".format(crestFac, wCr[0], wCr[1], wCr[2]))
        Log.d(TAG, "║  after diffuse×%.3f = (%.3f, %.3f, %.3f)".format(dFac, cA[0], cA[1], cA[2]))
        Log.d(TAG, "║  foam mix=%.3f   → (%.3f, %.3f, %.3f)".format(fM, cB[0], cB[1], cB[2]))
        Log.d(TAG, "║  fres target     = (%.3f, %.3f, %.3f)".format(fTgt[0], fTgt[1], fTgt[2]))
        Log.d(TAG, "║  after Fresnel   = (%.3f, %.3f, %.3f)  [yunseul=0, skyRefl=0 at this dist]".format(cC[0], cC[1], cC[2]))
        Log.d(TAG, "║  [shoreAlpha 계산]  edge0=%.1f  edge1=%.1f  distToWater=0.0".format(shoreE0, shoreE1))
        Log.d(TAG, "║    t=%.3f  1-smoothstep(t)=%.3f  ← 바다메시 투명도 (하늘색 혼합 아님!)".format(shoreT_wl, aWl))
        Log.d(TAG, "║    COMPOSITED=beach×%.3f + ocean×%.3f = (%.3f, %.3f, %.3f)".format(1f-aWl, aWl, comp[0], comp[1], comp[2]))
        Log.d(TAG, "║    skyReflect: distNorm=%.3f → smoothstep(0.58,0.92)=0  ← 수평선 먼바다만 적용".format(dNorm_wl))
        Log.d(TAG, "║  horizonColor    = (%.3f, %.3f, %.3f)  ← 비교용 하늘색 (skyReflect 경로)".format(hc[0], hc[1], hc[2]))
        Log.d(TAG, "╠$sep")

        // ── Far-ocean scan: distNorm 0.55–0.95  (skyReflect active zone) ─────────
        // Camera at eyePos=(0,1.8,18). Reference point: X=0, Z=eyeZ-dist (straight ahead).
        // For each distNorm sample: decompose final color into 3 additive contributions:
        //   (A) water base × diffuse  (B) skyReflect Δ  (C) yunseul Δ (in corridor centre)
        Log.d(TAG, "║ [FAR-OCEAN SCAN  skyReflect×0.28 (was 0.38×1.05)  pathLight×0.22]")
        Log.d(TAG, "║  horizonColor = (%.3f, %.3f, %.3f)  deepColor = (%.3f, %.3f, %.3f)  (no ×1.05)".format(
            hc[0], hc[1], hc[2], deepColor[0], deepColor[1], deepColor[2]))
        Log.d(TAG, "║  roughness=%.3f  yunseulStr=%.3f  (foam≈0 all: shoreBand→0 far from shore)".format(roughness, yunseulStr))

        // Sun corridor geometry: perpendicular direction to light XZ projection
        val lhLen2  = sqrt(Lx*Lx + Lz*Lz).coerceAtLeast(1e-6f)
        val lhNX2   = Lx / lhLen2       // normalized light X in XZ plane
        val lhNZ2   = Lz / lhLen2       // normalized light Z in XZ plane
        // perpXZ = vec2(-lhDir.y, lhDir.x) in GLSL vec2(X,Z) notation
        val perpXscan = -lhNZ2          // corridor perpendicular: world-X component
        val perpZscan =  lhNX2          // corridor perpendicular: world-Z component
        Log.d(TAG, "║  lightXZ = (%.3f, %.3f)  corrPerpXZ = (%.3f, %.3f)".format(lhNX2, lhNZ2, perpXscan, perpZscan))
        Log.d(TAG, "║")
        Log.d(TAG, "║  dNorm | dist |skyRefl| corrMask |corrBoost| yunseul(in)| water base        | +skyRefl          | +yunseul(in)      | Δlum_sky | Δlum_yu")

        val scanDN = floatArrayOf(0.55f, 0.65f, 0.75f, 0.85f, 0.92f, 0.97f)
        for (dn in scanDN) {
            val dist   = 68f * dn
            val wZscan = eyePos[2] - dist   // world Z directly ahead of camera
            val wYscan = tideY              // flat water surface

            // View vector V = normalize(eyePos - worldPoint)
            val vxSc = eyePos[0]; val vySc = eyePos[1] - wYscan; val vzSc = eyePos[2] - wZscan
            val vlSc = sqrt(vxSc*vxSc + vySc*vySc + vzSc*vzSc).coerceAtLeast(1e-6f)
            val VxSc = vxSc/vlSc; val VySc = vySc/vlSc; val VzSc = vzSc/vlSc

            // Half-vector H = normalize(L + V), NdotH = H.y (N=(0,1,0))
            val hxSc = Lx+VxSc; val hySc = Ly+VySc; val hzSc = Lz+VzSc
            val hlSc = sqrt(hxSc*hxSc + hySc*hySc + hzSc*hzSc).coerceAtLeast(1e-6f)
            val NdotHsc = (hySc/hlSc).coerceAtLeast(0f)

            // Specular contributions
            val sheenSc = Math.pow(NdotHsc.toDouble(), sheenExp.toDouble()).toFloat()
            val fineExpSc = 280f - (280f - 100f) * roughness
            val glintSc = Math.pow(NdotHsc.toDouble(), fineExpSc.toDouble()).toFloat() *
                          (0.5f + 0.5f * 1f)  // favour crest (waveH=+1 assumed)
            val sparkleSc = sheenSc * (0.45f - dn * 0.20f) + glintSc * (0.50f + dn * 1.40f)

            // Sun corridor: point is at X=0, Z=wZscan
            // toFrag = (worldX-camX, worldZ-camZ) = (0, wZscan-18) = (0, -dist)
            val perpDistSc = kotlin.math.abs((-dist) * perpZscan)  // = dist*|lhNX2|
            val corrHalfSc = 4f
            val corrMaskSc  = kotlin.math.exp((-perpDistSc*perpDistSc/(corrHalfSc*corrHalfSc)).toDouble()).toFloat()
            val corrSharpSc = corrMaskSc * corrMaskSc
            // corrBoost IN corridor (corrSharp=1) vs at this actual scan point
            val corrBoostIn  = 0.05f + 1f          * (0.50f + dn * 1.20f)
            val corrBoostAct = 0.05f + corrSharpSc  * (0.50f + dn * 1.20f)

            // yunseul: lightColor * yunseulStr * sparkle * corrBoost * deepZone(≈1 far)
            val lBrt = (lc[0] + lc[1] + lc[2]) / 3f
            val yunBrtIn  = lBrt * yunseulStr * sparkleSc * corrBoostIn
            val yunBrtAct = lBrt * yunseulStr * sparkleSc * corrBoostAct

            // skyReflect: reduced to 0.28 (was 0.38), no ×1.05
            val skyReflSc = smoothstep(0.58f, 0.92f, dn)
            val skyBlendSc = skyReflSc * 0.28f   // (1-foam)≈1

            // Depth blend — new formula: shoreZ/100 single driver, no crossover
            val distToWaterSc_f = wZscan - wzZ   // negative = seaward
            val shoreZsc  = (-distToWaterSc_f / 100f).coerceIn(0f, 1f)
            val sBsc      = smoothstep(0f, 1f, shoreZsc)
            val depthBlendSc = smoothstep(0.05f, 0.95f, sBsc + tide * 0.15f + dn * 0.08f)
            val wRsc = shallowColor[0] + depthBlendSc * (deepColor[0] - shallowColor[0])
            val wGsc = shallowColor[1] + depthBlendSc * (deepColor[1] - shallowColor[1])
            val wBsc = shallowColor[2] + depthBlendSc * (deepColor[2] - shallowColor[2])
            val NdotLsc = Ly.coerceAtLeast(0f)
            val dFsc = NdotLsc * 0.44f + 0.56f
            val bR = wRsc * dFsc; val bG = wGsc * dFsc; val bB = wBsc * dFsc  // water base

            // After skyReflect (no ×1.05)
            val sR = bR + skyBlendSc * (hc[0] - bR)
            val sG = bG + skyBlendSc * (hc[1] - bG)
            val sB = bB + skyBlendSc * (hc[2] - bB)
            val lumDeltaSky = 0.299f*(sR-bR) + 0.587f*(sG-bG) + 0.114f*(sB-bB)

            // pathLight (corridor only): in-corridor contribution at this distNorm
            val pathLightIn  = 1f * (0.05f + 0.22f * dn)   // corrMask=1 in corridor
            val pathTintR = lc[0] + (1f-lc[0]) * 0.20f*dn; val pathTintG = lc[1] + (1f-lc[1]) * 0.20f*dn; val pathTintB = lc[2] + (1f-lc[2]) * 0.20f*dn
            val pathMix  = pathLightIn.coerceAtMost(0.38f)
            val pInnerR  = bR + 0.22f*(pathTintR-bR); val pInnerG = bG + 0.22f*(pathTintG-bG); val pInnerB = bB + 0.22f*(pathTintB-bB)
            val pR = bR + pathMix*(pInnerR-bR); val pG = bG + pathMix*(pInnerG-bG); val pB = bB + pathMix*(pInnerB-bB)
            val pathDeltaR = pR - bR

            // yunseul delta (in corridor, deepZone=1)
            val yR = lc[0] * yunseulStr * sparkleSc * corrBoostIn
            val yG = lc[1] * yunseulStr * sparkleSc * corrBoostIn
            val yB = lc[2] * yunseulStr * sparkleSc * corrBoostIn
            val lumDeltaYun = 0.299f*yR + 0.587f*yG + 0.114f*yB

            // Foam: shoreBand ≈ 0 far from shore
            val distToWaterSc_loc = wZscan - wzZ
            val shoreBandSc = kotlin.math.exp((kotlin.math.abs(distToWaterSc_loc) * (-0.35)).toDouble()).toFloat()

            // seam: smoothstep(0.90,1.0,dn)*(1-corrMask*0.6)*0.55
            val seamSc = smoothstep(0.90f, 1.0f, dn) * (1f - corrMaskSc * 0.6f) * 0.55f

            Log.d(TAG, "║  %.2f  |%4.0fm |sky%.3f|path+%.3f| (%.3f,%.3f,%.3f)base | +sky→(%.3f,%.3f,%.3f) | Δlum_sky=+%.3f  Δpath_R=+%.3f  Δyun=+%.4f".format(
                dn, dist, skyReflSc, pathDeltaR,
                bR, bG, bB, sR, sG, sB,
                lumDeltaSky, pathDeltaR, lumDeltaYun))
            if (shoreBandSc > 0.001f) {
                Log.d(TAG, "║    ⚠ foam: distToWater=%.1f  shoreBand=%.4f  (not zero!)".format(distToWaterSc_loc, shoreBandSc))
            }
            if (seamSc > 0.01f) {
                Log.d(TAG, "║    seam blend %.3f × horizonColor×0.85 active here".format(seamSc))
            }
        }
        Log.d(TAG, "║")
        Log.d(TAG, "║  NOTE: path+X = pathLight R-channel delta IN sun corridor (corrMask=1)")
        Log.d(TAG, "║        sky+X  = skyReflect colour delta (mix×0.28 toward horizonColor)")
        Log.d(TAG, "╠$sep")

        // ── Depth blend gradient scan ──────────────────────────────────────────────
        // Shows depthBlend at each distance seaward of the waterline.
        val camToWL = eyePos[2] - wzZ   // camera-to-waterline distance
        Log.d(TAG, "║ [DEPTH BLEND SCAN  shoreZ/100 + tideBoost(%.3f) + distBias(×0.08)]".format(tide * 0.15f))
        Log.d(TAG, "║  camera→WL = %.1f m  (ocean starts here)".format(camToWL))
        Log.d(TAG, "║  seaward | dist  | shoreZ | sBlend | depthBlend | waterG  (%.3f→%.3f)".format(shallowColor[1], deepColor[1]))
        for (extra in floatArrayOf(2f, 5f, 10f, 15f, 20f, 30f, 40f, 55f)) {
            val distM  = camToWL + extra
            val dtw    = -extra    // distToWater (negative = seaward)
            val dNormV = (distM / 68f).coerceIn(0f, 1f)
            val szV    = (extra / 100f).coerceIn(0f, 1f)
            val sbV    = smoothstep(0f, 1f, szV)
            val dBlend = smoothstep(0.05f, 0.95f, sbV + tide * 0.15f + dNormV * 0.08f)
            val wG     = shallowColor[1] + dBlend * (deepColor[1] - shallowColor[1])
            Log.d(TAG, "║  %+5.0fm    %5.1fm  %.3f   %.3f   %.3f       %.3f".format(dtw, distM, szV, sbV, dBlend, wG))
        }
        Log.d(TAG, "╠$sep")

        // ── Near-shore multi-point scan: crest × foam contribution breakdown ──────
        // Shows how crest brightening and foam mix vary across dNorm 0.10–0.30
        // (roughly 7–20 m from camera, spanning the waterline zone).
        val sc2 = shallowColor
        Log.d(TAG, "║ [NEAR-SHORE SCAN  crest×0.20 / foam×0.58×(1-crest×0.5) — crest+foam breakdown]")
        Log.d(TAG, "║  dNorm | dist  | depBlend | waterG | +crest | after_dif | +foam | finalG | COMP_G")
        for (dn2 in floatArrayOf(0.10f, 0.15f, 0.20f, 0.25f, 0.30f)) {
            val distM2   = 68f * dn2
            val wZ2      = eyePos[2] - distM2
            val dtw2     = wZ2 - wzZ    // positive=inland, negative=seaward
            val szV2     = (-dtw2 / 100f).coerceIn(0f, 1f)
            val sbV2     = smoothstep(0f, 1f, szV2)
            val dBlend2  = smoothstep(0.05f, 0.95f, sbV2 + tide * 0.15f + dn2 * 0.08f)
            val waterG   = sc2[1] + dBlend2 * (deepColor[1] - sc2[1])
            // crest at maximum (waveH=+1)
            val crest2   = 0.20f
            val wCrG2    = waterG * (1f - crest2) + (waterG * 1.26f + 0.04f) * crest2
            // diffuse (flat N, NdotL=Ly)
            val dFac2    = Ly.coerceAtLeast(0f) * 0.44f + 0.56f
            val colG2    = wCrG2 * dFac2 * (0.87f + 0.13f * 0.5f)  // waveDetail≈0.5
            // foam (max, lacyMask=1)
            val foamAmt2 = 1f * 1f * (0.16f + windS * 0.42f) * 1f  // waveMask=1,lacyMask=1
            val foamMix2 = foamAmt2 * 0.58f * (1f - crest2 * 0.5f)
            val fG2      = colG2 + foamMix2 * (0.98f - colG2)
            // shoreAlpha at this distToWater
            val sAlpha2  = 1f - smoothstep(-0.5f, 3.0f, dtw2)
            val compG2   = sandDry[1] * (1f - sAlpha2) + fG2 * sAlpha2
            Log.d(TAG, "║  %.2f   |%5.1fm | %.3f    | %.3f  | %.3f  | %.3f     | %.3f | %.3f  | %.3f (dtw=%+.1f)".format(
                dn2, distM2, dBlend2, waterG, crest2, colG2, foamMix2, fG2, compG2, dtw2))
        }
        Log.d(TAG, "╠$sep")
        val trailGrdStart = maxOf(1.0f, waveReach * 0.5f + 0.6f)
        Log.d(TAG, "║ [BEACH FOAM through OCEAN EDGE  beach_frag §4 × (1 - shoreAlpha)]")
        Log.d(TAG, "║  waveReach = %.2f m  (t1=%.3f t2=%.3f, no per-column noise)".format(waveReach, t1, t2))
        Log.d(TAG, "║  shorelineMask = smoothstep(waveReach+1.2, 0.0, dtw)  ← peaks at dtw=0")
        Log.d(TAG, "║  trailGrdStart = max(1.0, wR×0.5+0.6) = %.3f m  (staggered after shorelineMask midpoint)".format(trailGrdStart))
        Log.d(TAG, "║  trailGuard    = smoothstep(%.3f, %.3f, dtw)".format(trailGrdStart, trailGrdStart + 2.0f))
        Log.d(TAG, "║  oceanAlpha    = 1 - smoothstep(-0.5, 3.0, dtw)  ← transparent 0→3 m inland")
        Log.d(TAG, "║  dtw  | shoreMask | trailGrd | effective | oceanAlpha | vis×0.38 (trail foam)")
        for (dtw in floatArrayOf(0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f)) {
            val foamMaskB   = smoothstep(waveReach + 1.2f, 0.0f, dtw)
            val trailGuard  = smoothstep(trailGrdStart, trailGrdStart + 2.0f, dtw)
            val effective   = foamMaskB * trailGuard
            val oceanAlpB   = 1f - smoothstep(-0.5f, 3.0f, dtw)
            val visTrail    = effective * (1f - oceanAlpB) * 0.38f
            val flag = if (visTrail > 0.08f) "  ← high" else ""
            Log.d(TAG, "║  %+.1f m   %.3f      %.3f      %.3f       %.3f       %.3f%s".format(
                dtw, foamMaskB, trailGuard, effective, oceanAlpB, visTrail, flag))
        }
        Log.d(TAG, "╚══════════════════════════════════════")
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    // ── Resource cleanup (called from GL thread via TideWatchView.release()) ──

    fun release() {
        if (!glReady) return
        glReady = false
        if (::sky.isInitialized)   sky.release()
        if (::beach.isInitialized) beach.release()
        if (::foam.isInitialized)  foam.release()
        if (::spray.isInitialized) spray.release()
        if (::ocean.isInitialized) ocean.release()
        if (ocProg != 0)           { GLES20.glDeleteProgram(ocProg); ocProg = 0 }
        if (normalMapTex != 0)     { GLES20.glDeleteTextures(1, intArrayOf(normalMapTex), 0); normalMapTex = 0 }
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

