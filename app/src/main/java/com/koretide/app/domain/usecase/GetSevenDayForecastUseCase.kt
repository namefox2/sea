package com.koretide.app.domain.usecase

import com.koretide.app.domain.model.DayForecast
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sin

class GetSevenDayForecastUseCase @Inject constructor() {

    private val labels = listOf("오늘", "내일", "모레", "+3일", "+4일", "+5일", "+6일")

    operator fun invoke(stationName: String?, region: String?): List<DayForecast> {
        val seed = (region ?: stationName ?: "").hashCode().toLong()
        return labels.mapIndexed { i, label ->
            DayForecast(
                label      = label,
                waterTemp  = mockValue(seed, i, 1L, 16f + abs(seed % 7).toFloat(), 1.5f, 10f, 30f),
                waveHeight = mockValue(seed, i, 3L, 0.3f + abs(seed % 5) * 0.15f, 0.2f, 0.1f, 10f),
                windSpeed  = mockValue(seed, i, 7L, 2f  + abs(seed % 8).toFloat(), 1.5f, 0.5f, 15f)
            )
        }
    }

    private fun mockValue(seed: Long, day: Int, seedMul: Long, base: Float, amp: Float, min: Float, max: Float): Float =
        (base + sin((seed * seedMul + day).toDouble()).toFloat() * amp).coerceIn(min, max).roundTo1dp()

    private fun Float.roundTo1dp() = (this * 10).toInt() / 10f
}
