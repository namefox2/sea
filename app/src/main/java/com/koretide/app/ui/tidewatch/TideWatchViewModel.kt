package com.koretide.app.ui.tidewatch

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koretide.app.domain.model.TideData
import com.koretide.app.domain.model.WindData
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
    private val getWindUseCase: GetWindUseCase
) : ViewModel() {

    private val _tideData = MutableStateFlow<TideData?>(null)
    val tideData: StateFlow<TideData?> = _tideData.asStateFlow()

    private val _windData = MutableStateFlow<WindData?>(null)
    val windData: StateFlow<WindData?> = _windData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var pollJob: Job? = null

    fun startPolling(stationCode: String, lat: Double, lng: Double) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                _isLoading.value = true
                try {
                    val tide = getTideUseCase(stationCode)
                    _tideData.value = tide
                    val wind = getWindUseCase(lat, lng, stationCode)
                    _windData.value = wind
                } catch (e: Exception) {
                    Log.w(TAG, "Poll failed for $stationCode", e)
                } finally {
                    _isLoading.value = false
                }
                delay(5 * 60_000L) // refresh every 5 minutes
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    companion object {
        private const val TAG = "TideWatchViewModel"
    }
}
