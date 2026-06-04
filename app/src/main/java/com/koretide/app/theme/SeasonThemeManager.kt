package com.koretide.app.theme

import android.content.Context
import android.content.res.Configuration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeasonThemeManager @Inject constructor() {

    private val themes: Map<String, ThemeConfig> = buildThemes()

    fun getThemeForContext(context: Context): ThemeConfig {
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        return if (isDark) themes["SUMMER_DARK"]!! else themes["SUMMER_LIGHT"]!!
    }

    fun allThemes(): List<ThemeConfig> = listOf(
        themes["SUMMER_LIGHT"]!!,
        themes["AUTUMN_LIGHT"]!!,
        themes["SUMMER_DARK"]!!
    )

    private fun buildThemes(): Map<String, ThemeConfig> = mapOf(
        "SUMMER_LIGHT" to ThemeConfig(
            id = "SUMMER_LIGHT", displayName = "맑은 낮바다", season = Season.SUMMER, isDark = false,
            skyTopColor = ThemeColors.SUMMER_LIGHT_SKY_TOP, skyBottomColor = ThemeColors.SUMMER_LIGHT_SKY_BOT,
            seaTopColor = ThemeColors.SUMMER_LIGHT_SEA_TOP, seaBottomColor = ThemeColors.SUMMER_LIGHT_SEA_BOT,
            mountainColor = ThemeColors.SUMMER_LIGHT_MTN, cloudColor = ThemeColors.SUMMER_LIGHT_CLOUD,
            sunColor = ThemeColors.SUMMER_LIGHT_SUN, waveColor = ThemeColors.SUMMER_LIGHT_WAVE,
            foamColor = ThemeColors.SUMMER_LIGHT_FOAM, tidalFlatColor = ThemeColors.SUMMER_LIGHT_FLAT,
            fogColor = ThemeColors.SUMMER_LIGHT_FOG, defaultWindBft = 5, hasSunGlitter = true, cloudDensity = 0.2f
        ),
        "AUTUMN_LIGHT" to ThemeConfig(
            id = "AUTUMN_LIGHT", displayName = "노을해안", season = Season.AUTUMN, isDark = false,
            skyTopColor = ThemeColors.AUTUMN_LIGHT_SKY_TOP, skyBottomColor = ThemeColors.AUTUMN_LIGHT_SKY_BOT,
            seaTopColor = ThemeColors.AUTUMN_LIGHT_SEA_TOP, seaBottomColor = ThemeColors.AUTUMN_LIGHT_SEA_BOT,
            mountainColor = ThemeColors.AUTUMN_LIGHT_MTN, cloudColor = ThemeColors.AUTUMN_LIGHT_CLOUD,
            sunColor = ThemeColors.AUTUMN_LIGHT_SUN, waveColor = ThemeColors.AUTUMN_LIGHT_WAVE,
            foamColor = ThemeColors.AUTUMN_LIGHT_FOAM, tidalFlatColor = ThemeColors.AUTUMN_LIGHT_FLAT,
            fogColor = ThemeColors.AUTUMN_LIGHT_FOG, defaultWindBft = 3, cloudDensity = 0.4f
        ),
        "SUMMER_DARK" to ThemeConfig(
            id = "SUMMER_DARK", displayName = "밤바다", season = Season.SUMMER, isDark = true,
            skyTopColor = ThemeColors.SUMMER_DARK_SKY_TOP, skyBottomColor = ThemeColors.SUMMER_DARK_SKY_BOT,
            seaTopColor = ThemeColors.SUMMER_DARK_SEA_TOP, seaBottomColor = ThemeColors.SUMMER_DARK_SEA_BOT,
            mountainColor = ThemeColors.SUMMER_DARK_MTN, cloudColor = ThemeColors.SUMMER_DARK_CLOUD,
            sunColor = ThemeColors.SUMMER_DARK_SUN, waveColor = ThemeColors.SUMMER_DARK_WAVE,
            foamColor = ThemeColors.SUMMER_DARK_FOAM, tidalFlatColor = ThemeColors.SUMMER_DARK_FLAT,
            fogColor = ThemeColors.SUMMER_DARK_FOG, defaultWindBft = 3, hasSunGlitter = true, cloudDensity = 0.5f
        )
    )
}
