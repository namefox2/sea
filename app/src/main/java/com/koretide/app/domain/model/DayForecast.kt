package com.koretide.app.domain.model

data class DayForecast(
    val label: String,       // "오늘", "내일", "모레", "+3일" …
    val waterTemp: Float?,   // °C
    val waveHeight: Float?,  // m
    val windSpeed: Float?    // m/s
)
