package com.koretide.app.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.koretide.app.R
import com.koretide.app.databinding.FragmentMapBinding
import com.koretide.app.domain.model.Station
import com.koretide.app.domain.model.StationRegion
import com.koretide.app.ui.main.SharedViewModel
import com.koretide.app.util.collectFlow
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MapViewModel by viewModels()
    private val sharedViewModel: SharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMapView()
        setupRegionTabs()
        observeState()
    }

    private fun setupMapView() {
        binding.koreaMapView.onPinClick = { station -> onPinSelected(station) }
    }

    private fun setupRegionTabs() {
        binding.chipAll.setOnClickListener   { viewModel.setRegionFilter(null) }
        binding.chipWest.setOnClickListener  { viewModel.setRegionFilter(StationRegion.WEST) }
        binding.chipSouth.setOnClickListener { viewModel.setRegionFilter(StationRegion.SOUTH) }
        binding.chipEast.setOnClickListener  { viewModel.setRegionFilter(StationRegion.EAST) }
        binding.chipJeju.setOnClickListener  { viewModel.setRegionFilter(StationRegion.JEJU) }
    }

    private fun observeState() {
        collectFlow(viewModel.stations) { stations ->
            binding.koreaMapView.pins = stations.map { station ->
                KoreaMapView.StationPin(station = station)
            }
        }
        collectFlow(sharedViewModel.selectedStation) { station ->
            binding.koreaMapView.selectedCode = station?.code
        }
        collectFlow(viewModel.selectedPin) { pin ->
            if (pin != null) {
                binding.tooltipCard.visibility = View.VISIBLE
                binding.tvTooltipName.text = pin.name
                binding.tvTooltipRegion.text = pin.region.displayName
            } else {
                binding.tooltipCard.visibility = View.GONE
            }
        }
    }

    private fun onPinSelected(station: Station) {
        viewModel.selectPin(station)
        sharedViewModel.selectStation(station)
        binding.tooltipCard.visibility = View.VISIBLE
        binding.tvTooltipName.text = station.name
        binding.tvTooltipRegion.text = station.region.displayName
        binding.btnViewDetail.setOnClickListener {
            findNavController().navigate(R.id.action_global_to_detail)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
