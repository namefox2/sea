package com.koretide.app.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koretide.app.domain.model.StationRegion
import com.koretide.app.domain.model.TideData
import com.koretide.app.domain.usecase.GetAllStationsUseCase
import com.koretide.app.domain.usecase.GetTideUseCase
import com.koretide.app.domain.usecase.SearchStationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchStationsUseCase: SearchStationsUseCase,
    private val getAllStationsUseCase: GetAllStationsUseCase,
    private val getTideUseCase: GetTideUseCase
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedRegion = MutableStateFlow<StationRegion?>(null)
    val selectedRegion: StateFlow<StationRegion?> = _selectedRegion.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private val _tideMap = MutableStateFlow<Map<String, TideData>>(emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val stations = combine(_query, _selectedRegion) { q, r -> Pair(q, r) }
        .flatMapLatest { (q, r) -> searchStationsUseCase(q, r) }

    val stationItems: StateFlow<List<StationAdapter.StationItem>> =
        combine(stations, _tideMap) { list, tideMap ->
            list.map { station ->
                val tide = tideMap[station.code]
                StationAdapter.StationItem(
                    station      = station,
                    tidePercent  = tide?.tidePercent
                        ?: station.lastTideLevel?.let { (it.toFloat() / 600f).coerceIn(0f, 1f) },
                    tideStatus   = tide?.tideStatus,
                    windBft      = null,
                    waterLevelCm = tide?.currentLevel ?: station.lastTideLevel
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshStations()
        loadTideDataForAll()
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun setRegionFilter(region: StationRegion?) {
        _selectedRegion.value = region
    }

    private fun refreshStations() {
        viewModelScope.launch {
            _isLoading.value = true
            _loadError.value = null
            try {
                getAllStationsUseCase.refresh()
            } catch (e: Exception) {
                Log.w("SearchViewModel", "station refresh failed", e)
                _loadError.value = "자료를 가져오는데 실패했습니다"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadTideDataForAll() {
        viewModelScope.launch {
            stations.collect { stationList ->
                stationList.forEach { station ->
                    if (_tideMap.value.containsKey(station.code)) return@forEach
                    launch {
                        runCatching { getTideUseCase(station.code) }
                            .onSuccess { tideData ->
                                _tideMap.update { it + (station.code to tideData) }
                            }
                            .onFailure { Log.w("SearchViewModel", "tide fetch failed: ${station.code}") }
                    }
                }
            }
        }
    }
}
