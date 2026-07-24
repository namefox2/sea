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
import com.koretide.app.util.BeaufortConverter
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

    // 관측소별 밀물/썰물·바람 정보를 API에서 가져오는 중인지 (첫 화면 로딩 표시용)
    private val _batchLoading = MutableStateFlow(false)
    val batchLoading: StateFlow<Boolean> = _batchLoading.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private val _batchLevels = MutableStateFlow<List<RecentTideLevel>>(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val stations = combine(_query, _selectedRegion) { q, r -> Pair(q, r) }
        .flatMapLatest { (q, r) -> searchStationsUseCase(q, r) }

    val stationItems: StateFlow<List<StationAdapter.StationItem>> =
        combine(stations, _batchLevels) { list, levels ->
            list.map { station ->
                // 좌표 최근접이 아닌 '관측소 코드'로 정확히 매칭한다. 코드가 없으면(=배치 조회
                // 실패) 이웃 값을 붙이지 않고 캐시된 마지막 수위(없으면 null)로 폴백한다.
                val match = levels.firstOrNull { it.code == station.code }
                val currentLevel = match?.levelCm ?: station.lastTideLevel
                // 조위%는 상세보기와 동일하게 '조석예보'의 저조~고조 범위 기준으로 계산한다
                // (dayMinCm=예보 저조, dayMaxCm=예보 고조). 예보가 없으면(min==max/null)
                // 조위%·간조/만조 라벨을 표시하지 않는다(null).
                val min = match?.dayMinCm
                val max = match?.dayMaxCm
                val tidePercent = if (currentLevel != null && min != null && max != null && max > min)
                    ((currentLevel - min).toFloat() / (max - min)).coerceIn(0f, 1f)
                else null
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
                    windBft      = match?.windSpeedMs?.let { BeaufortConverter.toBft(it) },
                    waterLevelCm = currentLevel
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            // 1) 캐시(디스크/메모리)가 있으면 즉시 표시 — 첫 화면 대기시간 제거
            runCatching { getBatchTideStatusUseCase.cached() }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { _batchLevels.value = it }
            // 2) 배치(관측소별 dtRecent)는 관측소 목록이 있어야 조회 가능하므로
            //    관측소 로드를 먼저 '완료'한 뒤 배치를 갱신한다.
            //    (이전엔 관측소 로드와 배치가 병렬로 시작돼, 첫 실행 시 배치가 빈 관측소
            //     목록을 읽어 수위/바람이 표시되지 않았다.)
            loadStations()
            doRefreshBatch()
        }
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun setRegionFilter(region: StationRegion?) {
        _selectedRegion.value = region
    }

    private suspend fun loadStations() {
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

    fun refreshBatch() {
        viewModelScope.launch { doRefreshBatch() }
    }

    // 캐시가 비어 있을 때(=보여줄 게 없을 때)만 로딩 인디케이터를 켠다.
    // 캐시가 이미 있으면 조용히 백그라운드 갱신만 한다.
    private suspend fun doRefreshBatch() {
        val hadData = _batchLevels.value.isNotEmpty()
        if (!hadData) _batchLoading.value = true
        runCatching { getBatchTideStatusUseCase() }
            .onSuccess { levels ->
                if (levels.isNotEmpty()) _batchLevels.value = levels
            }
            .onFailure { Log.w("SearchViewModel", "batch tide fetch failed", it) }
        _batchLoading.value = false
    }
}
