package com.koretide.app.domain.model

// code: 관측소 코드. 검색 목록에서 관측소↔수위 매칭을 좌표 최근접이 아닌 '코드'로 하기 위함
// (좌표 매칭은 해당 관측소의 배치 조회가 실패하면 이웃 관측소 값을 잘못 붙였다).
// dayMinCm/dayMaxCm: 그날 관측된 조위의 최소/최대. 관측소마다 조차가 다르므로(동해안 ~30cm,
// 서해안 ~600cm) 고정 척도 대신 이 범위로 상대 조위%를 계산한다.
data class RecentTideLevel(
    val code: String,
    val lat: Double,
    val lon: Double,
    val levelCm: Int?,
    val windSpeedMs: Float? = null,
    val dayMinCm: Int? = null,
    val dayMaxCm: Int? = null
)
