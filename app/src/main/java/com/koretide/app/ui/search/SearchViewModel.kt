package com.koretide.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koretide.app.domain.model.StationRegion
import com.koretide.app.domain.usecase.GetAllStationsUseCase
import com.koretide.app.domain.usecase.GetTideUseCase
import com.koretide.app.domain.usecase.GetWindUseCase
import com.koretide.app.domain.usecase.SearchStationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchStationsUseCase: SearchStationsUseCase,
    private val getAllStationsUseCase: GetAllStationsUseCase,
    private val getTideUseCase: GetTideUseCase,
    private val getWindUseCase: GetWindUseCase
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedRegion = MutableStateFlow<StationRegion?>(null)
    val selectedRegion: StateFlow<StationRegion?> = _selectedRegion.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val stations = combine(_query, _selectedRegion) { q, r -> Pair(q, r) }
        .flatMapLatest { (q, r) -> searchStationsUseCase(q, r) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val stationItems: StateFlow<List<StationAdapter.StationItem>> = stations
        .mapLatest { list ->
            coroutineScope {
                list.map { station ->
                    async {
                        val tide = runCatching { getTideUseCase(station.code) }.getOrNull()
                        val wind = runCatching {
                            getWindUseCase(station.lat, station.lng, station.code)
                        }.getOrNull()
                        StationAdapter.StationItem(
                            station = station,
                            tidePercent = tide?.tidePercent
                                ?: station.lastTideLevel?.let { (it.toFloat() / 600f).coerceIn(0f, 1f) },
                            tideStatus = tide?.tideStatus,
                            windBft = wind?.beaufort,
                            waterLevelCm = tide?.currentLevel
                        )
                    }
                }.awaitAll()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshStations()
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
            try {
                getAllStationsUseCase.refresh()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
