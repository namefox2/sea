package com.koretide.app.domain.usecase

import com.koretide.app.domain.model.RecentTideLevel
import com.koretide.app.domain.repository.TideRepository
import javax.inject.Inject

class GetBatchTideStatusUseCase @Inject constructor(
    private val repository: TideRepository
) {
    suspend operator fun invoke(): List<RecentTideLevel> = repository.getBatchRecentLevels()
}
