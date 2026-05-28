package com.koretide.app.domain.repository

import com.koretide.app.domain.model.WindData

interface WeatherRepository {
    suspend fun getWindData(lat: Double, lng: Double, stationCode: String): WindData
}
