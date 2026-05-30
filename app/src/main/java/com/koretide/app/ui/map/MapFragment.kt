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
        MobileAds.initialize(requireContext())
        binding.adViewMap.loadAd(AdRequest.Builder().build())
    }

    /**
     * ImageView(fitCenter)가 실제로 이미지를 그린 위치/크기를 읽어
     * KoreaMapView 핀 좌표계를 이미지 기준으로 보정한다.
     */
    private fun syncPinCoordinatesToImage() {
        mapLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            binding.mapImage.viewTreeObserver.removeOnGlobalLayoutListener(mapLayoutListener)
            mapLayoutListener = null
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
    }

    private fun setupActivityChips() {
        binding.chipActivityAll.setOnClickListener  { viewModel.setActivityFilter(null) }
        binding.chipHighTide.setOnClickListener      { viewModel.setActivityFilter(ActivityType.HIGH_TIDE) }
        binding.chipFishing.setOnClickListener      { viewModel.setActivityFilter(ActivityType.FISHING) }
        binding.chipSurfing.setOnClickListener      { viewModel.setActivityFilter(ActivityType.SURFING) }
        binding.chipTidalFlat.setOnClickListener    { viewModel.setActivityFilter(ActivityType.TIDAL_FLAT) }
        binding.chipSwimming.setOnClickListener     { viewModel.setActivityFilter(ActivityType.SWIMMING) }
        binding.chipScuba.setOnClickListener        { viewModel.setActivityFilter(ActivityType.SCUBA) }
    }

    private fun observeState() {
        collectFlow(viewModel.stations) { stations ->
            binding.koreaMapView.pins = stations.map { station ->
                KoreaMapView.StationPin(station = station)
            }
        }
        collectFlow(viewModel.activitySpots) { spots ->
            binding.koreaMapView.activitySpots = spots
        }
        collectFlow(sharedViewModel.selectedStation) { station ->
            binding.koreaMapView.selectedCode = station?.code
        }
        collectFlow(viewModel.selectedPin) { pin ->
            if (pin != null) {
                binding.tooltipCard.visibility = View.VISIBLE
                binding.tvTooltipName.text = pin.name
                binding.tvTooltipRegion.text = pin.region.displayName
                binding.btnViewDetail.visibility = View.VISIBLE
                binding.btnGoWatch.visibility = View.VISIBLE
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
        binding.btnViewDetail.visibility = View.VISIBLE
        binding.btnViewDetail.setOnClickListener {
            findNavController().navigate(R.id.action_global_to_detail)
        }
        binding.btnGoWatch.visibility = View.VISIBLE
        binding.btnGoWatch.setOnClickListener {
            sharedViewModel.requestTabNavigation(R.id.navigation_watch)
        }
    }

    private fun onActivityPinSelected(spot: ActivitySpot) {
        binding.tooltipCard.visibility = View.VISIBLE
        binding.tvTooltipName.text = spot.name
        binding.tvTooltipRegion.text = spot.type.displayName
        binding.btnViewDetail.visibility = View.GONE
        binding.btnGoWatch.visibility = View.VISIBLE
        binding.btnGoWatch.setOnClickListener {
            sharedViewModel.requestTabNavigation(R.id.navigation_watch)
        }
    }

    override fun onDestroyView() {
        // 레이아웃 리스너가 아직 등록돼 있으면 제거 (Fragment 누수 방지)
        mapLayoutListener?.let { binding.mapImage.viewTreeObserver.removeOnGlobalLayoutListener(it) }
        mapLayoutListener = null
        binding.adViewMap.destroy()
        super.onDestroyView()
        _binding = null
    }
}
