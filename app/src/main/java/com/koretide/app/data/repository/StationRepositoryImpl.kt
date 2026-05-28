package com.koretide.app.data.repository

import com.koretide.app.BuildConfig
import com.koretide.app.data.local.dao.StationDao
import com.koretide.app.data.local.entity.StationEntity
import com.koretide.app.data.remote.KhoaApiService
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
    private val stationDao: StationDao,
    private val khoaApi: KhoaApiService
) : StationRepository {

    override fun getAllStations(): Flow<List<Station>> =
        stationDao.getAllStations().map { list -> list.map { it.toDomain() } }

    override fun searchStations(query: String, region: StationRegion?): Flow<List<Station>> =
        stationDao.searchStations(query, region?.name).map { list -> list.map { it.toDomain() } }

    override suspend fun refreshStations() {
        val count = stationDao.count()
        if (count > 0) return

        if (BuildConfig.KHOA_API_KEY.isBlank()) {
            seedMockStations()
            return
        }

        try {
            val response = khoaApi.getStationList(BuildConfig.KHOA_API_KEY)
            val entities = response.result?.data?.mapNotNull { item ->
                val code = item.obsCode ?: return@mapNotNull null
                val name = item.obsName ?: return@mapNotNull null
                val lat = item.lat?.toDoubleOrNull() ?: return@mapNotNull null
                val lng = item.lon?.toDoubleOrNull() ?: return@mapNotNull null
                val region = resolveRegion(code)
                StationEntity(code, name, region.name, lat, lng)
            } ?: emptyList()

            if (entities.isEmpty()) seedMockStations()
            else stationDao.upsertAll(entities)
        } catch (e: Exception) {
            seedMockStations()
        }
    }

    override suspend fun getStation(code: String): Station? =
        stationDao.getStation(code)?.toDomain()

    private suspend fun seedMockStations() {
        stationDao.upsertAll(MockDataSource.stations.map { StationEntity.fromDomain(it) })
    }

    private fun resolveRegion(code: String): StationRegion {
        val num = code.removePrefix("DT_").toIntOrNull() ?: 0
        return when {
            num in 1..12 || num in 69..70 -> StationRegion.WEST
            num in 13..24 || num in 63..68 -> StationRegion.SOUTH
            num in 25..34 || num in 39..40 -> StationRegion.EAST
            num in 35..38 || num in 41..44 -> StationRegion.JEJU
            else -> StationRegion.SOUTH
        }
    }
}
