package com.koretide.app.usecase

import com.koretide.app.domain.model.TideData
import com.koretide.app.domain.model.TideRecord
import com.koretide.app.domain.model.TideStatus
import com.koretide.app.domain.repository.TideRepository
import com.koretide.app.domain.usecase.GetTideUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetTideUseCaseTest {

    private lateinit var repository: TideRepository
    private lateinit var useCase: GetTideUseCase

    private val mockTideData = TideData(
        stationCode = "DT_0001",
        currentLevel = 350,
        maxLevel = 600,
        minLevel = 50,
        tidePercent = 0.55f,
        tideStatus = TideStatus.RISING,
        highTideTime = "06:30",
        lowTideTime = "12:45",
        records = emptyList()
    )

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = GetTideUseCase(repository)
    }

    @Test
    fun `invoke returns tide data from repository`() = runTest {
        coEvery { repository.getTideData("DT_0001") } returns mockTideData
        val result = useCase("DT_0001")
        assertEquals(mockTideData, result)
        assertEquals(TideStatus.RISING, result.tideStatus)
        assertEquals(350, result.currentLevel)
    }

    @Test
    fun `tide percent is within valid range`() = runTest {
        coEvery { repository.getTideData(any()) } returns mockTideData
        val result = useCase("DT_0001")
        assert(result.tidePercent in 0f..1f)
    }
}
