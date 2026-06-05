package com.koretide.app.ui.tidewatch

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.koretide.app.databinding.FragmentTideWatchBinding
import com.koretide.app.domain.model.StationRegion
import com.koretide.app.domain.model.TideData
import com.koretide.app.theme.SeasonThemeManager
import com.koretide.app.ui.main.SharedViewModel
import com.koretide.app.util.collectFlow
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val PREFS_THEME = "theme_prefs"
private const val KEY_THEME_ID = "selected_theme_id"
private const val PREFS_WATCH = "watch_prefs"
private const val KEY_WAVE_SOUND = "wave_sound_enabled"

// Default tidal range used when no station data is available (서해 typical ~600 cm).
private const val DEFAULT_MAX_LEVEL = 600
private const val DEFAULT_MIN_LEVEL = 0

@AndroidEntryPoint
class TideWatchFragment : Fragment() {

    @Inject lateinit var seasonThemeManager: SeasonThemeManager

    private var _binding: FragmentTideWatchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TideWatchViewModel by viewModels()
    private val sharedViewModel: SharedViewModel by activityViewModels()

    private var uiVisible = true
    private var isImmersivePreset = false

    // Cached tidal range data — updated whenever real station data arrives.
    private var cachedRegion: StationRegion? = null
    private var cachedMaxLevel: Int = DEFAULT_MAX_LEVEL
    private var cachedMinLevel: Int = DEFAULT_MIN_LEVEL

    private val oceanSound = OceanSoundPlayer()
    private var soundEnabled = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTideWatchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val savedId = requireContext()
            .getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_ID, null)
        val theme = if (savedId != null) {
            seasonThemeManager.allThemes().firstOrNull { it.id == savedId }
                ?: seasonThemeManager.getThemeForContext(requireContext())
        } else {
            seasonThemeManager.getThemeForContext(requireContext())
        }
        binding.tideWatchView.themeConfig = theme

        setupSliders()
        setupSound()
        setupImmersiveButton()

        // Tap background to toggle immersive; slider panel consumes its own touches
        binding.root.setOnClickListener { toggleImmersive() }

        observeState()
        scheduleGlErrorCheck()
    }

    private fun scheduleGlErrorCheck() {
        Handler(Looper.getMainLooper()).postDelayed({
            val b   = _binding ?: return@postDelayed
            val err = b.tideWatchView.initError ?: return@postDelayed
            val ctx = context ?: return@postDelayed
            android.widget.Toast.makeText(ctx, "GL오류: $err", android.widget.Toast.LENGTH_LONG).show()
        }, 3000L)
    }

    private fun setupSliders() {
        val initialTidePct = binding.seekTide.progress / 100f
        binding.tideWatchView.setTide(initialTidePct)
        binding.tideWatchView.setMudflatExposure(
            computeMudflatExposure(initialTidePct, cachedMaxLevel, cachedMinLevel, cachedRegion)
        )
        binding.tideWatchView.setWind(binding.seekWind.progress)

        binding.seekWind.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                binding.tvWindBft.text = "$progress bft"
                binding.tideWatchView.setWind(progress)
                oceanSound.setIntensity(progress.coerceIn(0, 12) / 12f)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        binding.seekTide.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                binding.tvTidePct.text = "$progress%"
                val pct = progress / 100f
                binding.tideWatchView.setTide(pct)
                // Recompute mudflat exposure whenever the slider changes (user or code).
                binding.tideWatchView.setMudflatExposure(
                    computeMudflatExposure(pct, cachedMaxLevel, cachedMinLevel, cachedRegion)
                )
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun setupSound() {
        soundEnabled = requireContext()
            .getSharedPreferences(PREFS_WATCH, Context.MODE_PRIVATE)
            .getBoolean(KEY_WAVE_SOUND, false)
        binding.switchSound.isChecked = soundEnabled
        oceanSound.setIntensity(binding.seekWind.progress.coerceIn(0, 12) / 12f)
        if (soundEnabled) oceanSound.start()

        binding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            soundEnabled = isChecked
            requireContext()
                .getSharedPreferences(PREFS_WATCH, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_WAVE_SOUND, isChecked).apply()
            if (isChecked) oceanSound.start() else oceanSound.stop()
        }
    }

    private fun setupImmersiveButton() {
        binding.btnImmersive.setOnClickListener {
            isImmersivePreset = true
            binding.tideWatchView.applyImmersivePreset()
            // Hide the UI to enter full immersive view
            uiVisible = false
            binding.overlayCard.visibility = View.GONE
            binding.sliderPanel.visibility = View.GONE
            sharedViewModel.setWatchImmersive(true)
        }
    }

    private fun toggleImmersive() {
        uiVisible = !uiVisible
        val vis = if (uiVisible) View.VISIBLE else View.GONE
        binding.overlayCard.visibility = vis
        binding.sliderPanel.visibility = vis
        sharedViewModel.setWatchImmersive(!uiVisible)

        // Exiting immersive preset: restore real station data / slider state
        if (uiVisible && isImmersivePreset) {
            isImmersivePreset = false
            restoreStationView()
        }
    }

    // Re-applies the current station's data (or slider defaults) after exiting immersive preset.
    private fun restoreStationView() {
        val station = sharedViewModel.selectedStation.value
        binding.tideWatchView.setHasStation(station != null)

        val tideData = viewModel.tideData.value
        if (tideData != null) {
            applyTideData(binding, tideData)
        } else {
            val pct = binding.seekTide.progress / 100f
            binding.tideWatchView.setTide(pct)
            binding.tideWatchView.setMudflatExposure(
                computeMudflatExposure(pct, cachedMaxLevel, cachedMinLevel, cachedRegion)
            )
        }
        viewModel.windData.value?.let { data ->
            binding.tideWatchView.setWind(data.beaufort)
            binding.tideWatchView.windDirectionDeg = data.directionDeg
        }
    }

    private fun observeState() {
        collectFlow(sharedViewModel.selectedThemeId) { themeId ->
            val b = _binding ?: return@collectFlow
            if (themeId != null) {
                val newTheme = seasonThemeManager.allThemes().firstOrNull { it.id == themeId }
                if (newTheme != null) b.tideWatchView.themeConfig = newTheme
            }
        }

        collectFlow(sharedViewModel.selectedStation) { station ->
            val b = _binding ?: return@collectFlow
            cachedRegion = station?.region
            b.tideWatchView.setHasStation(station != null)
            if (station != null) {
                b.tvStationName.text = station.name
                viewModel.startPolling(station.code, station.lat, station.lng)
            }
        }

        collectFlow(viewModel.tideData) { data ->
            val b = _binding ?: return@collectFlow
            if (data != null && !isImmersivePreset) {
                applyTideData(b, data)
                sharedViewModel.updateTideData(data)
                updateMudflatGrade(b, data)
            }
        }

        collectFlow(viewModel.windData) { data ->
            val b = _binding ?: return@collectFlow
            if (data != null && !isImmersivePreset) {
                b.seekWind.progress = data.beaufort
                b.tideWatchView.windDirectionDeg = data.directionDeg
                oceanSound.setIntensity(data.beaufort.coerceIn(0, 12) / 12f)
                sharedViewModel.updateWindData(data)
                b.tvWindInfo.text = "${data.beaufortName} (${data.beaufort}bft, ${data.speedMs}m/s)"
            }
        }
    }

    private fun applyTideData(b: FragmentTideWatchBinding, data: TideData) {
        cachedMaxLevel = data.maxLevel
        cachedMinLevel = data.minLevel

        val exposure = computeMudflatExposure(data.currentLevel, data.maxLevel, data.minLevel, cachedRegion)
        val pct = (data.tidePercent * 100).toInt().coerceIn(0, 100)

        b.seekTide.progress = pct
        b.tideWatchView.setMudflatExposure(exposure)
        b.tvTideInfo.text = "${data.tideStatus.displayName} $pct%"
    }

    private fun updateMudflatGrade(b: FragmentTideWatchBinding, data: TideData) {
        val region = cachedRegion
        val hasTidalFlat = region == StationRegion.WEST || region == StationRegion.SOUTH
        val tidalRange = data.maxLevel - data.minLevel
        if (hasTidalFlat && tidalRange > 100) {
            val exposure = computeMudflatExposure(data.currentLevel, data.maxLevel, data.minLevel, region)
            b.tvMudflatGrade.text = when {
                exposure >= 0.60f -> "🦀 갯벌 매우 많이 드러남"
                exposure >= 0.30f -> "🦀 갯벌 보통 드러남"
                exposure >= 0.05f -> "🦀 갯벌 조금 드러남"
                else              -> null
            }
            b.tvMudflatGrade.visibility = if (exposure >= 0.05f) View.VISIBLE else View.GONE
        } else {
            b.tvMudflatGrade.visibility = View.GONE
        }
    }

    override fun onPause() {
        super.onPause()
        _binding?.tideWatchView?.onPause()
        oceanSound.stop()
        viewModel.stopPolling()
        if (!uiVisible) {
            uiVisible = true
            isImmersivePreset = false
            sharedViewModel.setWatchImmersive(false)
        }
    }

    override fun onResume() {
        super.onResume()
        _binding?.tideWatchView?.onResume()
        if (soundEnabled) oceanSound.start()
        val station = sharedViewModel.selectedStation.value
        if (station != null) {
            viewModel.startPolling(station.code, station.lat, station.lng)
        }
    }

    override fun onDestroyView() {
        sharedViewModel.setWatchImmersive(false)
        oceanSound.stop()
        _binding?.tideWatchView?.release()
        super.onDestroyView()
        _binding = null
    }
}

// ── Tidal flat exposure computation ──────────────────────────────────────────
// Returns a 0..1 value reflecting how much tidal flat is currently visible,
// combining: (1) how far the tide is out, (2) the local tidal range magnitude,
// and (3) a hard cap per coast type (동해 rarely exposes mudflat).
private fun computeMudflatExposure(
    currentLevel: Int, maxLevel: Int, minLevel: Int, region: StationRegion?
): Float {
    val tidalRange = (maxLevel - minLevel).toFloat()
    if (tidalRange <= 0f) return 0f

    // 0 = submerged (high tide), 1 = fully exposed (low tide)
    val tidePosition = ((maxLevel - currentLevel).toFloat() / tidalRange).coerceIn(0f, 1f)

    // How much this location's range allows mudflat to form
    val rangeFactor = when {
        tidalRange < 100f -> 0.00f                                                    // <1m: no flat
        tidalRange < 300f -> (tidalRange - 100f) / 200f * 0.30f                      // 1-3m: small
        tidalRange < 500f -> 0.30f + (tidalRange - 300f) / 200f * 0.50f              // 3-5m: moderate
        else              -> 0.80f + ((tidalRange - 500f) / 500f).coerceAtMost(1f) * 0.20f // 5m+: large
    }

    // Per-region hard cap: even at maximum range, 동해/제주 show almost no mudflat
    val regionCap = when (region) {
        StationRegion.WEST  -> 1.00f
        StationRegion.SOUTH -> 0.60f
        StationRegion.JEJU  -> 0.08f
        StationRegion.EAST  -> 0.04f
        null                -> 1.00f
    }

    return (tidePosition * rangeFactor * regionCap).coerceIn(0f, 1f)
}

// Overload for the manual slider (no actual level data, only tidePercent).
private fun computeMudflatExposure(
    tidePercent: Float, maxLevel: Int, minLevel: Int, region: StationRegion?
): Float {
    val tidalRange = (maxLevel - minLevel).toFloat()
    // tidePercent=0 → lowest tide → currentLevel=minLevel; tidePercent=1 → highest → currentLevel=maxLevel
    val currentLevel = (minLevel + tidePercent * tidalRange).toInt()
    return computeMudflatExposure(currentLevel, maxLevel, minLevel, region)
}
