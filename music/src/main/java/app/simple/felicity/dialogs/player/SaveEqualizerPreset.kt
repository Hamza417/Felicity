package app.simple.felicity.dialogs.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.databinding.DialogSaveEqualizerPresetBinding
import app.simple.felicity.engine.managers.EqualizerManager
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment

/**
 * A compact bottom-sheet dialog that lets the user type a name for their current
 * EQ settings and save them as a new preset.
 *
 * A [app.simple.felicity.decorations.views.EqualizerWaveView] previews the current band
 * gains in real time so the user can confirm they are saving the right curve before
 * committing — because nobody wants to save "My Preset" and discover later it is just flat.
 *
 * The [onSave] lambda is called with the trimmed name string when the user taps "Save".
 * The caller (usually [EqualizerPresets]) is responsible for calling the ViewModel to
 * actually persist the preset data.
 *
 * @author Hamza417
 */
class SaveEqualizerPreset : ScopedBottomSheetFragment() {

    private var binding: DialogSaveEqualizerPresetBinding? = null

    /** Callback invoked with the trimmed preset name when the user confirms the save. */
    var onSave: ((String) -> Unit)? = null

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        return DialogSaveEqualizerPresetBinding.inflate(inflater, container, false).also {
            binding = it
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Show the current EQ curve in the little wave preview so the user knows exactly
        // what they're about to save. If this is flat, maybe they should tweak a bit first!
        binding?.presetWavePreview?.setGains(EqualizerManager.getAllGains())

        // Focus the text field immediately so the keyboard pops up right away —
        // no need to tap the input field first.
        binding?.presetNameInput?.requestFocus()

        binding?.cancel?.setOnClickListener {
            dismissAllowingStateLoss()
        }

        binding?.save?.setOnClickListener {
            val name = binding?.presetNameInput?.text?.toString()?.trim() ?: ""
            if (name.isNotEmpty()) {
                onSave?.invoke(name)
                dismissAllowingStateLoss()
            } else {
                // Gently shake the input field to hint that a name is required.
                binding?.presetNameInput?.animate()
                    ?.translationX(10f)?.setDuration(50)
                    ?.withEndAction {
                        binding?.presetNameInput?.animate()
                            ?.translationX(-10f)?.setDuration(50)
                            ?.withEndAction {
                                binding?.presetNameInput?.animate()
                                    ?.translationX(0f)?.setDuration(50)?.start()
                            }?.start()
                    }?.start()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {

        const val TAG = "SaveEqualizerPreset"

        fun newInstance(): SaveEqualizerPreset = SaveEqualizerPreset().apply {
            arguments = Bundle()
        }

        /**
         * Shows the "Save Preset" dialog from a [FragmentManager].
         *
         * @param onSave Called with the trimmed preset name when the user confirms.
         */
        fun FragmentManager.showSaveEqualizerPreset(onSave: (String) -> Unit) {
            val fragment = newInstance()
            fragment.onSave = onSave
            fragment.show(this, TAG)
        }
    }
}

