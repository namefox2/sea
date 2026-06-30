package com.koretide.app.ui.tidewatch

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.koretide.app.databinding.FragmentTideWatchBinding
import com.koretide.app.domain.model.StationRegion
import com.koretide.app.domain.model.TideData
import com.koretide.app.theme.SeasonThemeManager
import com.koretide.app.ui.main.SharedViewModel
import com.koretide.app.util.collectFlow
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val PREFS_THEME = "theme_prefs"
private const val KEY_THEME_ID = "selected_theme_id"
private const val PREFS_WATCH = "watch_prefs"
private const val KEY_WAVE_SOUND = "wave_sound_enabled"

// 사용자가 물때 슬라이더를 직접 조절할 때 쓰는 수위 범위 — 지역 보정범위가 아닌
// 현재 '조차(m)'를 기준으로 한다. 조차가 클수록 물 변화 폭(span)이 커진다.
//   만조(tide=1) 시 maxZ≈16 → 카메라 근처까지 물이 참
//   조차 1 m ≈ Z 3.4단위 (서해 조차≈9.8 m → span≈34와 동일 기준)
private fun tidalRangeVisualRange(rangeM: Float): Pair<Float, Float> {
    val span = rangeM.coerceIn(0.5f, 10f) * 3.4f
    val maxZ = 16f
    return (maxZ - span) to maxZ
}

@AndroidEntryPoint
class TideWatchFragment : Fragment() {

    @Inject lateinit var seasonThemeManager: SeasonThemeManager

    private var _binding: FragmentTideWatchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TideWatchViewModel by viewModels()
    private val sharedViewModel: SharedViewModel by activityViewModels()

    private var uiVisible = true
    private var isImmersivePreset = false

    private var cachedRegion: StationRegion? = null
    // 오늘 실제 조차(m, API). 물때 슬라이더의 visual Z 범위와 갯벌 노출 계산에 사용.
    // (수면 매핑은 지역 보정범위가 아니라 조차 기준으로 통일 → 초기/수동 동작 동일)
    private var cachedTidalRangeM: Float = 4.5f

    private val oceanSound = OceanSoundPlayer()
    private var soundEnabled = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTideWatchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val savedId = requireContext()
            .getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_ID, null)
        val theme = if (savedId != null) {
            seasonThemeManager.allThemes().firstOrNull { it.id == savedId }
                ?: seasonThemeManager.getThemeForContext(requireContext())
        } else {
            seasonThemeManager.getThemeForContext(requireContext())
        }
        binding.tideWatchView.themeConfig = theme

        setupSliders()
        setupSound()
        setupImmersiveButton()

        // Tap background to toggle immersive; slider panel consumes its own touches
        binding.root.setOnClickListener { toggleImmersive() }

        observeState()
        scheduleGlErrorCheck()
    }

    private fun scheduleGlErrorCheck() {
        Handler(Looper.getMainLooper()).postDelayed({
            val b   = _binding ?: return@postDelayed
            val err = b.tideWatchView.initError ?: return@postDelayed
            val ctx = context ?: return@postDelayed
            android.widget.Toast.makeText(ctx, "GL오류: $err", android.widget.Toast.LENGTH_LONG).show()
        }, 3000L)
    }

    private fun setupSliders() {
        // 조차 기준 visual 범위 (수동 슬라이더와 동일 매핑)
        run {
            val (minZ, maxZ) = tidalRangeVisualRange(cachedTidalRangeM)
            binding.tideWatchView.setVisualRange(minZ, maxZ)
        }

        val initialT = binding.seekTide.progress / 100f
        binding.tideWatchView.setTide(initialT)
        binding.tideWatchView.setMudflatExposure(
            computeMudflatExposure(initialT, cachedTidalRangeM, cachedRegion)
        )
        // 조차 row: display-only — shows today's actual tidal range from API
        binding.seekTidalRange.isEnabled = false
        binding.tideWatchView.setWind(binding.seekWind.progress)

        binding.seekWind.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                binding.tvWindBft.text = "$progress bft"
                binding.tideWatchView.setWind(progress)
                oceanSound.setIntensity(progress.coerceIn(0, 12) / 12f)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // 물때 slider: calibratedT in [0,1] — higher = more water (correct direction)
        binding.seekTide.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                binding.tvTidePct.text = "$progress%"
                val t = progress / 100f
                // 사용자가 직접 물때를 조절할 땐 지역의 좁은 보정범위(예: 동해 -3~3) 대신
                // 현재 '조차(m)' 기준으로 매핑해 조차가 클수록 물이 더 크게 늘고 줄게 한다.
                // (지역 보정범위는 API 실측값을 사실적으로 보여줄 때만 의미가 있음)
                if (fromUser) {
                    val (minZ, maxZ) = tidalRangeVisualRange(cachedTidalRangeM)
                    binding.tideWatchView.setVisualRange(minZ, maxZ)
                }
                binding.tideWatchView.setTide(t)
                binding.tideWatchView.setMudflatExposure(
                    computeMudflatExposure(t, cachedTidalRangeM, cachedRegion)
                )
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun setupSound() {
        soundEnabled = requireContext()
            .getSharedPreferences(PREFS_WATCH, Context.MODE_PRIVATE)
            .getBoolean(KEY_WAVE_SOUND, false)
        binding.switchSound.isChecked = soundEnabled
        oceanSound.setIntensity(binding.seekWind.progress.coerceIn(0, 12) / 12f)
        if (soundEnabled) oceanSound.start()

        binding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            soundEnabled = isChecked
            requireContext()
                .getSharedPreferences(PREFS_WATCH, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_WAVE_SOUND, isChecked).apply()
            if (isChecked) oceanSound.start() else oceanSound.stop()
        }
    }

    private fun setupImmersiveButton() {
        binding.btnImmersive.setOnClickListener { resetToImmersiveDefault() }
    }

    // 기본 상태로 초기화: 지역정보 삭제 + 바람2/물때35/조차5.5 + 해 가운데(정오).
    // 초기화 버튼과 테마 변경 시 공용 사용.
    // Note: isImmersivePreset 는 일부러 건드리지 않음 — 더블탭 시 restoreStationView()가
    // 슬라이더 값을 바꾸는 것을 막기 위함.
    private fun resetToImmersiveDefault() {
        val b = _binding ?: return
        cachedTidalRangeM = 5.5f
        cachedRegion = null
        run {
            val (minZ, maxZ) = tidalRangeVisualRange(cachedTidalRangeM)  // 5.5m 기준
            b.tideWatchView.setVisualRange(minZ, maxZ)
        }
        // Set sliders first; their listeners may queue a mudflatExposure GL event
        b.seekWind.progress       = 2
        b.seekTide.progress       = 35
        b.seekTidalRange.progress = 55
        b.tvTidalRange.text       = "5.5m"
        // Apply preset AFTER sliders so its GL events queue last and win.
        b.tideWatchView.applyImmersivePreset()
        // Clear station info overlay
        b.tvStationName.text      = ""
        b.tvTideInfo.text         = ""
        b.tvWindInfo.text         = ""
        b.tvMudflatGrade.visibility = View.GONE
    }

    private fun toggleImmersive() {
        uiVisible = !uiVisible
        val vis = if (uiVisible) View.VISIBLE else View.GONE
        binding.overlayCard.visibility = vis
        binding.sliderPanel.visibility = vis
        sharedViewModel.setWatchImmersive(!uiVisible)

        // Exiting immersive preset: restore real station data / slider state
        if (uiVisible && isImmersivePreset) {
            isImmersivePreset = false
            restoreStationView()
        }
    }

    // Re-applies the current station's data (or slider defaults) after exiting immersive preset.
    private fun restoreStationView() {
        val station = sharedViewModel.selectedStation.value
        binding.tideWatchView.setHasStation(station != null)

        val tideData = viewModel.tideData.value
        if (tideData != null) {
            applyTideData(binding, tideData)
        } else {
            val t = binding.seekTide.progress / 100f
            binding.tideWatchView.setTide(t)
            binding.tideWatchView.setMudflatExposure(
                computeMudflatExposure(t, cachedTidalRangeM, cachedRegion)
            )
        }
        viewModel.windData.value?.let { data ->
            val waveBasedBft = data.waveHeightM?.let { h -> (h * 2.0 + 1.0).coerceIn(0.0, 12.0).toInt() }
            val effectiveBft = if (waveBasedBft != null) maxOf(data.beaufort, waveBasedBft) else data.beaufort
            binding.tideWatchView.setWind(effectiveBft)
            binding.tideWatchView.windDirectionDeg = data.directionDeg
        }
    }

    private fun observeState() {
        collectFlow(sharedViewModel.selectedThemeId) { themeId ->
            val b = _binding ?: return@collectFlow
            if (themeId != null) {
                val newTheme = seasonThemeManager.allThemes().firstOrNull { it.id == themeId }
                // 테마 변경 시 지역정보 초기화는 SharedViewModel.setSelectedTheme가 관측소를
                // 비우는 것으로 처리(아래 selectedStation 관측에서 기본값으로 리셋). 여기선 색/시각만 적용.
                if (newTheme != null) b.tideWatchView.themeConfig = newTheme
            }
        }

        collectFlow(sharedViewModel.selectedStation) { station ->
            val b = _binding ?: return@collectFlow
            isImmersivePreset = false
            cachedRegion = station?.region
            b.tideWatchView.setHasStation(station != null)
            if (station != null) {
                // 조차 데이터가 오기 전 임시 범위(직전 조차값). 데이터 도착 시 applyTideData가 갱신.
                run {
                    val (minZ, maxZ) = tidalRangeVisualRange(cachedTidalRangeM)
                    b.tideWatchView.setVisualRange(minZ, maxZ)
                }
                b.tvStationName.text = station.name
                // Detail에서 받아둔 캐시가 있으면 선적재 → 즉시 표시 + 5분 폴링 지연
                val cachedTide = sharedViewModel.cachedTideFor(station.code)
                val cachedWind = sharedViewModel.windData.value
                if (cachedTide != null) viewModel.preloadFromCache(station.code, cachedTide, cachedWind)
                viewModel.startPolling(station.code, station.lat, station.lng)
            } else {
                // 관측소 없음(앱 시작 또는 테마 변경으로 초기화) → 지역정보 제거 + 기본값.
                // 해 위치/색은 테마값을 유지(applyImmersivePreset의 정오 강제 사용 안 함).
                viewModel.stopPolling()
                cachedTidalRangeM = 5.5f
                val (minZ, maxZ) = tidalRangeVisualRange(cachedTidalRangeM)
                b.tideWatchView.setVisualRange(minZ, maxZ)
                b.seekWind.progress       = 2     // 리스너 → setWind(2)
                b.seekTide.progress       = 35    // 리스너 → setTide(0.35)
                b.seekTidalRange.progress = 55
                b.tvTidalRange.text       = "5.5m"
                b.tideWatchView.setMudflatExposure(0f)   // 슬라이더 리스너가 계산한 갯벌값 덮어쓰기
                b.tvStationName.text = ""
                b.tvTideInfo.text    = ""
                b.tvWindInfo.text    = ""
                b.tvMudflatGrade.visibility = View.GONE
            }
        }

        collectFlow(viewModel.sunTimes) { times ->
            val b = _binding ?: return@collectFlow
            if (times != null) {
                b.tideWatchView.setSunTimes(times.sunriseHour, times.sunsetHour, times.moonriseHour, times.moonsetHour)
            }
        }

        collectFlow(viewModel.tideData) { data ->
            val b = _binding ?: return@collectFlow
            if (data != null && !isImmersivePreset) {
                applyTideData(b, data)
                sharedViewModel.updateTideData(data)
                updateMudflatGrade(b, data)
            }
        }

        collectFlow(viewModel.windData) { data ->
            val b = _binding ?: return@collectFlow
            if (data != null && !isImmersivePreset) {
                // Blend forecast Beaufort with wave-based estimate: take the higher value
                // so that a calm wind forecast doesn't hide rough sea state from wave data.
                val waveBasedBft = data.waveHeightM?.let { h ->
                    (h * 2.0 + 1.0).coerceIn(0.0, 12.0).toInt()
                }
                val effectiveBft = if (waveBasedBft != null) maxOf(data.beaufort, waveBasedBft) else data.beaufort
                b.seekWind.progress = effectiveBft
                b.tideWatchView.windDirectionDeg = data.directionDeg
                oceanSound.setIntensity(effectiveBft.coerceIn(0, 12) / 12f)
                sharedViewModel.updateWindData(data)
                val waveStr = data.waveHeightM?.let { " / 파고 %.1fm".format(it) } ?: ""
                b.tvWindInfo.text = "${data.beaufortName} (${effectiveBft}bft, ${data.speedMs}m/s)$waveStr"
            }
        }
    }

    private fun applyTideData(b: FragmentTideWatchBinding, data: TideData) {
        cachedTidalRangeM = data.tidalRangeM

        // 조차 기준 visual 범위를 먼저 적용 — 수동 슬라이더와 '완전히 동일'한 매핑이라
        // 물때 %를 같은 값으로 되돌리면 항상 같은 바다가 된다(idempotent).
        run {
            val (minZ, maxZ) = tidalRangeVisualRange(data.tidalRangeM)
            b.tideWatchView.setVisualRange(minZ, maxZ)
        }

        // Display % = 오늘 상대 위치(상세화면과 동일). 3D 수면도 같은 값(daily %)으로 그린다.
        val pct = (data.tidePercent * 100).toInt().coerceIn(0, 100)
        b.seekTide.progress = pct
        b.tvTidePct.text    = "$pct%"

        b.seekTidalRange.progress = (data.tidalRangeM * 10).toInt().coerceIn(0, 100)
        b.tvTidalRange.text = "%.1fm".format(data.tidalRangeM)

        // 슬라이더는 정수 %만 표현 → 수동으로 같은 %로 돌렸을 때와 100% 동일하도록 pct/100 사용
        val t = pct / 100f
        b.tideWatchView.setTide(t)
        b.tideWatchView.setMudflatExposure(
            computeMudflatExposure(t, data.tidalRangeM, cachedRegion)
        )
        b.tvTideInfo.text = "${data.tideStatus.displayName} ${data.currentLevel}cm"
    }

    private fun updateMudflatGrade(b: FragmentTideWatchBinding, data: TideData) {
        val region = cachedRegion
        val hasTidalFlat = region == StationRegion.WEST || region == StationRegion.SOUTH
        if (hasTidalFlat && data.tidalRangeM > 1.0f) {
            val exposure = computeMudflatExposure(data.tidePercent, data.tidalRangeM, region)
            b.tvMudflatGrade.text = when {
                exposure >= 0.60f -> "🦀 갯벌 매우 많이 드러남"
                exposure >= 0.30f -> "🦀 갯벌 보통 드러남"
                exposure >= 0.05f -> "🦀 갯벌 조금 드러남"
                else              -> null
            }
            b.tvMudflatGrade.visibility = if (exposure >= 0.05f) View.VISIBLE else View.GONE
        } else {
            b.tvMudflatGrade.visibility = View.GONE
        }
    }

    override fun onPause() {
        super.onPause()
        _binding?.tideWatchView?.onPause()
        oceanSound.stop()
        viewModel.stopPolling()
        isImmersivePreset = false
        if (!uiVisible) {
            uiVisible = true
            sharedViewModel.setWatchImmersive(false)
        }
    }

    override fun onResume() {
        super.onResume()
        _binding?.tideWatchView?.onResume()
        if (soundEnabled) oceanSound.start()
        val station = sharedViewModel.selectedStation.value
        if (station != null && !viewModel.isPolling()) {
            viewModel.startPolling(station.code, station.lat, station.lng)
        }
    }

    override fun onDestroyView() {
        sharedViewModel.setWatchImmersive(false)
        oceanSound.stop()
        _binding?.tideWatchView?.release()
        super.onDestroyView()
        _binding = null
    }
}

// ── Tidal flat exposure computation ──────────────────────────────────────────
// Returns 0..1: how much tidal flat is currently visible.
//   tidePercent  — 0 = 간조 (low tide / exposed), 1 = 만조 (high tide / submerged)
//   tidalRangeM  — actual조차 in metres (from API or 조차 slider)
//   region       — per-coast hard cap (동해 almost never shows mudflat)
private fun computeMudflatExposure(
    tidePercent: Float, tidalRangeM: Float, region: StationRegion?
): Float {
    if (tidalRangeM <= 0.05f) return 0f

    // tidePercent=0 (간조) → tidePosition=1 (fully exposed)
    val tidePosition = (1f - tidePercent).coerceIn(0f, 1f)

    // rangeFactor: small조차 → little mudflat even at low tide (in metres)
    val rangeFactor = when {
        tidalRangeM < 1.0f -> 0.00f
        tidalRangeM < 3.0f -> (tidalRangeM - 1.0f) / 2.0f * 0.30f
        tidalRangeM < 5.0f -> 0.30f + (tidalRangeM - 3.0f) / 2.0f * 0.50f
        else               -> 0.80f + ((tidalRangeM - 5.0f) / 5.0f).coerceAtMost(1f) * 0.20f
    }

    // Per-region hard cap: 동해/제주 geography rarely forms exposed mudflat
    val regionCap = when (region) {
        StationRegion.WEST  -> 1.00f
        StationRegion.SOUTH -> 0.60f
        StationRegion.JEJU  -> 0.08f
        StationRegion.EAST  -> 0.04f
        null                -> 1.00f
    }

    return (tidePosition * rangeFactor * regionCap).coerceIn(0f, 1f)
}
