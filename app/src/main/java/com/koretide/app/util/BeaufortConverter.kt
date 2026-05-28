package com.koretide.app.util

import kotlin.math.pow
import kotlin.math.roundToInt

object BeaufortConverter {

    fun toBft(windSpeedMs: Float): Int =
        (0.837 * windSpeedMs.toDouble().pow(2.0 / 3.0)).roundToInt().coerceIn(0, 12)

    fun waveHeight(bft: Int): Float = when (bft) {
        0 -> 0.0f
        1 -> 0.1f
        2 -> 0.2f
        3 -> 0.6f
        4 -> 1.0f
        5 -> 2.0f
        6 -> 3.0f
        7 -> 4.0f
        8 -> 5.5f
        9 -> 7.0f
        10 -> 9.0f
        11 -> 11.5f
        else -> 14.0f
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
