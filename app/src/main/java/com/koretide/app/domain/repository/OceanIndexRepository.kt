package com.koretide.app.domain.repository

import com.koretide.app.domain.model.BeachIndexItem
import com.koretide.app.domain.model.IndexType
import com.koretide.app.domain.model.OceanIndex
import com.koretide.app.domain.model.StationRegion
import com.koretide.app.domain.model.TideData

interface OceanIndexRepository {
    suspend fun getAllIndices(
        date: String,
        region: String?,
        stationCode: String? = null,
        lat: Double? = null,
        lon: Double? = null,
        tideData: TideData? = null
    ): List<OceanIndex>

    suspend fun getBeachIndicesForRegion(
        date: String,
        type: IndexType,
        region: StationRegion
    ): List<BeachIndexItem>
}
