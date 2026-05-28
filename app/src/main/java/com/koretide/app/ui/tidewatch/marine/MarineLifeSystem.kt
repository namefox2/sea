package com.koretide.app.ui.tidewatch.marine

import android.graphics.Canvas

data class MarineLifeSettings(
    val fishEnabled: Boolean = true,
    val crabEnabled: Boolean = true,
    val clamEnabled: Boolean = true,
    val cuttlefishEnabled: Boolean = true
)

class MarineLifeSystem {

    private val fishSchool  = FishSchool()
    private val blueCrab    = BlueCrab()
    private val clam        = Clam()
    private val cuttlefish  = Cuttlefish()

    var settings: MarineLifeSettings = MarineLifeSettings()
        set(value) {
            field = value
            fishSchool.visible  = value.fishEnabled
            blueCrab.visible    = value.crabEnabled
            clam.visible        = value.clamEnabled
            cuttlefish.visible  = value.cuttlefishEnabled
        }

    fun onSizeChanged(w: Float, h: Float) {
        fishSchool.onSizeChanged(w, h)
        blueCrab.onSizeChanged(w, h)
        clam.onSizeChanged(w, h)
        cuttlefish.onSizeChanged(w, h)
    }

    fun update(animT: Float, seaY: Float, tidePercent: Float) {
        if (fishSchool.visible)  fishSchool.update(animT, seaY, tidePercent)
        if (blueCrab.visible)    blueCrab.update(animT, seaY, tidePercent)
        if (clam.visible)        clam.update(animT, seaY, tidePercent)
        if (cuttlefish.visible)  cuttlefish.update(animT, seaY, tidePercent)
    }

    fun draw(canvas: Canvas, seaY: Float, tidePercent: Float) {
        drawTidalCreatures(canvas, tidePercent)
        drawWaterCreatures(canvas)
    }

    fun drawTidalCreatures(canvas: Canvas, tidePercent: Float) {
        if (tidePercent < 0.45f) {
            if (clam.visible)     clam.draw(canvas)
            if (blueCrab.visible) blueCrab.draw(canvas)
        }
    }

    fun drawWaterCreatures(canvas: Canvas) {
        if (fishSchool.visible)  fishSchool.draw(canvas)
        if (cuttlefish.visible)  cuttlefish.draw(canvas)
    }
}
