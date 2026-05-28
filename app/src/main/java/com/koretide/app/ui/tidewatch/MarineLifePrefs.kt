package com.koretide.app.ui.tidewatch

import android.content.Context
import com.koretide.app.ui.tidewatch.marine.MarineLifeSettings

private const val PREFS = "marine_life_prefs"
private const val KEY_FISH  = "fish_enabled"
private const val KEY_CRAB  = "crab_enabled"
private const val KEY_CLAM  = "clam_enabled"
private const val KEY_CUTTLEFISH = "cuttlefish_enabled"

object MarineLifePrefs {

    fun save(context: Context, settings: MarineLifeSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_FISH,       settings.fishEnabled)
            .putBoolean(KEY_CRAB,       settings.crabEnabled)
            .putBoolean(KEY_CLAM,       settings.clamEnabled)
            .putBoolean(KEY_CUTTLEFISH, settings.cuttlefishEnabled)
            .apply()
    }

    fun load(context: Context): MarineLifeSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return MarineLifeSettings(
            fishEnabled       = prefs.getBoolean(KEY_FISH,       true),
            crabEnabled       = prefs.getBoolean(KEY_CRAB,       true),
            clamEnabled       = prefs.getBoolean(KEY_CLAM,       true),
            cuttlefishEnabled = prefs.getBoolean(KEY_CUTTLEFISH, true)
        )
    }
}
