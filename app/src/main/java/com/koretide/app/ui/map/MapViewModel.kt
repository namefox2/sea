package com.koretide.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koretide.app.domain.model.Station
import com.koretide.app.domain.model.StationRegion
import com.koretide.app.domain.usecase.GetAllStationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    getAllStationsUseCase: GetAllStationsUseCase
) : ViewModel() {

    private val _regionFilter = MutableStateFlow<StationRegion?>(null)
    val regionFilter: StateFlow<StationRegion?> = _regionFilter.asStateFlow()

    private val _selectedPin = MutableStateFlow<Station?>(null)
    val selectedPin: StateFlow<Station?> = _selectedPin.asStateFlow()

    val stations: StateFlow<List<Station>> = combine(
        getAllStationsUseCase(),
        _regionFilter
    ) { all, region ->
        if (region == null) all else all.filter { it.region == region }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setRegionFilter(region: StationRegion?) { _regionFilter.value = region }

    fun selectPin(station: Station) { _selectedPin.value = station }

    fun clearPin() { _selectedPin.value = null }
}
