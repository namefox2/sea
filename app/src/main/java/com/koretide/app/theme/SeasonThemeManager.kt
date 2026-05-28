package com.koretide.app.theme

import android.content.Context
import android.content.res.Configuration
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeasonThemeManager @Inject constructor() {

    private val themes: Map<String, ThemeConfig> = buildThemes()

    fun getTheme(date: LocalDate, isDark: Boolean): ThemeConfig {
        val season = detectSeason(date)
        val key = "${season.name}_${if (isDark) "DARK" else "LIGHT"}"
        return themes[key] ?: themes.values.first()
    }

    fun getThemeForContext(context: Context): ThemeConfig {
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        return getTheme(LocalDate.now(), isDark)
    }

    fun detectSeason(date: LocalDate): Season = when (date.monthValue) {
        in 3..5  -> Season.SPRING
        in 6..8  -> Season.SUMMER
        in 9..11 -> Season.AUTUMN
        else     -> Season.WINTER
    }

    fun allThemes(): List<ThemeConfig> = themes.values.toList()

    private fun buildThemes(): Map<String, ThemeConfig> = mapOf(
        "SPRING_DARK" to ThemeConfig(
            id = "SPRING_DARK", displayName = "새벽 새싹", season = Season.SPRING, isDark = true,
            skyTopColor = ThemeColors.SPRING_DARK_SKY_TOP, skyBottomColor = ThemeColors.SPRING_DARK_SKY_BOT,
            seaTopColor = ThemeColors.SPRING_DARK_SEA_TOP, seaBottomColor = ThemeColors.SPRING_DARK_SEA_BOT,
            mountainColor = ThemeColors.SPRING_DARK_MTN, cloudColor = ThemeColors.SPRING_DARK_CLOUD,
            sunColor = ThemeColors.SPRING_DARK_SUN, waveColor = ThemeColors.SPRING_DARK_WAVE,
            foamColor = ThemeColors.SPRING_DARK_FOAM, tidalFlatColor = ThemeColors.SPRING_DARK_FLAT,
            fogColor = ThemeColors.SPRING_DARK_FOG, defaultWindBft = 3, hasMist = true, cloudDensity = 0.4f
        ),
        "SPRING_LIGHT" to ThemeConfig(
            id = "SPRING_LIGHT", displayName = "봄빛 여명", season = Season.SPRING, isDark = false,
            skyTopColor = ThemeColors.SPRING_LIGHT_SKY_TOP, skyBottomColor = ThemeColors.SPRING_LIGHT_SKY_BOT,
            seaTopColor = ThemeColors.SPRING_LIGHT_SEA_TOP, seaBottomColor = ThemeColors.SPRING_LIGHT_SEA_BOT,
            mountainColor = ThemeColors.SPRING_LIGHT_MTN, cloudColor = ThemeColors.SPRING_LIGHT_CLOUD,
            sunColor = ThemeColors.SPRING_LIGHT_SUN, waveColor = ThemeColors.SPRING_LIGHT_WAVE,
            foamColor = ThemeColors.SPRING_LIGHT_FOAM, tidalFlatColor = ThemeColors.SPRING_LIGHT_FLAT,
            fogColor = ThemeColors.SPRING_LIGHT_FOG, defaultWindBft = 2, hasMist = true, cloudDensity = 0.3f
        ),
        "SUMMER_DARK" to ThemeConfig(
            id = "SUMMER_DARK", displayName = "밤바다", season = Season.SUMMER, isDark = true,
            skyTopColor = ThemeColors.SUMMER_DARK_SKY_TOP, skyBottomColor = ThemeColors.SUMMER_DARK_SKY_BOT,
            seaTopColor = ThemeColors.SUMMER_DARK_SEA_TOP, seaBottomColor = ThemeColors.SUMMER_DARK_SEA_BOT,
            mountainColor = ThemeColors.SUMMER_DARK_MTN, cloudColor = ThemeColors.SUMMER_DARK_CLOUD,
            sunColor = ThemeColors.SUMMER_DARK_SUN, waveColor = ThemeColors.SUMMER_DARK_WAVE,
            foamColor = ThemeColors.SUMMER_DARK_FOAM, tidalFlatColor = ThemeColors.SUMMER_DARK_FLAT,
            fogColor = ThemeColors.SUMMER_DARK_FOG, defaultWindBft = 6, hasSunGlitter = true, cloudDensity = 0.5f
        ),
        "SUMMER_LIGHT" to ThemeConfig(
            id = "SUMMER_LIGHT", displayName = "맑은 낮바다", season = Season.SUMMER, isDark = false,
            skyTopColor = ThemeColors.SUMMER_LIGHT_SKY_TOP, skyBottomColor = ThemeColors.SUMMER_LIGHT_SKY_BOT,
            seaTopColor = ThemeColors.SUMMER_LIGHT_SEA_TOP, seaBottomColor = ThemeColors.SUMMER_LIGHT_SEA_BOT,
            mountainColor = ThemeColors.SUMMER_LIGHT_MTN, cloudColor = ThemeColors.SUMMER_LIGHT_CLOUD,
            sunColor = ThemeColors.SUMMER_LIGHT_SUN, waveColor = ThemeColors.SUMMER_LIGHT_WAVE,
            foamColor = ThemeColors.SUMMER_LIGHT_FOAM, tidalFlatColor = ThemeColors.SUMMER_LIGHT_FLAT,
            fogColor = ThemeColors.SUMMER_LIGHT_FOG, defaultWindBft = 5, hasSunGlitter = true, cloudDensity = 0.2f
        ),
        "AUTUMN_DARK" to ThemeConfig(
            id = "AUTUMN_DARK", displayName = "황혼 갯벌", season = Season.AUTUMN, isDark = true,
            skyTopColor = ThemeColors.AUTUMN_DARK_SKY_TOP, skyBottomColor = ThemeColors.AUTUMN_DARK_SKY_BOT,
            seaTopColor = ThemeColors.AUTUMN_DARK_SEA_TOP, seaBottomColor = ThemeColors.AUTUMN_DARK_SEA_BOT,
            mountainColor = ThemeColors.AUTUMN_DARK_MTN, cloudColor = ThemeColors.AUTUMN_DARK_CLOUD,
            sunColor = ThemeColors.AUTUMN_DARK_SUN, waveColor = ThemeColors.AUTUMN_DARK_WAVE,
            foamColor = ThemeColors.AUTUMN_DARK_FOAM, tidalFlatColor = ThemeColors.AUTUMN_DARK_FLAT,
            fogColor = ThemeColors.AUTUMN_DARK_FOG, defaultWindBft = 4, cloudDensity = 0.6f
        ),
        "AUTUMN_LIGHT" to ThemeConfig(
            id = "AUTUMN_LIGHT", displayName = "노을 해안", season = Season.AUTUMN, isDark = false,
            skyTopColor = ThemeColors.AUTUMN_LIGHT_SKY_TOP, skyBottomColor = ThemeColors.AUTUMN_LIGHT_SKY_BOT,
            seaTopColor = ThemeColors.AUTUMN_LIGHT_SEA_TOP, seaBottomColor = ThemeColors.AUTUMN_LIGHT_SEA_BOT,
            mountainColor = ThemeColors.AUTUMN_LIGHT_MTN, cloudColor = ThemeColors.AUTUMN_LIGHT_CLOUD,
            sunColor = ThemeColors.AUTUMN_LIGHT_SUN, waveColor = ThemeColors.AUTUMN_LIGHT_WAVE,
            foamColor = ThemeColors.AUTUMN_LIGHT_FOAM, tidalFlatColor = ThemeColors.AUTUMN_LIGHT_FLAT,
            fogColor = ThemeColors.AUTUMN_LIGHT_FOG, defaultWindBft = 3, cloudDensity = 0.4f
        ),
        "WINTER_DARK" to ThemeConfig(
            id = "WINTER_DARK", displayName = "설야 바다", season = Season.WINTER, isDark = true,
            skyTopColor = ThemeColors.WINTER_DARK_SKY_TOP, skyBottomColor = ThemeColors.WINTER_DARK_SKY_BOT,
            seaTopColor = ThemeColors.WINTER_DARK_SEA_TOP, seaBottomColor = ThemeColors.WINTER_DARK_SEA_BOT,
            mountainColor = ThemeColors.WINTER_DARK_MTN, cloudColor = ThemeColors.WINTER_DARK_CLOUD,
            sunColor = ThemeColors.WINTER_DARK_SUN, waveColor = ThemeColors.WINTER_DARK_WAVE,
            foamColor = ThemeColors.WINTER_DARK_FOAM, tidalFlatColor = ThemeColors.WINTER_DARK_FLAT,
            fogColor = ThemeColors.WINTER_DARK_FOG, defaultWindBft = 7, hasSeagulls = false, cloudDensity = 0.9f
        ),
        "WINTER_LIGHT" to ThemeConfig(
            id = "WINTER_LIGHT", displayName = "달빛 설경", season = Season.WINTER, isDark = false,
            skyTopColor = ThemeColors.WINTER_LIGHT_SKY_TOP, skyBottomColor = ThemeColors.WINTER_LIGHT_SKY_BOT,
            seaTopColor = ThemeColors.WINTER_LIGHT_SEA_TOP, seaBottomColor = ThemeColors.WINTER_LIGHT_SEA_BOT,
            mountainColor = ThemeColors.WINTER_LIGHT_MTN, cloudColor = ThemeColors.WINTER_LIGHT_CLOUD,
            sunColor = ThemeColors.WINTER_LIGHT_SUN, waveColor = ThemeColors.WINTER_LIGHT_WAVE,
            foamColor = ThemeColors.WINTER_LIGHT_FOAM, tidalFlatColor = ThemeColors.WINTER_LIGHT_FLAT,
            fogColor = ThemeColors.WINTER_LIGHT_FOG, defaultWindBft = 6, hasSeagulls = false, cloudDensity = 0.7f
        )
    )
}
