package com.koretide.app.ui.tidewatch.marine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.sin
import kotlin.random.Random

class Clam : MarineCreature() {

    private data class ClamData(
        val x: Float, val y: Float,
        var openAngle: Float = 0f,
        var openDir: Float = 1f,
        var timer: Float = Random.nextFloat() * 200f,
        val scale: Float = 0.7f + Random.nextFloat() * 0.6f,
        val rotation: Float = -30f + Random.nextFloat() * 60f,
        val shellColor: Int
    )

    private val clams = mutableListOf<ClamData>()

    private val shellPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ridgePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1f
    }
    private val innerPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 0, 0, 0); style = Paint.Style.FILL
    }
    private val topShell = Path()
    private val bottomShell = Path()
    private val oval = RectF()

    // 조개 species colours: 백합 (white), 홍합 (dark), 가리비 (orange), 바지락 (grey-brown)
    private val clamColors = listOf(
        Color.argb(240, 230, 225, 210), // 백합 (white clam)
        Color.argb(240, 80, 60, 70),    // 홍합 (mussel, dark)
        Color.argb(240, 200, 140, 80),  // 가리비 (scallop, orange)
        Color.argb(240, 160, 145, 120)  // 바지락 (short-neck clam, grey-brown)
    )

    override fun onInit() {
        clams.clear()
        val count = 4 + Random.nextInt(4)
        repeat(count) { i ->
            val col = Random.nextInt(surfaceW.toInt()).toFloat()
            clams.add(ClamData(
                x = col,
                y = surfaceH * 0.67f + Random.nextFloat() * surfaceH * 0.04f,
                shellColor = clamColors[Random.nextInt(clamColors.size)]
            ))
        }
    }

    override fun update(animT: Float, seaY: Float, tidePercent: Float) {
        for (clam in clams) {
            clam.timer += 1f
            // Slowly open and close every ~5 seconds
            val cycle = sin((clam.timer * 0.015f).toDouble()).toFloat()
            clam.openAngle = ((cycle + 1f) / 2f * 35f)
        }
    }

    override fun draw(canvas: Canvas) {
        // Only draw when tidal flat is exposed
        for (clam in clams) {
            canvas.save()
            canvas.rotate(clam.rotation, clam.x, clam.y)
            drawClam(canvas, clam.x, clam.y, clam.openAngle, clam.scale, clam.shellColor)
            canvas.restore()
        }
    }

    private fun drawClam(canvas: Canvas, cx: Float, cy: Float, openAngle: Float, scale: Float, color: Int) {
        val w = 20f * scale; val h = 14f * scale
        val gap = openAngle * 0.3f * scale

        // Shadow under clam
        canvas.drawOval(cx - w, cy + h * 0.2f, cx + w, cy + h * 0.8f, shadowPaint)

        // Bottom shell (stays flat)
        shellPaint.color = color
        oval.set(cx - w, cy - h * 0.1f, cx + w, cy + h)
        canvas.drawArc(oval, 0f, 180f, false, shellPaint)

        // Ridges on bottom shell
        ridgePaint.color = Color.argb(80, 0, 0, 0)
        for (i in 1..5) {
            val f = i / 6f
            oval.set(cx - w * f, cy - h * 0.1f * f, cx + w * f, cy + h * f)
            canvas.drawArc(oval, 0f, 180f, false, ridgePaint)
        }

        // Inner flesh (visible when open)
        if (openAngle > 5f) {
            innerPaint.color = Color.argb(200, 255, 200, 160)
            oval.set(cx - w * 0.8f, cy - h * 0.05f, cx + w * 0.8f, cy + h * 0.7f)
            canvas.drawArc(oval, 0f, 180f, false, innerPaint)
            // Mantle edge
            innerPaint.color = Color.argb(150, 220, 150, 130)
            oval.set(cx - w * 0.8f, cy - h * 0.05f, cx + w * 0.8f, cy + h * 0.7f)
            canvas.drawArc(oval, 5f, 170f, false, innerPaint)
        }

        // Top shell (opens upward)
        canvas.save()
        canvas.rotate(-openAngle, cx, cy)
        shellPaint.color = darken(color, 0.85f)
        oval.set(cx - w, cy - h, cx + w, cy + h * 0.1f)
        canvas.drawArc(oval, 180f, 180f, false, shellPaint)
        // Ridges on top shell
        ridgePaint.color = Color.argb(80, 0, 0, 0)
        for (i in 1..5) {
            val f = i / 6f
            oval.set(cx - w * f, cy - h * f, cx + w * f, cy + h * 0.1f * f)
            canvas.drawArc(oval, 180f, 180f, false, ridgePaint)
        }
        canvas.restore()

        // Hinge dot
        shellPaint.color = darken(color, 0.7f)
        canvas.drawCircle(cx, cy, 3f * scale, shellPaint)
    }

    private fun darken(color: Int, factor: Float): Int {
        return Color.argb(
            Color.alpha(color),
            (Color.red(color) * factor).toInt(),
            (Color.green(color) * factor).toInt(),
            (Color.blue(color) * factor).toInt()
        )
    }
}
