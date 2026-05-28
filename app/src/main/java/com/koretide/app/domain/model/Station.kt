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
    JEJU("제주")
}
