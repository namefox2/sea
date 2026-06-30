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
        // 해역 폴리곤 (정점 = lat, lon 쌍의 평면 배열). 지도 GIS 방식: 좌표가 어느 폴리곤 안에
        // 있는지 ray-casting으로 판단. 우선순위 JEJU→EAST→SOUTH→WEST(겹치는 구간은 앞이 우선).
        // 동해 폴리곤의 서쪽 변은 부산(35.0,129.2)→고성(38.9,128.2) 사선이라, 해안선이 휘는
        // 동해안(속초·강릉~포항·울산)을 올바로 담고 부산 근처(가덕도 등)는 남해로 둔다.
        private val POLY_JEJU  = doubleArrayOf(31.5,124.0, 34.0,124.0, 34.0,127.6, 31.5,127.6)
        private val POLY_EAST  = doubleArrayOf(34.8,129.25, 38.9,128.2, 38.9,133.0, 34.8,133.0)
        private val POLY_SOUTH = doubleArrayOf(33.9,125.5, 35.5,125.5, 35.5,129.4, 33.9,129.4)
        private val POLY_WEST  = doubleArrayOf(34.0,123.0, 34.0,127.6, 39.6,127.6, 39.6,123.0)

        /** 위경도 → 해역. 폴리곤으로 판정하고, 어디에도 안 들면 위경도 휴리스틱으로 폴백. */
        fun fromCoords(lat: Double, lon: Double): StationRegion = when {
            inPolygon(lat, lon, POLY_JEJU)  -> JEJU
            inPolygon(lat, lon, POLY_EAST)  -> EAST
            inPolygon(lat, lon, POLY_SOUTH) -> SOUTH
            inPolygon(lat, lon, POLY_WEST)  -> WEST
            else                            -> heuristic(lat, lon)
        }

        private fun heuristic(lat: Double, lon: Double): StationRegion = when {
            lat < 33.9                                                     -> JEJU
            (lat >= 35.7 && lon >= 128.4) || (lat >= 35.0 && lon >= 129.1) -> EAST
            lat < 35.5 && lon >= 125.5                                     -> SOUTH
            else                                                          -> WEST
        }

        // ray-casting point-in-polygon. poly = [lat0,lon0, lat1,lon1, ...] (lat=Y, lon=X)
        private fun inPolygon(lat: Double, lon: Double, poly: DoubleArray): Boolean {
            var inside = false
            val n = poly.size / 2
            var j = n - 1
            for (i in 0 until n) {
                val yi = poly[i * 2];     val xi = poly[i * 2 + 1]
                val yj = poly[j * 2];     val xj = poly[j * 2 + 1]
                if ((yi > lat) != (yj > lat) &&
                    lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) {
                    inside = !inside
                }
                j = i
            }
            return inside
        }
    }
}
