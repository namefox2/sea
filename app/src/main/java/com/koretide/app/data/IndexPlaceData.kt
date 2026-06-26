package com.koretide.app.data

data class IndexPlace(val code: String, val name: String, val lat: Double, val lon: Double)

private fun List<IndexPlace>.nearest(lat: Double, lon: Double): IndexPlace? =
    minByOrNull { (it.lat - lat) * (it.lat - lat) + (it.lon - lon) * (it.lon - lon) }

// ─── 바다여행지수 (BA) ────────────────────────────────────────────────────────
object SeaTravelPlaceData {
    val places = listOf(
        IndexPlace("BA",     "부안",       35.730, 126.730),
        IndexPlace("BSNE",   "부산북동",    35.170, 129.120),
        IndexPlace("BSSW",   "부산남서",    35.100, 129.000),
        IndexPlace("GN",     "강릉",       37.750, 128.900),
        IndexPlace("HN",     "해남",       34.570, 126.600),
        IndexPlace("ICCN",   "인천내륙",    37.460, 126.700),
        IndexPlace("ICNWIS", "인천북서",    37.680, 126.400),
        IndexPlace("ICSWIS", "인천남서",    37.220, 126.200),
        IndexPlace("JJNE",   "제주북동",    33.520, 126.920),
        IndexPlace("JJNW",   "제주북서",    33.510, 126.520),
        IndexPlace("JJSE",   "제주남동",    33.250, 126.890),
        IndexPlace("JJSW",   "제주남서",    33.240, 126.490),
        IndexPlace("PH",     "포항",       36.020, 129.370),
        IndexPlace("SANE",   "신안북동",    34.840, 126.100),
        IndexPlace("SASW",   "신안남서",    34.250, 125.780),
        IndexPlace("SC",     "속초",       38.210, 128.590),
        IndexPlace("TAN",    "태안북부",    36.970, 126.300),
        IndexPlace("TAS",    "태안남부",    36.670, 126.270),
        IndexPlace("TY",     "통영",       34.850, 128.430),
        IndexPlace("YD",     "영덕",       36.520, 129.400),
        IndexPlace("YSCN",   "여수내륙",    34.760, 127.660),
        IndexPlace("YSIS",   "여수도서",    34.600, 127.490),
    )

    fun nearest(lat: Double, lon: Double): IndexPlace? = places.nearest(lat, lon)
}

// ─── 스킨스쿠버지수 (SS) ──────────────────────────────────────────────────────
object ScubaPlaceData {
    val places = listOf(
        IndexPlace("SS1",  "동명항",       38.170, 128.610),
        IndexPlace("SS2",  "남애항",       38.000, 128.680),
        IndexPlace("SS3",  "강문해변",     37.790, 128.960),
        IndexPlace("SS4",  "오산항",       37.440, 129.170),
        IndexPlace("SS5",  "월포해수욕장", 36.315, 129.383),
        IndexPlace("SS6",  "구조라해수욕장", 34.830, 128.720),
        IndexPlace("SS7",  "미조도",       34.730, 128.130),
        IndexPlace("SS8",  "거문도",       34.030, 127.310),
        IndexPlace("SS9",  "성산일출봉",   33.460, 126.940),
        IndexPlace("SS10", "문섬",         33.230, 126.560),
        IndexPlace("SS11", "홍도",         34.690, 125.190),
        IndexPlace("SS12", "울릉도",       37.490, 130.870),
        IndexPlace("SS13", "어영",         33.520, 126.430),
        IndexPlace("SS14", "태종대",       35.040, 129.080),
        IndexPlace("SS15", "격렬비열도",   36.600, 125.580),
        IndexPlace("SS16", "추자도",       33.960, 126.300),
        IndexPlace("SS17", "욕지도",       34.610, 128.380),
        IndexPlace("SS18", "추암",         37.500, 129.120),
    )

    fun nearest(lat: Double, lon: Double): IndexPlace? = places.nearest(lat, lon)
}

// ─── 서핑지수 (SR) ────────────────────────────────────────────────────────────
object SurfingPlaceData {
    val places = listOf(
        IndexPlace("SR1",  "송정해수욕장",       35.180, 129.220),
        IndexPlace("SR2",  "만리포해수욕장",     36.950, 126.133),
        IndexPlace("SR3",  "죽도해수욕장",       38.060, 128.690),
        IndexPlace("SR4",  "망상해수욕장",       37.594, 129.116),
        IndexPlace("SR5",  "곽지해수욕장",       33.470, 126.350),
        IndexPlace("SR6",  "다대포해수욕장",     35.060, 128.967),
        IndexPlace("SR7",  "진하해수욕장",       35.445, 129.391),
        IndexPlace("SR8",  "송지호해수욕장",     38.365, 128.499),
        IndexPlace("SR9",  "명사십리해수욕장",   34.790, 128.660),
        IndexPlace("SR10", "중문색달해수욕장",   33.244, 126.414),
        IndexPlace("SR11", "송정솔바람해수욕장", 38.050, 128.670),
        IndexPlace("SR12", "금진해수욕장",       37.640, 129.060),
        IndexPlace("SR13", "월포해수욕장",       36.315, 129.383),
    )

    fun nearest(lat: Double, lon: Double): IndexPlace? = places.nearest(lat, lon)
}

// ─── 갯벌체험지수 (TL) ───────────────────────────────────────────────────────
object TidalFlatPlaceData {
    val places = listOf(
        IndexPlace("TL1",  "우전마을",   34.920, 126.030),
        IndexPlace("TL2",  "백미리마을", 37.130, 126.790),
        IndexPlace("TL3",  "문항마을",   36.880, 126.450),
        IndexPlace("TL4",  "전곡리마을", 37.260, 126.680),
        IndexPlace("TL5",  "만돌마을",   34.350, 126.720),
        IndexPlace("TL6",  "하전마을",   35.420, 126.600),
        IndexPlace("TL7",  "궁평마을",   37.070, 126.740),
        IndexPlace("TL8",  "신리마을",   34.820, 126.100),
        IndexPlace("TL9",  "월하성마을", 34.400, 126.800),
        IndexPlace("TL10", "선감마을",   37.230, 126.580),
        IndexPlace("TL11", "선유도마을", 35.760, 126.510),
        IndexPlace("TL12", "무창포마을", 36.260, 126.490),
        IndexPlace("TL13", "돌머리마을", 35.070, 126.400),
        IndexPlace("TL14", "모항마을",   36.680, 126.300),
        IndexPlace("TL15", "종현마을",   34.730, 126.060),
        IndexPlace("TL16", "수문마을",   35.440, 126.580),
        IndexPlace("TL17", "신시도마을", 35.820, 126.570),
        IndexPlace("TL18", "대야도마을", 35.740, 126.460),
        IndexPlace("TL19", "장자도마을", 35.770, 126.540),
        IndexPlace("TL20", "다대마을",   34.830, 125.970),
        IndexPlace("TL21", "냉천마을",   35.060, 126.380),
        IndexPlace("TL22", "사금마을",   34.870, 126.120),
        IndexPlace("TL23", "서중마을",   34.950, 126.150),
        IndexPlace("TL24", "제부마을",   37.090, 126.710),
        IndexPlace("TL25", "병술만마을", 36.970, 126.240),
        IndexPlace("TL26", "송계마을",   36.400, 126.500),
        IndexPlace("TL27", "국화리마을", 34.750, 126.050),
        IndexPlace("TL28", "유포마을",   36.880, 126.550),
        IndexPlace("TL29", "대포마을",   36.900, 126.450),
        IndexPlace("TL30", "청용마을",   36.500, 126.550),
        IndexPlace("TL31", "백사마을",   34.600, 126.620),
        IndexPlace("TL32", "장양마을",   35.350, 126.480),
        IndexPlace("TL33", "거차마을",   34.520, 126.100),
        IndexPlace("TL34", "죽림마을",   35.180, 126.550),
        IndexPlace("TL35", "둔장마을",   35.500, 126.520),
        IndexPlace("TL36", "마시안마을", 37.440, 126.530),
        IndexPlace("TL37", "만대마을",   36.980, 126.290),
    )

    fun nearest(lat: Double, lon: Double): IndexPlace? = places.nearest(lat, lon)
}
