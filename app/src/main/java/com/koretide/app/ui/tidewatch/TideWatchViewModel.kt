package com.koretide.app.ui.tidewatch

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koretide.app.domain.model.TideData
import com.koretide.app.domain.model.WindData
import com.koretide.app.domain.model.SunTimes
import com.koretide.app.domain.usecase.GetRenderSunTimesUseCase
import com.koretide.app.domain.usecase.GetTideUseCase
import com.koretide.app.domain.usecase.GetWindUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TideWatchViewModel @Inject constructor(
    private val getTideUseCase: GetTideUseCase,
    private val getWindUseCase: GetWindUseCase,
    private val getRenderSunTimesUseCase: GetRenderSunTimesUseCase
) : ViewModel() {

    private val _tideData = MutableStateFlow<TideData?>(null)
    val tideData: StateFlow<TideData?> = _tideData.asStateFlow()

    private val _windData = MutableStateFlow<WindData?>(null)
    val windData: StateFlow<WindData?> = _windData.asStateFlow()

    private val _sunTimes = MutableStateFlow<SunTimes?>(null)
    val sunTimes: StateFlow<SunTimes?> = _sunTimes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var pollJob: Job? = null
    private var lastPollTime: Long = 0L
    private var currentStation: String? = null
    private var sunStation: String? = null

    fun isPolling(): Boolean = pollJob?.isActive == true

    // 캐시로 즉시 표시 + 폴링 첫 호출을 POLL_INTERVAL 만큼 지연.
    // currentStation을 함께 세팅해 startPolling이 lastPollTime을 0으로 리셋(=즉시 재호출)하지 않도록 한다.
    fun preloadFromCache(stationCode: String, tide: TideData, wind: WindData?) {
        if (wind != null) _windData.value = wind
        _tideData.value = tide
        currentStation = stationCode
        lastPollTime = System.currentTimeMillis()
    }

    fun startPolling(stationCode: String, lat: Double, lng: Double) {
        if (stationCode != currentStation) {
            // 캐시 프리로드가 없었던(새) 관측소 → 즉시 1회 조회
            lastPollTime = 0L
            currentStation = stationCode
        }
        // 일출/일몰 시간은 관측소가 바뀔 때 1회만 조회 (프리로드 여부와 무관)
        if (stationCode != sunStation) {
            sunStation = stationCode
            viewModelScope.launch {
                try { _sunTimes.value = getRenderSunTimesUseCase(lat, lng) }
                catch (e: Exception) { Log.w(TAG, "SunTimes fetch failed", e) }
            }
        }
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - lastPollTime
                val waitMs = (POLL_INTERVAL_MS - elapsed).coerceAtLeast(0L)
                if (waitMs > 0L) delay(waitMs)
                if (!isActive) break
                _isLoading.value = true
                try {
                    val tide = getTideUseCase(stationCode)
                    _tideData.value = tide
                    val wind = getWindUseCase(lat, lng, stationCode)
                    _windData.value = wind
                    lastPollTime = System.currentTimeMillis()
                } catch (e: Exception) {
                    Log.w(TAG, "Poll failed for $stationCode", e)
                } finally {
                    _isLoading.value = false
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    companion object {
        private const val TAG = "TideWatchViewModel"
        private const val POLL_INTERVAL_MS = 5 * 60_000L
    }
}
