package com.koretide.app.ui.main

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
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
    }

    private fun observeTabNavigation() {
        collectFlow(sharedViewModel.navigateToTab) { tabId ->
            if (tabId != null) {
                binding.bottomNavigation.selectedItemId = tabId
                sharedViewModel.onTabNavigated()
            }
        }
        collectFlow(sharedViewModel.isWatchImmersive) { immersive ->
            binding.bottomNavigation.visibility =
                if (immersive) android.view.View.GONE else android.view.View.VISIBLE
        }
    }
}
