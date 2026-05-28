package com.koretide.app.domain.model

data class TideRecord(
    val stationCode: String,
    val timestamp: Long,
    val waterLevel: Int,
    val isForecast: Boolean = false
)
