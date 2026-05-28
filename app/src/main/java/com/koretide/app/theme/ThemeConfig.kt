package com.koretide.app.theme

import android.graphics.Color

data class ThemeConfig(
    val id: String,
    val displayName: String,
    val season: Season,
    val isDark: Boolean,
    val skyTopColor: Int,
    val skyBottomColor: Int,
    val seaTopColor: Int,
    val seaBottomColor: Int,
    val mountainColor: Int,
    val cloudColor: Int,
    val sunColor: Int,
    val waveColor: Int,
    val foamColor: Int,
    val tidalFlatColor: Int,
    val fogColor: Int,
    val defaultWindBft: Int,
    val hasMist: Boolean = false,
    val hasSeagulls: Boolean = true,
    val hasSunGlitter: Boolean = true,
    val cloudDensity: Float = 0.5f
)

enum class Season { SPRING, SUMMER, AUTUMN, WINTER }

object ThemeColors {
    val SPRING_DARK_SKY_TOP = Color.parseColor("#1A2E1A")
    val SPRING_DARK_SKY_BOT = Color.parseColor("#2D4A2D")
    val SPRING_DARK_SEA_TOP = Color.parseColor("#1E3D1E")
    val SPRING_DARK_SEA_BOT = Color.parseColor("#0D250D")
    val SPRING_DARK_WAVE   = Color.parseColor("#2E6B2E")
    val SPRING_DARK_SUN    = Color.parseColor("#A8D88A")
    val SPRING_DARK_MTN    = Color.parseColor("#1A3D1A")
    val SPRING_DARK_CLOUD  = Color.parseColor("#2E5A2E")
    val SPRING_DARK_FLAT   = Color.parseColor("#3D5E2E")
    val SPRING_DARK_FOAM   = Color.parseColor("#C5E8B0")
    val SPRING_DARK_FOG    = Color.argb(80, 180, 220, 150)

    val SPRING_LIGHT_SKY_TOP = Color.parseColor("#D4EDBB")
    val SPRING_LIGHT_SKY_BOT = Color.parseColor("#E8F5D4")
    val SPRING_LIGHT_SEA_TOP = Color.parseColor("#4CAF50")
    val SPRING_LIGHT_SEA_BOT = Color.parseColor("#1B5E20")
    val SPRING_LIGHT_WAVE   = Color.parseColor("#66BB6A")
    val SPRING_LIGHT_SUN    = Color.parseColor("#FFF59D")
    val SPRING_LIGHT_MTN    = Color.parseColor("#388E3C")
    val SPRING_LIGHT_CLOUD  = Color.parseColor("#E8F5E9")
    val SPRING_LIGHT_FLAT   = Color.parseColor("#8BC34A")
    val SPRING_LIGHT_FOAM   = Color.parseColor("#F1F8E9")
    val SPRING_LIGHT_FOG    = Color.argb(60, 200, 240, 180)

    val SUMMER_DARK_SKY_TOP = Color.parseColor("#0A1628")
    val SUMMER_DARK_SKY_BOT = Color.parseColor("#0D2137")
    val SUMMER_DARK_SEA_TOP = Color.parseColor("#1565C0")
    val SUMMER_DARK_SEA_BOT = Color.parseColor("#0D47A1")
    val SUMMER_DARK_WAVE   = Color.parseColor("#1976D2")
    val SUMMER_DARK_SUN    = Color.parseColor("#81D4FA")
    val SUMMER_DARK_MTN    = Color.parseColor("#0D2137")
    val SUMMER_DARK_CLOUD  = Color.parseColor("#263850")
    val SUMMER_DARK_FLAT   = Color.parseColor("#1A3A5C")
    val SUMMER_DARK_FOAM   = Color.parseColor("#B3E5FC")
    val SUMMER_DARK_FOG    = Color.argb(60, 30, 80, 180)

    val SUMMER_LIGHT_SKY_TOP = Color.parseColor("#81D4FA")
    val SUMMER_LIGHT_SKY_BOT = Color.parseColor("#E1F5FE")
    val SUMMER_LIGHT_SEA_TOP = Color.parseColor("#0288D1")
    val SUMMER_LIGHT_SEA_BOT = Color.parseColor("#01579B")
    val SUMMER_LIGHT_WAVE   = Color.parseColor("#039BE5")
    val SUMMER_LIGHT_SUN    = Color.parseColor("#FFFDE7")
    val SUMMER_LIGHT_MTN    = Color.parseColor("#1565C0")
    val SUMMER_LIGHT_CLOUD  = Color.parseColor("#F0F9FF")
    val SUMMER_LIGHT_FLAT   = Color.parseColor("#26A69A")
    val SUMMER_LIGHT_FOAM   = Color.parseColor("#E1F5FE")
    val SUMMER_LIGHT_FOG    = Color.argb(40, 150, 200, 255)

    val AUTUMN_DARK_SKY_TOP = Color.parseColor("#2C1810")
    val AUTUMN_DARK_SKY_BOT = Color.parseColor("#4A2E18")
    val AUTUMN_DARK_SEA_TOP = Color.parseColor("#6D4C41")
    val AUTUMN_DARK_SEA_BOT = Color.parseColor("#3E2723")
    val AUTUMN_DARK_WAVE   = Color.parseColor("#8D6E63")
    val AUTUMN_DARK_SUN    = Color.parseColor("#FF8F00")
    val AUTUMN_DARK_MTN    = Color.parseColor("#4E342E")
    val AUTUMN_DARK_CLOUD  = Color.parseColor("#5D4037")
    val AUTUMN_DARK_FLAT   = Color.parseColor("#795548")
    val AUTUMN_DARK_FOAM   = Color.parseColor("#FFCCBC")
    val AUTUMN_DARK_FOG    = Color.argb(70, 180, 100, 50)

    val AUTUMN_LIGHT_SKY_TOP = Color.parseColor("#FFD54F")
    val AUTUMN_LIGHT_SKY_BOT = Color.parseColor("#FFECB3")
    val AUTUMN_LIGHT_SEA_TOP = Color.parseColor("#FF7043")
    val AUTUMN_LIGHT_SEA_BOT = Color.parseColor("#BF360C")
    val AUTUMN_LIGHT_WAVE   = Color.parseColor("#FF8A65")
    val AUTUMN_LIGHT_SUN    = Color.parseColor("#FFD740")
    val AUTUMN_LIGHT_MTN    = Color.parseColor("#E65100")
    val AUTUMN_LIGHT_CLOUD  = Color.parseColor("#FFF8E1")
    val AUTUMN_LIGHT_FLAT   = Color.parseColor("#A1887F")
    val AUTUMN_LIGHT_FOAM   = Color.parseColor("#FFF3E0")
    val AUTUMN_LIGHT_FOG    = Color.argb(50, 220, 150, 80)

    val WINTER_DARK_SKY_TOP = Color.parseColor("#0D0E1A")
    val WINTER_DARK_SKY_BOT = Color.parseColor("#1A1B2E")
    val WINTER_DARK_SEA_TOP = Color.parseColor("#263064")
    val WINTER_DARK_SEA_BOT = Color.parseColor("#1A1B3A")
    val WINTER_DARK_WAVE   = Color.parseColor("#3949AB")
    val WINTER_DARK_SUN    = Color.parseColor("#B39DDB")
    val WINTER_DARK_MTN    = Color.parseColor("#1C1B3A")
    val WINTER_DARK_CLOUD  = Color.parseColor("#2A2A4A")
    val WINTER_DARK_FLAT   = Color.parseColor("#3949AB")
    val WINTER_DARK_FOAM   = Color.parseColor("#C5CAE9")
    val WINTER_DARK_FOG    = Color.argb(120, 60, 60, 180)

    val WINTER_LIGHT_SKY_TOP = Color.parseColor("#B0BEC5")
    val WINTER_LIGHT_SKY_BOT = Color.parseColor("#ECEFF1")
    val WINTER_LIGHT_SEA_TOP = Color.parseColor("#5C6BC0")
    val WINTER_LIGHT_SEA_BOT = Color.parseColor("#283593")
    val WINTER_LIGHT_WAVE   = Color.parseColor("#7986CB")
    val WINTER_LIGHT_SUN    = Color.parseColor("#E8EAF6")
    val WINTER_LIGHT_MTN    = Color.parseColor("#455A64")
    val WINTER_LIGHT_CLOUD  = Color.parseColor("#FAFAFA")
    val WINTER_LIGHT_FLAT   = Color.parseColor("#90A4AE")
    val WINTER_LIGHT_FOAM   = Color.parseColor("#E8EAF6")
    val WINTER_LIGHT_FOG    = Color.argb(100, 180, 200, 240)
}
