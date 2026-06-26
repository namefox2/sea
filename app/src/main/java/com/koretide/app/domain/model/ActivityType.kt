package com.koretide.app.domain.model

enum class ActivityType(val displayName: String, val colorHex: String) {
    HIGH_TIDE("만조", "#FF7043"),
    FISHING("바다낚시", "#FF6B35"),
    SURFING("서핑", "#0097A7"),
    TIDAL_FLAT("갯벌체험", "#8D6E63"),
    SWIMMING("해수욕", "#F06292"),
    SCUBA("스킨스쿠버", "#1565C0"),
    SEA_TRAVEL("바다여행", "#00897B")
}
