package com.koretide.app.data.repository

import com.koretide.app.BuildConfig
import com.koretide.app.data.remote.KmaBeachApiService
import com.koretide.app.data.remote.dto.KmaBeachItem
import com.koretide.app.domain.model.IndexGrade
import com.koretide.app.domain.model.IndexType
import com.koretide.app.domain.model.OceanIndex
import com.koretide.app.domain.repository.OceanIndexRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OceanIndexRepositoryImpl @Inject constructor(
    private val api: KmaBeachApiService
) : OceanIndexRepository {

    private val key get() = BuildConfig.BEACH_API_KEY

    override suspend fun getAllIndices(date: String, region: String?): List<OceanIndex> {
        if (key.isBlank()) return IndexType.entries.map { mockIndex(it) }

        return coroutineScope {
            val beach    = async { fetch(date, region, IndexType.BEACH_SWIM)    { api.getBeachForecast(key, baseDate = it) } }
            val fishing  = async { fetch(date, region, IndexType.SEA_FISHING)   { api.getFishingForecast(key, baseDate = it) } }
            val sick     = async { fetch(date, region, IndexType.SEASICKNESS)   { api.getSeasicknessForecast(key, baseDate = it) } }
            val scuba    = async { fetch(date, region, IndexType.SCUBA_DIVING)  { api.getScubaForecast(key, baseDate = it) } }
            val tidal    = async { fetch(date, region, IndexType.TIDAL_FLAT)    { api.getTidalFlatForecast(key, baseDate = it) } }
            val surf     = async { fetch(date, region, IndexType.SURFING)       { api.getSurfingForecast(key, baseDate = it) } }
            val travel   = async { fetch(date, region, IndexType.SEA_TRAVEL)    { api.getSeaTravelForecast(key, baseDate = it) } }
            listOf(beach, fishing, sick, scuba, tidal, surf, travel).map { it.await() }
        }
    }

    private suspend fun fetch(
        date: String,
        region: String?,
        type: IndexType,
        call: suspend (String) -> com.koretide.app.data.remote.dto.KmaBeachResponse
    ): OceanIndex = try {
        val items = call(date).response?.body?.items?.item ?: emptyList()
        val item  = items.firstOrNull { matchesRegion(it.beachName, region) }
                  ?: items.firstOrNull()
        item?.toDomain(type) ?: mockIndex(type)
    } catch (e: Exception) {
        mockIndex(type)
    }

    private fun matchesRegion(name: String?, region: String?): Boolean {
        if (region.isNullOrBlank() || name == null) return false
        return name.contains(region.take(2))
    }

    private fun KmaBeachItem.toDomain(type: IndexType) = OceanIndex(
        type      = type,
        grade     = IndexGrade.fromString(grade),
        stats     = statsFor(type),
        beachName = beachName,
        date      = fcstDate?.let { formatDate(it) },
        isAvailable = true
    )

    // 활동 유형별 핵심 통계 선별
    private fun KmaBeachItem.statsFor(type: IndexType): List<Pair<String, String>> = buildList {
        when (type) {
            IndexType.BEACH_SWIM -> {
                waterTemp?.let  { add("수온"  to "${it}°C") }
                airTemp?.let    { add("기온"  to "${it}°C") }
                waveHeight?.let { add("파고"  to "${it}m")  }
                windSpeed?.let  { add("풍속"  to "${it}m/s") }
            }
            IndexType.SEA_FISHING -> {
                waveHeight?.let { add("파고"  to "${it}m")  }
                windSpeed?.let  { add("풍속"  to "${it}m/s") }
                weather?.let    { add("날씨"  to it) }
            }
            IndexType.SEASICKNESS -> {
                waveHeight?.let { add("파고"  to "${it}m")  }
                windSpeed?.let  { add("풍속"  to "${it}m/s") }
            }
            IndexType.SCUBA_DIVING -> {
                waterTemp?.let  { add("수온"  to "${it}°C") }
                waveHeight?.let { add("파고"  to "${it}m")  }
                weather?.let    { add("날씨"  to it) }
            }
            IndexType.TIDAL_FLAT -> {
                airTemp?.let    { add("기온"  to "${it}°C") }
                weather?.let    { add("날씨"  to it) }
            }
            IndexType.SURFING -> {
                waveHeight?.let { add("파고"  to "${it}m")  }
                windSpeed?.let  { add("풍속"  to "${it}m/s") }
                weather?.let    { add("날씨"  to it) }
            }
            IndexType.SEA_TRAVEL -> {
                airTemp?.let    { add("기온"  to "${it}°C") }
                waveHeight?.let { add("파고"  to "${it}m")  }
                weather?.let    { add("날씨"  to it) }
            }
        }
    }

    private fun formatDate(raw: String): String = runCatching {
        SimpleDateFormat("M월 d일", Locale.KOREA)
            .format(SimpleDateFormat("yyyyMMdd", Locale.KOREA).parse(raw)!!)
    }.getOrDefault(raw)

    private fun mockIndex(type: IndexType) = OceanIndex(
        type      = type,
        grade     = IndexGrade.GOOD,
        stats     = emptyList(),
        beachName = null,
        date      = SimpleDateFormat("M월 d일", Locale.KOREA).format(Date()),
        isAvailable = false
    )
}
