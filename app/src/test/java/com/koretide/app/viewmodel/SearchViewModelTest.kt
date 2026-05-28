package com.koretide.app.viewmodel

import app.cash.turbine.test
import com.koretide.app.domain.model.Station
import com.koretide.app.domain.model.StationRegion
import com.koretide.app.domain.usecase.GetAllStationsUseCase
import com.koretide.app.domain.usecase.SearchStationsUseCase
import com.koretide.app.ui.search.SearchViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var searchUseCase: SearchStationsUseCase
    private lateinit var getAllUseCase: GetAllStationsUseCase
    private lateinit var viewModel: SearchViewModel

    private val mockStations = listOf(
        Station("DT_0001", "인천", StationRegion.WEST, 37.45, 126.59),
        Station("DT_0017", "부산", StationRegion.SOUTH, 35.10, 129.04),
        Station("DT_0035", "제주", StationRegion.JEJU, 33.51, 126.52)
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        searchUseCase = mockk {
            every { invoke(any(), any()) } returns flowOf(mockStations)
        }
        getAllUseCase = mockk {
            every { invoke() } returns flowOf(mockStations)
            coEvery { refresh() } returns Unit
        }
        viewModel = SearchViewModel(searchUseCase, getAllUseCase)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial stations flow emits all stations`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.stations.test {
            val result = awaitItem()
            assert(result.size == 3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setQuery updates query state`() = runTest {
        viewModel.setQuery("인천")
        viewModel.query.test {
            assert(awaitItem() == "인천")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setRegionFilter updates region state`() = runTest {
        viewModel.setRegionFilter(StationRegion.WEST)
        viewModel.selectedRegion.test {
            assert(awaitItem() == StationRegion.WEST)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
