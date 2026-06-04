package com.koretide.app.domain.usecase

import com.koretide.app.domain.model.DayForecast
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sin

class GetSevenDayForecastUseCase @Inject constructor() {

    operator fun invoke(stationName: String?, region: String?): List<DayForecast> {
        val labels = listOf("오늘", "내일", "모레", "+3일", "+4일", "+5일", "+6일")
        // Seed from region so repeated calls return stable values
        val seed = (region ?: stationName ?: "").hashCode().toLong()
        return labels.mapIndexed { i, label ->
            DayForecast(
                label      = label,
                waterTemp  = mockTemp(seed, i),
                waveHeight = mockWave(seed, i),
                windSpeed  = mockWind(seed, i)
            )
        }
    }

    private fun mockTemp(seed: Long, day: Int): Float {
        val base = 16f + abs(seed % 7).toFloat()  // 16–22 °C range
        return (base + sin((seed + day).toDouble()).toFloat() * 1.5f)
            .coerceIn(10f, 30f)
            .let { (it * 10).toInt() / 10f }
    }

    private fun mockWave(seed: Long, day: Int): Float {
        val base = 0.3f + abs(seed % 5) * 0.15f
        return (base + sin((seed * 3 + day).toDouble()).toFloat() * 0.2f)
            .coerceAtLeast(0.1f)
            .let { (it * 10).toInt() / 10f }
    }

    private fun mockWind(seed: Long, day: Int): Float {
        val base = 2f + abs(seed % 8).toFloat()
        return (base + sin((seed * 7 + day).toDouble()).toFloat() * 1.5f)
            .coerceIn(0.5f, 15f)
            .let { (it * 10).toInt() / 10f }
    }
}
