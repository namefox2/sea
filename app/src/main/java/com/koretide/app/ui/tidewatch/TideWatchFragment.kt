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
import com.koretide.app.domain.model.TideData
import com.koretide.app.theme.SeasonThemeManager
import com.koretide.app.ui.main.SharedViewModel
import com.koretide.app.util.collectFlow
import com.koretide.app.util.gone
import com.koretide.app.util.visible
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
        binding.tideWatchView.setTide(binding.seekTide.progress / 100f)
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
                binding.tideWatchView.setTide(progress / 100f)
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

    private fun toggleImmersive() {
        uiVisible = !uiVisible
        val vis = if (uiVisible) View.VISIBLE else View.GONE
        binding.overlayCard.visibility = vis
        binding.sliderPanel.visibility = vis
        binding.bannerNoStation.visibility = if (uiVisible && sharedViewModel.selectedStation.value == null)
            View.VISIBLE else View.GONE
        sharedViewModel.setWatchImmersive(!uiVisible)
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
            if (station == null) {
                if (uiVisible) b.bannerNoStation.visible()
            } else {
                b.bannerNoStation.gone()
                b.tvStationName.text = station.name
                viewModel.startPolling(station.code, station.lat, station.lng)
            }
        }

        collectFlow(viewModel.tideData) { data ->
            val b = _binding ?: return@collectFlow
            if (data != null) {
                val pct = (data.tidePercent * 100).toInt().coerceIn(0, 100)
                b.seekTide.progress = pct
                sharedViewModel.updateTideData(data)
                b.tvTideInfo.text = "${data.tideStatus.displayName} $pct%"
                updateMudflatGrade(b, data)
            }
        }

        collectFlow(viewModel.windData) { data ->
            val b = _binding ?: return@collectFlow
            if (data != null) {
                b.seekWind.progress = data.beaufort
                b.tideWatchView.windDirectionDeg = data.directionDeg
                oceanSound.setIntensity(data.beaufort.coerceIn(0, 12) / 12f)
                sharedViewModel.updateWindData(data)
                b.tvWindInfo.text = "${data.beaufortName} (${data.beaufort}bft, ${data.speedMs}m/s)"
            }
        }
    }

    private fun updateMudflatGrade(b: FragmentTideWatchBinding, data: TideData) {
        val range = data.maxLevel - data.minLevel
        if (range > 100) {
            val exposure = (data.maxLevel - data.currentLevel).toFloat() / range.toFloat()
            b.tvMudflatGrade.text = when {
                exposure >= 0.67f -> "🦀 갯벌 매우 많이 드러남"
                exposure >= 0.33f -> "🦀 갯벌 보통 드러남"
                else              -> "🦀 갯벌 조금 드러남"
            }
            b.tvMudflatGrade.visibility = View.VISIBLE
        } else {
            b.tvMudflatGrade.visibility = View.GONE
        }
    }

    override fun onPause() {
        super.onPause()
        binding.tideWatchView.onPause()
        oceanSound.stop()
        viewModel.stopPolling()
        if (!uiVisible) {
            uiVisible = true
            sharedViewModel.setWatchImmersive(false)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.tideWatchView.onResume()
        if (soundEnabled) oceanSound.start()
        val station = sharedViewModel.selectedStation.value
        if (station != null) {
            viewModel.startPolling(station.code, station.lat, station.lng)
        }
    }

    override fun onDestroyView() {
        sharedViewModel.setWatchImmersive(false)
        oceanSound.stop()
        _binding?.tideWatchView?.release()  // free GL resources before losing reference
        super.onDestroyView()
        _binding = null
    }
}
