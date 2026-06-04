package com.koretide.app.ui.index

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.koretide.app.R
import com.koretide.app.databinding.FragmentIndexBinding
import com.koretide.app.domain.model.DayForecast
import com.koretide.app.domain.model.IndexGrade
import com.koretide.app.domain.model.OceanIndex
import com.koretide.app.domain.model.Station
import com.koretide.app.ui.main.SharedViewModel
import com.koretide.app.util.collectFlow
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class IndexFragment : Fragment() {

    private var _binding: FragmentIndexBinding? = null
    private val binding get() = _binding!!

    private val viewModel: IndexViewModel by viewModels()
    private val sharedViewModel: SharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIndexBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnIndexInfo.setOnClickListener { showIndexInfoDialog() }
        binding.btnGoWatch.setOnClickListener {
            sharedViewModel.requestTabNavigation(R.id.navigation_watch)
        }

        collectFlow(sharedViewModel.selectedStation) { station ->
            val region   = station?.regionShort()
            val name     = station?.name
            val tideData = sharedViewModel.tideData.value
            viewModel.load(region, name, station?.code, tideData)
        }

        collectFlow(viewModel.uiState) { state ->
            when (state) {
                is IndexUiState.Loading -> showLoading()
                is IndexUiState.Success -> showSuccess(state)
                is IndexUiState.Error   -> showError(state.message)
            }
        }
    }

    private fun showLoading() {
        binding.progressBar.isVisible = true
        binding.tvError.isVisible     = false
        hideResultArea()
    }

    private fun showError(msg: String) {
        binding.progressBar.isVisible = false
        binding.tvError.isVisible     = true
        binding.tvError.text          = msg
        hideResultArea()
    }

    // Hides all result-area views; showSuccess selectively re-shows them
    private fun hideResultArea() {
        binding.containerCards.isVisible  = false
        binding.tvForecastTitle.isVisible = false
        binding.scrollForecast.isVisible  = false
        binding.btnGoWatch.isVisible      = false
    }

    private fun showSuccess(state: IndexUiState.Success) {
        binding.progressBar.isVisible    = false
        binding.tvError.isVisible        = false
        binding.containerCards.isVisible = true

        binding.tvStationHint.text = state.stationName?.let { "📍 $it 기준" }
            ?: "관측소를 선택하면 해당 지역 지수를 표시합니다"

        val hasForecast = state.forecast.isNotEmpty()
        binding.tvForecastTitle.isVisible = hasForecast
        binding.scrollForecast.isVisible  = hasForecast
        binding.btnGoWatch.isVisible      = hasForecast
        if (hasForecast) renderForecast(state.forecast)

        binding.containerCards.removeAllViews()
        state.indices.forEach { binding.containerCards.addView(buildIndexCard(it)) }
    }

    private fun renderForecast(days: List<DayForecast>) {
        val container = binding.containerForecast
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        days.forEach { day ->
            val card = inflater.inflate(R.layout.item_forecast_day, container, false)
            card.findViewById<TextView>(R.id.tvDayLabel).text   = day.label
            card.findViewById<TextView>(R.id.tvWaterTemp).text  = day.waterTemp?.let  { "%.1f°C".format(it) } ?: "-"
            card.findViewById<TextView>(R.id.tvWaveHeight).text = day.waveHeight?.let { "%.1fm".format(it)  } ?: "-"
            card.findViewById<TextView>(R.id.tvWindSpeed).text  = day.windSpeed?.let  { "%.1fm/s".format(it)} ?: "-"
            container.addView(card)
        }
    }

    private fun buildIndexCard(index: OceanIndex): View {
        val inflater = LayoutInflater.from(requireContext())
        val card = inflater.inflate(R.layout.item_index_card, binding.containerCards, false)

        card.findViewById<TextView>(R.id.tvIndexEmoji).text = index.type.emoji
        card.findViewById<TextView>(R.id.tvIndexName).text  = index.type.displayName

        val tvGrade        = card.findViewById<TextView>(R.id.tvGrade)
        val tvBeach        = card.findViewById<TextView>(R.id.tvBeachName)
        val tvDate         = card.findViewById<TextView>(R.id.tvDate)
        val containerStats = card.findViewById<LinearLayout>(R.id.containerStats)
        val tvPending      = card.findViewById<TextView>(R.id.tvPending)

        val grade = index.grade
        if (grade != null) {
            tvGrade.isVisible   = true
            tvPending.isVisible = false
            if (index.isAvailable) {
                tvGrade.text = "${grade.emoji} Lv.${grade.level}  ${index.gradeLabel ?: grade.label}"
                tvGrade.setTextColor(ContextCompat.getColor(requireContext(), gradeTextColor(grade)))
                tvGrade.setBackgroundResource(gradeBg(grade))
                tvBeach.text = index.beachName ?: ""
                tvBeach.isVisible = index.beachName != null
                tvDate.text = index.date ?: ""
                tvDate.isVisible = index.date != null
                containerStats.isVisible = index.stats.isNotEmpty()
                containerStats.removeAllViews()
                index.stats.forEach { (label, value) ->
                    containerStats.addView(TextView(requireContext()).apply {
                        text = "$label $value"
                        textSize = 12f
                        setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                        setPadding(0, 2, 16, 2)
                    })
                }
            } else {
                tvGrade.text = "${grade.label} (샘플)"
                tvGrade.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
                tvGrade.background = null
                tvBeach.isVisible        = false
                tvDate.isVisible         = false
                containerStats.isVisible = false
            }
        } else {
            tvGrade.isVisible        = false
            tvPending.isVisible      = true
            tvBeach.isVisible        = false
            tvDate.isVisible         = false
            containerStats.isVisible = false
        }

        return card
    }

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

    private fun Station.regionShort(): String? {
        val nameTrimmed = name.trim()
        return listOf("인천", "부산", "여수", "목포", "제주", "강릉", "울산", "포항", "군산", "서귀포",
                      "완도", "통영", "마산", "창원", "거제", "광양", "속초", "동해", "삼척")
            .firstOrNull { nameTrimmed.contains(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
