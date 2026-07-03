package com.koretide.app.data

/**
 * 좌표를 가진 모든 장소 리스트(액티비티 스팟·해수욕장·각 지수 장소)를 이름→좌표로 모은 색인.
 *
 * 지수 API 응답에 좌표(lat/lot)가 없을 때, 같은 이름이 이 색인에 있으면 그 좌표를 빌려 쓴다.
 * (요청: "좌표가 없는 경우는 좌표있는 리스트중 이름으로 일치하는게 있으면 그냥 적용")
 */
object PlaceGazetteer {

    // 이름(정규화) → (lat, lon)
    private val byName: Map<String, Pair<Double, Double>> by lazy {
        val m = LinkedHashMap<String, Pair<Double, Double>>()
        fun add(name: String?, lat: Double, lon: Double) {
            val key = normalize(name)
            if (key.isNotEmpty() && !m.containsKey(key)) m[key] = lat to lon
        }
        MarineActivityData.getAllSpots().forEach { add(it.name, it.lat, it.lng) }
        BeachPlaceData.places.forEach          { add(it.name, it.lat, it.lon) }
        SurfingPlaceData.places.forEach        { add(it.name, it.lat, it.lon) }
        ScubaPlaceData.places.forEach          { add(it.name, it.lat, it.lon) }
        TidalFlatPlaceData.places.forEach      { add(it.name, it.lat, it.lon) }
        SeaTravelPlaceData.places.forEach      { add(it.name, it.lat, it.lon) }
        SeasicknessRouteData.routes.forEach    { add(it.name, it.lat, it.lon) }
        m
    }

    /** 이름으로 좌표 조회. 정확히 일치 → 안전한 부분 일치(질의가 등록명의 부분집합). 없으면 null. */
    fun coordsFor(name: String?): Pair<Double, Double>? {
        val key = normalize(name).ifEmpty { return null }
        byName[key]?.let { return it }
        // 안전한 방향만: 질의(key)가 등록명(k)의 부분 문자열일 때만 채택.
        // 반대 방향(key.contains(k))은 짧은 등록명이 긴 질의(예: 노선명 "여수-제주")에
        // 잘못 매칭돼 엉뚱한 좌표를 빌려오므로 사용하지 않음.
        return byName.entries.firstOrNull { (k, _) -> k.length >= 2 && k.contains(key) }?.value
    }

    // "송정해수욕장(남해)" → "송정해수욕장", 공백 제거 등 가벼운 정규화
    private fun normalize(raw: String?): String =
        raw?.substringBefore('(')?.replace(" ", "")?.trim().orEmpty()
}
