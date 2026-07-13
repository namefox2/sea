package com.koretide.app.ui.index

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koretide.app.domain.model.BeachIndexItem
import com.koretide.app.domain.model.IndexType
import com.koretide.app.domain.model.StationRegion
import com.koretide.app.domain.repository.OceanIndexRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed class IndexUiState {
    object Loading : IndexUiState()
    object TypeList : IndexUiState()
    data class RegionList(val type: IndexType) : IndexUiState()
    data class BeachList(
        val type: IndexType,
        val region: StationRegion,
        val beaches: List<BeachIndexItem>
    ) : IndexUiState()
    data class Error(val message: String) : IndexUiState()
}

sealed class IndexNav {
    object TypeList : IndexNav()
    data class RegionList(val type: IndexType) : IndexNav()
    data class BeachList(val type: IndexType, val region: StationRegion) : IndexNav()
}

@HiltViewModel
class IndexViewModel @Inject constructor(
    private val indexRepo: OceanIndexRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<IndexUiState>(IndexUiState.TypeList)
    val uiState = _uiState.asStateFlow()

    private val navStack = mutableListOf<IndexNav>(IndexNav.TypeList)
    private var cachedBeachList: IndexUiState.BeachList? = null

    fun loadTypeList() {
        navStack.clear()
        navStack.add(IndexNav.TypeList)
        cachedBeachList = null
        _uiState.value = IndexUiState.TypeList
    }

    fun selectType(type: IndexType) {
        navStack.add(IndexNav.RegionList(type))
        cachedBeachList = null
        _uiState.value = IndexUiState.RegionList(type)
    }

    fun selectRegion(type: IndexType, region: StationRegion) {
        navStack.add(IndexNav.BeachList(type, region))
        loadBeachList(type, region)
    }

    private fun loadBeachList(type: IndexType, region: StationRegion) {
        viewModelScope.launch {
            _uiState.value = IndexUiState.Loading
            try {
                val date = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
                val beaches = indexRepo.getBeachIndicesForRegion(date, type, region)
                val state = IndexUiState.BeachList(type, region, beaches)
                cachedBeachList = state
                _uiState.value = state
            } catch (e: Exception) {
                _uiState.value = IndexUiState.Error("지수를 불러올 수 없습니다")
            }
        }
    }

    fun navigateBack(): Boolean {
        if (navStack.size <= 1) return false
        // API 35(Java 21)의 List.removeLast()와 충돌해 Android 14 이하에서
        // NoSuchMethodError로 크래시하므로 removeAt(lastIndex)로 대체.
        navStack.removeAt(navStack.lastIndex)
        when (val screen = navStack.last()) {
            is IndexNav.TypeList   -> _uiState.value = IndexUiState.TypeList
            is IndexNav.RegionList -> _uiState.value = IndexUiState.RegionList(screen.type)
            is IndexNav.BeachList  -> {
                val cached = cachedBeachList
                if (cached != null && cached.type == screen.type && cached.region == screen.region) {
                    _uiState.value = cached
                } else {
                    loadBeachList(screen.type, screen.region)
                }
            }
        }
        return true
    }

    fun isAtRoot(): Boolean = navStack.size <= 1
}
