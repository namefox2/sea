package com.koretide.app.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        adapter = StationAdapter { station -> onStationSelected(station) }
        binding.recyclerStations.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerStations.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener { text ->
            viewModel.setQuery(text?.toString() ?: "")
        }
    }

    private fun setupChips() {
        val regionChips = mapOf(
            binding.chipAll    to null,
            binding.chipWest   to StationRegion.WEST,
            binding.chipSouth  to StationRegion.SOUTH,
            binding.chipEast   to StationRegion.EAST,
            binding.chipJeju   to StationRegion.JEJU
        )
        regionChips.forEach { (chip, region) ->
            chip.setOnClickListener {
                viewModel.setRegionFilter(region)
                regionChips.keys.forEach { it.isChecked = false }
                chip.isChecked = true
            }
        }
        binding.chipAll.isChecked = true
    }

    private fun observeState() {
        collectFlow(viewModel.stationItems) { items ->
            adapter.submitList(items)
            if (items.isEmpty()) {
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
    }

    private fun onStationSelected(station: Station) {
        sharedViewModel.selectStation(station)
        findNavController().navigate(R.id.action_global_to_detail)
    }

    override fun onResume() {
        super.onResume()
        // Clear state so each visit shows a fresh unfiltered search list.
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
