package com.koretide.app.domain.model

/**
 * Per-location tidal configuration.
 *
 * Stores reference high-tide and low-tide levels in metres above chart datum.
 * These bound the tidal range for a region and are used to derive
 * [waterLevelM] from a normalized 0..1 tide percentage.
 *
 * The API (국립해양조사원) returns actual cm readings which take precedence
 * when available ([TideData.currentWaterLevelM]). These regional defaults act
 * as a fallback and provide the visual scaling expected for each coast type.
 */
data class TidalConfig(
    /** 만조 수위 (m, 기준면 위). */
    val highTideLevelM: Float,
    /** 간조 수위 (m, 기준면 위). */
    val lowTideLevelM: Float,
) {
    /** 조차 = 만조 − 간조 (m). */
    val tidalRangeM: Float get() = (highTideLevelM - lowTideLevelM).coerceAtLeast(0f)

    /**
     * Interpolates the actual water level (m) from a 0..1 tide percentage.
     *
     *   currentWaterLevelM = lowTideLevelM + tidalRangeM × tidePercent
     *
     * tidePercent=0 → 간조(low tide), tidePercent=1 → 만조(high tide).
     */
    fun waterLevelM(tidePercent: Float): Float =
        lowTideLevelM + tidalRangeM * tidePercent.coerceIn(0f, 1f)

    companion object {
        /**
         * Representative reference tidal configs for each Korean coastal region.
         *
         * Sources: 국립해양조사원 조석 관측 자료 (평균 대조)
         *   서해  — 인천 8.4 m, 군산 6.2 m, 목포 3.1 m  → 평균 ~4.5 m 사용
         *   남해  — 여수 3.0 m, 부산 1.4 m              → 평균 ~1.7 m 사용
         *   동해  — 속초 0.3 m, 포항 0.3 m              → 0.30 m 사용
         *   제주  — 제주 0.8 m                           → 0.80 m 사용
         *
         * These defaults are overridden at runtime by the API's actual
         * daily maxLevel / minLevel when a station is selected.
         */
        fun forRegion(region: StationRegion): TidalConfig = when (region) {
            StationRegion.WEST  -> TidalConfig(highTideLevelM = 5.00f, lowTideLevelM = 0.50f)
            StationRegion.SOUTH -> TidalConfig(highTideLevelM = 2.00f, lowTideLevelM = 0.30f)
            StationRegion.EAST  -> TidalConfig(highTideLevelM = 0.45f, lowTideLevelM = 0.15f)
            StationRegion.JEJU  -> TidalConfig(highTideLevelM = 1.80f, lowTideLevelM = 1.00f)
        }
    }
}
