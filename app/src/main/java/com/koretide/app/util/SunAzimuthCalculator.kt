package com.koretide.app.util

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * Calculates sunrise/sunset azimuths (compass bearing from North, clockwise).
 *
 * KASI API only provides rise/set times; azimuth is derived mathematically:
 *   cos(Az) = (sin(δ) − sin(φ)·sin(h₀)) / (cos(φ)·cos(h₀))
 * where:
 *   δ  = solar declination for the given day
 *   φ  = observer latitude
 *   h₀ = −0.833° (standard horizon: refraction + solar disk radius)
 *   Az = 0-180° from North; sunrise < 180°, sunset = 360° − Az
 *
 * At 35°N the range is ~60° (summer, NE) to ~120° (winter, SE).
 */
object SunAzimuthCalculator {

    private const val RAD = PI / 180.0
    private const val H0  = -0.833 * RAD   // standard sunrise/sunset altitude

    fun solarDeclinationDeg(dayOfYear: Int): Double =
        -23.45 * cos(RAD * (360.0 / 365.0 * (dayOfYear + 10)))

    fun sunriseAzimuth(latDeg: Double, dayOfYear: Int): Double {
        val phi   = latDeg * RAD
        val delta = solarDeclinationDeg(dayOfYear) * RAD
        val cosAz = (sin(delta) - sin(phi) * sin(H0)) / (cos(phi) * cos(H0))
        return acos(cosAz.coerceIn(-1.0, 1.0)) / RAD  // 0–180° (East = 90°)
    }

    fun sunsetAzimuth(latDeg: Double, dayOfYear: Int): Double =
        360.0 - sunriseAzimuth(latDeg, dayOfYear)

    /**
     * Returns true when sunAzimuth is within ±toleranceDeg of seaAzimuth
     * (wraps correctly across 0°/360° boundary).
     */
    fun isOverSea(sunAzimuth: Double, seaAzimuth: Double, toleranceDeg: Double): Boolean {
        var diff = abs(sunAzimuth - seaAzimuth)
        if (diff > 180.0) diff = 360.0 - diff
        return diff <= toleranceDeg
    }

    /** Parse "YYYYMMDD" → day-of-year (1-based). Accounts for leap years. */
    fun dayOfYear(dateStr: String): Int {
        val year  = dateStr.substring(0, 4).toInt()
        val month = dateStr.substring(4, 6).toInt()
        val day   = dateStr.substring(6, 8).toInt()
        val dim   = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) dim[2] = 29
        var doy = 0
        for (m in 1 until month) doy += dim[m]
        return doy + day
    }

    /** "HHMMSS" → "HH:MM", returns null for blank/short input. */
    fun formatTime(hhmmss: String?): String? {
        if (hhmmss == null || hhmmss.length < 4) return null
        return "${hhmmss.substring(0, 2)}:${hhmmss.substring(2, 4)}"
    }
}
