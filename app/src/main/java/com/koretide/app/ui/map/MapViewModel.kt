package com.koretide.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koretide.app.data.MarineActivityData
import com.koretide.app.domain.model.ActivitySpot
import com.koretide.app.domain.model.ActivityType
import com.koretide.app.domain.model.IndexType
import com.koretide.app.domain.model.OceanIndex
import com.koretide.app.domain.model.Station
import com.koretide.app.domain.model.StationRegion
import com.koretide.app.domain.repository.OceanIndexRepository
import com.koretide.app.domain.usecase.GetAllStationsUseCase
import com.koretide.app.util.GeoUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    getAllStationsUseCase: GetAllStationsUseCase,
    private val oceanIndexRepo: OceanIndexRepository
) : ViewModel() {

    private val _activityFilter = MutableStateFlow<ActivityType?>(null)

    // 활동 탭 필터에 따라 관측소 목록을 노출. 전체/물멍일 때만 관측소를 보여주고,
    // 낚시·서핑 등 활동 필터에선 관측소 대신 활동 스팟만 표시한다.
    val stations: StateFlow<List<Station>> = combine(
        getAllStationsUseCase(),
        _activityFilter
    ) { all, activity ->
        when (activity) {
            null, ActivityType.HIGH_TIDE -> all   // 전체 / 물멍
            else -> emptyList()                   // 낚시/서핑/해수욕 등
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activitySpots: StateFlow<List<ActivitySpot>> = _activityFilter
        .map { type ->
            when (type) {
                null -> MarineActivityData.getAllSpots()      // 전체
                ActivityType.HIGH_TIDE -> emptyList()         // 물멍
                else -> MarineActivityData.getSpots(type)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setActivityFilter(type: ActivityType?) { _activityFilter.value = type }

    // 활동 스팟(정적 데이터)에는 지수 등급이 없으므로, 스팟의 타입+지역에 해당하는
    // 지수 목록을 API에서 받아 스팟 좌표와 가장 가까운 항목의 등급을 반환한다.
    suspend fun indexFor(spot: ActivitySpot): OceanIndex? {
        val type = spot.type.toIndexType() ?: return null
        val region = StationRegion.fromCoords(spot.lat, spot.lng)
        val date = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
        return runCatching {
            oceanIndexRepo.getBeachIndicesForRegion(date, type, region)
                .minByOrNull { GeoUtils.distSq(it.lat, it.lon, spot.lat, spot.lng) }
        }.getOrNull()?.index
    }

    private fun ActivityType.toIndexType(): IndexType? = when (this) {
        ActivityType.FISHING    -> IndexType.SEA_FISHING
        ActivityType.SURFING    -> IndexType.SURFING
        ActivityType.TIDAL_FLAT -> IndexType.TIDAL_FLAT
        ActivityType.SWIMMING   -> IndexType.BEACH_SWIM
        ActivityType.SCUBA      -> IndexType.SCUBA_DIVING
        ActivityType.SEA_TRAVEL -> IndexType.SEA_TRAVEL
        ActivityType.HIGH_TIDE  -> null   // 관측소(물멍) — 지수 대상 아님
    }
}
