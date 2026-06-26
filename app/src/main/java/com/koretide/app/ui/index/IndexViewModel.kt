package com.koretide.app.ui.index

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koretide.app.data.BeachPlaceData
import com.koretide.app.domain.model.BeachIndexItem
import com.koretide.app.domain.model.DayForecast
import com.koretide.app.domain.model.IndexType
import com.koretide.app.domain.model.OceanIndex
import com.koretide.app.domain.model.Station
import com.koretide.app.domain.model.StationRegion
import com.koretide.app.domain.model.TideData
import com.koretide.app.domain.repository.OceanIndexRepository
import com.koretide.app.domain.usecase.GetNearestStationUseCase
import com.koretide.app.domain.usecase.GetOceanIndicesUseCase
import com.koretide.app.domain.usecase.GetSevenDayForecastUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed class IndexUiState {
    object Loading : IndexUiState()
    data class TypeList(
        val stationName: String?,
        val gradeByType: Map<IndexType, OceanIndex?>
    ) : IndexUiState()
    data class RegionList(
        val type: IndexType,
        val grades: List<Pair<StationRegion, OceanIndex>>
    ) : IndexUiState()
    data class BeachList(
        val type: IndexType,
        val region: StationRegion,
        val beaches: List<BeachIndexItem>
    ) : IndexUiState()
    data class Forecast(
        val type: IndexType,
        val region: StationRegion,
        val beachName: String?,
        val forecast: List<DayForecast>
    ) : IndexUiState()
    data class Error(val message: String) : IndexUiState()
}

sealed class IndexNav {
    object TypeList : IndexNav()
    data class RegionList(val type: IndexType) : IndexNav()
    data class BeachList(val type: IndexType, val region: StationRegion) : IndexNav()
    data class Forecast(val type: IndexType, val region: StationRegion) : IndexNav()
}

@HiltViewModel
class IndexViewModel @Inject constructor(
    private val getOceanIndices: GetOceanIndicesUseCase,
    private val getSevenDayForecast: GetSevenDayForecastUseCase,
    private val indexRepo: OceanIndexRepository,
    private val getNearestStation: GetNearestStationUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<IndexUiState>(IndexUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _watchStation = MutableSharedFlow<Station?>(extraBufferCapacity = 1)
    val watchStation: SharedFlow<Station?> = _watchStation.asSharedFlow()

    private val navStack = mutableListOf<IndexNav>(IndexNav.TypeList)
    private var cachedTypeList: IndexUiState.TypeList? = null
    private var cachedRegionList: IndexUiState.RegionList? = null
    private var cachedBeachList: IndexUiState.BeachList? = null
    private var currentForecastRegion: StationRegion? = null
    private var currentBeach: BeachIndexItem? = null

    fun loadTypeList(station: Station?, tideData: TideData?) {
        navStack.clear()
        navStack.add(IndexNav.TypeList)
        cachedRegionList = null
        cachedBeachList = null
        currentBeach = null

        viewModelScope.launch {
            _uiState.value = IndexUiState.Loading
            try {
                val gradeByType: Map<IndexType, OceanIndex?> = if (station != null) {
                    val region = station.region.representativeName()
                    val indices = getOceanIndices(region, station.code, station.lat, station.lng, tideData)
                    indices.associate { it.type to it }
                } else {
                    emptyMap()
                }
                val state = IndexUiState.TypeList(station?.name, gradeByType)
                cachedTypeList = state
                _uiState.value = state
            } catch (e: Exception) {
                val state = IndexUiState.TypeList(station?.name, emptyMap())
                cachedTypeList = state
                _uiState.value = state
            }
        }
    }

    fun selectType(type: IndexType) {
        navStack.add(IndexNav.RegionList(type))
        cachedBeachList = null
        currentBeach = null

        viewModelScope.launch {
            _uiState.value = IndexUiState.Loading
            try {
                val date = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
                val grades = indexRepo.getRegionGrades(date, type)
                val state = IndexUiState.RegionList(type, grades)
                cachedRegionList = state
                _uiState.value = state
            } catch (e: Exception) {
                _uiState.value = IndexUiState.Error("지수를 불러올 수 없습니다")
            }
        }
    }

    fun selectRegion(type: IndexType, region: StationRegion) {
        currentForecastRegion = region
        currentBeach = null
        if (type == IndexType.BEACH_SWIM) {
            navStack.add(IndexNav.BeachList(type, region))
            loadBeachList(type, region)
        } else {
            navStack.add(IndexNav.Forecast(type, region))
            loadForecast(type, region, null)
        }
    }

    fun selectBeach(beach: BeachIndexItem, type: IndexType, region: StationRegion) {
        navStack.add(IndexNav.Forecast(type, region))
        currentBeach = beach
        loadForecast(type, region, beach.name)
    }

    private fun loadBeachList(type: IndexType, region: StationRegion) {
        viewModelScope.launch {
            _uiState.value = IndexUiState.Loading
            try {
                val date = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
                val beaches = indexRepo.getBeachIndicesForRegion(date, type, region)
                val state = IndexUiState.BeachList(type, region, beaches)
                cachedBeachList = state
                _uiState.value = state
            } catch (e: Exception) {
                _uiState.value = IndexUiState.Error("지수를 불러올 수 없습니다")
            }
        }
    }

    private fun loadForecast(type: IndexType, region: StationRegion, beachName: String?) {
        viewModelScope.launch {
            _uiState.value = IndexUiState.Loading
            val forecast = getSevenDayForecast(beachName ?: region.displayName, region.displayName)
            _uiState.value = IndexUiState.Forecast(type, region, beachName, forecast)
        }
    }

    fun navigateBack(): Boolean {
        if (navStack.size <= 1) return false
        navStack.removeLast()
        when (val screen = navStack.last()) {
            is IndexNav.TypeList -> {
                currentBeach = null
                _uiState.value = cachedTypeList ?: IndexUiState.TypeList(null, emptyMap())
            }
            is IndexNav.RegionList -> {
                currentBeach = null
                val cached = cachedRegionList
                if (cached != null && cached.type == screen.type) {
                    _uiState.value = cached
                } else {
                    selectType(screen.type)
                }
            }
            is IndexNav.BeachList -> {
                currentBeach = null
                val cached = cachedBeachList
                if (cached != null && cached.type == screen.type && cached.region == screen.region) {
                    _uiState.value = cached
                } else {
                    loadBeachList(screen.type, screen.region)
                }
            }
            is IndexNav.Forecast -> {
                // Edge case: go back to beach list if available
                val cached = cachedBeachList
                if (cached != null) _uiState.value = cached
            }
        }
        return true
    }

    fun isAtRoot(): Boolean = navStack.size <= 1

    fun requestWatchForCurrentRegion() {
        viewModelScope.launch {
            val beach = currentBeach
            if (beach != null) {
                val station = getNearestStation(beach.lat, beach.lon)
                _watchStation.emit(station)
            } else {
                val region = currentForecastRegion ?: return@launch
                val repBeach = BeachPlaceData.representativeFor(region)
                val station = if (repBeach != null) getNearestStation(repBeach.lat, repBeach.lon) else null
                _watchStation.emit(station)
            }
        }
    }

    private fun StationRegion.representativeName(): String = when (this) {
        StationRegion.WEST  -> "인천"
        StationRegion.SOUTH -> "여수"
        StationRegion.EAST  -> "강릉"
        StationRegion.JEJU  -> "제주"
    }
}
