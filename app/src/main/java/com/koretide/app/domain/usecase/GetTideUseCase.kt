package com.koretide.app.domain.usecase

import com.koretide.app.domain.model.TideData
import com.koretide.app.domain.repository.TideRepository
import javax.inject.Inject

class GetTideUseCase @Inject constructor(
    private val repository: TideRepository
) {
    suspend operator fun invoke(stationCode: String): TideData =
        repository.getTideData(stationCode)
}
