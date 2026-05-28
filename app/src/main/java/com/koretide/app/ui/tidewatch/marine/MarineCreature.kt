package com.koretide.app.ui.tidewatch.marine

import android.graphics.Canvas

abstract class MarineCreature {
    var visible: Boolean = true
    protected var surfaceW: Float = 1f
    protected var surfaceH: Float = 1f

    fun onSizeChanged(w: Float, h: Float) {
        surfaceW = w; surfaceH = h
        onInit()
    }

    abstract fun onInit()
    abstract fun update(animT: Float, seaY: Float, tidePercent: Float)
    abstract fun draw(canvas: Canvas)

    protected fun wrap(x: Float, margin: Float = 80f): Float =
        if (x > surfaceW + margin) -margin else x
}
