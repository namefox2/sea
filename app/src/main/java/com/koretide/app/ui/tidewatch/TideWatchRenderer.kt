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
    // Visual Z bounds from TidalCalibration — waterline maps linearly from
    // calibVisualMinZ (historical all-time low) to calibVisualMaxZ (all-time high).
    // Default = 서해 calibration so the demo view looks dramatic.
    @Volatile var calibVisualMinZ = -18f
    @Volatile var calibVisualMaxZ =  16f
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
    // Real sun/moon times from KASI API — only used when useDefaultSun=false (station mode)
    @Volatile var sunriseHour  = 6.0f
    @Volatile var sunsetHour   = 18.0f
    @Volatile var moonriseHour = -1f   // < 0 = no data
    @Volatile var moonsetHour  = -1f   // cross-midnight-adjusted (e.g. 25.48 for 01:29 next day)

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

        // Station mode: map clock hour → solar-normalized LUT hour so colors match real sunrise/sunset
        sampleSkyLut(if (useDefaultSun) hour else toSolarLutHour(hour))

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

        // Waterline Z: linear mapping from calibrated tide position to scene Z.
        // tide = calibratedT in [0,1]: 0 → historical all-time low, 1 → all-time high.
        // Per-region calibration (서해 default shown here):
        //   tide=0 → calibVisualMinZ=-18 (갯벌 최대 노출)
        //   tide=1 → calibVisualMaxZ=+16 (바다 가득)
        //   강화 간조(80 cm) calibratedT≈0.06 → Z≈-16.9 ✓
        //   강화 만조(870 cm) calibratedT≈0.87 → Z≈+12   ✓
        //   삼척 간조(110 cm) calibratedT≈0.33 → Z≈-1.0  ✓
        //   삼척 만조(170 cm) calibratedT≈0.58 → Z≈+0.5  ✓
        val waterlineBase = calibVisualMinZ + tide * (calibVisualMaxZ - calibVisualMinZ)
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

    // Write sun/moon direction into pre-allocated lightDir member (no heap allocation)
    private fun computeLightDir(hour: Float) {
        if (useDefaultSun) {
            // Theme mode: hardcoded sunrise=6 sunset=18 so theme hours look correct
            val hourAngle = ((hour - 6f) / 12f) * PI.toFloat()
            val elevation = (sin(hourAngle.toDouble()).toFloat() * 0.8f + 0.1f).coerceAtLeast(0.05f)
            val azimuth   = cos(hourAngle.toDouble()).toFloat()
            val len = sqrt((azimuth * azimuth + elevation * elevation + 0.36f).toDouble()).toFloat()
            lightDir[0] = azimuth / len; lightDir[1] = elevation / len; lightDir[2] = -0.6f / len
            return
        }
        // Station mode: real sun/moon arc from KASI API times
        val isDay = hour in sunriseHour..sunsetHour
        if (isDay) {
            val dayFrac   = (hour - sunriseHour) / (sunsetHour - sunriseHour).coerceAtLeast(0.1f)
            val hourAngle = (dayFrac * PI).toFloat()
            val elevation = (sin(hourAngle.toDouble()).toFloat() * 0.8f + 0.1f).coerceAtLeast(0.05f)
            val azimuth   = cos(hourAngle.toDouble()).toFloat()
            val len = sqrt((azimuth * azimuth + elevation * elevation + 0.36f).toDouble()).toFloat()
            lightDir[0] = azimuth / len; lightDir[1] = elevation / len; lightDir[2] = -0.6f / len
        } else {
            val moonFrac  = moonArcFraction(hour).coerceIn(0f, 1f)
            val hourAngle = (moonFrac * PI).toFloat()
            val elevation = (sin(hourAngle.toDouble()).toFloat() * 0.7f + 0.05f).coerceAtLeast(0.02f)
            val azimuth   = cos(hourAngle.toDouble()).toFloat()
            val len = sqrt((azimuth * azimuth + elevation * elevation + 0.36f).toDouble()).toFloat()
            lightDir[0] = azimuth / len; lightDir[1] = elevation / len; lightDir[2] = -0.6f / len
        }
    }

    // 태양 정규화 LUT 시간: [일출, 일몰] → [6, 18], 나머지는 선형 매핑
    private fun toSolarLutHour(clockHour: Float): Float {
        val rise = sunriseHour; val set = sunsetHour
        return when {
            clockHour < rise -> clockHour / rise.coerceAtLeast(0.1f) * 6f
            clockHour <= set -> (clockHour - rise) / (set - rise).coerceAtLeast(0.1f) * 12f + 6f
            else             -> 18f + (clockHour - set) / (24f - set).coerceAtLeast(0.1f) * 6f
        }
    }

    // 달 호(arc)에서의 0..1 위치. 자정을 넘는 월몰은 moonsetHour > 24 로 전달됨.
    private fun moonArcFraction(hour: Float): Float {
        val mr = moonriseHour; val ms = moonsetHour
        if (mr < 0f || ms < 0f) {
            // 폴백: 태양 정오 반대편을 피크로 하는 단순 호
            val solarNoon = (sunriseHour + sunsetHour) / 2f
            val moonMid   = (solarNoon + 12f) % 24f
            val dist      = ((hour - moonMid + 36f) % 24f) - 12f   // [-12, 12]
            return 1f - (kotlin.math.abs(dist) / 12f)
        }
        // ms > 24 이면 자정을 넘는 월몰 (예: 25.48 = 다음날 01:29)
        // 자정 이후 월몰 전 시간대는 hour+24 로 변환해 arc 범위 [mr, ms] 안에 포함
        val adjHour = if (ms > 24f && hour < ms - 24f) hour + 24f else hour
        return if (adjHour < mr || adjHour > ms) -0.05f
        else (adjHour - mr) / (ms - mr)
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

        val lc = dbgLightColor; val hc = dbgHorizonColor; val sc = shallowColor
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
        Log.d(TAG, "║ [COLORS]")
        Log.d(TAG, "║  shallowColor = (%.3f, %.3f, %.3f)  sandDry = (%.3f, %.3f, %.3f)".format(
            sc[0], sc[1], sc[2], sandDry[0], sandDry[1], sandDry[2]))
        Log.d(TAG, "║  deepColor    = (%.3f, %.3f, %.3f)  lightColor = (%.3f, %.3f, %.3f)".format(
            deepColor[0], deepColor[1], deepColor[2], lc[0], lc[1], lc[2]))
        Log.d(TAG, "╠$sep")
        // ── Foam pipeline diagnostic ──────────────────────────────────────────────
        // shader col = (shoreBand, v_Foam, shoreBand×v_Foam), shoreAlpha forced=1
        // shoreBand = exp(-|dtw|×0.40)
        // v_Foam    = ss(tideY+amp×0.45, tideY+amp×1.05, p.y)  [GPU-side, no CPU mirror]
        // minAmp    = windAmp²×0.62 + windAmp×0.18 + 0.06   (always ≥ 0.06 m)
        val wS    = wAmp.coerceIn(0f, 1f)
        val mAmp  = wAmp * wAmp * 0.62f + wAmp * 0.18f + 0.06f
        val tideY = tide * 1.4f - 0.7f + dbgWindSurge
        Log.d(TAG, "║ [FOAM PIPELINE DIAGNOSTIC]")
        Log.d(TAG, "║  windS=%.3f  minAmp=%.4fm  tideY=%.3fm  waterlineZ=%.2fm".format(wS, mAmp, tideY, wzZ))
        Log.d(TAG, "║  v_Foam>0 when p.y > tideY+amp×0.45 = %.4fm".format(tideY + mAmp * 0.45f))
        Log.d(TAG, "║  v_Foam=1 when p.y > tideY+amp×1.05 = %.4fm  (wave crest ceiling=tideY+amp×1.5=%.4fm)".format(
            tideY + mAmp * 1.05f, tideY + mAmp * 1.5f))
        Log.d(TAG, "║  shoreBand=exp(-|dtw|×0.40): dtw -10=%.3f  -5=%.3f  -2=%.3f  0=%.3f  +2=%.3f".format(
            Math.exp(-10.0 * 0.40).toFloat(), Math.exp(-5.0 * 0.40).toFloat(),
            Math.exp(-2.0 * 0.40).toFloat(), 1.0f,
            Math.exp(-2.0 * 0.40).toFloat()))
        Log.d(TAG, "║  shoreWaveMask=ss(0.05,0.65,waveH)  waveH_max_shore≈retain×1.5/1.4")
        val retain = 0.08f + (0.45f - 0.08f) * tide
        val waveHMaxShore = retain * 1.5f / 1.4f
        Log.d(TAG, "║  retain=%.3f  waveH_max_shore≈%.3f  shoreWaveMask≈%.3f".format(
            retain, waveHMaxShore, smoothstep(0.05f, 0.65f, waveHMaxShore)))
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

