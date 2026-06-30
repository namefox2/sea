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
import com.koretide.app.domain.model.TidalCalibration
import com.koretide.app.theme.SeasonThemeManager
import com.koretide.app.ui.main.SharedViewModel
import com.koretide.app.util.collectFlow
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val PREFS_THEME = "theme_prefs"
private const val KEY_THEME_ID = "selected_theme_id"
private const val PREFS_WATCH = "watch_prefs"
private const val KEY_WAVE_SOUND = "wave_sound_enabled"

@AndroidEntryPoint
class TideWatchFragment : Fragment() {

    @Inject lateinit var seasonThemeManager: SeasonThemeManager

    private var _binding: FragmentTideWatchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TideWatchViewModel by viewModels()
    private val sharedViewModel: SharedViewModel by activityViewModels()

    private var uiVisible = true
    private var isImmersivePreset = false

    private var cachedRegion: StationRegion? = null
    // Per-region historical calibration; drives waterlineZ via absolute cm → visual Z mapping.
    private var cachedCalibration: TidalCalibration = TidalCalibration.forRegion(StationRegion.WEST)
    // Today's actual tidal range in metres (from API) — used only for mudflat exposure.
    private var cachedTidalRangeM: Float = 4.5f

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
        // Apply default (서해) calibration visual range on startup
        binding.tideWatchView.setVisualRange(cachedCalibration.visualMinZ, cachedCalibration.visualMaxZ)

        val initialT = binding.seekTide.progress / 100f
        binding.tideWatchView.setTide(initialT)
        binding.tideWatchView.setMudflatExposure(
            computeMudflatExposure(initialT, cachedTidalRangeM, cachedRegion)
        )
        // 조차 row: display-only — shows today's actual tidal range from API
        binding.seekTidalRange.isEnabled = false
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

        // 물때 slider: calibratedT in [0,1] — higher = more water (correct direction)
        binding.seekTide.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                binding.tvTidePct.text = "$progress%"
                val t = progress / 100f
                binding.tideWatchView.setTide(t)
                binding.tideWatchView.setMudflatExposure(
                    computeMudflatExposure(t, cachedTidalRangeM, cachedRegion)
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
            // Note: isImmersivePreset intentionally NOT set here — keeps double-tap from
            // calling restoreStationView() and changing slider values unexpectedly.
            cachedTidalRangeM = 5.5f
            // 지역정보 삭제 → visual Z 범위도 기본(서해)값으로 복원해 첫 실행과 동일하게 동작.
            // (좁은 지역 범위가 남아 있으면 물때를 올릴 때 수면선이 거의 안 올라가
            //  beach 셰이더의 underwater 어둡게 처리만 커져 "바다 아래가 까매지는" 문제 발생)
            cachedRegion = null
            cachedCalibration = TidalCalibration.forRegion(StationRegion.WEST)
            binding.tideWatchView.setVisualRange(cachedCalibration.visualMinZ, cachedCalibration.visualMaxZ)
            // Set sliders first; their listeners may queue a mudflatExposure GL event
            binding.seekWind.progress       = 2
            binding.seekTide.progress       = 35
            binding.seekTidalRange.progress = 55
            binding.tvTidalRange.text       = "5.5m"
            // Apply preset AFTER sliders so its GL events queue last and win.
            // Sets useDefaultSun=true, defaultHour=noon, tide=35%, wind=2bft, mudflat=0.
            binding.tideWatchView.applyImmersivePreset()
            // Clear station info overlay
            binding.tvStationName.text      = ""
            binding.tvTideInfo.text         = ""
            binding.tvWindInfo.text         = ""
            binding.tvMudflatGrade.visibility = View.GONE
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
            val t = binding.seekTide.progress / 100f
            binding.tideWatchView.setTide(t)
            binding.tideWatchView.setMudflatExposure(
                computeMudflatExposure(t, cachedTidalRangeM, cachedRegion)
            )
        }
        viewModel.windData.value?.let { data ->
            val waveBasedBft = data.waveHeightM?.let { h -> (h * 2.0 + 1.0).coerceIn(0.0, 12.0).toInt() }
            val effectiveBft = if (waveBasedBft != null) maxOf(data.beaufort, waveBasedBft) else data.beaufort
            binding.tideWatchView.setWind(effectiveBft)
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
            isImmersivePreset = false
            cachedRegion = station?.region
            cachedCalibration = TidalCalibration.forRegion(
                station?.region ?: StationRegion.WEST
            )
            b.tideWatchView.setVisualRange(cachedCalibration.visualMinZ, cachedCalibration.visualMaxZ)
            b.tideWatchView.setHasStation(station != null)
            if (station != null) {
                b.tvStationName.text = station.name
                // Detail에서 받아둔 캐시가 있으면 선적재 → 즉시 표시 + 5분 폴링 지연
                val cachedTide = sharedViewModel.cachedTideFor(station.code)
                val cachedWind = sharedViewModel.windData.value
                if (cachedTide != null) viewModel.preloadFromCache(station.code, cachedTide, cachedWind)
                viewModel.startPolling(station.code, station.lat, station.lng)
            }
        }

        collectFlow(viewModel.sunTimes) { times ->
            val b = _binding ?: return@collectFlow
            if (times != null) {
                b.tideWatchView.setSunTimes(times.sunriseHour, times.sunsetHour, times.moonriseHour, times.moonsetHour)
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
                // Blend forecast Beaufort with wave-based estimate: take the higher value
                // so that a calm wind forecast doesn't hide rough sea state from wave data.
                val waveBasedBft = data.waveHeightM?.let { h ->
                    (h * 2.0 + 1.0).coerceIn(0.0, 12.0).toInt()
                }
                val effectiveBft = if (waveBasedBft != null) maxOf(data.beaufort, waveBasedBft) else data.beaufort
                b.seekWind.progress = effectiveBft
                b.tideWatchView.windDirectionDeg = data.directionDeg
                oceanSound.setIntensity(effectiveBft.coerceIn(0, 12) / 12f)
                sharedViewModel.updateWindData(data)
                val waveStr = data.waveHeightM?.let { " / 파고 %.1fm".format(it) } ?: ""
                b.tvWindInfo.text = "${data.beaufortName} (${effectiveBft}bft, ${data.speedMs}m/s)$waveStr"
            }
        }
    }

    private fun applyTideData(b: FragmentTideWatchBinding, data: TideData) {
        cachedTidalRangeM = data.tidalRangeM

        // Display % matches Detail screen (today's relative position)
        val displayPct = (data.tidePercent * 100).toInt().coerceIn(0, 100)
        b.seekTide.progress = displayPct
        b.tvTidePct.text    = "$displayPct%"

        b.seekTidalRange.progress = (data.tidalRangeM * 10).toInt().coerceIn(0, 100)
        b.tvTidalRange.text = "%.1fm".format(data.tidalRangeM)

        // 3D scene uses historical regional calibration for visual accuracy
        val calibratedT = cachedCalibration.calibratedT(data.currentLevel)
        b.tideWatchView.setTide(calibratedT)
        b.tideWatchView.setMudflatExposure(
            computeMudflatExposure(calibratedT, data.tidalRangeM, cachedRegion)
        )
        b.tvTideInfo.text = "${data.tideStatus.displayName} ${data.currentLevel}cm"
    }

    private fun updateMudflatGrade(b: FragmentTideWatchBinding, data: TideData) {
        val region = cachedRegion
        val hasTidalFlat = region == StationRegion.WEST || region == StationRegion.SOUTH
        if (hasTidalFlat && data.tidalRangeM > 1.0f) {
            val calibratedT = cachedCalibration.calibratedT(data.currentLevel)
            val exposure = computeMudflatExposure(calibratedT, data.tidalRangeM, region)
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
        isImmersivePreset = false
        if (!uiVisible) {
            uiVisible = true
            sharedViewModel.setWatchImmersive(false)
        }
    }

    override fun onResume() {
        super.onResume()
        _binding?.tideWatchView?.onResume()
        if (soundEnabled) oceanSound.start()
        val station = sharedViewModel.selectedStation.value
        if (station != null && !viewModel.isPolling()) {
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
// Returns 0..1: how much tidal flat is currently visible.
//   tidePercent  — 0 = 간조 (low tide / exposed), 1 = 만조 (high tide / submerged)
//   tidalRangeM  — actual조차 in metres (from API or 조차 slider)
//   region       — per-coast hard cap (동해 almost never shows mudflat)
private fun computeMudflatExposure(
    tidePercent: Float, tidalRangeM: Float, region: StationRegion?
): Float {
    if (tidalRangeM <= 0.05f) return 0f

    // tidePercent=0 (간조) → tidePosition=1 (fully exposed)
    val tidePosition = (1f - tidePercent).coerceIn(0f, 1f)

    // rangeFactor: small조차 → little mudflat even at low tide (in metres)
    val rangeFactor = when {
        tidalRangeM < 1.0f -> 0.00f
        tidalRangeM < 3.0f -> (tidalRangeM - 1.0f) / 2.0f * 0.30f
        tidalRangeM < 5.0f -> 0.30f + (tidalRangeM - 3.0f) / 2.0f * 0.50f
        else               -> 0.80f + ((tidalRangeM - 5.0f) / 5.0f).coerceAtMost(1f) * 0.20f
    }

    // Per-region hard cap: 동해/제주 geography rarely forms exposed mudflat
    val regionCap = when (region) {
        StationRegion.WEST  -> 1.00f
        StationRegion.SOUTH -> 0.60f
        StationRegion.JEJU  -> 0.08f
        StationRegion.EAST  -> 0.04f
        null                -> 1.00f
    }

    return (tidePosition * rangeFactor * regionCap).coerceIn(0f, 1f)
}
