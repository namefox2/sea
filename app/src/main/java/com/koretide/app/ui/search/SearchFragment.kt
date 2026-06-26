package com.koretide.app.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.koretide.app.R
import com.koretide.app.databinding.FragmentSearchBinding
import com.koretide.app.domain.model.Station
import com.koretide.app.domain.model.StationRegion
import com.koretide.app.ui.main.SharedViewModel
import com.koretide.app.util.collectFlow
import com.koretide.app.util.gone
import com.koretide.app.util.visible
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels()
    private val sharedViewModel: SharedViewModel by activityViewModels()

    private lateinit var adapter: StationAdapter
    private var navigating = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdMob()
        setupRecyclerView()
        setupSearch()
        setupChips()
        setupTooltip()
        observeState()
    }

    private fun setupAdMob() {
        try {
            MobileAds.initialize(requireContext())
            binding.adBannerSearch.loadAd(AdRequest.Builder().build())
        } catch (e: Exception) {
            android.util.Log.w("SearchFragment", "AdMob init failed", e)
        }
    }

    private fun setupRecyclerView() {
        adapter = StationAdapter { station -> onStationTapped(station) }
        binding.recyclerStations.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerStations.adapter = adapter
        // Tap on list background dismisses tooltip
        binding.recyclerStations.setOnClickListener { dismissTooltip() }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener { text ->
            viewModel.setQuery(text?.toString() ?: "")
        }
    }

    private fun setupChips() {
        val regionChips = mapOf(
            binding.chipAll   to null,
            binding.chipWest  to StationRegion.WEST,
            binding.chipSouth to StationRegion.SOUTH,
            binding.chipEast  to StationRegion.EAST,
            binding.chipJeju  to StationRegion.JEJU
        )
        regionChips.forEach { (chip, region) ->
            chip.setOnClickListener {
                viewModel.setRegionFilter(region)
                regionChips.keys.forEach { it.isChecked = false }
                chip.isChecked = true
                dismissTooltip()
            }
        }
        binding.chipAll.isChecked = true
    }

    private fun setupTooltip() {
        binding.btnTooltipDetail.setOnClickListener {
            val station = currentTooltipStation ?: return@setOnClickListener
            sharedViewModel.selectStation(station)
            dismissTooltip()
            safeNavigate { findNavController().navigate(R.id.action_global_to_detail) }
        }
        binding.btnTooltipWatch.setOnClickListener {
            val station = currentTooltipStation ?: return@setOnClickListener
            sharedViewModel.selectStation(station)
            dismissTooltip()
            sharedViewModel.requestTabNavigation(R.id.navigation_watch)
        }
    }

    private fun observeState() {
        collectFlow(viewModel.stationItems) { items ->
            if (viewModel.loadError.value != null) return@collectFlow
            adapter.submitList(items)
            if (items.isEmpty()) {
                binding.tvEmpty.setText(R.string.no_stations)
                binding.tvEmpty.visible()
                binding.recyclerStations.gone()
            } else {
                binding.tvEmpty.gone()
                binding.recyclerStations.visible()
            }
        }
        collectFlow(viewModel.isLoading) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
        collectFlow(viewModel.loadError) { error ->
            if (error != null) {
                binding.tvEmpty.text = error
                binding.tvEmpty.visible()
                binding.recyclerStations.gone()
            }
        }
    }

    private var currentTooltipStation: Station? = null

    private fun onStationTapped(station: Station) {
        if (currentTooltipStation?.code == station.code && binding.cardStationTooltip.isVisible) {
            // second tap on same station → go to detail
            sharedViewModel.selectStation(station)
            dismissTooltip()
            safeNavigate { findNavController().navigate(R.id.action_global_to_detail) }
            return
        }
        currentTooltipStation = station
        binding.tvTooltipName.text   = station.name
        binding.tvTooltipRegion.text = "${station.region.displayName} · ${station.code}"
        binding.cardStationTooltip.visible()
    }

    private fun dismissTooltip() {
        currentTooltipStation = null
        _binding?.cardStationTooltip?.gone()
    }

    private fun safeNavigate(block: () -> Unit) {
        if (navigating) return
        navigating = true
        try { block() } catch (e: Exception) {
            android.util.Log.w("SearchFragment", "navigation failed", e)
            navigating = false
        }
    }

    override fun onResume() {
        super.onResume()
        navigating = false
        dismissTooltip()
        _binding?.etSearch?.text?.clear()
        viewModel.setQuery("")
        viewModel.setRegionFilter(null)
        _binding?.chipAll?.isChecked = true
    }

    override fun onDestroyView() {
        _binding?.adBannerSearch?.destroy()
        super.onDestroyView()
        _binding = null
    }
}
