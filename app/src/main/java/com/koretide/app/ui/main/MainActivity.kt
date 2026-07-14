package com.koretide.app.ui.main

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.koretide.app.R
import com.koretide.app.databinding.ActivityMainBinding
import com.koretide.app.theme.SeasonThemeManager
import com.koretide.app.util.collectFlow
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var seasonThemeManager: SeasonThemeManager

    private lateinit var binding: ActivityMainBinding
    private val sharedViewModel: SharedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdgeInsetsSafetyNet()
        setupNavigation()
        observeTabNavigation()
    }

    // 테마의 windowOptOutEdgeToEdgeEnforcement 가 정상 적용되면 시스템 바 인셋이 0으로
    // 전달되어 이 리스너는 아무 것도 하지 않는다(=여백 없음, 기존 레이아웃 유지).
    // 혹시 특정 기기/빌드에서 Android 15의 edge-to-edge 가 강제되면, 시스템 바(상태바·
    // 내비게이션 바) 높이만큼 루트에 여백을 줘 광고/탭바 등이 시스템 바와 겹치지 않게 한다.
    private fun applyEdgeToEdgeInsetsSafetyNet() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)

        // setupWithNavController 의 기본 동작은 non-tab 화면(Detail 등)에서 Search 탭을 누를 때
        // popUpTo 를 올바르게 처리 못할 수 있으므로, Search 탭은 항상 루트로 직접 팝
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.navigation_search) {
                navController.popBackStack(R.id.navigation_search, false)
                true
            } else {
                NavigationUI.onNavDestinationSelected(item, navController)
            }
        }
    }

    private fun observeTabNavigation() {
        collectFlow(sharedViewModel.navigateToTab) { tabId ->
            if (tabId != null) {
                try {
                    binding.bottomNavigation.selectedItemId = tabId
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "Tab navigation failed: $tabId", e)
                }
                sharedViewModel.onTabNavigated()
            }
        }
        collectFlow(sharedViewModel.isWatchImmersive) { immersive ->
            binding.bottomNavigation.visibility =
                if (immersive) android.view.View.GONE else android.view.View.VISIBLE
        }
    }
}
