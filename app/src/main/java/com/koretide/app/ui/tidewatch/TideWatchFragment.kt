package com.koretide.app.ui.tidewatch

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        binding.tideWatchView.marineSettings = MarineLifePrefs.load(requireContext())

        binding.btnMarineSettings.setOnClickListener {
            val sheet = MarineSettingsSheet()
            sheet.onSettingsChanged = { settings ->
                binding.tideWatchView.marineSettings = settings
            }
            sheet.show(childFragmentManager, MarineSettingsSheet.TAG)
        }

        // Tap anywhere (except buttons) to toggle immersive mode
        binding.root.setOnClickListener { toggleImmersive() }
        // Buttons should not toggle immersive when clicked
        binding.btnMarineSettings.setOnClickListener {
            if (!uiVisible) return@setOnClickListener
            val sheet = MarineSettingsSheet()
            sheet.onSettingsChanged = { settings ->
                binding.tideWatchView.marineSettings = settings
            }
            sheet.show(childFragmentManager, MarineSettingsSheet.TAG)
        }

        observeState()
    }

    private fun toggleImmersive() {
        uiVisible = !uiVisible
        val vis = if (uiVisible) View.VISIBLE else View.GONE
        binding.overlayCard.visibility = vis
        binding.btnMarineSettings.visibility = vis
        binding.bannerNoStation.visibility = if (uiVisible && sharedViewModel.selectedStation.value == null)
            View.VISIBLE else View.GONE
        sharedViewModel.setWatchImmersive(!uiVisible)
    }

    private fun observeState() {
        collectFlow(sharedViewModel.selectedThemeId) { themeId ->
            if (themeId != null) {
                val newTheme = seasonThemeManager.allThemes().firstOrNull { it.id == themeId }
                if (newTheme != null) binding.tideWatchView.themeConfig = newTheme
            }
        }
        collectFlow(sharedViewModel.selectedStation) { station ->
            if (station == null) {
                if (uiVisible) binding.bannerNoStation.visible()
            } else {
                binding.bannerNoStation.gone()
                binding.tvStationName.text = station.name
                viewModel.startPolling(station.code, station.lat, station.lng)
            }
        }

        collectFlow(viewModel.tideData) { data ->
            if (data != null) {
                binding.tideWatchView.setTide(data.tidePercent)
                sharedViewModel.updateTideData(data)
                binding.tvTideInfo.text = "${data.tideStatus.displayName} ${(data.tidePercent * 100).toInt()}%"
            }
        }

        collectFlow(viewModel.windData) { data ->
            if (data != null) {
                binding.tideWatchView.setWind(data.beaufort)
                binding.tideWatchView.windDirectionDeg = data.directionDeg
                sharedViewModel.updateWindData(data)
                binding.tvWindInfo.text = "${data.beaufortName} (${data.beaufort}bft, ${data.speedMs}m/s)"
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopPolling()
        // Always restore UI when leaving the screen
        if (!uiVisible) {
            uiVisible = true
            sharedViewModel.setWatchImmersive(false)
        }
    }

    override fun onResume() {
        super.onResume()
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
