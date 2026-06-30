package com.koretide.app.data

/**
 * 뱃멀미지수 운항 노선(nvgtNm) → 대표 좌표(출발항 기준).
 *
 * 뱃멀미 API 응답에는 좌표가 없고 운항 노선명(nvgtNm, 예: "인천-백령")만 내려온다.
 * 지역 분류·물멍 연결을 위해 출발항의 좌표를 부여해 두고, 이름으로 좌표를 찾는다.
 * (출발항 좌표 → 해역 폴리곤 분류와도 일관됨)
 */
object SeasicknessRouteData {

    data class Route(val code: String, val name: String, val lat: Double, val lon: Double)

    val routes: List<Route> = listOf(
        Route("SK1",  "인천-백령",   37.470, 126.620),
        Route("SK2",  "인천-연평",   37.470, 126.620),
        Route("SK3",  "군산-어청",   35.980, 126.560),
        Route("SK4",  "목포-제주",   34.780, 126.380),
        Route("SK5",  "완도-제주",   34.310, 126.760),
        Route("SK6",  "녹동-제주",   34.530, 127.130),
        Route("SK7",  "포항-울릉",   36.050, 129.370),
        Route("SK8",  "강릉-울릉",   37.770, 128.950),
        Route("SK9",  "묵호-울릉",   37.550, 129.120),
        Route("SK10", "부산-제주",   35.100, 129.040),
        Route("SK11", "부산-대마",   35.100, 129.040),
        Route("SK12", "부산-후쿠오카", 35.100, 129.040),
        Route("SK13", "부산-오사카", 35.100, 129.040),
        Route("SK14", "격포-위도",   35.620, 126.460),
        Route("SK16", "완도-청산",   34.310, 126.760),
        Route("SK17", "통영-소매물", 34.840, 128.420),
        Route("SK18", "목포-홍도",   34.780, 126.380),
        Route("SK19", "인천-단동",   37.470, 126.620),
        Route("SK20", "인천-석도",   37.470, 126.620),
        Route("SK21", "대천-외연",   36.300, 126.510),
        Route("SK22", "여수-제주",   34.740, 127.740),
        Route("SK23", "후포-울릉",   36.680, 129.460),
        Route("SK24", "인천-덕적",   37.470, 126.620),
        Route("SK25", "여수-거문",   34.740, 127.740),
        Route("SK26", "여수-연도",   34.740, 127.740),
        Route("SK27", "울릉-독도",   37.490, 130.900),
        Route("SK28", "모슬포-마라도", 33.210, 126.250),
        Route("SK29", "인천-제주",   37.470, 126.620),
        Route("SK30", "목포-가거",   34.780, 126.380),
        Route("SK31", "삼덕-욕지",   34.770, 128.550),
        Route("SK32", "삼천포-제주", 34.930, 128.070),
    )

    private val byName: Map<String, Route> = routes.associateBy { it.name }

    fun nameFor(code: String?): String? =
        code?.trim()?.uppercase()?.let { c -> routes.firstOrNull { it.code == c }?.name }

    fun coordsFor(name: String?): Pair<Double, Double>? =
        name?.trim()?.let { byName[it] }?.let { it.lat to it.lon }
}
