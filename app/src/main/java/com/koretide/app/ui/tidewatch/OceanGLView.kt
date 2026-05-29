package com.koretide.app.ui.tidewatch

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import com.koretide.app.theme.ThemeConfig

class OceanGLView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val renderer = OceanRenderer(context.applicationContext)

    var themeConfig: ThemeConfig? = null
        set(value) {
            field = value
            if (value != null) queueEvent { renderer.applyTheme(value) }
        }

    fun setTide(percent: Float) {
        renderer.tidePct = percent.coerceIn(0f, 1f)
    }

    fun setWind(beaufort: Int) {
        renderer.windBft = beaufort.toFloat().coerceIn(0f, 12f)
    }

    var windDirectionDeg: Float
        get() = Math.toDegrees(renderer.windDir.toDouble()).toFloat()
        set(value) { renderer.windDir = Math.toRadians(value.toDouble()).toFloat() }

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}
