package com.koretide.app.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koretide.app.domain.model.RecentTideLevel
import com.koretide.app.domain.model.StationRegion
import com.koretide.app.domain.model.TideStatus
import com.koretide.app.domain.usecase.GetAllStationsUseCase
import com.koretide.app.domain.usecase.GetBatchTideStatusUseCase
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
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchStationsUseCase: SearchStationsUseCase,
    private val getAllStationsUseCase: GetAllStationsUseCase,
    private val getBatchTideStatusUseCase: GetBatchTideStatusUseCase
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedRegion = MutableStateFlow<StationRegion?>(null)
    val selectedRegion: StateFlow<StationRegion?> = _selectedRegion.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private val _batchLevels = MutableStateFlow<List<RecentTideLevel>>(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val stations = combine(_query, _selectedRegion) { q, r -> Pair(q, r) }
        .flatMapLatest { (q, r) -> searchStationsUseCase(q, r) }

    val stationItems: StateFlow<List<StationAdapter.StationItem>> =
        combine(stations, _batchLevels) { list, levels ->
            list.map { station ->
                val match = levels.nearestTo(station.lat, station.lng)
                val currentLevel = match?.levelCm ?: station.lastTideLevel
                val tidePercent = currentLevel?.let { lvl ->
                    ((lvl - 50f) / 550f).coerceIn(0f, 1f)
                }
                val tideStatus = tidePercent?.let {
                    when {
                        it > 0.88f -> TideStatus.HIGH_TIDE
                        it < 0.12f -> TideStatus.LOW_TIDE
                        else       -> null
                    }
                }
                StationAdapter.StationItem(
                    station      = station,
                    tidePercent  = tidePercent,
                    tideStatus   = tideStatus,
                    windBft      = match?.windSpeedMs?.let { speedToBeaufort(it) },
                    waterLevelCm = currentLevel
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshStations()
        refreshBatch()
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

    fun refreshBatch() {
        viewModelScope.launch {
            runCatching { getBatchTideStatusUseCase() }
                .onSuccess { levels ->
                    if (levels.isNotEmpty()) _batchLevels.value = levels
                }
                .onFailure { Log.w("SearchViewModel", "batch tide fetch failed", it) }
        }
    }

    private fun List<RecentTideLevel>.nearestTo(lat: Double, lng: Double): RecentTideLevel? =
        minByOrNull { (it.lat - lat) * (it.lat - lat) + (it.lon - lng) * (it.lon - lng) }

    private fun speedToBeaufort(ms: Float): Int = when {
        ms < 0.3f  -> 0
        ms < 1.5f  -> 1
        ms < 3.3f  -> 2
        ms < 5.5f  -> 3
        ms < 7.9f  -> 4
        ms < 10.7f -> 5
        ms < 13.8f -> 6
        ms < 17.1f -> 7
        ms < 20.7f -> 8
        ms < 24.4f -> 9
        ms < 28.4f -> 10
        ms < 32.6f -> 11
        else       -> 12
    }
}
