package com.koretide.app.domain.usecase

import com.koretide.app.domain.model.Station
import com.koretide.app.domain.model.StationRegion
import com.koretide.app.domain.repository.StationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchStationsUseCase @Inject constructor(
    private val repository: StationRepository
) {
    operator fun invoke(query: String, region: StationRegion?): Flow<List<Station>> =
        repository.searchStations(query, region)
}
