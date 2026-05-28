package com.koretide.app.ui.tidewatch.marine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class BlueCrab : MarineCreature() {

    private data class CrabData(
        var x: Float, var y: Float,
        var direction: Int,       // +1 right, -1 left
        var phase: Float,
        var speed: Float,
        var clawOpen: Float = 0f,  // 0=closed, 1=open
        var clawTimer: Float = 0f
    )

    private val crabs = mutableListOf<CrabData>()
    private val shellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val clawPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val legPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.5f; strokeCap = Paint.Cap.ROUND
    }
    private val eyePaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shellPath  = Path()

    override fun onInit() {
        crabs.clear()
        repeat(2) { i ->
            crabs.add(CrabData(
                x = Random.nextFloat() * surfaceW,
                y = surfaceH * 0.67f + i * 15f,
                direction = if (Random.nextBoolean()) 1 else -1,
                phase = Random.nextFloat() * 6f,
                speed = 0.6f + Random.nextFloat() * 0.8f
            ))
        }
    }

    override fun update(animT: Float, seaY: Float, tidePercent: Float) {
        val flatY = seaY - 28f
        for (crab in crabs) {
            crab.phase += 0.12f
            crab.clawTimer += 0.02f
            crab.clawOpen = ((sin(crab.clawTimer.toDouble()) + 1f) / 2f).toFloat()

            // Crabs scuttle sideways — only on tidal flat when tide is low enough
            if (tidePercent < 0.45f) {
                crab.x += crab.direction * crab.speed
                crab.y += (flatY + 15f - crab.y) * 0.02f
            } else {
                // Swim away when tide rises
                crab.y += (flatY - surfaceH * 0.05f - crab.y) * 0.01f
            }

            // Reverse direction at edges
            if (crab.x < -30f || crab.x > surfaceW + 30f) {
                crab.x = if (crab.direction > 0) -20f else surfaceW + 20f
                crab.y = seaY - 30f + Random.nextFloat() * 15f
                crab.speed = 0.5f + Random.nextFloat() * 0.8f
            }
        }
    }

    override fun draw(canvas: Canvas) {
        for (crab in crabs) {
            drawCrab(canvas, crab.x, crab.y, crab.direction, crab.phase, crab.clawOpen, 1f)
        }
    }

    private fun drawCrab(canvas: Canvas, cx: Float, cy: Float, dir: Int, phase: Float,
                          clawOpen: Float, scale: Float) {
        val w = 22f * scale; val h = 14f * scale
        val bobY = sin(phase.toDouble()).toFloat() * 1.5f * scale

        // Shell (꽃게 has distinctive wide carapace with spines)
        shellPaint.color = Color.argb(220, 55, 120, 180) // blue
        shellPath.reset()
        shellPath.moveTo(cx - w, cy + bobY)
        shellPath.cubicTo(cx - w * 1.2f, cy - h + bobY, cx - w * 0.3f, cy - h * 1.2f + bobY, cx, cy - h * 1.1f + bobY)
        shellPath.cubicTo(cx + w * 0.3f, cy - h * 1.2f + bobY, cx + w * 1.2f, cy - h + bobY, cx + w, cy + bobY)
        shellPath.cubicTo(cx + w * 0.6f, cy + h * 0.5f + bobY, cx - w * 0.6f, cy + h * 0.5f + bobY, cx - w, cy + bobY)
        canvas.drawPath(shellPath, shellPaint)

        // Shell markings
        shellPaint.color = Color.argb(80, 100, 180, 220)
        canvas.drawOval(cx - w * 0.5f, cy - h * 0.9f + bobY, cx + w * 0.5f, cy + bobY, shellPaint)

        // Spines on shell edge
        shellPaint.color = Color.argb(220, 40, 100, 160)
        for (i in -2..2) {
            val sx = cx + i * w * 0.45f
            canvas.drawCircle(sx, cy - h * 1.1f + bobY, 2.5f * scale, shellPaint)
        }

        // Walking legs (4 per side)
        legPaint.color = Color.argb(220, 45, 110, 170)
        for (side in listOf(-1, 1)) {
            for (leg in 0..3) {
                val lx0 = cx + side * w * (0.3f + leg * 0.18f)
                val ly0 = cy + bobY
                val legPhase = phase + leg * 0.4f
                val lx1 = lx0 + side * (14f + leg * 4f) * scale
                val ly1 = cy + h * 0.6f + sin(legPhase.toDouble()).toFloat() * 4f * scale + bobY
                canvas.drawLine(lx0, ly0, lx1, ly1, legPaint)
                // lower segment
                canvas.drawLine(lx1, ly1, lx1 + side * 6f * scale, ly1 + 10f * scale, legPaint)
            }
        }

        // Swimming paddle (후방 노 모양 다리)
        legPaint.color = Color.argb(180, 60, 140, 200)
        legPaint.strokeWidth = 4f
        for (side in listOf(-1, 1)) {
            val lx0 = cx + side * w * 0.85f
            canvas.drawLine(lx0, cy + bobY, lx0 + side * 18f * scale, cy + h + bobY, legPaint)
        }
        legPaint.strokeWidth = 2.5f

        // Claws (대형 집게발)
        val clawDir = if (dir > 0) 1f else -1f
        for (side in listOf(-1, 1)) {
            val clawBaseX = cx + side * w * 0.6f
            val clawAngle = (clawOpen * 20f + if (side > 0) 10f else -10f) * dir
            clawPaint.color = Color.argb(220, 55, 120, 180)
            // Upper claw arm
            val armEndX = clawBaseX + side * 24f * scale
            val armEndY = cy - h * 0.2f + bobY
            canvas.drawLine(clawBaseX, cy + bobY, armEndX, armEndY, legPaint.also { it.strokeWidth = 5f; it.color = Color.argb(220, 45, 110, 170) })
            // Movable claw finger
            clawPaint.color = Color.argb(200, 230, 120, 50) // orange tips
            val fingerLen = 12f * scale
            val upperY = armEndY - clawOpen * 8f * scale
            val lowerY = armEndY + (1f - clawOpen) * 8f * scale
            canvas.drawLine(armEndX, armEndY, armEndX + side * fingerLen, upperY, legPaint.also { it.strokeWidth = 3f })
            canvas.drawLine(armEndX, armEndY, armEndX + side * fingerLen, lowerY, legPaint)
        }
        legPaint.strokeWidth = 2.5f
        legPaint.color = Color.argb(220, 45, 110, 170)

        // Eyes on stalks
        eyePaint.color = Color.argb(220, 30, 80, 140)
        canvas.drawCircle(cx - w * 0.3f, cy - h * 1.2f + bobY, 4f * scale, eyePaint)
        canvas.drawCircle(cx + w * 0.3f, cy - h * 1.2f + bobY, 4f * scale, eyePaint)
        eyePaint.color = Color.BLACK
        canvas.drawCircle(cx - w * 0.3f, cy - h * 1.2f + bobY, 2.5f * scale, eyePaint)
        canvas.drawCircle(cx + w * 0.3f, cy - h * 1.2f + bobY, 2.5f * scale, eyePaint)
        eyePaint.color = Color.WHITE
        canvas.drawCircle(cx - w * 0.25f, cy - h * 1.25f + bobY, 1f * scale, eyePaint)
        canvas.drawCircle(cx + w * 0.35f, cy - h * 1.25f + bobY, 1f * scale, eyePaint)
        eyePaint.color = Color.BLACK
    }
}
