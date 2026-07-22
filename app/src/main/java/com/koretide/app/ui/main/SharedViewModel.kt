package com.koretide.app.ui.main

import androidx.lifecycle.ViewModel
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
    private var tideDataStation: String? = null

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

    fun clearSelectedStation() {
        _selectedStation.value = null
    }

    fun updateTideData(data: TideData, forStation: String? = null) {
        tideDataStation = forStation ?: _selectedStation.value?.code
        _tideData.value = data
    }

    fun cachedTideFor(stationCode: String): TideData? =
        _tideData.value?.takeIf { tideDataStation == stationCode }

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
        val changed = _selectedThemeId.value != themeId
        _selectedThemeId.value = themeId
        // 테마를 바꾸면 선택된 관측소를 비워 물멍 화면이 기본값으로 초기화되게 한다.
        if (changed) clearSelectedStation()
    }
}
