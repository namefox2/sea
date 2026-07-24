package com.koretide.app.domain.model

// code: 관측소 코드. 검색 목록에서 관측소↔수위 매칭을 좌표 최근접이 아닌 '코드'로 하기 위함
// (좌표 매칭은 해당 관측소의 배치 조회가 실패하면 이웃 관측소 값을 잘못 붙였다).
// dayMinCm/dayMaxCm: 그날 '조석예보'의 저조/고조(=하루 조차). 상세보기와 동일한 기준으로
// 조위%를 계산하려고 담는다. (관측 범위는 아침엔 좁아 부정확해서 예보 고/저조를 쓴다.)
data class RecentTideLevel(
    val code: String,
    val lat: Double,
    val lon: Double,
    val levelCm: Int?,
    val windSpeedMs: Float? = null,
    val dayMinCm: Int? = null,
    val dayMaxCm: Int? = null
)
