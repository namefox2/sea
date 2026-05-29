package com.koretide.app.domain.model

data class ActivitySpot(
    val name: String,
    val type: ActivityType,
    val lat: Double,
    val lng: Double,
    val description: String = ""
)
