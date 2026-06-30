package com.koretide.app.data

// 뱃멀미지수 운항코드(nvgtCode) → 운항 경로명.
// KHOA 뱃멀미지수 API 응답은 nvgtCode만 내려주므로, 화면 표시는 이 표로 이름을 매핑한다.
object SeasicknessRouteData {
    val routeNames: Map<String, String> = mapOf(
        "SK1"  to "인천-백령",
        "SK2"  to "인천-연평",
        "SK3"  to "군산-어청",
        "SK4"  to "목포-제주",
        "SK5"  to "완도-제주",
        "SK6"  to "녹동-제주",
        "SK7"  to "포항-울릉",
        "SK8"  to "강릉-울릉",
        "SK9"  to "묵호-울릉",
        "SK10" to "부산-제주",
        "SK11" to "부산-대마",
        "SK12" to "부산-후쿠오카",
        "SK13" to "부산-오사카",
        "SK14" to "격포-위도",
        "SK16" to "완도-청산",
        "SK17" to "통영-소매물",
        "SK18" to "목포-홍도",
        "SK19" to "인천-단동",
        "SK20" to "인천-석도",
        "SK21" to "대천-외연",
        "SK22" to "여수-제주",
        "SK23" to "후포-울릉",
        "SK24" to "인천-덕적",
        "SK25" to "여수-거문",
        "SK26" to "여수-연도",
        "SK27" to "울릉-독도",
        "SK28" to "모슬포-마라도",
        "SK29" to "인천-제주",
        "SK30" to "목포-가거",
        "SK31" to "삼덕-욕지",
        "SK32" to "삼천포-제주",
    )

    fun nameFor(code: String?): String? =
        code?.trim()?.uppercase()?.let { routeNames[it] }
}
