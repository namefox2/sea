package com.koretide.app.domain.usecase

import com.koretide.app.domain.model.IndexType
import com.koretide.app.domain.model.OceanIndex
import com.koretide.app.domain.repository.OceanIndexRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class GetOceanIndicesUseCase @Inject constructor(
    private val repo: OceanIndexRepository
) {
    suspend operator fun invoke(region: String?): List<OceanIndex> {
        val date = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
        val beach = repo.getBeachIndex(date, region)
        return buildList {
            add(beach)
            add(placeholder(IndexType.SEA_FISHING))
            add(placeholder(IndexType.SEASICKNESS))
            add(placeholder(IndexType.SCUBA_DIVING))
            add(placeholder(IndexType.TIDAL_FLAT))
            add(placeholder(IndexType.SURFING))
            add(placeholder(IndexType.SEA_TRAVEL))
        }
    }

    private fun placeholder(type: IndexType) = OceanIndex(
        type = type, grade = null, stats = emptyList(),
        beachName = null, date = null, isAvailable = false
    )
}
