package com.koretide.app.ui.map

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.NaverMap
import com.naver.maps.map.NaverMapSdk
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.util.MarkerIcons
import com.koretide.app.R
import com.koretide.app.databinding.FragmentMapBinding
import com.koretide.app.domain.model.ActivitySpot
import com.koretide.app.domain.model.ActivityType
import com.koretide.app.domain.model.Station
import com.koretide.app.ui.main.SharedViewModel
import com.koretide.app.util.CrashLogger
import com.koretide.app.util.collectFlow
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private var navigating = false
    private var naverMap: NaverMap? = null
    private val stationMarkers = mutableListOf<Marker>()
    private val spotMarkers = mutableListOf<Marker>()
    private var activitySpotNearestStation: Station? = null

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
        // 네이버 지도 인증 실패(예: 800) 시 정확한 코드/메시지를 진단 로그에 남긴다.
        // 설정 → 오류 진단 로그에서 확인/복사 가능.
        NaverMapSdk.getInstance(requireContext()).onAuthFailedListener =
            NaverMapSdk.OnAuthFailedListener { ex ->
                val msg = "Naver Map 인증 실패: $ex  pkg=${requireContext().packageName}"
                Log.e("MapFragment", msg)
                CrashLogger.log(requireContext(), msg)
            }

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)
        setupTabs()
        setupAdMob()
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

    override fun onMapReady(map: NaverMap) {
        if (_binding == null) return
        naverMap = map
        map.moveCamera(CameraUpdate.scrollAndZoomTo(LatLng(36.5, 127.8), 5.8))
        map.setOnMapClickListener { _, _ -> dismissTooltip() }
        updateStationMarkers(viewModel.stations.value)
        updateSpotMarkers(viewModel.activitySpots.value)
    }

    private fun observeState() {
        collectFlow(viewModel.stations) { stations ->
            if (naverMap != null) updateStationMarkers(stations)
        }
        collectFlow(viewModel.activitySpots) { spots ->
            if (naverMap != null) updateSpotMarkers(spots)
        }
        collectFlow(viewModel.activityFilter) { filter ->
            val b = _binding ?: return@collectFlow
            val showIndex = filter != null && filter != ActivityType.HIGH_TIDE
            b.btnViewIndex.visibility = if (showIndex) View.VISIBLE else View.GONE
        }
    }

    // Creates a Naver map marker with shared defaults
    private fun createMarker(
        lat: Double, lng: Double, name: String,
        tintColor: Int, minZoom: Double,
        onClick: () -> Unit
    ): Marker = Marker().apply {
        position = LatLng(lat, lng)
        icon = MarkerIcons.BLACK
        iconTintColor = tintColor
        width = Marker.SIZE_AUTO
        height = Marker.SIZE_AUTO
        captionText = name
        captionTextSize = 10f
        captionMinZoom = minZoom
        setOnClickListener { onClick(); true }
    }

    private fun updateStationMarkers(stations: List<Station>) {
        stationMarkers.forEach { it.map = null }
        stationMarkers.clear()
        val map = naverMap ?: return
        stations.forEach { station ->
            val marker = createMarker(station.lat, station.lng, station.name,
                Color.rgb(21, 101, 192), 8.0) { onPinSelected(station) }
            marker.map = map
            stationMarkers.add(marker)
        }
    }

    private fun updateSpotMarkers(spots: List<ActivitySpot>) {
        spotMarkers.forEach { it.map = null }
        spotMarkers.clear()
        val map = naverMap ?: return
        spots.forEach { spot ->
            val marker = createMarker(spot.lat, spot.lng, spot.name,
                spotColor(spot.type), 7.0) { onActivityPinSelected(spot) }
            marker.map = map
            spotMarkers.add(marker)
        }
    }

    private fun spotColor(type: ActivityType): Int = when (type) {
        ActivityType.FISHING    -> Color.rgb(76, 175, 80)
        ActivityType.SURFING    -> Color.rgb(0, 188, 212)
        ActivityType.TIDAL_FLAT -> Color.rgb(121, 85, 72)
        ActivityType.SWIMMING   -> Color.rgb(33, 150, 243)
        ActivityType.SCUBA      -> Color.rgb(13, 71, 161)
        ActivityType.SEA_TRAVEL -> Color.rgb(0, 137, 123)
        ActivityType.HIGH_TIDE  -> Color.rgb(21, 101, 192)
    }

    // 탭 위치 → 활동 필터 (null = 전체). fragment_map.xml의 TabItem 순서와 일치해야 함.
    private val tabFilters = listOf<ActivityType?>(
        null,                     // 전체
        ActivityType.HIGH_TIDE,   // 관측소
        ActivityType.FISHING,     // 바다낚시
        ActivityType.SURFING,     // 서핑
        ActivityType.TIDAL_FLAT,  // 갯벌체험
        ActivityType.SWIMMING,    // 해수욕
        ActivityType.SCUBA,       // 스킨스쿠버
        ActivityType.SEA_TRAVEL   // 바다여행
    )

    private fun setupTabs() {
        binding.tabActivities.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                dismissTooltip()
                viewModel.setActivityFilter(tabFilters.getOrNull(tab.position))
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })

        binding.btnViewIndex.setOnClickListener {
            sharedViewModel.requestTabNavigation(R.id.navigation_index)
        }
    }

    private fun onPinSelected(station: Station) {
        activitySpotNearestStation = null
        sharedViewModel.selectStation(station)
        val b = _binding ?: return
        b.tooltipCard.visibility = View.VISIBLE
        b.tvTooltipName.text = station.name
        b.tvTooltipRegion.text = station.region.displayName
        b.btnViewDetail.visibility = View.VISIBLE
        b.btnViewDetail.setOnClickListener { safeNavigate { findNavController().navigate(R.id.action_global_to_detail) } }
        b.btnGoWatch.visibility = View.VISIBLE
        b.btnGoWatch.setOnClickListener { safeNavigate { sharedViewModel.requestTabNavigation(R.id.navigation_watch) } }
    }

    private fun onActivityPinSelected(spot: ActivitySpot) {
        val nearest = viewModel.findNearestStation(spot.lat, spot.lng)
        activitySpotNearestStation = nearest

        val b = _binding ?: return
        b.tooltipCard.visibility = View.VISIBLE
        b.tvTooltipName.text = spot.name
        b.tvTooltipRegion.text = buildString {
            append(spot.type.displayName)
            if (nearest != null) append(" · 인근: ${nearest.name}")
        }
        if (nearest != null) {
            b.btnViewDetail.visibility = View.VISIBLE
            b.btnViewDetail.setOnClickListener {
                sharedViewModel.selectStation(nearest)
                safeNavigate { findNavController().navigate(R.id.action_global_to_detail) }
            }
        } else {
            b.btnViewDetail.visibility = View.GONE
        }
        b.btnGoWatch.visibility = View.VISIBLE
        b.btnGoWatch.setOnClickListener {
            activitySpotNearestStation?.let { sharedViewModel.selectStation(it) }
            safeNavigate { sharedViewModel.requestTabNavigation(R.id.navigation_watch) }
        }
    }

    // Prevents double-navigation; resets automatically on onResume
    private fun safeNavigate(block: () -> Unit) {
        if (navigating) return
        navigating = true
        try {
            block()
        } catch (e: Exception) {
            android.util.Log.w("MapFragment", "navigation failed", e)
            navigating = false
        }
    }

    private fun dismissTooltip() {
        _binding?.tooltipCard?.visibility = View.GONE
        activitySpotNearestStation = null
        navigating = false
    }

    override fun onStart()  { super.onStart();  _binding?.mapView?.onStart() }
    override fun onResume() { super.onResume(); _binding?.mapView?.onResume(); navigating = false }
    override fun onPause()  { super.onPause();  _binding?.mapView?.onPause() }
    override fun onStop()   { super.onStop();   _binding?.mapView?.onStop() }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _binding?.mapView?.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        _binding?.mapView?.onLowMemory()
    }

    override fun onDestroyView() {
        stationMarkers.forEach { it.map = null }
        stationMarkers.clear()
        spotMarkers.forEach { it.map = null }
        spotMarkers.clear()
        naverMap = null
        binding.adViewMap.destroy()
        binding.mapView.onDestroy()
        super.onDestroyView()
        _binding = null
    }
}
