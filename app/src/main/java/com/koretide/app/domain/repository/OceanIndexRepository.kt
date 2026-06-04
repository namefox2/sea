package com.koretide.app.domain.repository

import com.koretide.app.domain.model.OceanIndex

interface OceanIndexRepository {
    suspend fun getAllIndices(date: String, region: String?): List<OceanIndex>
}
