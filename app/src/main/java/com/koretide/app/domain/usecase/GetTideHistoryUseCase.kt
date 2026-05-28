package com.koretide.app.domain.usecase

import com.koretide.app.domain.model.TideRecord
import com.koretide.app.domain.repository.TideRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class GetTideHistoryUseCase @Inject constructor(
    private val repository: TideRepository
) {
    suspend operator fun invoke(stationCode: String): List<TideRecord> {
        val today = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
        return repository.getTideHistory(stationCode, today)
    }
}
