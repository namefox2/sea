package com.koretide.app.domain.model

data class TideData(
    val stationCode: String,
    /** Raw water level reported by the API (cm above chart datum / 기준면). */
    val currentLevel: Int,
    /** Today's maximum water level from the tide table (cm). */
    val maxLevel: Int,
    /** Today's minimum water level from the tide table (cm). */
    val minLevel: Int,
    /** Normalized tide position: 0 = 간조, 1 = 만조. */
    val tidePercent: Float,
    val tideStatus: TideStatus,
    val highTideTime: String?,
    val lowTideTime: String?,
    val records: List<TideRecord> = emptyList()
) {
    val isStale: Boolean get() = false

    // ── Water level in metres (derived from API cm values) ───────────────────
    // These properties expose the raw API data in SI units so callers do not
    // need to know the internal cm representation.

    /** Current water level in metres above chart datum. */
    val currentWaterLevelM: Float get() = currentLevel / 100f

    /** Today's high-tide level in metres (daily maximum from API tide table). */
    val highTideLevelM: Float get() = maxLevel / 100f

    /** Today's low-tide level in metres (daily minimum from API tide table). */
    val lowTideLevelM: Float get() = minLevel / 100f

    /** Tidal range in metres for today (= highTideLevelM − lowTideLevelM). */
    val tidalRangeM: Float get() = (maxLevel - minLevel) / 100f
}

enum class TideStatus(val displayName: String) {
    RISING("밀물"),
    FALLING("썰물"),
    HIGH_TIDE("만조"),
    LOW_TIDE("간조")
}
