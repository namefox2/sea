package com.koretide.app.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.koretide.app.domain.model.Station
import com.koretide.app.domain.model.TideStatus
import kotlin.math.abs

class KoreaMapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class StationPin(
        val station: Station,
        val tidePercent: Float = 0.5f,
        val tideStatus: TideStatus? = null
    )

    var pins: List<StationPin> = emptyList()
        set(value) { field = value; invalidate() }

    var onPinClick: ((Station) -> Unit)? = null
    var selectedCode: String? = null
        set(value) { field = value; invalidate() }

    private val landPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D4E8C2")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6A8F4A")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pinBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val selectedPinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }

    // Korean peninsula bounding box (approximate)
    private val LAT_MIN = 33.0
    private val LAT_MAX = 38.6
    private val LNG_MIN = 125.0
    private val LNG_MAX = 130.0

    private var scaleX = 1f
    private var scaleY = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    private val koreaPath = Path()
    private var pathDirty = true

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padH = w * 0.1f
        val padV = h * 0.1f
        scaleX = (w - 2 * padH) / (LNG_MAX - LNG_MIN).toFloat()
        scaleY = (h - 2 * padV) / (LAT_MAX - LAT_MIN).toFloat()
        offsetX = padH
        offsetY = padV
        pathDirty = true
    }

    private fun buildKoreaPath() {
        if (!pathDirty) return
        koreaPath.reset()
        // Simplified Korean peninsula outline as control points
        val pts = arrayOf(
            // West coast (top to bottom)
            floatArrayOf(126.6f, 37.7f), floatArrayOf(126.4f, 37.5f), floatArrayOf(126.3f, 37.2f),
            floatArrayOf(126.1f, 37.0f), floatArrayOf(126.5f, 36.5f), floatArrayOf(126.8f, 36.0f),
            floatArrayOf(126.5f, 35.7f), floatArrayOf(126.4f, 35.3f), floatArrayOf(126.4f, 34.8f),
            // South coast
            floatArrayOf(126.8f, 34.5f), floatArrayOf(127.5f, 34.3f), floatArrayOf(128.5f, 34.5f),
            floatArrayOf(129.1f, 35.0f),
            // East coast
            floatArrayOf(129.4f, 35.5f), floatArrayOf(129.5f, 36.0f), floatArrayOf(129.4f, 36.5f),
            floatArrayOf(129.2f, 37.2f), floatArrayOf(128.8f, 37.8f), floatArrayOf(128.5f, 38.3f),
            floatArrayOf(128.0f, 38.6f),
            // North border (simplified)
            floatArrayOf(126.6f, 38.3f), floatArrayOf(126.6f, 37.7f)
        )
        koreaPath.moveTo(lngToX(pts[0][0]), latToY(pts[0][1]))
        for (i in 1 until pts.size) {
            koreaPath.lineTo(lngToX(pts[i][0]), latToY(pts[i][1]))
        }
        koreaPath.close()
        pathDirty = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        buildKoreaPath()
        canvas.drawPath(koreaPath, landPaint)
        canvas.drawPath(koreaPath, borderPaint)

        // Draw Jeju separately
        canvas.drawOval(
            RectF(lngToX(126.1f) - 20, latToY(33.6f) - 10, lngToX(126.9f) + 20, latToY(33.1f) + 10),
            landPaint
        )

        for (pin in pins) {
            val x = lngToX(pin.station.lng.toFloat())
            val y = latToY(pin.station.lat.toFloat())
            val r = if (pin.station.code == selectedCode) 14f else 10f

            pinPaint.color = when (pin.tideStatus) {
                TideStatus.RISING, TideStatus.HIGH_TIDE -> Color.parseColor("#2196F3")
                TideStatus.FALLING, TideStatus.LOW_TIDE -> Color.parseColor("#FF9800")
                null -> Color.parseColor("#9E9E9E")
            }
            pinPaint.style = Paint.Style.FILL
            canvas.drawCircle(x, y, r, pinPaint)
            canvas.drawCircle(x, y, r, pinBorderPaint)

            if (pin.station.code == selectedCode) {
                canvas.drawCircle(x, y, r + 3, selectedPinPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val touchX = event.x
            val touchY = event.y
            val hit = pins.firstOrNull { pin ->
                val px = lngToX(pin.station.lng.toFloat())
                val py = latToY(pin.station.lat.toFloat())
                abs(px - touchX) < 30f && abs(py - touchY) < 30f
            }
            if (hit != null) {
                onPinClick?.invoke(hit.station)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun lngToX(lng: Float): Float =
        offsetX + (lng - LNG_MIN).toFloat() * scaleX

    private fun latToY(lat: Float): Float =
        height - offsetY - (lat - LAT_MIN).toFloat() * scaleY
}
