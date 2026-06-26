package com.koretide.app.domain.model

/**
 * A beach or coastal point with a defined sea-facing direction.
 * seaAzimuth: degrees from North (clockwise) toward the open sea.
 * azimuthTolerance: ± degrees within which the sun is considered "over the sea".
 */
data class SunriseSpot(
    val name: String,
    val lat: Double,
    val lon: Double,
    val seaAzimuth: Double,
    val azimuthTolerance: Double = 75.0
)
