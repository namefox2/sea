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
                queueEvent {
                    renderer.deepColor    = floatArrayOf(theme.seaBottomColor.r(), theme.seaBottomColor.g(), theme.seaBottomColor.b())
                    renderer.shallowColor = floatArrayOf(theme.seaTopColor.r(),    theme.seaTopColor.g(),    theme.seaTopColor.b())
                    val flat = theme.tidalFlatColor
                    renderer.sandDry  = floatArrayOf(
                        (flat.r() * 0.85f + 0.15f).coerceAtMost(1f),
                        (flat.g() * 0.85f + 0.12f).coerceAtMost(1f),
                        (flat.b() * 0.80f + 0.08f).coerceAtMost(1f))
                    renderer.sandWet  = floatArrayOf(flat.r(), flat.g(), flat.b())
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

    val initError: String? get() = renderer.initError

    // onPause() / onResume() are inherited from GLSurfaceView — TideWatchFragment calls them directly
}
