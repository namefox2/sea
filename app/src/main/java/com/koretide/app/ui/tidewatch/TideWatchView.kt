package com.koretide.app.ui.tidewatch

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Handler
import android.os.HandlerThread
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.content.Context
import android.util.AttributeSet
import com.koretide.app.theme.ThemeConfig
import com.koretide.app.util.BeaufortConverter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class TideWatchView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    @Volatile var tidePercent: Float = 0.5f
    @Volatile var windBeaufort: Int = 3
    @Volatile var windDirectionDeg: Float = 225f
    @Volatile var themeConfig: ThemeConfig? = null

    private var targetTide: Float = 0.5f
    private var targetWind: Int = 3

    private var renderThread: HandlerThread? = null
    private var renderHandler: Handler? = null
    private var isRendering = false

    private var surfaceW = 1
    private var surfaceH = 1
    private var animT = 0f

    // Pre-allocated paints
    private val skyPaint     = Paint(Paint.ANTI_ALIAS_FLAG)
    private val seaPaint     = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint    = Paint(Paint.ANTI_ALIAS_FLAG)
    private val whitecapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255); style = Paint.Style.FILL
    }
    private val foamPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 255, 255, 255); style = Paint.Style.FILL
    }
    private val sunPaint     = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mtnPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val flatPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val cloudPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fogPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rainPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 180, 210, 255); strokeWidth = 1.5f; style = Paint.Style.STROKE
    }
    private val glitterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 240, 160)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val sprayPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 240, 248, 255); style = Paint.Style.FILL
    }
    private val gullPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(200, 50, 50, 80)
    }
    private val compassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val compassTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f; textAlign = Paint.Align.CENTER
    }

    private val wavePath = Path()
    private val mtnPath  = Path()
    private val flatPath = Path()
    private val gullPath = Path()
    private val rayPath  = Path()
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(30, 255, 255, 200); style = Paint.Style.FILL
    }

    // Particle data
    private data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float,
                                 var life: Float, var maxLife: Float, var r: Float)

    private val foamParticles  = Array(40) { randomFoam() }
    private val sprayParticles = Array(20) { randomSpray() }
    private val glitterPoints  = Array(30) { randomGlitter() }

    private data class Gull(var x: Float, var y: Float, var phase: Float, var speed: Float)
    private val seagulls = Array(5) { i ->
        Gull(i * 180f + 60f, i * 30f + 80f, i * 1.2f, 0.4f + i * 0.15f)
    }
    private data class Cloud(var x: Float, var y: Float, var r: Float, var speed: Float)
    private val clouds = Array(5) { i ->
        Cloud(i * 200f + 50f, i * 25f + 40f, 50f + i * 20f, 0.3f + i * 0.1f)
    }

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isRendering = true
        renderThread = HandlerThread("TideWaveRender").apply { start() }
        renderHandler = Handler(renderThread!!.looper)
        scheduleFrame()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        surfaceW = width.coerceAtLeast(1)
        surfaceH = height.coerceAtLeast(1)
        resetParticles()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isRendering = false
        renderHandler?.removeCallbacksAndMessages(null)
        renderThread?.quitSafely()
        try { renderThread?.join(500) } catch (_: InterruptedException) {}
        renderThread = null
        renderHandler = null
    }

    fun setTide(percent: Float) { targetTide = percent.coerceIn(0f, 1f) }
    fun setWind(bft: Int) { targetWind = bft.coerceIn(0, 12) }

    private fun scheduleFrame() {
        if (!isRendering) return
        renderHandler?.postDelayed({
            if (!isRendering) return@postDelayed
            val surf = holder.surface
            if (surf.isValid) {
                val canvas = holder.lockCanvas() ?: run { scheduleFrame(); return@postDelayed }
                try {
                    animT += 1f
                    tidePercent += (targetTide - tidePercent) * 0.015f
                    if (animT.toInt() % 60 == 0 && windBeaufort != targetWind) {
                        windBeaufort += if (targetWind > windBeaufort) 1 else -1
                    }
                    renderFrame(canvas)
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
            scheduleFrame()
        }, 16L)
    }

    private fun renderFrame(canvas: Canvas) {
        val theme = themeConfig ?: return
        val w = surfaceW.toFloat()
        val h = surfaceH.toFloat()
        val tide = tidePercent
        val wind = windBeaufort
        val seaY = h * (0.72f - tide * 0.3f)

        drawSky(canvas, w, h, wind, theme)
        drawClouds(canvas, w, h, wind, theme)
        if (wind <= 7) drawSun(canvas, w, h, tide, wind, theme)
        drawMountains(canvas, w, h, theme)
        if (tide < 0.22f) drawTidalFlat(canvas, w, h, tide, theme)
        drawSea(canvas, w, h, seaY, wind, theme)
        drawWaves(canvas, w, h, seaY, tide, wind, theme)
        if (wind >= 4) drawWhitecaps(canvas, w, h, seaY, tide, wind)
        updateAndDrawFoam(canvas, w, h, seaY, wind)
        if (wind >= 7) updateAndDrawSpray(canvas, h, seaY, wind)
        if (wind <= 5) drawGlitter(canvas, w, h, seaY, tide)
        if (wind <= 3) drawSunRays(canvas, w, h, seaY, tide)
        if (wind <= 9 && theme.hasSeagulls) drawSeagulls(canvas, w, h, seaY, wind)
        if (wind >= 8) drawFog(canvas, w, h, seaY, theme)
        if (wind >= 9) drawRain(canvas, w, h, wind)
        drawCompass(canvas, w, h, wind)
    }

    private fun drawSky(canvas: Canvas, w: Float, h: Float, wind: Int, theme: ThemeConfig) {
        skyPaint.shader = LinearGradient(0f, 0f, 0f, h * 0.75f,
            theme.skyTopColor, theme.skyBottomColor, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, skyPaint)
    }

    private fun drawClouds(canvas: Canvas, w: Float, h: Float, wind: Int, theme: ThemeConfig) {
        cloudPaint.color = theme.cloudColor
        val density = theme.cloudDensity
        val speed = 0.3f + wind * 0.15f
        for (cloud in clouds) {
            cloud.x = (cloud.x + cloud.speed * speed) % (w + 200f)
            if (cloud.x > w + 100f) cloud.x = -100f
            val alpha = (density * 200).toInt().coerceIn(30, 200)
            cloudPaint.alpha = alpha
            canvas.drawOval(
                cloud.x - cloud.r, cloud.y - cloud.r * 0.6f,
                cloud.x + cloud.r, cloud.y + cloud.r * 0.6f, cloudPaint
            )
            canvas.drawOval(
                cloud.x - cloud.r * 0.6f, cloud.y - cloud.r * 0.8f,
                cloud.x + cloud.r * 0.6f, cloud.y + cloud.r * 0.2f, cloudPaint
            )
        }
    }

    private fun drawSun(canvas: Canvas, w: Float, h: Float, tide: Float, wind: Int, theme: ThemeConfig) {
        val sunX = w * 0.75f
        val sunY = h * (0.15f - tide * 0.05f)
        val sunR = 35f
        val vis = (1f - wind * 0.08f).coerceAtLeast(0f)

        sunHaloPaint.shader = RadialGradient(sunX, sunY, sunR * 3f,
            Color.argb((100 * vis).toInt(), Color.red(theme.sunColor),
                Color.green(theme.sunColor), Color.blue(theme.sunColor)),
            Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawCircle(sunX, sunY, sunR * 3f, sunHaloPaint)

        sunPaint.color = Color.argb((255 * vis).toInt(), Color.red(theme.sunColor),
            Color.green(theme.sunColor), Color.blue(theme.sunColor))
        canvas.drawCircle(sunX, sunY, sunR, sunPaint)
    }

    private fun drawMountains(canvas: Canvas, w: Float, h: Float, theme: ThemeConfig) {
        mtnPaint.color = theme.mountainColor
        mtnPath.reset()
        val baseY = h * 0.58f
        mtnPath.moveTo(0f, baseY)
        mtnPath.cubicTo(w * 0.1f, h * 0.42f, w * 0.18f, h * 0.38f, w * 0.25f, h * 0.45f)
        mtnPath.cubicTo(w * 0.32f, h * 0.52f, w * 0.38f, h * 0.35f, w * 0.45f, h * 0.42f)
        mtnPath.cubicTo(w * 0.52f, h * 0.48f, w * 0.58f, h * 0.30f, w * 0.65f, h * 0.40f)
        mtnPath.cubicTo(w * 0.72f, h * 0.50f, w * 0.80f, h * 0.44f, w * 0.88f, h * 0.50f)
        mtnPath.lineTo(w, baseY)
        mtnPath.lineTo(w, h * 0.65f)
        mtnPath.lineTo(0f, h * 0.65f)
        mtnPath.close()
        canvas.drawPath(mtnPath, mtnPaint)
    }

    private fun drawTidalFlat(canvas: Canvas, w: Float, h: Float, tide: Float, theme: ThemeConfig) {
        flatPaint.color = theme.tidalFlatColor
        val flatH = h * (0.22f - tide) * 0.8f
        flatPath.reset()
        flatPath.moveTo(0f, h * 0.75f)
        flatPath.lineTo(0f, h * 0.75f + flatH)
        flatPath.lineTo(w, h * 0.75f + flatH)
        flatPath.lineTo(w, h * 0.75f)
        flatPath.close()
        canvas.drawPath(flatPath, flatPaint)
    }

    private fun drawSea(canvas: Canvas, w: Float, h: Float, seaY: Float, wind: Int, theme: ThemeConfig) {
        seaPaint.shader = LinearGradient(0f, seaY, 0f, h,
            theme.seaTopColor, theme.seaBottomColor, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, seaY, w, h, seaPaint)
    }

    private fun drawWaves(canvas: Canvas, w: Float, h: Float, seaY: Float, tide: Float, wind: Int, theme: ThemeConfig) {
        val numLayers = when {
            wind <= 2 -> 2
            wind <= 5 -> 3
            wind <= 8 -> 4
            else      -> 5
        }
        for (layer in 0 until numLayers) {
            val alpha = 100 - layer * 15
            wavePaint.color = Color.argb(alpha, Color.red(theme.waveColor),
                Color.green(theme.waveColor), Color.blue(theme.waveColor))
            wavePaint.style = Paint.Style.FILL

            wavePath.reset()
            val layerY = seaY + layer * 8f
            wavePath.moveTo(0f, h)
            wavePath.lineTo(0f, layerY + waveY(0f, layer, wind, tide))
            var x = 0f
            while (x <= w) {
                wavePath.lineTo(x, layerY + waveY(x, layer, wind, tide))
                x += 6f
            }
            wavePath.lineTo(w, h)
            wavePath.close()
            canvas.drawPath(wavePath, wavePaint)
        }
    }

    private fun waveY(x: Float, layer: Int, wind: Int, tide: Float): Float {
        val bftH = BeaufortConverter.waveHeight(wind)
        val amp  = (3.5f + bftH * 18f - layer * 1.2f) * (0.3f + tide * 0.7f)
        val freq = (0.022f - layer * 0.003f) * (1f + wind * 0.03f)
        val ph   = animT * (0.015f + wind * 0.002f - layer * 0.002f) * 1.0f + layer * 1.1f

        var y = sin((x * freq + ph).toDouble()).toFloat() * amp +
                cos((x * freq * 0.55f + ph * 1.4f).toDouble()).toFloat() * amp * 0.38f
        if (wind > 4) {
            y += sin((x * freq * 0.5f + ph * 0.7f).toDouble()).toFloat() * amp * 0.18f
        }
        return y
    }

    private fun drawWhitecaps(canvas: Canvas, w: Float, h: Float, seaY: Float, tide: Float, wind: Int) {
        val count = (wind - 3) * 4
        val seed = animT.toLong() / 30
        val r = Random(seed)
        repeat(count) {
            val x = r.nextFloat() * w
            val y = seaY + waveY(x, 0, wind, tide) - 4f
            canvas.drawOval(x - 18f, y - 5f, x + 18f, y + 5f, whitecapPaint)
        }
    }

    private fun updateAndDrawFoam(canvas: Canvas, w: Float, h: Float, seaY: Float, wind: Int) {
        val active = (wind * 4).coerceAtMost(foamParticles.size)
        for (i in 0 until active) {
            val p = foamParticles[i]
            p.x += p.vx
            p.y += p.vy * 0.3f
            p.life -= 0.012f
            if (p.life <= 0f || p.x > w || p.x < 0f) {
                foamParticles[i] = randomFoam(w, seaY)
                continue
            }
            foamPaint.alpha = (p.life / p.maxLife * 200).toInt()
            canvas.drawCircle(p.x, p.y, p.r, foamPaint)
        }
    }

    private fun updateAndDrawSpray(canvas: Canvas, h: Float, seaY: Float, wind: Int) {
        val active = ((wind - 6) * 4).coerceAtMost(sprayParticles.size)
        for (i in 0 until active) {
            val p = sprayParticles[i]
            p.x  += p.vx
            p.y  += p.vy
            p.vy += 0.15f
            p.life -= 0.02f
            if (p.life <= 0f || p.y > seaY + 30f) {
                sprayParticles[i] = randomSpray(p.x, seaY)
                continue
            }
            sprayPaint.alpha = (p.life / p.maxLife * 180).toInt()
            canvas.drawCircle(p.x, p.y, p.r, sprayPaint)
        }
    }

    private fun drawGlitter(canvas: Canvas, w: Float, h: Float, seaY: Float, tide: Float) {
        val intensity = (1f - windBeaufort * 0.2f).coerceAtLeast(0f)
        for (i in glitterPoints.indices) {
            val p = glitterPoints[i]
            val flicker = abs(sin((animT * 0.1f + i).toDouble())).toFloat()
            if (flicker < 0.5f) continue
            p.x += p.vx * 0.5f
            if (p.x > w) p.x = 0f
            glitterPaint.alpha = (flicker * intensity * 200).toInt()
            val s = p.r * flicker
            canvas.drawLine(p.x - s, p.y, p.x + s, p.y, glitterPaint)
            canvas.drawLine(p.x, p.y - s, p.x, p.y + s, glitterPaint)
        }
    }

    private fun drawSunRays(canvas: Canvas, w: Float, h: Float, seaY: Float, tide: Float) {
        val sunX = w * 0.75f
        val sunY = h * 0.15f
        repeat(6) { i ->
            val angle = (i * 60f + animT * 0.1f) * (PI / 180f)
            rayPath.reset()
            rayPath.moveTo(sunX, sunY)
            rayPath.lineTo(
                sunX + cos(angle - 0.1).toFloat() * 300f,
                sunY + sin(angle - 0.1).toFloat() * 300f
            )
            rayPath.lineTo(
                sunX + cos(angle + 0.1).toFloat() * 300f,
                sunY + sin(angle + 0.1).toFloat() * 300f
            )
            rayPath.close()
            canvas.drawPath(rayPath, rayPaint)
        }
    }

    private fun drawSeagulls(canvas: Canvas, w: Float, h: Float, seaY: Float, wind: Int) {
        for (gull in seagulls) {
            gull.x = (gull.x + gull.speed * (1f + wind * 0.1f)) % (w + 100f)
            if (gull.x > w + 50f) gull.x = -50f
            gull.phase += 0.05f
            val wingY = sin(gull.phase.toDouble()).toFloat() * 8f
            gullPath.reset()
            gullPath.moveTo(gull.x - 20f, gull.y)
            gullPath.quadTo(gull.x - 10f, gull.y + wingY, gull.x, gull.y)
            gullPath.quadTo(gull.x + 10f, gull.y + wingY, gull.x + 20f, gull.y)
            canvas.drawPath(gullPath, gullPaint)
        }
    }

    private fun drawFog(canvas: Canvas, w: Float, h: Float, seaY: Float, theme: ThemeConfig) {
        fogPaint.color = theme.fogColor
        val layers = 3
        for (i in 0..layers) {
            val y = seaY - 20f + i * 30f
            val shift = sin((animT * 0.005f + i).toDouble()).toFloat() * 20f
            fogPaint.shader = LinearGradient(0f, y, 0f, y + 30f,
                theme.fogColor, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawRect(shift, y, w + shift, y + 30f, fogPaint)
        }
        fogPaint.shader = null
    }

    private fun drawRain(canvas: Canvas, w: Float, h: Float, wind: Int) {
        val count = (wind - 8) * 30 + 40
        val angle = 0.3f
        for (i in 0 until count) {
            val x = ((i * 47f + animT * 6f) % (w + 50f)) - 25f
            val y = ((i * 83f + animT * 14f) % h)
            canvas.drawLine(x, y, x + angle * 15f, y + 15f, rainPaint)
        }
    }

    private fun drawCompass(canvas: Canvas, w: Float, h: Float, wind: Int) {
        val cx = w - 60f; val cy = h - 60f; val r = 30f
        compassPaint.color = Color.argb(160, 0, 0, 0); compassPaint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, r, compassPaint)
        compassPaint.color = Color.argb(200, 255, 255, 255); compassPaint.style = Paint.Style.STROKE
        compassPaint.strokeWidth = 1.5f
        canvas.drawCircle(cx, cy, r, compassPaint)

        val angleRad = (windDirectionDeg - 90f) * PI.toFloat() / 180f
        val arrowX = cx + cos(angleRad) * (r - 8f)
        val arrowY = cy + sin(angleRad) * (r - 8f)
        compassPaint.color = Color.argb(220, 255, 80, 80)
        canvas.drawLine(cx, cy, arrowX, arrowY, compassPaint)

        compassTextPaint.color = Color.argb(200, 255, 255, 255)
        val bftName = BeaufortConverter.name(wind)
        val shortName = if (bftName.length > 4) bftName.take(4) else bftName
        canvas.drawText("${wind}bft", cx, cy + r + 22f, compassTextPaint)
    }

    private fun resetParticles() {
        val w = surfaceW.toFloat()
        val seaY = surfaceH * 0.55f
        for (i in foamParticles.indices)  foamParticles[i]  = randomFoam(w, seaY)
        for (i in sprayParticles.indices) sprayParticles[i] = randomSpray(w * Random.nextFloat(), seaY)
        for (i in glitterPoints.indices)  glitterPoints[i]  = randomGlitter(w, surfaceH.toFloat())
        for ((i, g) in seagulls.withIndex()) {
            g.x = i * (w / 5f) + Random.nextFloat() * 100f
            g.y = surfaceH * (0.2f + Random.nextFloat() * 0.15f)
        }
    }

    private fun randomFoam(w: Float = 400f, seaY: Float = 300f) = Particle(
        x = Random.nextFloat() * w,
        y = seaY + Random.nextFloat() * 20f,
        vx = (Random.nextFloat() - 0.5f) * 2f,
        vy = -Random.nextFloat() * 0.5f,
        life = Random.nextFloat(),
        maxLife = 1f,
        r = 2f + Random.nextFloat() * 4f
    )

    private fun randomSpray(x: Float = 200f, seaY: Float = 300f) = Particle(
        x = x,
        y = seaY,
        vx = (Random.nextFloat() - 0.5f) * 4f,
        vy = -Random.nextFloat() * 6f - 2f,
        life = 0.5f + Random.nextFloat() * 0.5f,
        maxLife = 1f,
        r = 1.5f + Random.nextFloat() * 2.5f
    )

    private fun randomGlitter(w: Float = 400f, h: Float = 600f) = Particle(
        x = Random.nextFloat() * w,
        y = h * 0.6f + Random.nextFloat() * h * 0.35f,
        vx = 0.3f + Random.nextFloat() * 0.7f,
        vy = 0f,
        life = 1f,
        maxLife = 1f,
        r = 3f + Random.nextFloat() * 5f
    )
}
