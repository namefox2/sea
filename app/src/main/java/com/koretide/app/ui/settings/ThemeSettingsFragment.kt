package com.koretide.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.koretide.app.R
import com.koretide.app.databinding.FragmentThemeSettingsBinding
import com.koretide.app.theme.SeasonThemeManager
import com.koretide.app.theme.tagline
import com.koretide.app.theme.ThemeConfig
import com.koretide.app.ui.main.SharedViewModel
import com.koretide.app.util.CrashLogger
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
        setupCrashLog()
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
        binding.tvCurrentSeason.text = activeTheme.tagline()

        val adapter = ThemeCardAdapter(themes, activeTheme.id) { selected ->
            saveTheme(selected)
            sharedViewModel.setSelectedTheme(selected.id)
            binding.tvCurrentTheme.text = selected.displayName
            binding.tvCurrentSeason.text = selected.tagline()
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

    private fun setupCrashLog() {
        binding.btnViewCrashLog.setOnClickListener { showCrashLogDialog() }
        binding.btnClearCrashLog.setOnClickListener {
            CrashLogger.clear(requireContext())
            Toast.makeText(requireContext(), "오류 로그가 삭제됐습니다", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCrashLogDialog() {
        val log = CrashLogger.read(requireContext())
        val content = if (log.isBlank()) "기록된 오류 로그가 없습니다." else log

        val tv = TextView(requireContext()).apply {
            text = content
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(40, 32, 40, 32)
        }
        val scroll = ScrollView(requireContext()).apply { addView(tv) }

        val builder = AlertDialog.Builder(requireContext())
            .setTitle("오류 진단 로그")
            .setView(scroll)
            .setPositiveButton("닫기", null)

        if (log.isNotBlank()) {
            builder.setNeutralButton("클립보드 복사") { _, _ ->
                val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("crash_log", log))
                Toast.makeText(requireContext(), "클립보드에 복사됐습니다", Toast.LENGTH_SHORT).show()
            }
        }
        builder.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
