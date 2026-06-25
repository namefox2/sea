package com.koretide.app.ui.main

import androidx.lifecycle.ViewModel
import com.koretide.app.R
import com.koretide.app.domain.model.Station
import com.koretide.app.domain.model.TideData
import com.koretide.app.domain.model.WindData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SharedViewModel @Inject constructor() : ViewModel() {

    private val _selectedStation = MutableStateFlow<Station?>(null)
    val selectedStation: StateFlow<Station?> = _selectedStation.asStateFlow()

    private val _tideData = MutableStateFlow<TideData?>(null)
    val tideData: StateFlow<TideData?> = _tideData.asStateFlow()

    private val _windData = MutableStateFlow<WindData?>(null)
    val windData: StateFlow<WindData?> = _windData.asStateFlow()

    private val _navigateToTab = MutableStateFlow<Int?>(null)
    val navigateToTab: StateFlow<Int?> = _navigateToTab.asStateFlow()

    private val _isWatchImmersive = MutableStateFlow(false)
    val isWatchImmersive: StateFlow<Boolean> = _isWatchImmersive.asStateFlow()

    private val _selectedThemeId = MutableStateFlow<String?>(null)
    val selectedThemeId: StateFlow<String?> = _selectedThemeId.asStateFlow()

    fun selectStation(station: Station) {
        _selectedStation.value = station
    }

    fun updateTideData(data: TideData) {
        _tideData.value = data
    }

    fun updateWindData(data: WindData) {
        _windData.value = data
    }

    fun requestTabNavigation(tabIndex: Int) {
        _navigateToTab.value = tabIndex
    }

    fun onTabNavigated() {
        _navigateToTab.value = null
    }

    fun setWatchImmersive(immersive: Boolean) {
        _isWatchImmersive.value = immersive
    }

    fun setSelectedTheme(themeId: String) {
        _selectedThemeId.value = themeId
    }

    fun openIndexForStation(station: Station) {
        _selectedStation.value = station
        requestTabNavigation(R.id.navigation_index)
    }
}
