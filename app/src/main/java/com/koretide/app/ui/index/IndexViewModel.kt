package com.koretide.app.ui.index

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koretide.app.domain.model.DayForecast
import com.koretide.app.domain.model.IndexType
import com.koretide.app.domain.model.OceanIndex
import com.koretide.app.domain.model.Station
import com.koretide.app.domain.model.StationRegion
import com.koretide.app.domain.model.TideData
import com.koretide.app.domain.repository.OceanIndexRepository
import com.koretide.app.domain.usecase.GetOceanIndicesUseCase
import com.koretide.app.domain.usecase.GetSevenDayForecastUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed class IndexUiState {
    object Loading : IndexUiState()

    // Screen 1: list of all index types (optional grade if station selected)
    data class TypeList(
        val stationName: String?,
        val gradeByType: Map<IndexType, OceanIndex?>
    ) : IndexUiState()

    // Screen 2: grades for all 4 regions for a specific index type
    data class RegionList(
        val type: IndexType,
        val grades: List<Pair<StationRegion, OceanIndex>>
    ) : IndexUiState()

    // Screen 3: 7-day forecast for a selected region
    data class Forecast(
        val type: IndexType,
        val region: StationRegion,
        val forecast: List<DayForecast>
    ) : IndexUiState()

    data class Error(val message: String) : IndexUiState()
}

sealed class IndexNav {
    object TypeList : IndexNav()
    data class RegionList(val type: IndexType) : IndexNav()
    data class Forecast(val type: IndexType, val region: StationRegion) : IndexNav()
}

@HiltViewModel
class IndexViewModel @Inject constructor(
    private val getOceanIndices: GetOceanIndicesUseCase,
    private val getSevenDayForecast: GetSevenDayForecastUseCase,
    private val indexRepo: OceanIndexRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<IndexUiState>(IndexUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val navStack = mutableListOf<IndexNav>(IndexNav.TypeList)
    private var cachedTypeList: IndexUiState.TypeList? = null
    private var cachedRegionList: IndexUiState.RegionList? = null

    fun loadTypeList(station: Station?, tideData: TideData?) {
        navStack.clear()
        navStack.add(IndexNav.TypeList)
        cachedRegionList = null

        viewModelScope.launch {
            _uiState.value = IndexUiState.Loading
            try {
                val gradeByType: Map<IndexType, OceanIndex?> = if (station != null) {
                    val region = station.region.representativeName()
                    val indices = getOceanIndices(region, station.code, tideData)
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
        navStack.add(IndexNav.Forecast(type, region))

        viewModelScope.launch {
            _uiState.value = IndexUiState.Loading
            val forecast = getSevenDayForecast(region.displayName, null)
            _uiState.value = IndexUiState.Forecast(type, region, forecast)
        }
    }

    // Returns true if handled (still in a sub-screen), false if already at root
    fun navigateBack(): Boolean {
        if (navStack.size <= 1) return false
        navStack.removeLast()
        when (val screen = navStack.last()) {
            is IndexNav.TypeList -> {
                _uiState.value = cachedTypeList ?: IndexUiState.TypeList(null, emptyMap())
            }
            is IndexNav.RegionList -> {
                val cached = cachedRegionList
                if (cached != null && cached.type == screen.type) {
                    _uiState.value = cached
                } else {
                    selectType(screen.type)
                }
            }
            is IndexNav.Forecast -> selectRegion(screen.type, screen.region)
        }
        return true
    }

    fun isAtRoot(): Boolean = navStack.size <= 1

    private fun StationRegion.representativeName(): String = when (this) {
        StationRegion.WEST  -> "인천"
        StationRegion.SOUTH -> "여수"
        StationRegion.EAST  -> "강릉"
        StationRegion.JEJU  -> "제주"
    }
}
