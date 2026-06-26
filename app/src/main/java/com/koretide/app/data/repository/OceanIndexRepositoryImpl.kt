package com.koretide.app.data.repository

import com.koretide.app.BuildConfig
import com.koretide.app.data.BeachPlaceData
import com.koretide.app.data.ScubaPlaceData
import com.koretide.app.data.SeaTravelPlaceData
import com.koretide.app.data.SurfingPlaceData
import com.koretide.app.data.TidalFlatPlaceData
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
        lat: Double?,
        lon: Double?,
        tideData: TideData?
    ): List<OceanIndex> {
        if (key.isBlank()) throw IllegalStateException("API 키가 설정되지 않았습니다")

        // 지수 유형별 가장 가까운 placeCode 결정
        val hsCode = if (lat != null && lon != null) BeachPlaceData.nearest(lat, lon)?.code else null
        val baCode = if (lat != null && lon != null) SeaTravelPlaceData.nearest(lat, lon)?.code else null
        val ssCode = if (lat != null && lon != null) ScubaPlaceData.nearest(lat, lon)?.code else null
        val srCode = if (lat != null && lon != null) SurfingPlaceData.nearest(lat, lon)?.code else null
        val tlCode = if (lat != null && lon != null) TidalFlatPlaceData.nearest(lat, lon)?.code else null

        return coroutineScope {
            val beach   = async { fetch(date, hsCode, IndexType.BEACH_SWIM)   { api.getBeachForecast(key, it, date) } }
            val fishing = async { fetch(date, hsCode, IndexType.SEA_FISHING)  { api.getFishingForecast(key, it, date) } }
            val sick    = async { fetch(date, hsCode, IndexType.SEASICKNESS)  { api.getSeasicknessForecast(key, it, date) } }
            val scuba   = async { fetch(date, ssCode, IndexType.SCUBA_DIVING) { api.getScubaForecast(key, it, date) } }
            val tidal   = async { tidalFlatIndex(date, tlCode, tideData) }
            val surf    = async { fetch(date, srCode, IndexType.SURFING)      { api.getSurfingForecast(key, it, date) } }
            val travel  = async { fetch(date, baCode, IndexType.SEA_TRAVEL)   { api.getSeaTravelForecast(key, it, date) } }
            listOf(beach, fishing, sick, scuba, tidal, surf, travel).map { it.await() }
        }
    }

    private suspend fun tidalFlatIndex(date: String, placeCode: String?, tideData: TideData?): OceanIndex {
        val range = tideData?.let { it.maxLevel - it.minLevel } ?: 0
        if (tideData != null && range > 100) {
            val exposure = (tideData.maxLevel - tideData.currentLevel).toFloat() / range.toFloat()
            val (grade, gradeLabel) = when {
                exposure >= 0.67f -> IndexGrade.VERY_GOOD to "노출 매우 많음"
                exposure >= 0.33f -> IndexGrade.GOOD      to "노출 보통"
                else              -> IndexGrade.FAIR       to "노출 적음"
            }
            return OceanIndex(
                type        = IndexType.TIDAL_FLAT,
                grade       = grade,
                gradeLabel  = gradeLabel,
                stats       = listOf(
                    "노출도" to "${(exposure * 100).toInt()}%",
                    "수위"  to "${tideData.currentLevel}cm"
                ),
                beachName   = null,
                date        = SimpleDateFormat("M월 d일", Locale.KOREA).format(Date()),
                isAvailable = true
            )
        }
        return fetch(date, placeCode, IndexType.TIDAL_FLAT) { api.getTidalFlatForecast(key, it, date) }
    }

    // bbchNm 키워드로 지역 구분 (placeCode가 응답에 포함되지 않으므로 이름으로 매핑)
    private val regionBeachKeywords = mapOf(
        StationRegion.WEST  to listOf("대천", "춘장대", "무창포", "꽃지", "만리포", "몽산포", "을왕리", "변산", "신두리", "어은돌", "학암포", "연포", "가계", "선유도", "구시포"),
        StationRegion.SOUTH to listOf("해운대", "광안리", "송도", "구조라", "다대포", "일광", "진하", "임랑", "상주해수욕장", "만성리", "율포", "송호"),
        StationRegion.EAST  to listOf("경포", "망상", "속초", "낙산", "영일대", "삼척", "주문진", "고래불", "화진포", "관성", "칠포", "송지호"),
        StationRegion.JEJU  to listOf("함덕", "협재", "중문", "이호", "표선", "명사십리")
    )

    override suspend fun getRegionGrades(
        date: String,
        type: IndexType
    ): List<Pair<StationRegion, OceanIndex>> {
        if (key.isBlank()) throw IllegalStateException("API 키가 설정되지 않았습니다")

        // placeCode=null → 전국 해수욕장 데이터 가져온 뒤 bbchNm 키워드로 지역 분류
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
            val keywords = regionBeachKeywords[region] ?: emptyList()
            val item = allItems.firstOrNull { item ->
                keywords.any { kw -> item.bbchNm?.contains(kw) == true }
            }
            region to (item?.toDomain(type) ?: unavailableIndex(type))
        }
    }

    private suspend fun fetch(
        date: String,
        placeCode: String?,
        type: IndexType,
        call: suspend (String?) -> com.koretide.app.data.remote.dto.KhoaIndexResponse
    ): OceanIndex = try {
        val items = call(placeCode).body?.items?.item ?: emptyList()
        val item  = items.firstOrNull()
        item?.toDomain(type) ?: unavailableIndex(type)
    } catch (e: Exception) {
        unavailableIndex(type)
    }

    private fun KhoaIndexItem.toDomain(type: IndexType) = OceanIndex(
        type        = type,
        grade       = IndexGrade.fromString(totalIndex),
        stats       = statsFor(type),
        beachName   = bbchNm,
        date        = predcYmd?.let { formatDate(it) },
        isAvailable = true
    )

    private fun KhoaIndexItem.statsFor(type: IndexType): List<Pair<String, String>> = buildList {
        when (type) {
            IndexType.BEACH_SWIM -> {
                avgWtem?.let  { add("수온"  to "${it}°C") }
                avgArtmp?.let { add("기온"  to "${it}°C") }
                maxWvhgt?.let { add("파고"  to "${it}m")  }
                maxWspd?.let  { add("풍속"  to "${it}m/s") }
            }
            IndexType.SEA_FISHING -> {
                maxWvhgt?.let { add("파고"  to "${it}m")  }
                maxWspd?.let  { add("풍속"  to "${it}m/s") }
            }
            IndexType.SEASICKNESS -> {
                maxWvhgt?.let { add("파고"  to "${it}m")  }
                maxWspd?.let  { add("풍속"  to "${it}m/s") }
            }
            IndexType.SCUBA_DIVING -> {
                avgWtem?.let  { add("수온"  to "${it}°C") }
                maxWvhgt?.let { add("파고"  to "${it}m")  }
            }
            IndexType.TIDAL_FLAT -> {
                avgArtmp?.let { add("기온"  to "${it}°C") }
                opnStat?.let  { add("상태"  to it) }
            }
            IndexType.SURFING -> {
                maxWvhgt?.let { add("파고"  to "${it}m")  }
                maxWspd?.let  { add("풍속"  to "${it}m/s") }
            }
            IndexType.SEA_TRAVEL -> {
                avgArtmp?.let { add("기온"  to "${it}°C") }
                maxWvhgt?.let { add("파고"  to "${it}m")  }
            }
        }
    }

    // yyyy-MM-dd 또는 yyyyMMdd 양쪽 형식 지원
    private fun formatDate(raw: String): String = runCatching {
        val fmt = if (raw.contains('-')) "yyyy-MM-dd" else "yyyyMMdd"
        SimpleDateFormat("M월 d일", Locale.KOREA)
            .format(SimpleDateFormat(fmt, Locale.KOREA).parse(raw)!!)
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
