package com.koretide.app.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.koretide.app.domain.model.Station
import com.koretide.app.domain.model.TideStatus
import kotlin.math.sqrt

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
        color = Color.parseColor("#C8DFB0"); style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5A7A3A"); style = Paint.Style.STROKE; strokeWidth = 1.8f
    }
    private val seaLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7BAE"); textSize = 32f; textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val cityLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333"); textSize = 19f; textAlign = Paint.Align.CENTER
    }
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pinBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val selectedRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700"); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val pinLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111111"); textSize = 17f; textAlign = Paint.Align.CENTER
    }
    private val dmzPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AA555555"); style = Paint.Style.STROKE; strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }

    private val LAT_MIN = 32.8
    private val LAT_MAX = 38.8
    private val LNG_MIN = 124.6
    private val LNG_MAX = 130.2

    private var scX = 1f
    private var scY = 1f
    private var offX = 0f
    private var offY = 0f

    private val peninsulaPath = Path()
    private val jejuPath = Path()
    private var pathDirty = true

    init { isClickable = true; isFocusable = true }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padH = w * 0.10f; val padV = h * 0.08f
        scX = (w - 2 * padH) / (LNG_MAX - LNG_MIN).toFloat()
        scY = (h - 2 * padV) / (LAT_MAX - LAT_MIN).toFloat()
        offX = padH; offY = padV
        pathDirty = true
    }

    private fun buildPaths() {
        if (!pathDirty) return
        // Korean peninsula outline (clockwise, approximate)
        val pts = listOf(
            126.0f to 38.3f, 126.5f to 38.4f, 127.2f to 38.45f,
            127.8f to 38.5f, 128.3f to 38.4f, 128.6f to 38.3f,
            // East coast S
            128.8f to 37.9f, 129.1f to 37.4f, 129.3f to 37.1f,
            129.5f to 36.7f, 129.5f to 36.4f, 129.4f to 36.0f,
            129.4f to 35.6f, 129.3f to 35.3f, 129.1f to 35.1f,
            // South coast E→W
            128.8f to 34.9f, 128.4f to 34.7f, 128.0f to 34.55f,
            127.7f to 34.45f, 127.3f to 34.42f, 127.0f to 34.5f,
            126.7f to 34.55f, 126.5f to 34.6f, 126.3f to 34.7f,
            126.4f to 34.9f,
            // West coast N
            126.5f to 35.1f, 126.5f to 35.45f, 126.4f to 35.65f,
            126.3f to 35.85f, 126.55f to 36.1f, 126.65f to 36.4f,
            126.5f to 36.65f, 126.35f to 36.95f, 126.5f to 37.15f,
            126.65f to 37.35f, 126.5f to 37.55f, 126.2f to 37.65f,
            125.95f to 37.75f, 125.8f to 38.05f, 125.9f to 38.2f,
            126.0f to 38.3f
        )
        peninsulaPath.reset()
        peninsulaPath.moveTo(lngToX(pts[0].first), latToY(pts[0].second))
        for (i in 1 until pts.size) {
            peninsulaPath.lineTo(lngToX(pts[i].first), latToY(pts[i].second))
        }
        peninsulaPath.close()

        // Jeju Island
        jejuPath.reset()
        val cx = lngToX(126.55f); val cy = latToY(33.38f)
        val rx = scX * 0.44f; val ry = scY * 0.24f
        jejuPath.addOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW)
        pathDirty = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        buildPaths()

        canvas.drawPath(peninsulaPath, landPaint)
        canvas.drawPath(peninsulaPath, borderPaint)
        canvas.drawPath(jejuPath, landPaint)
        canvas.drawPath(jejuPath, borderPaint)

        // DMZ dashed line
        canvas.drawLine(lngToX(126.0f), latToY(38.3f), lngToX(128.6f), latToY(38.3f), dmzPaint)

        // Sea labels
        seaLabelPaint.textSize = (width * 0.055f).coerceIn(22f, 40f)
        canvas.drawText("서  해", lngToX(125.4f), latToY(36.5f), seaLabelPaint)
        canvas.drawText("동  해", lngToX(130.0f), latToY(37.0f), seaLabelPaint)
        canvas.drawText("남  해", lngToX(127.6f), latToY(33.85f), seaLabelPaint)

        // City labels
        cityLabelPaint.textSize = (width * 0.042f).coerceIn(15f, 28f)
        canvas.drawText("서울", lngToX(127.0f), latToY(37.6f), cityLabelPaint)
        canvas.drawText("부산", lngToX(129.05f), latToY(35.22f), cityLabelPaint)
        canvas.drawText("강릉", lngToX(128.85f), latToY(37.78f), cityLabelPaint)
        canvas.drawText("광주", lngToX(126.85f), latToY(35.17f), cityLabelPaint)
        canvas.drawText("제주", lngToX(126.55f), latToY(33.22f), cityLabelPaint)

        // Station pins
        for (pin in pins) {
            val x = lngToX(pin.station.lng.toFloat())
            val y = latToY(pin.station.lat.toFloat())
            val isSelected = pin.station.code == selectedCode
            val r = if (isSelected) 12f else 8f

            pinPaint.color = when (pin.tideStatus) {
                TideStatus.RISING, TideStatus.HIGH_TIDE -> Color.parseColor("#1565C0")
                TideStatus.FALLING, TideStatus.LOW_TIDE -> Color.parseColor("#E65100")
                null -> Color.parseColor("#0288D1")
            }
            canvas.drawCircle(x, y, r, pinPaint)
            canvas.drawCircle(x, y, r, pinBorderPaint)
            if (isSelected) {
                canvas.drawCircle(x, y, r + 4f, selectedRingPaint)
                pinLabelPaint.textSize = (width * 0.038f).coerceIn(13f, 22f)
                canvas.drawText(pin.station.name, x, y - r - 7f, pinLabelPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val tx = event.x; val ty = event.y
            val closest = pins.minByOrNull { pin ->
                val dx = lngToX(pin.station.lng.toFloat()) - tx
                val dy = latToY(pin.station.lat.toFloat()) - ty
                dx * dx + dy * dy
            }
            if (closest != null) {
                val dx = lngToX(closest.station.lng.toFloat()) - tx
                val dy = latToY(closest.station.lat.toFloat()) - ty
                if (sqrt(dx * dx + dy * dy) < 48f) {
                    onPinClick?.invoke(closest.station)
                    performClick()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    fun lngToX(lng: Float): Float = offX + (lng - LNG_MIN).toFloat() * scX
    fun latToY(lat: Float): Float = height - offY - (lat - LAT_MIN).toFloat() * scY
}
