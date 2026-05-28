package com.koretide.app.domain.usecase

import com.koretide.app.domain.model.WindData
import com.koretide.app.domain.repository.WeatherRepository
import javax.inject.Inject

class GetWindUseCase @Inject constructor(
    private val repository: WeatherRepository
) {
    suspend operator fun invoke(lat: Double, lng: Double, stationCode: String): WindData =
        repository.getWindData(lat, lng, stationCode)
}
