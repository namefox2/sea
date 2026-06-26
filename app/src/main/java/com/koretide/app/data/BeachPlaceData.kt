package com.koretide.app.data

import com.koretide.app.domain.model.StationRegion
import kotlin.math.pow

data class BeachPlace(
    val code: String,             // HS1, HS2, ...
    val name: String,             // 대천해수욕장
    val lat: Double,
    val lon: Double,
    val region: StationRegion
)

object BeachPlaceData {

    val places: List<BeachPlace> = listOf(

        // ── 서해안 (WEST) ──────────────────────────────────────────────
        BeachPlace("HS1",  "대천해수욕장",     36.337, 126.503, StationRegion.WEST),
        BeachPlace("HS2",  "춘장대해수욕장",   36.163, 126.524, StationRegion.WEST),
        BeachPlace("HS3",  "무창포해수욕장",   36.235, 126.546, StationRegion.WEST),
        BeachPlace("HS4",  "꽃지해수욕장",     36.742, 126.334, StationRegion.WEST),
        BeachPlace("HS5",  "만리포해수욕장",   36.950, 126.133, StationRegion.WEST),
        BeachPlace("HS6",  "몽산포해수욕장",   36.647, 126.424, StationRegion.WEST),
        BeachPlace("HS7",  "우전해수욕장",     34.920, 126.030, StationRegion.WEST),
        BeachPlace("HS8",  "연포해수욕장",     36.643, 126.314, StationRegion.WEST),
        BeachPlace("HS9",  "어은돌해수욕장",   36.510, 126.356, StationRegion.WEST),
        BeachPlace("HS10", "신두리해수욕장",   37.038, 126.303, StationRegion.WEST),
        BeachPlace("HS11", "학암포해수욕장",   37.117, 126.290, StationRegion.WEST),
        BeachPlace("HS12", "가마미해수욕장",   34.893, 126.347, StationRegion.WEST),
        BeachPlace("HS15", "가계해수욕장",     35.170, 126.127, StationRegion.WEST),
        BeachPlace("HS16", "을왕리해수욕장",   37.468, 126.378, StationRegion.WEST),
        BeachPlace("HS51", "변산해수욕장",     35.685, 126.570, StationRegion.WEST),
        BeachPlace("HS52", "선유도해수욕장",   35.888, 126.428, StationRegion.WEST),
        BeachPlace("HS53", "구시포해수욕장",   35.656, 126.560, StationRegion.WEST),

        // ── 남해안 (SOUTH) ─────────────────────────────────────────────
        BeachPlace("HS17", "만성리해수욕장",   34.765, 127.633, StationRegion.SOUTH),
        BeachPlace("HS18", "상주해수욕장",     34.876, 128.043, StationRegion.SOUTH),
        BeachPlace("HS23", "율포해수욕장",     34.659, 126.956, StationRegion.SOUTH),
        BeachPlace("HS24", "송호해수욕장",     34.498, 126.649, StationRegion.SOUTH),
        BeachPlace("HS28", "구조라해수욕장",   34.838, 128.670, StationRegion.SOUTH),
        BeachPlace("HS29", "해운대해수욕장",   35.158, 129.159, StationRegion.SOUTH),
        BeachPlace("HS30", "광안리해수욕장",   35.153, 129.118, StationRegion.SOUTH),
        BeachPlace("HS31", "송도해수욕장",     35.090, 129.022, StationRegion.SOUTH),
        BeachPlace("HS32", "송정해수욕장(남해)", 35.179, 129.200, StationRegion.SOUTH),
        BeachPlace("HS33", "다대포해수욕장",   35.060, 128.967, StationRegion.SOUTH),
        BeachPlace("HS34", "일광해수욕장",     35.279, 129.244, StationRegion.SOUTH),
        BeachPlace("HS50", "진하해수욕장",     35.445, 129.391, StationRegion.SOUTH),
        BeachPlace("HS54", "임랑해수욕장",     35.283, 129.264, StationRegion.SOUTH),

        // ── 동해안 (EAST) ──────────────────────────────────────────────
        BeachPlace("HS35", "경포해수욕장",     37.798, 128.904, StationRegion.EAST),
        BeachPlace("HS36", "망상해수욕장",     37.594, 129.116, StationRegion.EAST),
        BeachPlace("HS37", "속초해수욕장",     38.209, 128.584, StationRegion.EAST),
        BeachPlace("HS38", "낙산해수욕장",     38.118, 128.627, StationRegion.EAST),
        BeachPlace("HS39", "영일대해수욕장",   36.048, 129.374, StationRegion.EAST),
        BeachPlace("HS40", "월포해수욕장",     36.315, 129.383, StationRegion.EAST),
        BeachPlace("HS41", "주문진해수욕장",   37.897, 128.823, StationRegion.EAST),
        BeachPlace("HS42", "고래불해수욕장",   36.594, 129.451, StationRegion.EAST),
        BeachPlace("HS43", "화진포해수욕장",   38.510, 128.448, StationRegion.EAST),
        BeachPlace("HS44", "관성해수욕장",     38.439, 128.458, StationRegion.EAST),
        BeachPlace("HS46", "삼척해수욕장",     37.460, 129.167, StationRegion.EAST),
        BeachPlace("HS47", "송지호해수욕장",   38.365, 128.499, StationRegion.EAST),
        BeachPlace("HS49", "칠포해수욕장",     36.234, 129.362, StationRegion.EAST),
        BeachPlace("HS55", "송정해수욕장(동해)", 37.898, 128.823, StationRegion.EAST),

        // ── 제주 (JEJU) ────────────────────────────────────────────────
        BeachPlace("HS19", "명사십리해수욕장", 33.483, 126.867, StationRegion.JEJU),
        BeachPlace("HS20", "함덕서우봉해수욕장", 33.547, 126.670, StationRegion.JEJU),
        BeachPlace("HS21", "협재해수욕장",     33.390, 126.239, StationRegion.JEJU),
        BeachPlace("HS22", "중문색달해수욕장", 33.244, 126.414, StationRegion.JEJU),
        BeachPlace("HS25", "이호테우해수욕장", 33.498, 126.433, StationRegion.JEJU),
        BeachPlace("HS26", "표선해비치해수욕장", 33.323, 126.845, StationRegion.JEJU)
    )

    // 지역별 대표 해수욕장 (물멍 관측소 탐색 기준)
    private val regionalRepresentative = mapOf(
        StationRegion.WEST  to "HS1",   // 대천해수욕장
        StationRegion.SOUTH to "HS29",  // 해운대해수욕장
        StationRegion.EAST  to "HS35",  // 경포해수욕장
        StationRegion.JEJU  to "HS20"   // 함덕서우봉해수욕장
    )

    fun findByCode(code: String): BeachPlace? = places.find { it.code == code }

    fun findByName(name: String): BeachPlace? = places.find { it.name == name }

    fun representativeFor(region: StationRegion): BeachPlace? =
        regionalRepresentative[region]?.let { findByCode(it) }

    /** 주어진 좌표에서 가장 가까운 해수욕장 (단순 유클리드 거리 비교). */
    fun nearest(lat: Double, lon: Double): BeachPlace? =
        places.minByOrNull { (it.lat - lat).pow(2) + (it.lon - lon).pow(2) }

    fun byRegion(region: StationRegion): List<BeachPlace> =
        places.filter { it.region == region }
}
