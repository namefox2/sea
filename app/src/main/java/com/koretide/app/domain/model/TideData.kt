package com.koretide.app.domain.model

data class TideData(
    val stationCode: String,
    val currentLevel: Int,
    val maxLevel: Int,
    val minLevel: Int,
    val tidePercent: Float,
    val tideStatus: TideStatus,
    val highTideTime: String?,
    val lowTideTime: String?,
    val records: List<TideRecord> = emptyList()
) {
    val isStale: Boolean get() = false
}

enum class TideStatus(val displayName: String) {
    RISING("밀물"),
    FALLING("썰물"),
    HIGH_TIDE("만조"),
    LOW_TIDE("간조")
}
