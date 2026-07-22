package com.koretide.app.util

import kotlin.math.cos

object GeoUtils {

    // 최근접 탐색용 근사 거리 제곱(도²). 경도차를 위도 코사인으로 보정해
    // '1° 위도 == 1° 경도'로 취급하던 편향을 없앤다(한국 위도에서 경도 ≈ 0.81× 위도).
    // 실제 거리(km)가 아니라 상대 비교·컷오프용 값이다.
    fun distSq(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = lat1 - lat2
        val dLon = (lon1 - lon2) * cos(Math.toRadians((lat1 + lat2) / 2.0))
        return dLat * dLat + dLon * dLon
    }
}
