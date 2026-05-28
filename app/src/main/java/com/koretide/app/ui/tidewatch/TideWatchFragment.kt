package com.koretide.app.ui.tidewatch

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

@AndroidEntryPoint
class TideWatchFragment : Fragment() {

    @Inject lateinit var seasonThemeManager: SeasonThemeManager

    private var _binding: FragmentTideWatchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TideWatchViewModel by viewModels()
    private val sharedViewModel: SharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTideWatchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val theme = seasonThemeManager.getThemeForContext(requireContext())
        binding.tideWatchView.themeConfig = theme

        observeState()
    }

    private fun observeState() {
        collectFlow(sharedViewModel.selectedStation) { station ->
            if (station == null) {
                binding.bannerNoStation.visible()
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
        _binding = null
    }
}
