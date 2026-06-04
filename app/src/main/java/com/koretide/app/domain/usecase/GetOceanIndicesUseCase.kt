package com.koretide.app.domain.usecase

import com.koretide.app.domain.model.OceanIndex
import com.koretide.app.domain.model.TideData
import com.koretide.app.domain.repository.OceanIndexRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class GetOceanIndicesUseCase @Inject constructor(
    private val repo: OceanIndexRepository
) {
    suspend operator fun invoke(
        region: String?,
        stationCode: String? = null,
        tideData: TideData? = null
    ): List<OceanIndex> {
        val date = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
        return repo.getAllIndices(date, region, stationCode, tideData)
    }
}
