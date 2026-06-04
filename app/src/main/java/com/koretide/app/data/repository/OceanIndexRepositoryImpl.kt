package com.koretide.app.data.repository

import com.koretide.app.BuildConfig
import com.koretide.app.data.remote.KmaBeachApiService
import com.koretide.app.data.remote.dto.KmaBeachItem
import com.koretide.app.domain.model.IndexGrade
import com.koretide.app.domain.model.IndexType
import com.koretide.app.domain.model.OceanIndex
import com.koretide.app.domain.repository.OceanIndexRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OceanIndexRepositoryImpl @Inject constructor(
    private val beachApi: KmaBeachApiService
) : OceanIndexRepository {

    private val apiKey get() = BuildConfig.BEACH_API_KEY

    override suspend fun getBeachIndex(date: String, region: String?): OceanIndex {
        if (apiKey.isBlank()) return mockBeachIndex()

        return try {
            val resp = beachApi.getBeachForecast(serviceKey = apiKey, baseDate = date)
            val items = resp.response?.body?.items?.item ?: emptyList()

            // Prefer a beach matching the selected region, fall back to first available
            val item = items.firstOrNull { matchesRegion(it.beachName, region) }
                ?: items.firstOrNull()

            item?.toDomain() ?: mockBeachIndex()
        } catch (e: Exception) {
            mockBeachIndex()
        }
    }

    private fun matchesRegion(beachName: String?, region: String?): Boolean {
        if (region.isNullOrBlank() || beachName == null) return false
        return beachName.contains(region.take(2))
    }

    private fun KmaBeachItem.toDomain() = OceanIndex(
        type     = IndexType.BEACH_SWIM,
        grade    = IndexGrade.fromString(grade),
        stats    = buildList {
            waterTemp?.let { add("수온" to "${it}°C") }
            airTemp?.let   { add("기온" to "${it}°C") }
            waveHeight?.let{ add("파고" to "${it}m") }
            windSpeed?.let { add("풍속" to "${it}m/s") }
            weather?.let   { add("날씨" to it) }
        },
        beachName   = beachName,
        date        = fcstDate?.let { formatDate(it) },
        isAvailable = true
    )

    private fun formatDate(raw: String): String = runCatching {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.KOREA)
        val out = SimpleDateFormat("M월 d일", Locale.KOREA)
        out.format(sdf.parse(raw)!!)
    }.getOrDefault(raw)

    private fun mockBeachIndex() = OceanIndex(
        type      = IndexType.BEACH_SWIM,
        grade     = IndexGrade.GOOD,
        stats     = listOf("수온" to "22°C", "기온" to "28°C", "파고" to "0.5m", "풍속" to "3m/s"),
        beachName = "샘플 해수욕장",
        date      = SimpleDateFormat("M월 d일", Locale.KOREA).format(Date()),
        isAvailable = false
    )
}
