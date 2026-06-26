package com.koretide.app.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koretide.app.domain.model.StationRegion
import com.koretide.app.domain.usecase.GetAllStationsUseCase
import com.koretide.app.domain.usecase.SearchStationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchStationsUseCase: SearchStationsUseCase,
    private val getAllStationsUseCase: GetAllStationsUseCase
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedRegion = MutableStateFlow<StationRegion?>(null)
    val selectedRegion: StateFlow<StationRegion?> = _selectedRegion.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val stations = combine(_query, _selectedRegion) { q, r -> Pair(q, r) }
        .flatMapLatest { (q, r) -> searchStationsUseCase(q, r) }

    // 즉시 emit — API 호출 없이 DB 데이터만 사용
    // 조위/바람 실시간 정보는 지역 선택 후 TideWatchFragment에서 로드
    val stationItems: StateFlow<List<StationAdapter.StationItem>> = stations
        .map { list ->
            list.map { station ->
                StationAdapter.StationItem(
                    station = station,
                    tidePercent = station.lastTideLevel?.let { (it.toFloat() / 600f).coerceIn(0f, 1f) },
                    tideStatus = null,
                    windBft = null,
                    waterLevelCm = station.lastTideLevel
                )
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
}
