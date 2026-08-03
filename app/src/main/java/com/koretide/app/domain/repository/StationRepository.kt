package com.koretide.app.domain.repository

import com.koretide.app.domain.model.Station
import com.koretide.app.domain.model.StationRegion
import kotlinx.coroutines.flow.Flow

interface StationRepository {
    fun searchStations(query: String, region: StationRegion?): Flow<List<Station>>
    fun getAllStations(): Flow<List<Station>>
    suspend fun refreshStations()
}
