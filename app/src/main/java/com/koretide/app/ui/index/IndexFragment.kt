package com.koretide.app.ui.index

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.koretide.app.R
import com.koretide.app.databinding.FragmentIndexBinding
import com.koretide.app.domain.model.IndexGrade
import com.koretide.app.domain.model.IndexType
import com.koretide.app.domain.model.StationRegion
import com.koretide.app.ui.main.SharedViewModel
import com.koretide.app.util.collectFlow
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class IndexFragment : Fragment() {

    private var _binding: FragmentIndexBinding? = null
    private val binding get() = _binding!!

    private val viewModel: IndexViewModel by viewModels()
    private val sharedViewModel: SharedViewModel by activityViewModels()

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (!viewModel.navigateBack()) {
                isEnabled = false
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIndexBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        binding.btnIndexInfo.setOnClickListener { showIndexInfoDialog() }
        binding.btnGoWatch.setOnClickListener {
            viewModel.requestWatchForCurrentRegion()
        }
        binding.btnBackFromRegion.setOnClickListener { viewModel.navigateBack() }
        binding.btnBackFromForecast.setOnClickListener { viewModel.navigateBack() }
        binding.btnBackFromBeach.setOnClickListener { viewModel.navigateBack() }

        collectFlow(sharedViewModel.selectedStation) {
            if (viewModel.isAtRoot()) viewModel.loadTypeList()
        }

        collectFlow(viewModel.watchStation) { station ->
            if (station != null) sharedViewModel.selectStation(station)
            sharedViewModel.requestTabNavigation(R.id.navigation_watch)
        }

        collectFlow(viewModel.uiState) { state ->
            backCallback.isEnabled = !viewModel.isAtRoot()
            when (state) {
                is IndexUiState.Loading    -> showLoading()
                is IndexUiState.TypeList   -> showTypeList()
                is IndexUiState.RegionList -> showRegionList(state)
                is IndexUiState.BeachList  -> showBeachList(state)
                is IndexUiState.Forecast   -> showForecast(state)
                is IndexUiState.Error      -> showError(state.message)
            }
        }
    }

    // ── Screen visibility helpers ─────────────────────────────────

    private fun showScreen(screen: Int) {
        binding.screenTypeList.isVisible   = screen == 1
        binding.screenRegionList.isVisible = screen == 2
        binding.screenBeachList.isVisible  = screen == 3
        binding.screenForecast.isVisible   = screen == 4
    }

    private fun showLoading() {
        // keep current screen, show progress on the active one
        binding.progressTypeList.isVisible   = binding.screenTypeList.isVisible
        binding.progressRegionList.isVisible = binding.screenRegionList.isVisible
        binding.progressBeachList.isVisible  = binding.screenBeachList.isVisible
        binding.progressBar.isVisible        = binding.screenForecast.isVisible
    }

    private fun showError(msg: String) {
        showScreen(4)
        binding.tvError.isVisible = true
        binding.tvError.text = msg
        binding.progressBar.isVisible = false
        binding.containerForecast.removeAllViews()
    }

    // ── Screen 1: TypeList ────────────────────────────────────────

    private fun showTypeList() {
        showScreen(1)
        binding.progressTypeList.isVisible = false
        binding.tvStationHint.isVisible = false

        binding.containerTypeList.removeAllViews()
        for (type in IndexType.values()) {
            val card = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_index_type_card, binding.containerTypeList, false)

            card.findViewById<TextView>(R.id.tvTypeEmoji).text = type.emoji
            card.findViewById<TextView>(R.id.tvTypeName).text  = type.displayName
            card.findViewById<TextView>(R.id.tvTypeGrade).isVisible = false

            card.setOnClickListener { viewModel.selectType(type) }
            binding.containerTypeList.addView(card)
        }
    }

    // ── Screen 2: RegionList ──────────────────────────────────────

    private fun showRegionList(state: IndexUiState.RegionList) {
        showScreen(2)
        binding.progressRegionList.isVisible = false
        binding.tvRegionListTitle.text = "${state.type.emoji} ${state.type.displayName}"

        binding.containerRegionList.removeAllViews()
        for (region in StationRegion.values()) {
            val row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_region_grade_row, binding.containerRegionList, false)

            row.findViewById<TextView>(R.id.tvRegionName).text = region.displayName
            row.findViewById<TextView>(R.id.tvRegionGrade).isVisible = false

            row.setOnClickListener { viewModel.selectRegion(state.type, region) }
            binding.containerRegionList.addView(row)
        }
    }

    // ── Screen 3: BeachList ──────────────────────────────────────

    private fun showBeachList(state: IndexUiState.BeachList) {
        showScreen(3)
        binding.progressBeachList.isVisible = false
        binding.tvBeachListTitle.text = "${state.region.displayName} · ${state.type.emoji} ${state.type.displayName}"

        binding.containerBeachList.removeAllViews()
        for (item in state.beaches) {
            val row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_region_grade_row, binding.containerBeachList, false)

            row.findViewById<TextView>(R.id.tvRegionName).text = item.name

            val grade = item.index.grade
            val opnStat = item.index.opnStat
            val tvGrade = row.findViewById<TextView>(R.id.tvRegionGrade)
            if (grade != null && item.index.isAvailable) {
                val statusSuffix = opnStat?.let { " · $it" } ?: ""
                tvGrade.text = "${grade.emoji} Lv.${grade.level} ${grade.label}$statusSuffix"
                tvGrade.setTextColor(ContextCompat.getColor(requireContext(), gradeTextColor(grade)))
                tvGrade.setBackgroundResource(gradeBg(grade))
            } else if (opnStat != null) {
                tvGrade.text = opnStat
                val color = if (opnStat == "개장") R.color.status_rising else R.color.status_falling
                tvGrade.setTextColor(ContextCompat.getColor(requireContext(), color))
                tvGrade.background = null
            } else {
                tvGrade.text = "정보 없음"
                tvGrade.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
                tvGrade.background = null
            }

            row.setOnClickListener { viewModel.selectBeach(item, state.type, state.region) }
            binding.containerBeachList.addView(row)
        }
    }

    // ── Screen 4: Forecast ────────────────────────────────────────

    private fun showForecast(state: IndexUiState.Forecast) {
        showScreen(4)
        binding.progressBar.isVisible = false
        binding.tvError.isVisible = false
        val title = if (state.beachName != null) {
            "${state.beachName} · ${state.type.emoji} 7일 예보"
        } else {
            "${state.region.displayName} · ${state.type.emoji} 7일 예보"
        }
        val statusSuffix = state.opnStat?.let { "  [$it]" } ?: ""
        binding.tvForecastHeaderTitle.text = "$title$statusSuffix"

        binding.containerForecast.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        for (day in state.forecast) {
            val card = inflater.inflate(R.layout.item_forecast_day, binding.containerForecast, false)
            card.findViewById<TextView>(R.id.tvDayLabel).text   = day.label
            card.findViewById<TextView>(R.id.tvWaterTemp).text  = day.waterTemp?.let  { "%.1f°C".format(it) } ?: "-"
            card.findViewById<TextView>(R.id.tvWaveHeight).text = day.waveHeight?.let { "%.1fm".format(it)  } ?: "-"
            card.findViewById<TextView>(R.id.tvWindSpeed).text  = day.windSpeed?.let  { "%.1fm/s".format(it)} ?: "-"
            binding.containerForecast.addView(card)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private fun showIndexInfoDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("해양활동지수 레벨 안내")
            .setMessage(
                "🟢 Lv.1 매우좋음 — 해양활동 최적\n" +
                "🔵 Lv.2 좋음    — 해양활동 양호\n" +
                "🟡 Lv.3 보통    — 해양활동 주의\n" +
                "🟠 Lv.4 나쁨    — 해양활동 자제\n" +
                "🔴 Lv.5 매우나쁨 — 해양활동 위험"
            )
            .setPositiveButton("확인", null)
            .show()
    }

    private fun gradeTextColor(grade: IndexGrade): Int = when (grade) {
        IndexGrade.VERY_GOOD -> R.color.status_high
        IndexGrade.GOOD      -> R.color.teal
        IndexGrade.FAIR      -> R.color.status_falling
        IndexGrade.BAD       -> R.color.status_low
        IndexGrade.VERY_BAD  -> android.R.color.holo_red_dark
    }

    private fun gradeBg(grade: IndexGrade): Int = when (grade) {
        IndexGrade.VERY_GOOD, IndexGrade.GOOD -> R.drawable.bg_grade_good
        IndexGrade.FAIR                        -> R.drawable.bg_grade_fair
        IndexGrade.BAD, IndexGrade.VERY_BAD   -> R.drawable.bg_grade_bad
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
