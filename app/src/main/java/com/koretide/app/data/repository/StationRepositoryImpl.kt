package com.koretide.app.data.repository

import android.util.Log
import com.koretide.app.BuildConfig
import com.koretide.app.data.local.dao.StationDao
import com.koretide.app.data.local.entity.StationEntity
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
        if (apiKey.isBlank()) throw IllegalStateException("API 키가 설정되지 않았습니다")
        val items = odCloudApi.getStations(serviceKey = apiKey).data.orEmpty()
        Log.d(TAG, "▶ refreshStations: API 응답 ${items.size}개")

        val typeBreakdown = items.groupBy { it.type ?: "null" }.mapValues { it.value.size }
        Log.d(TAG, "  관측소 유형별: $typeBreakdown")

        if (items.isEmpty()) throw Exception("관측소 데이터가 없습니다")

        val entities = items
            .filter { it.type == "조위관측소" }
            .mapNotNull { item ->
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
        Log.d(TAG, "  조위관측소 필터 후: ${entities.size}개 저장")
        stationDao.upsertAll(entities)
    }

    override suspend fun getStation(code: String): Station? =
        stationDao.getStation(code)?.toDomain()

    override suspend fun getNearestStation(lat: Double, lon: Double): Station? =
        stationDao.getAllStationsSnapshot()
            .minByOrNull { (it.lat - lat) * (it.lat - lat) + (it.lng - lon) * (it.lng - lon) }
            ?.toDomain()

    companion object { private const val TAG = "StationRepo" }

    // 좌표 기반 지역 판별 (공용 분류기 사용)
    private fun regionFromCoords(lat: Double, lon: Double): StationRegion =
        StationRegion.fromCoords(lat, lon)
}
