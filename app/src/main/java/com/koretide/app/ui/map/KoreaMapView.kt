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
import com.koretide.app.domain.model.ActivitySpot
import com.koretide.app.domain.model.ActivityType
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

    var activitySpots: List<ActivitySpot> = emptyList()
        set(value) { field = value; invalidate() }

    var onPinClick: ((Station) -> Unit)? = null
    var onActivityPinClick: ((ActivitySpot) -> Unit)? = null
    var selectedCode: String? = null
        set(value) { field = value; invalidate() }

    // Sea background — light blue like Wikipedia
    private val seaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C8E0F0")
    }
    // North Korea region — warm gray
    private val northKoreaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0D8CC"); style = Paint.Style.FILL
    }
    private val northKoreaBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A09080"); style = Paint.Style.STROKE; strokeWidth = 1.2f
    }
    // South Korea land — light cream (Wikipedia style)
    private val landPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5F3EE"); style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#909090"); style = Paint.Style.STROKE; strokeWidth = 1.6f
    }
    // Province boundary lines — thin gray
    private val provincePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A8A8A8"); style = Paint.Style.STROKE
        strokeWidth = 1.0f
    }
    // DMZ dashed line
    private val dmzPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC774444"); style = Paint.Style.STROKE; strokeWidth = 2.0f
        pathEffect = DashPathEffect(floatArrayOf(9f, 5f), 0f)
    }
    // Sea name labels
    private val seaLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7BAE"); textAlign = Paint.Align.CENTER
        isFakeBoldText = true; letterSpacing = 0.15f
    }
    // Province labels
    private val provinceLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555"); textAlign = Paint.Align.CENTER
    }
    // City labels
    private val cityLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333"); textAlign = Paint.Align.CENTER
    }
    private val cityDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#666666"); style = Paint.Style.FILL
    }
    // Station pins
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pinBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val selectedRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700"); style = Paint.Style.STROKE; strokeWidth = 3.5f
    }
    private val pinLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111111"); textAlign = Paint.Align.CENTER
    }

    // Activity spot pin paints — one per ActivityType, cached to avoid allocations in onDraw
    private val activityPinPaints: Map<ActivityType, Paint> = ActivityType.values().associateWith { type ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(type.colorHex)
            style = Paint.Style.FILL
        }
    }

    // Geographic bounds
    private val LAT_MIN = 32.6
    private val LAT_MAX = 39.2
    private val LNG_MIN = 123.8
    private val LNG_MAX = 131.0

    private var scX = 1f
    private var scY = 1f
    private var offX = 0f
    private var offY = 0f

    private val peninsulaPath = Path()
    private val northKoreaPath = Path()
    private val jejuPath = Path()
    private val geojeIslandPath = Path()
    private val jindo1Path = Path()
    private val ganghwaPath = Path()
    private val dmzPath = Path()
    private val provinceBorderPaths = mutableListOf<Path>()
    private var pathDirty = true

    private fun drawSquare(
        canvas: Canvas,
        x: Float,
        y: Float,
        r: Float,
        paint: Paint
    ) {
        canvas.drawRect(
            x - r,
            y - r,
            x + r,
            y + r,
            paint
        )
    }
    private fun drawTriangle(
        canvas: Canvas,
        x: Float,
        y: Float,
        r: Float,
        paint: Paint
    ) {
        val path = Path()

        path.moveTo(x, y - r)
        path.lineTo(x + r, y + r)
        path.lineTo(x - r, y + r)
        path.close()

        canvas.drawPath(path, paint)
    }
    private fun drawDiamond(
        canvas: Canvas,
        x: Float,
        y: Float,
        r: Float,
        paint: Paint
    ) {
        val path = Path()

        path.moveTo(x, y - r)
        path.lineTo(x + r, y)
        path.lineTo(x, y + r)
        path.lineTo(x - r, y)
        path.close()

        canvas.drawPath(path, paint)
    }
    private fun drawStar(
        canvas: Canvas,
        x: Float,
        y: Float,
        r: Float,
        paint: Paint
    ) {
        val path = Path()

        for (i in 0 until 10) {
            val angle = Math.PI / 5 * i - Math.PI / 2
            val radius = if (i % 2 == 0) r else r / 2

            val px = x + (radius * kotlin.math.cos(angle)).toFloat()
            val py = y + (radius * kotlin.math.sin(angle)).toFloat()

            if (i == 0) path.moveTo(px, py)
            else path.lineTo(px, py)
        }

        path.close()
        canvas.drawPath(path, paint)
    }
    // Province boundary polylines — approximate geographic coordinates
    private val provinceBorders = listOf(
        // Gyeonggi / Chungnam–Chungbuk (roughly 37°N belt, W→E)
        listOf(126.32f to 37.67f, 126.6f to 37.35f, 127.0f to 37.1f,
               127.5f to 36.95f, 128.0f to 36.95f, 128.3f to 36.88f),
        // Gyeonggi / Gangwon (NE from above 3-way point, to DMZ)
        listOf(128.0f to 36.95f, 128.1f to 37.25f, 128.35f to 37.65f, 128.62f to 38.26f),
        // Chungnam / Jeonbuk (W coast inland)
        listOf(126.62f to 36.05f, 126.82f to 35.82f, 127.0f to 35.75f, 127.5f to 35.75f),
        // Chungbuk / Gyeongbuk (central ridge)
        listOf(127.5f to 36.95f, 127.8f to 36.65f, 128.1f to 36.5f, 128.3f to 36.5f),
        // Gangwon / Gyeongbuk (east coast mountains)
        listOf(128.3f to 36.5f, 128.55f to 36.85f, 129.0f to 37.05f, 129.18f to 37.28f),
        // Jeonbuk / Jeonnam (Jeolla dividing line)
        listOf(126.38f to 35.18f, 126.65f to 35.05f, 127.0f to 34.95f,
               127.4f to 35.02f, 127.6f to 35.25f),
        // Jeonbuk / Gyeongnam (Jiri mountain corridor)
        listOf(127.5f to 35.75f, 127.78f to 35.55f, 128.12f to 35.5f, 128.45f to 35.45f),
        // Jeonnam / Gyeongnam (south boundary)
        listOf(127.4f to 35.02f, 127.72f to 34.82f, 128.0f to 34.85f, 128.22f to 34.88f),
        // Gyeongbuk / Gyeongnam (east-center ridge)
        listOf(128.3f to 36.5f, 128.55f to 36.1f, 128.72f to 35.72f,
               129.0f to 35.5f, 129.35f to 35.35f)
    )
    private fun drawHeart(
        canvas: Canvas,
        x: Float,
        y: Float,
        r: Float,
        paint: Paint
    ) {
        val path = Path()

        path.moveTo(x, y + r)

        path.cubicTo(
            x - r * 2,
            y,
            x - r,
            y - r * 2,
            x,
            y - r
        )

        path.cubicTo(
            x + r,
            y - r * 2,
            x + r * 2,
            y,
            x,
            y + r
        )

        canvas.drawPath(path, paint)
    }
    // Province name label positions
    private data class ProvinceLabel(val name: String, val lng: Float, val lat: Float)
    private val provinceLabels = listOf(
        ProvinceLabel("경기", 127.1f, 37.35f),
        ProvinceLabel("강원", 128.45f, 37.5f),
        ProvinceLabel("충남", 126.9f, 36.48f),
        ProvinceLabel("충북", 127.78f, 36.62f),
        ProvinceLabel("전북", 127.08f, 35.62f),
        ProvinceLabel("전남", 126.82f, 34.82f),
        ProvinceLabel("경북", 128.68f, 36.08f),
        ProvinceLabel("경남", 128.18f, 35.28f),
        ProvinceLabel("제주", 126.55f, 33.4f)
    )

    init { isClickable = true; isFocusable = true }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padH = w * 0.08f
        val padV = h * 0.06f
        scX = (w - 2 * padH) / (LNG_MAX - LNG_MIN).toFloat()
        scY = (h - 2 * padV) / (LAT_MAX - LAT_MIN).toFloat()
        offX = padH
        offY = padV
        pathDirty = true
    }

    /**
     * 이미지뷰의 fitCenter 렌더링 영역을 알려주면 핀 좌표계를 이미지에 맞게 재계산한다.
     * MapFragment에서 이미지 레이아웃 완료 후 호출할 것.
     *
     * @param imgLeft   뷰 내에서 이미지가 시작되는 x 픽셀
     * @param imgTop    뷰 내에서 이미지가 시작되는 y 픽셀
     * @param imgWidth  렌더링된 이미지 픽셀 너비
     * @param imgHeight 렌더링된 이미지 픽셀 높이
     */
    fun setMapImageRect(imgLeft: Float, imgTop: Float, imgWidth: Float, imgHeight: Float) {
        scX = imgWidth  / (LNG_MAX - LNG_MIN).toFloat()
        scY = imgHeight / (LAT_MAX - LAT_MIN).toFloat()
        offX = imgLeft
        // latToY uses: height - offY - (lat-LAT_MIN)*scY
        // At LAT_MIN: height - offY = imgTop + imgHeight  →  offY = height - imgTop - imgHeight
        offY = height - imgTop - imgHeight
        pathDirty = true
        invalidate()
    }

    private fun buildPaths() {
        if (!pathDirty) return

        // ─── South Korea mainland (clockwise from NW DMZ) ────────────────────
        val mainland = listOf(
            // DMZ line (west to east, ~38.3°N)
            126.02f to 38.27f, 126.45f to 38.32f, 126.85f to 38.38f,
            127.28f to 38.42f, 127.68f to 38.45f, 128.05f to 38.38f,
            128.35f to 38.3f,  128.62f to 38.26f,
            // East coast going south
            128.72f to 38.08f, 128.82f to 37.9f,
            128.88f to 37.77f,
            128.9f  to 37.62f,
            129.05f to 37.5f,
            129.18f to 37.28f,
            129.27f to 37.1f,
            129.38f to 36.95f,
            129.42f to 36.72f,
            129.5f  to 36.45f,
            129.5f  to 36.2f,
            129.45f to 35.98f,
            129.45f to 35.8f,
            129.42f to 35.65f,
            129.35f to 35.5f,
            129.38f to 35.35f,
            129.28f to 35.2f,
            129.12f to 35.08f,
            129.05f to 35.08f,
            128.95f to 35.05f,
            // SE corner (Geoje strait)
            128.8f  to 34.88f,
            128.68f to 34.82f,
            // South coast west
            128.48f to 34.73f,
            128.22f to 34.62f,
            128.02f to 34.62f,
            127.76f to 34.5f,
            127.45f to 34.45f,
            127.2f  to 34.5f,
            126.98f to 34.58f,
            126.78f to 34.67f,
            126.58f to 34.73f,
            126.42f to 34.85f,
            126.35f to 34.98f,
            126.3f  to 35.05f,
            // West coast going north
            126.38f to 35.18f,
            126.5f  to 35.28f,
            126.45f to 35.42f,
            126.35f to 35.58f,
            126.28f to 35.73f,
            126.42f to 35.88f,
            126.58f to 36.05f,
            126.62f to 36.2f,
            126.65f to 36.35f,
            126.52f to 36.52f,
            126.42f to 36.68f,
            126.5f  to 36.82f,
            126.62f to 36.97f,
            126.52f to 37.12f,
            126.6f  to 37.27f,
            126.65f to 37.42f,
            126.48f to 37.55f,
            126.32f to 37.67f,
            126.18f to 37.77f,
            126.05f to 37.87f,
            126.0f  to 38.0f,
            125.95f to 38.1f,
            125.92f to 38.2f,
            126.02f to 38.27f
        )
        peninsulaPath.reset()
        peninsulaPath.moveTo(lngToX(mainland[0].first), latToY(mainland[0].second))
        for (i in 1 until mainland.size) {
            peninsulaPath.lineTo(lngToX(mainland[i].first), latToY(mainland[i].second))
        }
        peninsulaPath.close()

        // ─── North Korea (simplified, above DMZ) ─────────────────────────────
        val northKorea = listOf(
            125.92f to 38.2f, 125.95f to 38.1f, 126.0f  to 38.0f,
            126.02f to 38.27f, 126.45f to 38.32f, 126.85f to 38.38f,
            127.28f to 38.42f, 127.68f to 38.45f, 128.05f to 38.38f,
            128.35f to 38.3f,  128.62f to 38.26f, 128.72f to 38.08f,
            128.85f to 38.1f,  129.0f  to 38.3f,  129.0f  to 38.6f,
            128.6f  to 38.72f, 128.0f  to 38.78f, 127.3f  to 38.75f,
            126.8f  to 38.68f, 126.3f  to 38.6f,  125.85f to 38.5f,
            125.6f  to 38.42f, 125.3f  to 38.42f, 125.1f  to 38.38f,
            125.1f  to 38.3f,  125.35f to 38.22f, 125.6f  to 38.2f,
            125.92f to 38.2f
        )
        northKoreaPath.reset()
        northKoreaPath.moveTo(lngToX(northKorea[0].first), latToY(northKorea[0].second))
        for (i in 1 until northKorea.size) {
            northKoreaPath.lineTo(lngToX(northKorea[i].first), latToY(northKorea[i].second))
        }
        northKoreaPath.close()

        // ─── Jeju Island ──────────────────────────────────────────────────────
        jejuPath.reset()
        val jcx = lngToX(126.55f); val jcy = latToY(33.38f)
        val jrx = scX * 0.5f; val jry = scY * 0.26f
        jejuPath.addOval(RectF(jcx - jrx, jcy - jry, jcx + jrx, jcy + jry), Path.Direction.CW)

        // ─── Geoje Island (SE coast) ──────────────────────────────────────────
        geojeIslandPath.reset()
        val gcx = lngToX(128.62f); val gcy = latToY(34.87f)
        val grx = scX * 0.2f; val gry = scY * 0.15f
        geojeIslandPath.addOval(RectF(gcx - grx, gcy - gry, gcx + grx, gcy + gry), Path.Direction.CW)

        // ─── Jindo Island (SW coast) ──────────────────────────────────────────
        jindo1Path.reset()
        val jdcx = lngToX(126.27f); val jdcy = latToY(34.48f)
        val jdrx = scX * 0.17f; val jdry = scY * 0.14f
        jindo1Path.addOval(RectF(jdcx - jdrx, jdcy - jdry, jdcx + jdrx, jdcy + jdry), Path.Direction.CW)

        // ─── Ganghwa Island (NW coast near Incheon) ───────────────────────────
        ganghwaPath.reset()
        val gacx = lngToX(126.47f); val gacy = latToY(37.73f)
        val garx = scX * 0.12f; val gary = scY * 0.15f
        ganghwaPath.addOval(RectF(gacx - garx, gacy - gary, gacx + garx, gacy + gary), Path.Direction.CW)

        // ── DMZ dashed line ──────────────────────────────────────────────────
        val dmzPts = listOf(
            126.02f to 38.27f, 126.45f to 38.32f, 126.85f to 38.38f,
            127.28f to 38.42f, 127.68f to 38.45f, 128.05f to 38.38f,
            128.35f to 38.3f,  128.62f to 38.26f
        )
        dmzPath.reset()
        dmzPath.moveTo(lngToX(dmzPts[0].first), latToY(dmzPts[0].second))
        for (i in 1 until dmzPts.size) dmzPath.lineTo(lngToX(dmzPts[i].first), latToY(dmzPts[i].second))

        // ── Province border path cache ────────────────────────────────────────
        provinceBorderPaths.clear()
        for (border in provinceBorders) {
            if (border.isEmpty()) continue
            val p = Path()
            p.moveTo(lngToX(border[0].first), latToY(border[0].second))
            for (i in 1 until border.size) p.lineTo(lngToX(border[i].first), latToY(border[i].second))
            provinceBorderPaths.add(p)
        }

        pathDirty = false
    }

    override fun onDraw(canvas: Canvas) {

        // Station pins
        for (pin in pins) {
            val x = lngToX(pin.station.lng.toFloat())
            val y = latToY(pin.station.lat.toFloat())
            val isSelected = pin.station.code == selectedCode
            val r = if (isSelected) 13f else 9f

            pinPaint.color = when (pin.tideStatus) {
                TideStatus.RISING, TideStatus.HIGH_TIDE -> Color.parseColor("#1565C0")
                TideStatus.FALLING, TideStatus.LOW_TIDE -> Color.parseColor("#E65100")
                null -> Color.parseColor("#0288D1")
            }
            canvas.drawCircle(x, y, r, pinPaint)
            canvas.drawCircle(x, y, r, pinBorderPaint)
            if (isSelected) {
                canvas.drawCircle(x, y, r + 5f, selectedRingPaint)
                pinLabelPaint.textSize = (width * 0.038f).coerceIn(12f, 20f)
                canvas.drawText(pin.station.name, x, y - r - 8f, pinLabelPaint)
            }
        }

        // Activity spot pins (diamond shape)
        for (spot in activitySpots) {
            val x = lngToX(spot.lng.toFloat())
            val y = latToY(spot.lat.toFloat())

            val paint = activityPinPaints[spot.type] ?: continue
            val r = 11f

            when (spot.type) {
                ActivityType.HIGH_TIDE -> drawStar(canvas, x, y, r, paint)

                ActivityType.FISHING -> drawDiamond(canvas, x, y, r, paint)

                ActivityType.SURFING -> drawTriangle(canvas, x, y, r, paint)

                ActivityType.TIDAL_FLAT -> drawSquare(canvas, x, y, r, paint)

                ActivityType.SWIMMING -> canvas.drawCircle(x, y, r, paint)

                ActivityType.SCUBA -> drawHeart(canvas, x, y, 7f, paint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val tx = event.x; val ty = event.y

            // Check activity spots first
            val closestActivity = activitySpots.minByOrNull { spot ->
                val dx = lngToX(spot.lng.toFloat()) - tx
                val dy = latToY(spot.lat.toFloat()) - ty
                dx * dx + dy * dy
            }
            if (closestActivity != null) {
                val dx = lngToX(closestActivity.lng.toFloat()) - tx
                val dy = latToY(closestActivity.lat.toFloat()) - ty
                if (sqrt(dx * dx + dy * dy) < 52f) {
                    onActivityPinClick?.invoke(closestActivity)
                    performClick()
                    return true
                }
            }

            // Then check station pins
            val closest = pins.minByOrNull { pin ->
                val dx = lngToX(pin.station.lng.toFloat()) - tx
                val dy = latToY(pin.station.lat.toFloat()) - ty
                dx * dx + dy * dy
            }
            if (closest != null) {
                val dx = lngToX(closest.station.lng.toFloat()) - tx
                val dy = latToY(closest.station.lat.toFloat()) - ty
                if (sqrt(dx * dx + dy * dy) < 52f) {
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
