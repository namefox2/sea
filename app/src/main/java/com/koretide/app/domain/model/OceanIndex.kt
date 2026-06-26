package com.koretide.app.domain.model

data class OceanIndex(
    val type: IndexType,
    val grade: IndexGrade?,
    val gradeLabel: String? = null,
    val stats: List<Pair<String, String>>,
    val beachName: String?,
    val date: String?,
    val isAvailable: Boolean,
    val opnStat: String? = null  // 개장상태 (개장/폐장/비개장)
)

enum class IndexType(val displayName: String, val emoji: String) {
    BEACH_SWIM("해수욕장지수", "🏖️"),
    SEA_FISHING("바다낚시", "🎣"),
    SEASICKNESS("뱃멀미", "⛵"),
    SCUBA_DIVING("스킨스쿠버", "🤿"),
    TIDAL_FLAT("갯벌체험", "🦀"),
    SURFING("서핑지수", "🏄"),
    SEA_TRAVEL("바다여행", "🚢")
}

enum class IndexGrade(val label: String, val level: Int, val emoji: String) {
    VERY_GOOD("매우좋음", 1, "🟢"),
    GOOD("좋음",         2, "🔵"),
    FAIR("보통",         3, "🟡"),
    BAD("나쁨",          4, "🟠"),
    VERY_BAD("매우나쁨", 5, "🔴");

    companion object {
        fun fromString(s: String?): IndexGrade? = when (s?.trim()?.uppercase()) {
            "A", "매우좋음", "VERY_GOOD" -> VERY_GOOD
            "B", "좋음",   "GOOD"       -> GOOD
            "C", "보통",   "FAIR"       -> FAIR
            "D", "나쁨",   "BAD"        -> BAD
            "E", "매우나쁨","VERY_BAD"   -> VERY_BAD
            else -> null
        }
    }
}
