package com.koretide.app.data

import com.koretide.app.domain.model.ActivitySpot
import com.koretide.app.domain.model.ActivityType

object MarineActivityData {

    private val spots = listOf(
        // 바다낚시
        ActivitySpot("통영 낚시", ActivityType.FISHING, 34.86, 128.43),
        ActivitySpot("포항 낚시", ActivityType.FISHING, 35.99, 129.36),
        ActivitySpot("여수 낚시", ActivityType.FISHING, 34.76, 127.66),
        ActivitySpot("태안 낚시", ActivityType.FISHING, 36.75, 126.30),
        ActivitySpot("완도 낚시", ActivityType.FISHING, 34.31, 126.75),
        ActivitySpot("속초 낚시", ActivityType.FISHING, 38.21, 128.59),
        ActivitySpot("군산 낚시", ActivityType.FISHING, 35.97, 126.71),
        ActivitySpot("제주 낚시", ActivityType.FISHING, 33.50, 126.53),
        ActivitySpot("삼척 낚시", ActivityType.FISHING, 37.45, 129.17),
        ActivitySpot("목포 낚시", ActivityType.FISHING, 34.81, 126.38),

        // 서핑
        ActivitySpot("양양 죽도해변", ActivityType.SURFING, 38.00, 128.62),
        ActivitySpot("양양 인구해변", ActivityType.SURFING, 38.06, 128.68),
        ActivitySpot("부산 송정", ActivityType.SURFING, 35.18, 129.20),
        ActivitySpot("강릉 경포", ActivityType.SURFING, 37.80, 128.91),
        ActivitySpot("제주 중문", ActivityType.SURFING, 33.24, 126.41),
        ActivitySpot("고성 봉포", ActivityType.SURFING, 38.34, 128.54),
        ActivitySpot("울산 진하", ActivityType.SURFING, 35.35, 129.35),

        // 갯벌체험
        ActivitySpot("서산 천수만", ActivityType.TIDAL_FLAT, 36.69, 126.61),
        ActivitySpot("보령 무창포", ActivityType.TIDAL_FLAT, 36.31, 126.55),
        ActivitySpot("신안 증도", ActivityType.TIDAL_FLAT, 34.96, 126.22),
        ActivitySpot("고창 동호", ActivityType.TIDAL_FLAT, 35.41, 126.34),
        ActivitySpot("강화 동막", ActivityType.TIDAL_FLAT, 37.61, 126.43),
        ActivitySpot("안산 대부도", ActivityType.TIDAL_FLAT, 37.28, 126.66),
        ActivitySpot("태안 몽산포", ActivityType.TIDAL_FLAT, 36.63, 126.37),

        // 해수욕
        ActivitySpot("부산 해운대", ActivityType.SWIMMING, 35.16, 129.16),
        ActivitySpot("강릉 경포대", ActivityType.SWIMMING, 37.80, 128.91),
        ActivitySpot("양양 낙산", ActivityType.SWIMMING, 38.12, 128.70),
        ActivitySpot("제주 협재", ActivityType.SWIMMING, 33.39, 126.24),
        ActivitySpot("보령 대천", ActivityType.SWIMMING, 36.31, 126.55),
        ActivitySpot("부산 광안리", ActivityType.SWIMMING, 35.15, 129.12),
        ActivitySpot("속초 속초해변", ActivityType.SWIMMING, 38.21, 128.59),
        ActivitySpot("여수 만성리", ActivityType.SWIMMING, 34.71, 127.73),
        ActivitySpot("제주 함덕", ActivityType.SWIMMING, 33.54, 126.67),
        ActivitySpot("동해 망상", ActivityType.SWIMMING, 37.56, 129.12),

        // 스킨스쿠버
        ActivitySpot("제주 우도", ActivityType.SCUBA, 33.49, 126.95),
        ActivitySpot("통영 한산도", ActivityType.SCUBA, 34.76, 128.53),
        ActivitySpot("포항 구룡포", ActivityType.SCUBA, 35.99, 129.57),
        ActivitySpot("거제 해금강", ActivityType.SCUBA, 34.79, 128.74),
        ActivitySpot("완도 청산도", ActivityType.SCUBA, 34.16, 126.87),
        ActivitySpot("제주 서귀포", ActivityType.SCUBA, 33.25, 126.56),
        ActivitySpot("삼척 이사부", ActivityType.SCUBA, 37.44, 129.16)
    )

    // 지수 API용 좌표 데이터셋(IndexPlaceData/BeachPlaceData)을 같은 카테고리의 ActivitySpot으로 변환.
    // "위경도 좌표가 있는 모든 지점"을 지도에 노출하기 위함.
    private fun indexSpots(type: ActivityType): List<ActivitySpot> = when (type) {
        ActivityType.SURFING    -> SurfingPlaceData.places.map  { ActivitySpot(it.name, type, it.lat, it.lon) }
        ActivityType.TIDAL_FLAT -> TidalFlatPlaceData.places.map { ActivitySpot(it.name, type, it.lat, it.lon) }
        ActivityType.SCUBA      -> ScubaPlaceData.places.map    { ActivitySpot(it.name, type, it.lat, it.lon) }
        ActivityType.SEA_TRAVEL -> SeaTravelPlaceData.places.map { ActivitySpot(it.name, type, it.lat, it.lon) }
        ActivityType.SWIMMING   -> BeachPlaceData.places.map    { ActivitySpot(it.name, type, it.lat, it.lon) }
        else                    -> emptyList()
    }

    fun getSpots(type: ActivityType): List<ActivitySpot> =
        dedup(spots.filter { it.type == type } + indexSpots(type))

    fun getAllSpots(): List<ActivitySpot> =
        ActivityType.values()
            .filter { it != ActivityType.HIGH_TIDE }   // 관측소는 별도 레이어
            .flatMap { getSpots(it) }

    // 같은 카테고리 내 ~1km(0.01°) 이내 중복 지점은 핀이 겹치지 않도록 하나만 남김
    private fun dedup(list: List<ActivitySpot>): List<ActivitySpot> {
        val seen = HashSet<String>()
        return list.filter { s ->
            seen.add("${s.type}:${Math.round(s.lat * 100)}:${Math.round(s.lng * 100)}")
        }
    }
}
