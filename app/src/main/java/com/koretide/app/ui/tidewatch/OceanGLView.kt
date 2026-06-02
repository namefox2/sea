package com.koretide.app.ui.tidewatch

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import com.koretide.app.theme.ThemeConfig
import kotlin.math.PI

class OceanGLView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val renderer = OceanRenderer(context.applicationContext)

    var themeConfig: ThemeConfig? = null
        set(value) { field = value; value?.let { renderer.applyTheme(it) } }

    var windDirectionDeg: Float = 225f
        set(value) { field = value; renderer.windDir = (value * PI / 180.0).toFloat() }

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setTide(percent: Float) { renderer.tidePct = percent.coerceIn(0f, 1f) }
    fun setWind(bft: Int)       { renderer.windBft = bft.coerceIn(0, 12).toFloat() }
    // onPause() / onResume() — inherited from GLSurfaceView
}
