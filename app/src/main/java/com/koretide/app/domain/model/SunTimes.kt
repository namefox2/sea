package com.koretide.app.domain.model

data class SunTimes(
    val sunriseHour:  Float,  // e.g. 5.2f  = 05:12
    val sunsetHour:   Float,  // e.g. 19.95f = 19:57
    val moonriseHour: Float,  // < 0 = no data for today
    val moonsetHour:  Float   // cross-midnight-adjusted: e.g. 25.48f if moonset is 01:29 next day; < 0 = no data
)
