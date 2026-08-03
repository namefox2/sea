package com.koretide.app.domain.repository

import com.koretide.app.domain.model.BeachIndexItem
import com.koretide.app.domain.model.IndexType
import com.koretide.app.domain.model.StationRegion

interface OceanIndexRepository {
    suspend fun getBeachIndicesForRegion(
        date: String,
        type: IndexType,
        region: StationRegion
    ): List<BeachIndexItem>
}
