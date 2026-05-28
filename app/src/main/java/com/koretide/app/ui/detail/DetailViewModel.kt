package com.koretide.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koretide.app.domain.model.Station
import com.koretide.app.domain.model.TideData
import com.koretide.app.domain.model.WindData
import com.koretide.app.domain.usecase.GetTideHistoryUseCase
import com.koretide.app.domain.usecase.GetTideUseCase
import com.koretide.app.domain.usecase.GetWindUseCase
import com.koretide.app.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val getTideUseCase: GetTideUseCase,
    private val getWindUseCase: GetWindUseCase,
    private val getTideHistoryUseCase: GetTideHistoryUseCase
) : ViewModel() {

    private val _tideResult = MutableStateFlow<Result<TideData>>(Result.Loading)
    val tideResult: StateFlow<Result<TideData>> = _tideResult.asStateFlow()

    private val _windResult = MutableStateFlow<Result<WindData>?>(null)
    val windResult: StateFlow<Result<WindData>?> = _windResult.asStateFlow()

    fun loadData(station: Station) {
        viewModelScope.launch {
            _tideResult.value = Result.Loading
            try {
                val tide = getTideUseCase(station.code)
                _tideResult.value = Result.Success(tide)
            } catch (e: Exception) {
                _tideResult.value = Result.Error(e.message ?: "Unknown error", e)
            }
        }
        viewModelScope.launch {
            try {
                val wind = getWindUseCase(station.lat, station.lng, station.code)
                _windResult.value = Result.Success(wind)
            } catch (e: Exception) {
                _windResult.value = Result.Error(e.message ?: "Unknown error", e)
            }
        }
    }
}
