package com.koretide.app.domain.model

data class WindData(
    val stationCode: String,
    val speedMs: Float,
    val beaufort: Int,
    val directionDeg: Float,
    val beaufortName: String,
    val waveHeightM: Float? = null
)
