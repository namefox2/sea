package com.koretide.app.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.koretide.app.R
import com.koretide.app.databinding.FragmentThemeSettingsBinding
import com.koretide.app.theme.Season
import com.koretide.app.theme.SeasonThemeManager
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
        setupSupportButtons()
    }

    private fun setupThemeCards() {
        val themes = seasonThemeManager.allThemes()
        val savedId = requireContext()
            .getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_ID, null)
        val activeTheme = if (savedId != null) {
            themes.firstOrNull { it.id == savedId }
                ?: seasonThemeManager.getThemeForContext(requireContext())
        } else {
            seasonThemeManager.getThemeForContext(requireContext())
        }

        binding.tvCurrentTheme.text = activeTheme.displayName
        binding.tvCurrentSeason.text = activeTheme.season.koreanName() +
                if (activeTheme.isDark) " (야간)" else " (주간)"

        val adapter = ThemeCardAdapter(themes, activeTheme.id) { selected ->
            saveTheme(selected)
            binding.tvCurrentTheme.text = selected.displayName
            binding.tvCurrentSeason.text = selected.season.koreanName() +
                    if (selected.isDark) " (야간)" else " (주간)"
            Toast.makeText(requireContext(), "${selected.displayName} 테마로 변경됐어요", Toast.LENGTH_SHORT).show()
        }
        binding.recyclerThemes.adapter = adapter
        val cols = 2
        binding.recyclerThemes.layoutManager =
            androidx.recyclerview.widget.GridLayoutManager(requireContext(), cols)
    }

    private fun saveTheme(theme: ThemeConfig) {
        requireContext()
            .getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME_ID, theme.id).apply()
    }

    private fun setupSupportButtons() {
        val amounts = listOf(
            binding.btnSupport400  to getString(R.string.support_400),
            binding.btnSupport900  to getString(R.string.support_900),
            binding.btnSupport1500 to getString(R.string.support_1500),
            binding.btnSupport2000 to getString(R.string.support_2000)
        )
        amounts.forEach { (btn, _) ->
            btn.setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.support_thanks_title))
                    .setMessage(getString(R.string.support_thanks_msg))
                    .setPositiveButton(getString(R.string.support_ok), null)
                    .show()
            }
        }
    }

    private fun Season.koreanName() = when (this) {
        Season.SPRING -> "봄"
        Season.SUMMER -> "여름"
        Season.AUTUMN -> "가을"
        Season.WINTER -> "겨울"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
