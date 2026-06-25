package com.koretide.app.ui.main

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
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
        setupNavigation()
        observeTabNavigation()
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
