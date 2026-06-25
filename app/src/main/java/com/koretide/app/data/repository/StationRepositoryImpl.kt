package com.koretide.app.data.repository

import com.koretide.app.data.local.dao.StationDao
import com.koretide.app.data.local.entity.StationEntity
import com.koretide.app.data.remote.MockDataSource
import com.koretide.app.domain.model.Station
import com.koretide.app.domain.model.StationRegion
import com.koretide.app.domain.repository.StationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StationRepositoryImpl @Inject constructor(
    private val stationDao: StationDao
) : StationRepository {

    override fun getAllStations(): Flow<List<Station>> =
        stationDao.getAllStations().map { list -> list.map { it.toDomain() } }

    override fun searchStations(query: String, region: StationRegion?): Flow<List<Station>> =
        stationDao.searchStations(query, region?.name).map { list -> list.map { it.toDomain() } }

    override suspend fun refreshStations() {
        val count = stationDao.count()
        if (count > 0) return
        seedMockStations()
    }

    override suspend fun getStation(code: String): Station? =
        stationDao.getStation(code)?.toDomain()

    private suspend fun seedMockStations() {
        stationDao.upsertAll(MockDataSource.stations.map { StationEntity.fromDomain(it) })
    }
}
