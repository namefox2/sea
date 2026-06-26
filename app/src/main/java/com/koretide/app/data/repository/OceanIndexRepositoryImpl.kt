package com.koretide.app.data.repository

import com.koretide.app.BuildConfig
import com.koretide.app.data.remote.KhoaIndexApiService
import com.koretide.app.data.remote.dto.KhoaIndexItem
import com.koretide.app.domain.model.IndexGrade
import com.koretide.app.domain.model.IndexType
import com.koretide.app.domain.model.OceanIndex
import com.koretide.app.domain.model.StationRegion
import com.koretide.app.domain.model.TideData
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
    private val api: KhoaIndexApiService
) : OceanIndexRepository {

    private val key get() = BuildConfig.KHOA_API_KEY

    override suspend fun getAllIndices(
        date: String,
        region: String?,
        stationCode: String?,
        tideData: TideData?
    ): List<OceanIndex> {
        if (key.isBlank()) throw IllegalStateException("API 키가 설정되지 않았습니다")

        return coroutineScope {
            val beach   = async { fetch(date, stationCode, IndexType.BEACH_SWIM)   { api.getBeachForecast(key, it, date) } }
            val fishing = async { fetch(date, stationCode, IndexType.SEA_FISHING)  { api.getFishingForecast(key, it, date) } }
            val sick    = async { fetch(date, stationCode, IndexType.SEASICKNESS)  { api.getSeasicknessForecast(key, it, date) } }
            val scuba   = async { fetch(date, stationCode, IndexType.SCUBA_DIVING) { api.getScubaForecast(key, it, date) } }
            val tidal   = async { tidalFlatIndex(date, stationCode, tideData) }
            val surf    = async { fetch(date, stationCode, IndexType.SURFING)      { api.getSurfingForecast(key, it, date) } }
            val travel  = async { fetch(date, stationCode, IndexType.SEA_TRAVEL)   { api.getSeaTravelForecast(key, it, date) } }
            listOf(beach, fishing, sick, scuba, tidal, surf, travel).map { it.await() }
        }
    }

    private suspend fun tidalFlatIndex(date: String, stationCode: String?, tideData: TideData?): OceanIndex {
        val range = tideData?.let { it.maxLevel - it.minLevel } ?: 0
        if (tideData != null && range > 100) {
            val exposure = (tideData.maxLevel - tideData.currentLevel).toFloat() / range.toFloat()
            val (grade, gradeLabel) = when {
                exposure >= 0.67f -> IndexGrade.VERY_GOOD to "노출 매우 많음"
                exposure >= 0.33f -> IndexGrade.GOOD      to "노출 보통"
                else              -> IndexGrade.FAIR      to "노출 적음"
            }
            return OceanIndex(
                type       = IndexType.TIDAL_FLAT,
                grade      = grade,
                gradeLabel = gradeLabel,
                stats      = listOf(
                    "노출도" to "${(exposure * 100).toInt()}%",
                    "수위"  to "${tideData.currentLevel}cm"
                ),
                beachName   = null,
                date        = SimpleDateFormat("M월 d일", Locale.KOREA).format(Date()),
                isAvailable = true
            )
        }
        return fetch(date, stationCode, IndexType.TIDAL_FLAT) { api.getTidalFlatForecast(key, it, date) }
    }

    // obs_post_nm 키워드로 지역 구분 (KHOA 지수 API의 obs_code는 DT_XXXX 조위계 코드와 무관)
    private val regionKeywords = mapOf(
        StationRegion.WEST  to listOf("인천", "평택", "보령", "군산", "목포", "태안", "당진", "부안", "서해"),
        StationRegion.SOUTH to listOf("여수", "부산", "통영", "거제", "완도", "남해", "진도", "고흥", "거문"),
        StationRegion.EAST  to listOf("강릉", "속초", "동해", "울산", "포항", "삼척", "고성", "죽변", "영덕"),
        StationRegion.JEJU  to listOf("제주", "서귀포")
    )

    override suspend fun getRegionGrades(
        date: String,
        type: IndexType
    ): List<Pair<StationRegion, OceanIndex>> {
        if (key.isBlank()) throw IllegalStateException("API 키가 설정되지 않았습니다")

        // obsCode=null → 전국 모든 관측소 데이터 한 번에 가져옴, 그 후 지역 키워드로 분류
        val allItems: List<KhoaIndexItem> = when (type) {
            IndexType.BEACH_SWIM   -> api.getBeachForecast(key, null, date, 100)
            IndexType.SEA_FISHING  -> api.getFishingForecast(key, null, date, 100)
            IndexType.SEASICKNESS  -> api.getSeasicknessForecast(key, null, date, 100)
            IndexType.SCUBA_DIVING -> api.getScubaForecast(key, null, date, 100)
            IndexType.TIDAL_FLAT   -> api.getTidalFlatForecast(key, null, date, 100)
            IndexType.SURFING      -> api.getSurfingForecast(key, null, date, 100)
            IndexType.SEA_TRAVEL   -> api.getSeaTravelForecast(key, null, date, 100)
        }.body?.items?.item ?: emptyList()

        return StationRegion.values().map { region ->
            val keywords = regionKeywords[region] ?: emptyList()
            val item = allItems.firstOrNull { item ->
                keywords.any { kw -> item.obsPostNm?.contains(kw) == true }
            }
            region to (item?.toDomain(type) ?: unavailableIndex(type))
        }
    }

    private suspend fun fetch(
        date: String,
        stationCode: String?,
        type: IndexType,
        call: suspend (String?) -> com.koretide.app.data.remote.dto.KhoaIndexResponse
    ): OceanIndex = try {
        val items = call(stationCode).body?.items?.item ?: emptyList()
        val item  = items.firstOrNull { it.obsCode == stationCode }
                  ?: items.firstOrNull()
        item?.toDomain(type) ?: unavailableIndex(type)
    } catch (e: Exception) {
        unavailableIndex(type)
    }

    private fun KhoaIndexItem.toDomain(type: IndexType) = OceanIndex(
        type      = type,
        grade     = IndexGrade.fromString(fcstGrade),
        stats     = statsFor(type),
        beachName = obsPostNm,
        date      = fcstDate?.let { formatDate(it) },
        isAvailable = true
    )

    private fun KhoaIndexItem.statsFor(type: IndexType): List<Pair<String, String>> = buildList {
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

    private fun unavailableIndex(type: IndexType) = OceanIndex(
        type        = type,
        grade       = null,
        stats       = emptyList(),
        beachName   = null,
        date        = null,
        isAvailable = false
    )
}
