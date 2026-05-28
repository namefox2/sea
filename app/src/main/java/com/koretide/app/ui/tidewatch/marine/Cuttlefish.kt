package com.koretide.app.ui.tidewatch.marine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import kotlin.random.Random

class Cuttlefish : MarineCreature() {

    private data class OctopusData(
        var x: Float, var y: Float,
        var phase: Float, var speed: Float,
        var colorPhase: Float = 0f,
        var direction: Int = 1, // 1=right, -1=left (jet propulsion direction)
        var pulsing: Float = 0f
    )

    private val octopi = mutableListOf<OctopusData>()
    private val bodyPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tentaclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3.5f; strokeCap = Paint.Cap.ROUND
    }
    private val spotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val eyePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tentaclePath = Path()
    private val oval = RectF()

    override fun onInit() {
        octopi.clear()
        repeat(1 + Random.nextInt(2)) { i ->
            octopi.add(OctopusData(
                x = if (i == 0) -60f else surfaceW + 60f,
                y = surfaceH * 0.75f + Random.nextFloat() * surfaceH * 0.12f,
                phase = Random.nextFloat() * 6f,
                speed = 1.0f + Random.nextFloat() * 0.8f,
                direction = if (i == 0) 1 else -1
            ))
        }
    }

    override fun update(animT: Float, seaY: Float, tidePercent: Float) {
        val swimY = seaY + surfaceH * 0.06f
        for (oct in octopi) {
            oct.phase += 0.06f
            oct.colorPhase += 0.03f
            oct.pulsing = ((sin(oct.phase.toDouble()) + 1f) / 2f).toFloat()

            // Jet propulsion: move against tentacle side (tentacles trail behind)
            oct.x += oct.direction * oct.speed
            oct.y += (swimY + Random.nextFloat() * surfaceH * 0.04f - oct.y) * 0.01f

            // Wrap around screen
            if (oct.x > surfaceW + 80f) {
                oct.x = -70f
                oct.direction = 1
                oct.y = seaY + surfaceH * 0.04f + Random.nextFloat() * surfaceH * 0.15f
                oct.speed = 0.8f + Random.nextFloat() * 1f
            } else if (oct.x < -80f) {
                oct.x = surfaceW + 70f
                oct.direction = -1
                oct.y = seaY + surfaceH * 0.04f + Random.nextFloat() * surfaceH * 0.15f
                oct.speed = 0.8f + Random.nextFloat() * 1f
            }
        }
    }

    override fun draw(canvas: Canvas) {
        for (oct in octopi) {
            drawOctopus(canvas, oct.x, oct.y, oct.phase, oct.pulsing, oct.colorPhase, oct.direction)
        }
    }

    private fun drawOctopus(canvas: Canvas, cx: Float, cy: Float, phase: Float, pulse: Float,
                              colorPhase: Float, dir: Int) {
        val scale = 1f
        // Chromatophore color change: 쭈꾸미 ranges reddish-brown to grey to spotted
        val r = (180 + sin(colorPhase.toDouble()).toFloat() * 60).toInt().coerceIn(80, 240)
        val g = (80 + sin((colorPhase * 0.7f).toDouble()).toFloat() * 40).toInt().coerceIn(30, 140)
        val b = (60 + sin((colorPhase * 1.3f).toDouble()).toFloat() * 30).toInt().coerceIn(20, 120)
        val bodyColor = Color.argb(220, r, g, b)

        // 8 tentacles trailing from body (쭈꾸미 style: shorter arms)
        val tentacleBaseX = cx - dir * 14f * scale
        val tentacleBaseY = cy + 8f * scale
        tentaclePaint.color = Color.argb(200, r - 30, g - 20, b - 20)

        for (i in 0..7) {
            val angle = (i / 8f) * 2f * PI.toFloat() + phase * 0.5f
            val waveOff = sin((phase + i * 0.7f).toDouble()).toFloat()
            val len = (28f + waveOff * 6f) * scale
            val ex = tentacleBaseX + dir * (-cos(angle.toDouble()).toFloat() * len * 0.6f)
            val ey = tentacleBaseY + sin(angle.toDouble()).toFloat() * len

            tentaclePath.reset()
            tentaclePath.moveTo(tentacleBaseX, tentacleBaseY)
            // Suction cups and wave
            val midX = (tentacleBaseX + ex) / 2f + waveOff * 8f
            val midY = (tentacleBaseY + ey) / 2f
            tentaclePath.quadTo(midX, midY, ex, ey)
            tentaclePaint.strokeWidth = 4f - i * 0.3f
            canvas.drawPath(tentaclePath, tentaclePaint)
        }

        // Mantle body (slightly contracted when pulsing = propulsion)
        val bodyH = (32f - pulse * 6f) * scale
        val bodyW = (20f + pulse * 3f) * scale
        val bobY = sin((phase * 1.5f).toDouble()).toFloat() * 3f
        bodyPaint.color = bodyColor
        oval.set(cx - bodyW, cy - bodyH + bobY, cx + bodyW, cy + 4f + bobY)
        canvas.drawOval(oval, bodyPaint)

        // Texture spots (chromatophores)
        spotPaint.color = Color.argb(100, r - 50, g - 30, b)
        for (i in 0..5) {
            val sx = cx + (i - 2.5f) * bodyW * 0.35f
            val sy = cy - bodyH * 0.5f + bobY + (i % 2) * 6f
            canvas.drawCircle(sx, sy, 3f * scale, spotPaint)
        }

        // Eyes (large w-pupil style)
        eyePaint.color = Color.argb(240, 240, 220, 180)
        val eyeOffX = 8f * scale * dir
        canvas.drawCircle(cx + eyeOffX, cy - bodyH * 0.55f + bobY, 6f * scale, eyePaint)
        canvas.drawCircle(cx - eyeOffX, cy - bodyH * 0.55f + bobY, 6f * scale, eyePaint)
        eyePaint.color = Color.argb(200, 30, 20, 60)
        // W-shaped pupil approximated as stretched horizontal oval
        oval.set(cx + eyeOffX - 4f * scale, cy - bodyH * 0.55f + bobY - 2f * scale,
                 cx + eyeOffX + 4f * scale, cy - bodyH * 0.55f + bobY + 2f * scale)
        canvas.drawOval(oval, eyePaint)
        oval.set(cx - eyeOffX - 4f * scale, cy - bodyH * 0.55f + bobY - 2f * scale,
                 cx - eyeOffX + 4f * scale, cy - bodyH * 0.55f + bobY + 2f * scale)
        canvas.drawOval(oval, eyePaint)
        eyePaint.color = Color.WHITE
        canvas.drawCircle(cx + eyeOffX + 2f, cy - bodyH * 0.6f + bobY, 1.5f * scale, eyePaint)
        canvas.drawCircle(cx - eyeOffX + 2f, cy - bodyH * 0.6f + bobY, 1.5f * scale, eyePaint)

        // Ink cloud hint (tiny dark puff behind when moving fast)
        if (pulse > 0.7f) {
            val inkX = cx - dir * (bodyW + 8f)
            spotPaint.color = Color.argb(40, 30, 20, 50)
            canvas.drawCircle(inkX, cy + bobY, 8f * scale * pulse, spotPaint)
        }
    }
}
