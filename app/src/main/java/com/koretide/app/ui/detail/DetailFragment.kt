package com.koretide.app.ui.detail

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.koretide.app.R
import com.koretide.app.databinding.FragmentDetailBinding
import com.koretide.app.domain.model.TideData
import com.koretide.app.ui.main.SharedViewModel
import com.koretide.app.util.Result
import com.koretide.app.util.collectFlow
import com.koretide.app.util.gone
import com.koretide.app.util.visible
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DetailFragment : Fragment() {

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DetailViewModel by viewModels()
    private val sharedViewModel: SharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChart()
        observeState()

        binding.btnViewWatch.setOnClickListener {
            sharedViewModel.requestTabNavigation(R.id.navigation_watch)
        }
    }

    private fun setupChart() {
        binding.chart.apply {
            description.isEnabled = false
            legend.isEnabled = true
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            xAxis.setDrawGridLines(false)
            axisRight.isEnabled = false
        }
    }

    private fun observeState() {
        collectFlow(sharedViewModel.selectedStation) { station ->
            if (station != null) {
                binding.tvNoStation.gone()
                binding.contentGroup.visible()
                // Hide transient views immediately after group becomes visible
                binding.progressBar.gone()
                binding.tvError.gone()
                binding.tvStationName.text = station.name
                viewModel.loadData(station)
            } else {
                binding.tvNoStation.visible()
                binding.contentGroup.gone()
            }
        }

        collectFlow(viewModel.tideResult) { result ->
            when (result) {
                is Result.Loading -> binding.progressBar.visible()
                is Result.Success -> {
                    binding.progressBar.gone()
                    bindTideData(result.data)
                }
                is Result.Error -> {
                    binding.progressBar.gone()
                    binding.tvError.text = result.message
                    binding.tvError.visible()
                }
            }
        }

        collectFlow(viewModel.windResult) { result ->
            if (result is Result.Success) {
                binding.tvWind.text = "${result.data.beaufortName} (${result.data.beaufort}bft)"
            }
        }
    }

    private fun bindTideData(data: TideData) {
        binding.tvError.gone()
        binding.waterLevelView.setTidePercent(data.tidePercent)
        binding.tvCurrentLevel.text = "${data.currentLevel}cm"
        binding.tvTideStatus.text = data.tideStatus.displayName
        binding.tvHighTide.text = "만조: ${data.highTideTime ?: "--"} (${data.maxLevel}cm)"
        binding.tvLowTide.text  = "간조: ${data.lowTideTime  ?: "--"} (${data.minLevel}cm)"
        binding.tvTidePercent.text = "${(data.tidePercent * 100).toInt()}%"

        if (data.records.isNotEmpty()) {
            val entries = data.records.mapIndexed { i, r -> Entry(i.toFloat(), r.waterLevel.toFloat()) }
            val dataSet = LineDataSet(entries, "조위 (cm)").apply {
                color = Color.parseColor("#0288D1")
                setCircleColor(Color.parseColor("#0288D1"))
                circleRadius = 2f
                lineWidth = 2f
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }
            binding.chart.data = LineData(dataSet)
            binding.chart.invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
