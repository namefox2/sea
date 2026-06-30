package com.koretide.app.domain.model

data class Station(
    val code: String,
    val name: String,
    val region: StationRegion,
    val lat: Double,
    val lng: Double,
    val lastTideLevel: Int? = null,
    val lastUpdated: Long? = null
)

enum class StationRegion(val displayName: String) {
    WEST("서해안"),
    SOUTH("남해안"),
    EAST("동해안"),
    JEJU("제주");

    companion object {
        /**
         * 위경도 기반 해역 분류.
         * 동해안은 북부(속초~포항)가 경도 ~128.4°, 남부(울산~기장)가 ~129.1°로 해안선이 휘므로
         * 단일 경도 임계값으로는 부산 근처(가덕도 등)를 동해로 오분류한다 → 위도별 분기 사용.
         *   가덕도(35.0, 128.8) → 남해, 강릉(37.8, 128.9)·울산(35.5, 129.4) → 동해
         */
        fun fromCoords(lat: Double, lon: Double): StationRegion = when {
            lat < 33.9                                                     -> JEJU
            (lat >= 35.7 && lon >= 128.4) || (lat >= 35.0 && lon >= 129.1) -> EAST
            lat < 35.5 && lon >= 125.5                                     -> SOUTH
            else                                                          -> WEST
        }
    }
}
