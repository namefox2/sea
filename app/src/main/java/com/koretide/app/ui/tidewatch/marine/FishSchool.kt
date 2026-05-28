package com.koretide.app.ui.tidewatch.marine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.sin
import kotlin.random.Random

class FishSchool : MarineCreature() {

    data class FishData(
        var x: Float, var y: Float,
        var phase: Float, var speed: Float,
        val bodyColor: Int, val scale: Float,
        var targetY: Float = 0f
    )

    private val school = mutableListOf<FishData>()
    private val bodyPath = Path()
    private val tailPath = Path()
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val eyePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 255, 255); style = Paint.Style.FILL
    }

    // Several species: 전갱이(고등어), 조기, 도미, 숭어
    private val speciesColors = listOf(
        Color.argb(220, 160, 180, 120), // 전갱이 (greenish silver)
        Color.argb(220, 200, 170, 90),  // 조기 (golden yellow)
        Color.argb(220, 200, 100, 100), // 도미 (pink-red)
        Color.argb(220, 130, 160, 160)  // 숭어 (grey-green)
    )

    override fun onInit() {
        school.clear()
        val baseColor = speciesColors[Random.nextInt(speciesColors.size)]
        val count = 3 + Random.nextInt(3)
        repeat(count) { i ->
            school.add(FishData(
                x = -100f - i * 55f,
                y = surfaceH * 0.8f,
                phase = i * 0.7f,
                speed = 1.8f + Random.nextFloat() * 1.5f,
                bodyColor = baseColor,
                scale = 0.7f + Random.nextFloat() * 0.5f
            ))
        }
    }

    override fun update(animT: Float, seaY: Float, tidePercent: Float) {
        val swimY = seaY + surfaceH * 0.08f
        for (fish in school) {
            fish.x += fish.speed
            fish.phase += 0.09f
            val wobble = sin(fish.phase.toDouble()).toFloat() * 10f
            fish.y += (swimY + wobble - fish.y) * 0.03f

            if (fish.x > surfaceW + 80f) {
                fish.x = -(60f + school.indexOf(fish) * 50f)
                fish.targetY = seaY + surfaceH * 0.05f + Random.nextFloat() * surfaceH * 0.2f
                fish.speed = 1.5f + Random.nextFloat() * 2f
                // Occasionally switch species colour
                if (Random.nextFloat() < 0.3f) {
                    val c = speciesColors[Random.nextInt(speciesColors.size)]
                    school[school.indexOf(fish)] = fish.copy(bodyColor = c)
                }
            }
        }
    }

    override fun draw(canvas: Canvas) {
        for (fish in school) {
            if (fish.x < -90f || fish.x > surfaceW + 90f) continue
            drawFish(canvas, fish.x, fish.y, fish.scale, fish.phase, fish.bodyColor)
        }
    }

    private fun drawFish(canvas: Canvas, x: Float, y: Float, scale: Float, phase: Float, color: Int) {
        val bw = 28f * scale
        val bh = 9f * scale
        val bend = sin(phase.toDouble()).toFloat() * 4f * scale

        bodyPaint.color = color

        // Body
        bodyPath.reset()
        bodyPath.moveTo(x + bw, y + bend)
        bodyPath.cubicTo(x + bw * 0.5f, y - bh + bend * 0.4f,  x - bw * 0.2f, y - bh, x - bw * 0.7f, y)
        bodyPath.cubicTo(x - bw * 0.2f, y + bh,  x + bw * 0.5f, y + bh + bend * 0.4f, x + bw, y + bend)
        canvas.drawPath(bodyPath, bodyPaint)

        // Tail fork
        val twag = sin((phase * 2f).toDouble()).toFloat() * 6f * scale
        tailPath.reset()
        tailPath.moveTo(x - bw * 0.7f, y)
        tailPath.lineTo(x - bw * 1.55f, y - bh + twag)
        tailPath.lineTo(x - bw * 1.2f,  y)
        tailPath.lineTo(x - bw * 1.55f, y + bh + twag)
        tailPath.close()
        canvas.drawPath(tailPath, bodyPaint)

        // Dorsal fin
        bodyPaint.alpha = 160
        tailPath.reset()
        tailPath.moveTo(x + bw * 0.2f, y - bh + bend * 0.4f)
        tailPath.quadTo(x, y - bh * 2f, x - bw * 0.2f, y - bh)
        tailPath.close()
        canvas.drawPath(tailPath, bodyPaint)
        bodyPaint.alpha = 255

        // Scale line
        bodyPaint.color = Color.argb(50, 0, 0, 0)
        bodyPaint.style = Paint.Style.STROKE
        bodyPaint.strokeWidth = 0.8f
        canvas.drawArc(x - bw * 0.3f, y - bh * 0.8f, x + bw * 0.5f, y + bh * 0.8f, -30f, 240f, false, bodyPaint)
        bodyPaint.style = Paint.Style.FILL

        // Eye
        canvas.drawCircle(x + bw * 0.45f, y - bh * 0.1f + bend * 0.3f, 3f * scale, eyePaint)
        eyePaint.color = Color.WHITE
        canvas.drawCircle(x + bw * 0.5f, y - bh * 0.15f + bend * 0.3f, 1.2f * scale, eyePaint)
        eyePaint.color = Color.BLACK

        // Shine
        canvas.drawOval(x + bw * 0.05f, y - bh * 0.6f + bend * 0.2f,
                        x + bw * 0.55f, y + bend * 0.2f, shinePaint)
    }
}
