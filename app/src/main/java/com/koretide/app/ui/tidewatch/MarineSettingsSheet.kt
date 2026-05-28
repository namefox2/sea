package com.koretide.app.ui.tidewatch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.koretide.app.databinding.SheetMarineSettingsBinding
import com.koretide.app.ui.tidewatch.marine.MarineLifeSettings

class MarineSettingsSheet : BottomSheetDialogFragment() {

    private var _binding: SheetMarineSettingsBinding? = null
    private val binding get() = _binding!!

    var onSettingsChanged: ((MarineLifeSettings) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = SheetMarineSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val saved = MarineLifePrefs.load(requireContext())

        binding.switchFish.isChecked       = saved.fishEnabled
        binding.switchCrab.isChecked       = saved.crabEnabled
        binding.switchClam.isChecked       = saved.clamEnabled
        binding.switchCuttlefish.isChecked = saved.cuttlefishEnabled

        val applyAndSave = {
            val settings = MarineLifeSettings(
                fishEnabled       = binding.switchFish.isChecked,
                crabEnabled       = binding.switchCrab.isChecked,
                clamEnabled       = binding.switchClam.isChecked,
                cuttlefishEnabled = binding.switchCuttlefish.isChecked
            )
            MarineLifePrefs.save(requireContext(), settings)
            onSettingsChanged?.invoke(settings)
        }

        binding.switchFish.setOnCheckedChangeListener      { _, _ -> applyAndSave() }
        binding.switchCrab.setOnCheckedChangeListener      { _, _ -> applyAndSave() }
        binding.switchClam.setOnCheckedChangeListener      { _, _ -> applyAndSave() }
        binding.switchCuttlefish.setOnCheckedChangeListener{ _, _ -> applyAndSave() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "MarineSettingsSheet"
    }
}
