package com.koretide.app.domain.usecase

import com.koretide.app.domain.model.RecentTideLevel
import com.koretide.app.domain.repository.TideRepository
import javax.inject.Inject

class GetBatchTideStatusUseCase @Inject constructor(
    private val repository: TideRepository
) {
    suspend operator fun invoke(): List<RecentTideLevel> = repository.getBatchRecentLevels()

    /** 캐시된 값만 즉시 반환 (네트워크 호출 없음). 첫 화면 즉시 표시용. */
    suspend fun cached(): List<RecentTideLevel> = repository.getCachedBatchLevels()
}
