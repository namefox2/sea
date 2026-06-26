package com.koretide.app.domain.usecase

import com.koretide.app.BuildConfig
import com.koretide.app.data.remote.KasiApiService
import com.koretide.app.domain.model.SunViewInfo
import com.koretide.app.domain.model.SunriseSpot
import com.koretide.app.util.SunAzimuthCalculator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches rise/set times from KASI API and combines them with mathematically
 * computed azimuths to determine whether the sun rises or sets over the sea
 * for a given beach spot.
 *
 * Falls back to math-only when API key is blank or the call fails.
 */
@Singleton
class GetSunViewInfoUseCase @Inject constructor(
    private val kasiApi: KasiApiService
) {
    private val key get() = BuildConfig.KASI_API_KEY

    suspend operator fun invoke(spot: SunriseSpot, date: String): SunViewInfo {
        val doy           = SunAzimuthCalculator.dayOfYear(date)
        val riseAz        = SunAzimuthCalculator.sunriseAzimuth(spot.lat, doy)
        val setAz         = SunAzimuthCalculator.sunsetAzimuth(spot.lat, doy)
        val riseOverSea   = SunAzimuthCalculator.isOverSea(riseAz, spot.seaAzimuth, spot.azimuthTolerance)
        val setOverSea    = SunAzimuthCalculator.isOverSea(setAz,  spot.seaAzimuth, spot.azimuthTolerance)

        var sunriseTime: String? = null
        var sunsetTime:  String? = null

        if (key.isNotBlank()) {
            try {
                val resp = kasiApi.getRiseSetByCoords(
                    serviceKey = key,
                    locdate    = date,
                    longitude  = "%.4f".format(spot.lon),
                    latitude   = "%.4f".format(spot.lat)
                )
                val item = resp.body?.items?.item?.firstOrNull()
                sunriseTime = SunAzimuthCalculator.formatTime(item?.sunrise)
                sunsetTime  = SunAzimuthCalculator.formatTime(item?.sunset)
            } catch (_: Exception) {
                // fall through with null times; azimuth math still valid
            }
        }

        return SunViewInfo(
            spot            = spot,
            date            = date,
            sunriseTime     = sunriseTime,
            sunsetTime      = sunsetTime,
            sunriseAzimuth  = riseAz,
            sunsetAzimuth   = setAz,
            sunriseOverSea  = riseOverSea,
            sunsetOverSea   = setOverSea
        )
    }
}
