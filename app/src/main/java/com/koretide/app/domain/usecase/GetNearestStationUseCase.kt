package com.koretide.app.domain.usecase

import com.koretide.app.domain.model.Station
import com.koretide.app.domain.repository.StationRepository
import javax.inject.Inject

class GetNearestStationUseCase @Inject constructor(
    private val repo: StationRepository
) {
    suspend operator fun invoke(lat: Double, lon: Double): Station? =
        repo.getNearestStation(lat, lon)
}
