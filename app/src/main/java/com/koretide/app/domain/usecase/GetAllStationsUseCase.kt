package com.koretide.app.domain.usecase

import com.koretide.app.domain.model.Station
import com.koretide.app.domain.repository.StationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllStationsUseCase @Inject constructor(
    private val repository: StationRepository
) {
    operator fun invoke(): Flow<List<Station>> = repository.getAllStations()
    suspend fun refresh() = repository.refreshStations()
}
