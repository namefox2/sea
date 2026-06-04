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
                queueEvent {
                    renderer.defaultHour  = themeHour
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

    fun setTide(percent: Float)  { queueEvent { renderer.tidePercent = percent.coerceIn(0f, 1f) } }
    fun setWind(bft: Int)        { queueEvent { renderer.windAmp = (bft.coerceIn(0, 12) / 12f) } }
    fun setHasStation(has: Boolean) { queueEvent { renderer.useDefaultSun = !has } }

    val initError: String? get() = renderer.initError

    // Called from Fragment.onDestroyView to free GPU resources while context is still alive
    fun release() {
        queueEvent { renderer.release() }
    }

    // onPause() / onResume() are inherited from GLSurfaceView — TideWatchFragment calls them directly
}
