package com.koretide.app.ui.detail

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.koretide.app.R
import kotlin.math.sin

class WaterLevelView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var tidePercent: Float = 0f
    private var animPercent: Float = 0f
    private var animator: ValueAnimator? = null
    private var animT: Float = 0f

    private val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2196F3.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    private val lowLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF9800.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    private val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 48f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val wavePath = Path()

    private val waveRunnable = object : Runnable {
        override fun run() {
            animT += 0.05f
            invalidate()
            postDelayed(this, 16)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(waveRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(waveRunnable)
        animator?.cancel()
    }

    fun setTidePercent(percent: Float) {
        tidePercent = percent.coerceIn(0f, 1f)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(animPercent, tidePercent).apply {
            duration = 1200
            interpolator = DecelerateInterpolator()
            addUpdateListener { animPercent = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val waterY = h * (1f - animPercent)

        // Gradient for water
        waterPaint.shader = LinearGradient(
            0f, waterY, 0f, h,
            0xFF0288D1.toInt(), 0xFF01579B.toInt(),
            Shader.TileMode.CLAMP
        )

        // Wavy surface path
        wavePath.reset()
        wavePath.moveTo(0f, h)
        wavePath.lineTo(0f, waterY)
        var x = 0f
        while (x <= w) {
            val y = waterY + sin((x / w * 4 * Math.PI + animT).toFloat()) * (h * 0.015f)
            wavePath.lineTo(x, y)
            x += 4f
        }
        wavePath.lineTo(w, h)
        wavePath.close()
        canvas.drawPath(wavePath, waterPaint)

        // Reference lines
        val highY = h * 0.1f
        val lowY  = h * 0.9f
        canvas.drawLine(0f, highY, w, highY, highLinePaint)
        canvas.drawLine(0f, lowY,  w, lowY,  lowLinePaint)

        // Percentage text
        if (waterY < h * 0.5f) {
            canvas.drawText("${(animPercent * 100).toInt()}%", w / 2, waterY + 60f, percentPaint)
        } else {
            percentPaint.color = 0xFF1565C0.toInt()
            canvas.drawText("${(animPercent * 100).toInt()}%", w / 2, waterY - 20f, percentPaint)
            percentPaint.color = 0xFFFFFFFF.toInt()
        }
    }
}
