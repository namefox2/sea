package com.koretide.app.ui.tidewatch

import android.content.Context
import android.graphics.Color
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import com.koretide.app.theme.ThemeConfig

class TideWatchView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val renderer = TideWatchRenderer(context.applicationContext)

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setPreserveEGLContextOnPause(true)  // prevent shader recompile on every navigation
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    var themeConfig: ThemeConfig? = null
        set(value) {
            field = value
            value?.let { theme ->
                fun Int.r() = Color.red(this) / 255f
                fun Int.g() = Color.green(this) / 255f
                fun Int.b() = Color.blue(this) / 255f
                // Hour that best represents this theme's time-of-day for the default (no-station) view
                val themeHour = when (theme.id) {
                    "AUTUMN_LIGHT" -> 18.0f  // 노을해안 → sunset
                    "SUMMER_DARK"  -> 22.0f  // 밤바다 → night
                    else           -> 12.0f  // 맑은 낮바다 → noon
                }
                // Centred sun/moon + 윤슬 color for themed presets
                val centre = theme.id == "AUTUMN_LIGHT" || theme.id == "SUMMER_DARK"
                // Silver moonlight for 밤바다; warm gold for 노을해안; null = LUT-driven
                val lightOverride: FloatArray? = when (theme.id) {
                    "SUMMER_DARK"  -> floatArrayOf(0.72f, 0.80f, 0.95f) // silver moonlight
                    "AUTUMN_LIGHT" -> floatArrayOf(1.00f, 0.62f, 0.22f) // warm sunset gold
                    else           -> null
                }
                queueEvent {
                    renderer.defaultHour        = themeHour
                    renderer.centerLightInView  = centre
                    renderer.lightColorOverride = lightOverride
                    renderer.deepColor    = floatArrayOf(theme.seaTopColor.r(),    theme.seaTopColor.g(),    theme.seaTopColor.b())
                    renderer.shallowColor = floatArrayOf(theme.seaBottomColor.r(), theme.seaBottomColor.g(), theme.seaBottomColor.b())
                    val flat = theme.tidalFlatColor
                    renderer.sandDry  = floatArrayOf(
                        (flat.r() * 0.85f + 0.15f).coerceAtMost(1f),
                        (flat.g() * 0.85f + 0.12f).coerceAtMost(1f),
                        (flat.b() * 0.80f + 0.08f).coerceAtMost(1f))
                    renderer.sandWet  = floatArrayOf(flat.r(), flat.g(), flat.b())
                    renderer.skyHorizonTheme = floatArrayOf(theme.skyBottomColor.r(), theme.skyBottomColor.g(), theme.skyBottomColor.b())
                    renderer.skyZenithTheme  = floatArrayOf(theme.skyTopColor.r(),    theme.skyTopColor.g(),    theme.skyTopColor.b())
                    renderer.sunColorTheme   = floatArrayOf(theme.sunColor.r(),       theme.sunColor.g(),       theme.sunColor.b())
                }
            }
        }

    var windDirectionDeg: Float = 225f
        set(value) {
            field = value
            queueEvent { renderer.windDirRad = Math.toRadians(value.toDouble()).toFloat() }
        }

    fun setTide(calibratedT: Float) { queueEvent { renderer.tidePercent = calibratedT.coerceIn(0f, 1f) } }
    fun setWind(bft: Int)           { queueEvent { renderer.windAmp = (bft.coerceIn(0, 12) / 12f) } }
    fun setHasStation(has: Boolean) { queueEvent { renderer.useDefaultSun = !has } }
    fun setMudflatExposure(exposure: Float) { queueEvent { renderer.mudflatExposure = exposure.coerceIn(0f, 1f) } }
    /** Sets the visual Z bounds from TidalCalibration — call whenever the region changes. */
    fun setVisualRange(minZ: Float, maxZ: Float) {
        queueEvent {
            renderer.calibVisualMinZ = minZ
            renderer.calibVisualMaxZ = maxZ
        }
    }

    // Reset preset: noon sun, bft-2 wind, 35% tide, clean sandy beach (no mudflat).
    // Does NOT override calibVisualMinZ/MaxZ so regional range stays correct.
    fun applyImmersivePreset() {
        queueEvent {
            renderer.useDefaultSun   = true
            renderer.defaultHour     = 12.0f
            renderer.tidePercent     = 0.35f
            renderer.windAmp         = 2f / 12f
            renderer.mudflatExposure = 0f    // always clean beach regardless of region
        }
    }

    fun requestDebugDump() { renderer.debugDumpRequested = true }

    val initError: String? get() = renderer.initError

    // Called from Fragment.onDestroyView to free GPU resources while context is still alive
    fun release() {
        queueEvent { renderer.release() }
    }

    // onPause() / onResume() are inherited from GLSurfaceView — TideWatchFragment calls them directly
}
