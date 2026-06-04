package com.koretide.app.ui.index

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koretide.app.domain.model.DayForecast
import com.koretide.app.domain.model.OceanIndex
import com.koretide.app.domain.model.TideData
import com.koretide.app.domain.usecase.GetOceanIndicesUseCase
import com.koretide.app.domain.usecase.GetSevenDayForecastUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class IndexUiState {
    object Loading : IndexUiState()
    data class Success(
        val indices: List<OceanIndex>,
        val stationName: String?,
        val forecast: List<DayForecast>
    ) : IndexUiState()
    data class Error(val message: String) : IndexUiState()
}

@HiltViewModel
class IndexViewModel @Inject constructor(
    private val getOceanIndices: GetOceanIndicesUseCase,
    private val getSevenDayForecast: GetSevenDayForecastUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<IndexUiState>(IndexUiState.Loading)
    val uiState = _uiState.asStateFlow()

    fun load(
        region: String?,
        stationName: String?,
        stationCode: String? = null,
        tideData: TideData? = null
    ) {
        viewModelScope.launch {
            _uiState.value = IndexUiState.Loading
            try {
                val indices = getOceanIndices(region, stationCode, tideData)
                val forecast = getSevenDayForecast(stationName, region)
                _uiState.value = IndexUiState.Success(indices, stationName, forecast)
            } catch (e: Exception) {
                _uiState.value = IndexUiState.Error(e.message ?: "오류가 발생했습니다")
            }
        }
    }
}
