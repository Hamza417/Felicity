package app.simple.felicity.dialogs.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.databinding.DialogImportEqualizerPresetBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.repository.models.EqualizerPreset
import app.simple.felicity.shared.utils.PEQFileParser
import app.simple.felicity.shared.utils.ViewUtils.shakeView

/**
 * A compact bottom-sheet dialog that lets the user type a name for their current
 * EQ settings and save them as a new preset.
 *
 * A [app.simple.felicity.decorations.equalizer.EqualizerWaveView] previews the current band
 * gains in real time so the user can confirm they are saving the right curve before
 * committing — because nobody wants to save "My Preset" and discover later it is just flat.
 *
 * The [onSave] lambda is called with the trimmed name string when the user taps "Save".
 * The caller (usually [EqualizerPresets]) is responsible for calling the ViewModel to
 * actually persist the preset data.
 *
 * @author Hamza417
 */
class ImportEqualizerPreset : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogImportEqualizerPresetBinding

    /** Callback invoked with the trimmed preset name and category when the user confirms the save. */
    var onImport: ((String, String) -> Unit)? = null
    var parsedPreset: PEQFileParser.ParsedPreset? = null

    private var currentCategory = EqualizerPreset.CATEGORY_HEADPHONE_PROFILES

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogImportEqualizerPresetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val preset = parsedPreset ?: run {
            dismissAllowingStateLoss()
            return
        }

        if (preset.isPeq) {
            binding.presetWavePreview.setPeqBands(preset.bands)
        } else {
            binding.presetWavePreview.setGains(preset.bands.map { it.gain }.toFloatArray())
        }

        binding.presetNameInput.setText(preset.name)
        binding.presetNameInput.requestFocus()

        updateCategoryUi()

        binding.categorySavedPresets.setOnClickListener {
            currentCategory = EqualizerPreset.CATEGORY_SAVED_PRESETS
            updateCategoryUi()
        }

        binding.categoryHeadphoneProfiles.setOnClickListener {
            currentCategory = EqualizerPreset.CATEGORY_HEADPHONE_PROFILES
            updateCategoryUi()
        }

        binding.cancel.setOnClickListener {
            dismissAllowingStateLoss()
        }

        binding.importBtn.setOnClickListener {
            val name = binding.presetNameInput.text.toString().trim()
            if (name.isNotEmpty()) {
                onImport?.invoke(name, currentCategory)
                dismissAllowingStateLoss()
            } else {
                binding.presetNameInput.shakeView()
            }
        }
    }

    private fun updateCategoryUi() {
        binding.categorySavedPresets.setUseAccentColor(currentCategory == EqualizerPreset.CATEGORY_SAVED_PRESETS)
        binding.categoryHeadphoneProfiles.setUseAccentColor(currentCategory == EqualizerPreset.CATEGORY_HEADPHONE_PROFILES)
    }

    companion object {

        const val TAG = "ImportEqualizerPreset"

        fun newInstance(): ImportEqualizerPreset = ImportEqualizerPreset().apply {
            arguments = Bundle()
        }

        /**
         * Shows the "Import Preset" dialog from a [FragmentManager].
         *
         * @param preset The parsed preset to import.
         * @param onImport Called with the trimmed preset name and category when the user confirms.
         */
        fun FragmentManager.showImportEqualizerPreset(
                preset: PEQFileParser.ParsedPreset,
                onImport: (String, String) -> Unit
        ) {
            val fragment = newInstance()
            fragment.parsedPreset = preset
            fragment.onImport = onImport
            fragment.show(this, TAG)
        }
    }
}

