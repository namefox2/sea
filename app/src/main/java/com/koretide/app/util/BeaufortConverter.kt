package com.koretide.app.util

object BeaufortConverter {

    // 공식 보퍼트 풍력계급 임계표(m/s). 근사식 대신 표준 구간을 써야 검색·상세 등
    // 앱 전체에서 같은 풍속이 항상 같은 계급으로 표시된다.
    fun toBft(windSpeedMs: Float): Int = when {
        windSpeedMs < 0.3f  -> 0
        windSpeedMs < 1.6f  -> 1
        windSpeedMs < 3.4f  -> 2
        windSpeedMs < 5.5f  -> 3
        windSpeedMs < 8.0f  -> 4
        windSpeedMs < 10.8f -> 5
        windSpeedMs < 13.9f -> 6
        windSpeedMs < 17.2f -> 7
        windSpeedMs < 20.8f -> 8
        windSpeedMs < 24.5f -> 9
        windSpeedMs < 28.5f -> 10
        windSpeedMs < 32.7f -> 11
        else                -> 12
    }

    fun name(bft: Int): String = when (bft) {
        0 -> "고요"
        1 -> "실바람"
        2 -> "남실바람"
        3 -> "산들바람"
        4 -> "건들바람"
        5 -> "흔들바람"
        6 -> "된바람"
        7 -> "센바람"
        8 -> "큰바람"
        9 -> "큰센바람"
        10 -> "노대바람"
        11 -> "왕바람"
        else -> "폭풍"
    }
}
