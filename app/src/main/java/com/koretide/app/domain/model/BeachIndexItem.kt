package com.koretide.app.domain.model

data class BeachIndexItem(
    val code: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val region: StationRegion,
    val index: OceanIndex
)
