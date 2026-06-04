package com.koretide.app.domain.repository

import com.koretide.app.domain.model.OceanIndex
import com.koretide.app.domain.model.TideData

interface OceanIndexRepository {
    suspend fun getAllIndices(
        date: String,
        region: String?,
        stationCode: String? = null,
        tideData: TideData? = null
    ): List<OceanIndex>
}
