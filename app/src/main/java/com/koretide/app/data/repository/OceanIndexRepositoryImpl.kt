package com.koretide.app.data.repository

import com.koretide.app.BuildConfig
import com.koretide.app.data.BeachPlaceData
import com.koretide.app.data.PlaceGazetteer
import com.koretide.app.data.ScubaPlaceData
import com.koretide.app.data.SeaTravelPlaceData
import com.koretide.app.data.SurfingPlaceData
import com.koretide.app.data.TidalFlatPlaceData
import com.koretide.app.data.remote.KhoaIndexApiService
import com.koretide.app.data.remote.dto.KhoaIndexItem
import com.koretide.app.domain.model.BeachIndexItem
import com.koretide.app.domain.model.IndexGrade
import com.koretide.app.domain.model.IndexType
import com.koretide.app.domain.model.OceanIndex
import com.koretide.app.domain.model.StationRegion
import com.koretide.app.domain.model.TideData
import com.koretide.app.domain.repository.OceanIndexRepository
import com.koretide.app.util.GeoUtils
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
            // 낚시/뱃멀미는 전용 placeCode 데이터셋이 없어 전체 응답에서 좌표로 가장 가까운 지점 선택
            val fishing = async { nearestIndex(date, lat, lon, IndexType.SEA_FISHING) }
            val sick    = async { nearestIndex(date, lat, lon, IndexType.SEASICKNESS) }
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

    override suspend fun getBeachIndicesForRegion(
        date: String,
        type: IndexType,
        region: StationRegion
    ): List<BeachIndexItem> {
        if (key.isBlank()) throw IllegalStateException("API 키가 설정되지 않았습니다")

        // 낚시/뱃멀미: 전용 해수욕장 좌표가 없으므로 API 응답의 좌표(lat/lot)로 지역 분류 후 그대로 나열.
        // 응답에 좌표가 없으면 이름으로 PlaceGazetteer에서 좌표를 빌려온다.
        if (type == IndexType.SEA_FISHING || type == IndexType.SEASICKNESS) {
            return fetchAllItems(type, date)
                .mapNotNull { item ->
                    val (la, lo) = item.resolvedCoords(type) ?: return@mapNotNull null
                    if (regionFromCoords(la, lo) != region) return@mapNotNull null
                    // 이름이 비면 좌표 라벨로 폴백 (아래 이름 기준 중복 제거가 좌표별로 유지되도록)
                    val nm = item.displayName(type)?.takeIf { it.isNotBlank() }
                        ?: "지점 %.3f, %.3f".format(la, lo)
                    BeachIndexItem(
                        code   = nm,
                        name   = nm,
                        lat    = la,
                        lon    = lo,
                        region = region,
                        index  = item.toDomain(type)
                    )
                }
                // 이름 기준 중복 제거(오전/오후 등). 뱃멀미는 출발항이 같아도 노선명이 달라
                // 좌표 기준으로 합치면 안 됨(인천발 6개 노선 등).
                .distinctBy { it.name }
        }

        val beaches = BeachPlaceData.byRegion(region)
        val allItems: List<KhoaIndexItem> = fetchAllItems(type, date, 100)
        return beaches.map { beach ->
            val item = allItems.minByOrNull { apiItem ->
                GeoUtils.distSq(apiItem.lat ?: 999.0, apiItem.lot ?: 999.0, beach.lat, beach.lon)
            }?.takeIf { apiItem ->
                GeoUtils.distSq(apiItem.lat ?: 999.0, apiItem.lot ?: 999.0, beach.lat, beach.lon) < 0.01
            }
            BeachIndexItem(
                code   = beach.code,
                name   = beach.name,
                lat    = beach.lat,
                lon    = beach.lon,
                region = beach.region,
                index  = item?.toDomain(type) ?: unavailableIndex(type)
            )
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

    // 전체 응답 조회. 낚시는 gubun(갯바위/선상)으로, 나머지는 placeCode=null로 조회.
    private suspend fun fetchAllItems(type: IndexType, date: String, rows: Int = 200): List<KhoaIndexItem> =
        when (type) {
            IndexType.SEA_FISHING -> {
                // 바다낚시는 placeCode가 아니라 gubun으로 조회 → 갯바위·선상 둘 다 합침
                val rock = api.getFishingForecast(key, gubun = "갯바위", numOfRows = rows).body?.items?.item ?: emptyList()
                val boat = api.getFishingForecast(key, gubun = "선상",   numOfRows = rows).body?.items?.item ?: emptyList()
                rock + boat
            }
            IndexType.BEACH_SWIM   -> api.getBeachForecast(key, null, date, rows).body?.items?.item ?: emptyList()
            IndexType.SEASICKNESS  -> api.getSeasicknessForecast(key, null, date, rows).body?.items?.item ?: emptyList()
            IndexType.SCUBA_DIVING -> api.getScubaForecast(key, null, date, rows).body?.items?.item ?: emptyList()
            IndexType.TIDAL_FLAT   -> api.getTidalFlatForecast(key, null, date, rows).body?.items?.item ?: emptyList()
            IndexType.SURFING      -> api.getSurfingForecast(key, null, date, rows).body?.items?.item ?: emptyList()
            IndexType.SEA_TRAVEL   -> api.getSeaTravelForecast(key, null, date, rows).body?.items?.item ?: emptyList()
        }

    // 좌표로 가장 가까운 지점의 지수 (낚시/뱃멀미 — 전용 placeCode 데이터셋이 없는 유형)
    private suspend fun nearestIndex(date: String, lat: Double?, lon: Double?, type: IndexType): OceanIndex {
        if (lat == null || lon == null) return unavailableIndex(type)
        return try {
            val item = fetchAllItems(type, date)
                .mapNotNull { it.resolvedCoords(type)?.let { c -> it to c } }
                .minByOrNull { (_, c) -> val dLat = c.first - lat; val dLon = c.second - lon; dLat * dLat + dLon * dLon }
                ?.first
            item?.toDomain(type) ?: unavailableIndex(type)
        } catch (e: Exception) {
            unavailableIndex(type)
        }
    }

    private fun KhoaIndexItem.toDomain(type: IndexType) = OceanIndex(
        type        = type,
        grade       = IndexGrade.fromString(totalIndex),
        stats       = statsFor(type),
        beachName   = displayName(type),
        date        = predcYmd?.let { formatDate(it) },
        isAvailable = true,
        opnStat     = opnStat
    )

    // 지수 유형별 표시 이름: 낚시는 seafsPstnNm(지점명), 뱃멀미는 nvgtNm(운항 노선명),
    // 그 외는 bbchNm(해수욕장명). (API 문서 기준 실제 응답 필드)
    private fun KhoaIndexItem.displayName(type: IndexType): String? = when (type) {
        IndexType.SEA_FISHING -> firstNonBlank(seafsPstnNm, bbchNm)
        IndexType.SEASICKNESS -> firstNonBlank(nvgtNm, bbchNm)
        else                  -> bbchNm
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }

    // 좌표 해석: 응답에 lat/lot가 있으면 사용, 없으면 이름으로 PlaceGazetteer에서 조회
    private fun KhoaIndexItem.resolvedCoords(type: IndexType): Pair<Double, Double>? {
        if (lat != null && lot != null) return lat to lot
        return PlaceGazetteer.coordsFor(displayName(type))
    }

    // 좌표 기반 지역 판별 (공용 분류기 사용)
    private fun regionFromCoords(lat: Double, lon: Double): StationRegion =
        StationRegion.fromCoords(lat, lon)

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
