package com.koretide.app.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.koretide.app.databinding.FragmentThemeSettingsBinding
import com.koretide.app.theme.SeasonThemeManager
import com.koretide.app.theme.tagline
import com.koretide.app.theme.ThemeConfig
import com.koretide.app.ui.main.SharedViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val PREFS_THEME = "theme_prefs"
private const val KEY_THEME_ID = "selected_theme_id"

@AndroidEntryPoint
class ThemeSettingsFragment : Fragment() {

    @Inject lateinit var seasonThemeManager: SeasonThemeManager

    private var _binding: FragmentThemeSettingsBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentThemeSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupThemeCards()
    }

    private fun setupThemeCards() {
        val themes = seasonThemeManager.allThemes()
        val savedId = requireContext()
            .getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_ID, null)
        val activeTheme = themes.firstOrNull { it.id == savedId }
            ?: seasonThemeManager.getThemeForContext(requireContext())

        binding.tvCurrentTheme.text  = activeTheme.displayName
        binding.tvCurrentSeason.text = activeTheme.tagline()

        binding.recyclerThemes.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerThemes.adapter = ThemeCardAdapter(themes, activeTheme.id) { selected ->
            saveTheme(selected)
            sharedViewModel.setSelectedTheme(selected.id)
            binding.tvCurrentTheme.text  = selected.displayName
            binding.tvCurrentSeason.text = selected.tagline()
            Toast.makeText(requireContext(), "${selected.displayName} 테마로 변경됐어요", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveTheme(theme: ThemeConfig) {
        requireContext()
            .getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME_ID, theme.id).apply()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
