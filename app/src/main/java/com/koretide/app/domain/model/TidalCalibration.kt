package com.koretide.app.domain.model

/**
 * Per-region mapping from absolute API water level (cm above chart datum) to
 * visual scene Z coordinates.
 *
 * histMinCm / histMaxCm are the all-time (historical) low/high tide levels
 * for the region — NOT today's daily min/max.  Using absolute levels means
 * 강화 만조=870cm always fills the screen and 삼척 간조=110cm always shows a
 * half-and-half view, regardless of what the daily tidePercent happens to be.
 *
 * Reference values from 국립해양조사원 (approximate):
 *   서해(강화 기준) : 간조 최저 ~20 cm, 만조 최고 ~980 cm
 *   남해            : 간조 최저 ~20 cm, 만조 최고 ~450 cm
 *   동해(삼척 기준) : 간조 최저 ~30 cm, 만조 최고 ~270 cm
 *   제주            : 간조 최저 ~0  cm, 만조 최고 ~290 cm
 *
 * Visual Z range is tuned so that:
 *   서해 간조(~80 cm)  → waterlineZ ≈ -16  (갯벌 거의 전부 드러남)
 *   서해 만조(~870 cm) → waterlineZ ≈ +12  (바다 가득)
 *   동해 간조(~110 cm) → waterlineZ ≈ -1   (해수욕장 반/바다 반)
 *   동해 만조(~170 cm) → waterlineZ ≈ +0.5 (바다 조금 늘어남)
 *   제주 간조(~50 cm)  → waterlineZ ≈ +6   (바위/해안 조금 드러남)
 *   제주 만조(~220 cm) → waterlineZ ≈ +10  (바다 가득 — 섬 해안선)
 */
data class TidalCalibration(
    val histMinCm: Int,
    val histMaxCm: Int,
    val visualMinZ: Float,
    val visualMaxZ: Float
) {
    /** Normalised position in [0,1]: 0 = historical all-time low, 1 = all-time high. */
    fun calibratedT(currentLevelCm: Int): Float =
        ((currentLevelCm - histMinCm).toFloat() / (histMaxCm - histMinCm))
            .coerceIn(0f, 1f)

    companion object {
        fun forRegion(region: StationRegion): TidalCalibration = when (region) {
            StationRegion.WEST  -> TidalCalibration( 20, 1000, -18f,  16f)
            StationRegion.SOUTH -> TidalCalibration( 20,  450, -10f,  12f)
            StationRegion.EAST  -> TidalCalibration( 30,  270,  -3f,   3f)
            StationRegion.JEJU  -> TidalCalibration(  0,  290,   5f,  12f)
        }
    }
}
