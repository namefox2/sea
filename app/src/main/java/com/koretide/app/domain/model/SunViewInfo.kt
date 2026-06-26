package com.koretide.app.domain.model

data class SunViewInfo(
    val spot: SunriseSpot,
    val date: String,               // YYYYMMDD
    val sunriseTime: String?,       // "HH:MM" local time
    val sunsetTime: String?,        // "HH:MM" local time
    val sunriseAzimuth: Double,     // degrees from North (clockwise)
    val sunsetAzimuth: Double,      // degrees from North (clockwise)
    val sunriseOverSea: Boolean,    // true when sunrise is in the sea-facing direction
    val sunsetOverSea: Boolean      // true when sunset is in the sea-facing direction
)
