package com.koretide.app.ui.map

import android.graphics.Matrix
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.koretide.app.R
import com.koretide.app.databinding.FragmentMapBinding
import com.koretide.app.domain.model.ActivitySpot
import com.koretide.app.domain.model.ActivityType
import com.koretide.app.domain.model.Station
import com.koretide.app.domain.model.StationRegion
import com.koretide.app.ui.main.SharedViewModel
import com.koretide.app.util.collectFlow
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private var mapLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var activitySpotNearestStation: Station? = null
    private var navigating = false  // guard against double-tap navigation crash

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
        setupActivityChips()
        setupAdMob()
        syncPinCoordinatesToImage()
        observeState()
    }

    private fun setupAdMob() {
        try {
            MobileAds.initialize(requireContext())
            binding.adViewMap.loadAd(AdRequest.Builder().build())
        } catch (e: Exception) {
            android.util.Log.w("MapFragment", "AdMob init failed", e)
        }
    }

    /**
     * ImageView(fitCenter)가 실제로 이미지를 그린 위치/크기를 읽어
     * KoreaMapView 핀 좌표계를 이미지 기준으로 보정한다.
     */
    private fun syncPinCoordinatesToImage() {
        mapLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            // 제거하지 않고 유지 — 광고 로드·툴팁 표시 등 레이아웃 변경 시 자동 재동기
            val drawable = binding.mapImage.drawable ?: return@OnGlobalLayoutListener
            val m = FloatArray(9)
            binding.mapImage.imageMatrix.getValues(m)
            val imgW = drawable.intrinsicWidth  * m[Matrix.MSCALE_X]
            val imgH = drawable.intrinsicHeight * m[Matrix.MSCALE_Y]
            binding.koreaMapView.setMapImageRect(m[Matrix.MTRANS_X], m[Matrix.MTRANS_Y], imgW, imgH)
        }
        binding.mapImage.viewTreeObserver.addOnGlobalLayoutListener(mapLayoutListener)
    }

    private fun setupMapView() {
        binding.koreaMapView.onPinClick = { station -> onPinSelected(station) }
        binding.koreaMapView.onActivityPinClick = { spot -> onActivityPinSelected(spot) }
        binding.koreaMapView.onEmptyTap = { dismissTooltip() }
    }

    private fun dismissTooltip() {
        binding.tooltipCard.visibility = View.GONE
        binding.koreaMapView.clearSelection()
        activitySpotNearestStation = null
        navigating = false
        viewModel.clearPin()
    }

    private fun navigateToDetail() {
        if (navigating) return
        navigating = true
        try {
            findNavController().navigate(R.id.action_global_to_detail)
        } catch (e: Exception) {
            android.util.Log.w("MapFragment", "navigateToDetail failed", e)
            navigating = false
        }
    }

    private fun navigateToWatch() {
        if (navigating) return
        navigating = true
        sharedViewModel.requestTabNavigation(R.id.navigation_watch)
    }

    private fun setupActivityChips() {
        val allChips = listOf(
            binding.chipActivityAll, binding.chipHighTide, binding.chipFishing,
            binding.chipSurfing, binding.chipTidalFlat, binding.chipSwimming, binding.chipScuba
        )
        fun selectChip(chip: com.google.android.material.chip.Chip, filter: ActivityType?) {
            allChips.forEach { it.isChecked = false }
            chip.isChecked = true
            viewModel.setActivityFilter(filter)
        }
        binding.chipActivityAll.setOnClickListener  { selectChip(binding.chipActivityAll, null) }
        binding.chipHighTide.setOnClickListener     { selectChip(binding.chipHighTide,    ActivityType.HIGH_TIDE) }
        binding.chipFishing.setOnClickListener      { selectChip(binding.chipFishing,     ActivityType.FISHING) }
        binding.chipSurfing.setOnClickListener      { selectChip(binding.chipSurfing,     ActivityType.SURFING) }
        binding.chipTidalFlat.setOnClickListener    { selectChip(binding.chipTidalFlat,   ActivityType.TIDAL_FLAT) }
        binding.chipSwimming.setOnClickListener     { selectChip(binding.chipSwimming,    ActivityType.SWIMMING) }
        binding.chipScuba.setOnClickListener        { selectChip(binding.chipScuba,       ActivityType.SCUBA) }
    }

    private fun observeState() {
        collectFlow(viewModel.stations) { stations ->
            val b = _binding ?: return@collectFlow
            b.koreaMapView.pins = stations.map { station ->
                KoreaMapView.StationPin(station = station)
            }
        }
        collectFlow(viewModel.activitySpots) { spots ->
            val b = _binding ?: return@collectFlow
            b.koreaMapView.activitySpots = spots
        }
        collectFlow(sharedViewModel.selectedStation) { station ->
            val b = _binding ?: return@collectFlow
            b.koreaMapView.selectedCode = station?.code
        }
        collectFlow(viewModel.selectedPin) { pin ->
            val b = _binding ?: return@collectFlow
            if (pin != null) {
                b.tooltipCard.visibility = View.VISIBLE
                b.tvTooltipName.text = pin.name
                b.tvTooltipRegion.text = pin.region.displayName
                b.btnViewDetail.visibility = View.VISIBLE
                b.btnGoWatch.visibility = View.VISIBLE
            } else {
                b.tooltipCard.visibility = View.GONE
            }
        }
    }

    private fun onPinSelected(station: Station) {
        activitySpotNearestStation = null
        viewModel.selectPin(station)
        sharedViewModel.selectStation(station)
        // selectedCode 설정은 sharedViewModel 옵저버가 처리 → 자동으로 selectedSpotKey 해제
        binding.tooltipCard.visibility = View.VISIBLE
        binding.tvTooltipName.text = station.name
        binding.tvTooltipRegion.text = station.region.displayName
        binding.btnViewDetail.visibility = View.VISIBLE
        binding.btnViewDetail.setOnClickListener { navigateToDetail() }
        binding.btnGoWatch.visibility = View.VISIBLE
        binding.btnGoWatch.setOnClickListener { navigateToWatch() }
    }

    private fun onActivityPinSelected(spot: ActivitySpot) {
        // 스팟 선택 → 스테이션 핀 하이라이트 해제, 스팟 핀 하이라이트
        binding.koreaMapView.selectedSpotKey = "${spot.lat}_${spot.lng}"

        val nearest = viewModel.findNearestStation(spot.lat.toDouble(), spot.lng.toDouble())
        activitySpotNearestStation = nearest

        binding.tooltipCard.visibility = View.VISIBLE
        binding.tvTooltipName.text = spot.name
        binding.tvTooltipRegion.text = buildString {
            append(spot.type.displayName)
            if (nearest != null) append(" · 인근: ${nearest.name}")
        }
        if (nearest != null) {
            binding.btnViewDetail.visibility = View.VISIBLE
            binding.btnViewDetail.setOnClickListener {
                sharedViewModel.selectStation(nearest)
                navigateToDetail()
            }
        } else {
            binding.btnViewDetail.visibility = View.GONE
        }
        binding.btnGoWatch.visibility = View.VISIBLE
        binding.btnGoWatch.setOnClickListener {
            activitySpotNearestStation?.let { sharedViewModel.selectStation(it) }
            navigateToWatch()
        }
    }

    override fun onResume() {
        super.onResume()
        navigating = false
    }

    override fun onDestroyView() {
        mapLayoutListener?.let { binding.mapImage.viewTreeObserver.removeOnGlobalLayoutListener(it) }
        mapLayoutListener = null
        binding.adViewMap.destroy()
        super.onDestroyView()
        _binding = null
    }
}
