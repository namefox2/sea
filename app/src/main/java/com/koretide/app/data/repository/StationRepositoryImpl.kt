package com.koretide.app.data.repository

import android.util.Log
import com.koretide.app.BuildConfig
import com.koretide.app.data.local.dao.StationDao
import com.koretide.app.data.local.entity.StationEntity
import com.koretide.app.data.remote.MockDataSource
import com.koretide.app.data.remote.OdCloudApi
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
    private val odCloudApi: OdCloudApi
) : StationRepository {

    private val apiKey get() = BuildConfig.KHOA_API_KEY

    override fun getAllStations(): Flow<List<Station>> =
        stationDao.getAllStations().map { list -> list.map { it.toDomain() } }

    override fun searchStations(query: String, region: StationRegion?): Flow<List<Station>> =
        stationDao.searchStations(query, region?.name).map { list -> list.map { it.toDomain() } }

    override suspend fun refreshStations() {
        if (stationDao.count() > 0) return
        if (apiKey.isBlank()) {
            seedMockStations()
            return
        }
        try {
            val items = odCloudApi.getStations(serviceKey = apiKey).data.orEmpty()
            Log.d("StationRepo", "API returned ${items.size} stations")
            val entities = items.mapNotNull { item ->
                val code = item.code ?: return@mapNotNull null
                val name = item.name ?: return@mapNotNull null
                val lat  = item.lat?.toDoubleOrNull() ?: return@mapNotNull null
                val lon  = item.lon?.toDoubleOrNull() ?: return@mapNotNull null
                StationEntity(
                    code   = code,
                    name   = name,
                    region = regionFromCoords(lat, lon).name,
                    lat    = lat,
                    lng    = lon
                )
            }
            if (entities.isNotEmpty()) {
                stationDao.upsertAll(entities)
            } else {
                seedMockStations()
            }
        } catch (e: Exception) {
            Log.w("StationRepo", "API fetch failed → seeding mock stations", e)
            seedMockStations()
        }
    }

    override suspend fun getStation(code: String): Station? =
        stationDao.getStation(code)?.toDomain()

    private suspend fun seedMockStations() {
        stationDao.upsertAll(MockDataSource.stations.map { StationEntity.fromDomain(it) })
    }

    // 좌표 기반 지역 판별
    private fun regionFromCoords(lat: Double, lon: Double): StationRegion = when {
        lat < 34.1                   -> StationRegion.JEJU   // 제주 (위도 34.1° 미만)
        lon >= 128.5                 -> StationRegion.EAST   // 동해 (경도 128.5° 이상)
        lat < 35.5 && lon >= 125.5  -> StationRegion.SOUTH  // 남해 (위도 35.5° 미만 + 경도 125.5° 이상)
        else                         -> StationRegion.WEST   // 서해
    }
}
