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
import com.koretide.app.theme.SeasonThemeManager
import com.koretide.app.ui.main.SharedViewModel
import com.koretide.app.util.collectFlow
import com.koretide.app.util.gone
import com.koretide.app.util.visible
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val PREFS_THEME = "theme_prefs"
private const val KEY_THEME_ID = "selected_theme_id"

@AndroidEntryPoint
class TideWatchFragment : Fragment() {

    @Inject lateinit var seasonThemeManager: SeasonThemeManager

    private var _binding: FragmentTideWatchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TideWatchViewModel by viewModels()
    private val sharedViewModel: SharedViewModel by activityViewModels()

    private var uiVisible = true

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

        // Tap background to toggle immersive; slider panel consumes its own touches
        binding.root.setOnClickListener { toggleImmersive() }

        observeState()
        scheduleGlErrorCheck()
    }

    private fun scheduleGlErrorCheck() {
        Handler(Looper.getMainLooper()).postDelayed({
            val b = _binding ?: return@postDelayed
            val err = b.tideWatchView.initError ?: return@postDelayed
            android.widget.Toast.makeText(requireContext(), "GL오류: $err", android.widget.Toast.LENGTH_LONG).show()
        }, 3000L)
    }

    private fun setupSliders() {
        binding.tideWatchView.setTide(binding.seekTide.progress / 100f)
        binding.tideWatchView.setWind(binding.seekWind.progress)

        binding.seekWind.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                binding.tvWindBft.text = "$progress bft"
                binding.tideWatchView.setWind(progress)
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
            }
        }

        collectFlow(viewModel.windData) { data ->
            val b = _binding ?: return@collectFlow
            if (data != null) {
                b.seekWind.progress = data.beaufort
                b.tideWatchView.windDirectionDeg = data.directionDeg
                sharedViewModel.updateWindData(data)
                b.tvWindInfo.text = "${data.beaufortName} (${data.beaufort}bft, ${data.speedMs}m/s)"
            }
        }
    }

    override fun onPause() {
        super.onPause()
        binding.tideWatchView.onPause()
        viewModel.stopPolling()
        if (!uiVisible) {
            uiVisible = true
            sharedViewModel.setWatchImmersive(false)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.tideWatchView.onResume()
        val station = sharedViewModel.selectedStation.value
        if (station != null) {
            viewModel.startPolling(station.code, station.lat, station.lng)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sharedViewModel.setWatchImmersive(false)
        _binding = null
    }
}
