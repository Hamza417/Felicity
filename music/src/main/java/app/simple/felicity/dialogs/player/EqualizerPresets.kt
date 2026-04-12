package app.simple.felicity.dialogs.player

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.simple.felicity.adapters.ui.lists.AdapterEqualizerPresets
import app.simple.felicity.databinding.DialogEqualizerPresetsBinding
import app.simple.felicity.dialogs.player.SaveEqualizerPreset.Companion.showSaveEqualizerPreset
import app.simple.felicity.engine.managers.EqualizerManager
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.preferences.EqualizerPreferences
import app.simple.felicity.repository.models.EqualizerPreset
import app.simple.felicity.viewmodels.dialogs.EqualizerPresetsViewModel
import kotlinx.coroutines.launch

/**
 * Bottom-sheet dialog that lists every saved equalizer preset (both built-in factory ones
 * and anything the user has saved themselves).
 *
 * Tapping a preset immediately applies its band gains and preamp to the live audio engine
 * via [EqualizerManager], and also persists the new values through [EqualizerPreferences]
 * so the settings survive the next app restart. No "Apply" button needed — one tap is all
 * it takes to change the vibe of your music.
 *
 * Long-pressing a user-created preset offers a delete confirmation. Built-in presets ignore
 * long-presses entirely because some things are just too good to delete accidentally.
 *
 * The "Save Preset" button at the top opens [SaveEqualizerPreset] so the user can snapshot
 * the current EQ settings into a named preset without leaving the dialog.
 *
 * @author Hamza417
 */
class EqualizerPresets : ScopedBottomSheetFragment() {

    private var binding: DialogEqualizerPresetsBinding? = null
    private val viewModel: EqualizerPresetsViewModel by viewModels()

    /** Callback invoked when the user applies a preset so the parent fragment can refresh its UI. */
    var onPresetApplied: ((EqualizerPreset) -> Unit)? = null

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        return DialogEqualizerPresetsBinding.inflate(inflater, container, false).also {
            binding = it
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = AdapterEqualizerPresets(
                onPresetClicked = ::applyPreset,
                onPresetLongClicked = ::offerDelete
        )

        binding?.presetsRecyclerView?.adapter = adapter

        // Open the "Save Preset" dialog when the button is tapped. No need to dismiss
        // this dialog first — both can live side by side happily.
        binding?.savePresetButton?.setOnClickListener {
            childFragmentManager.showSaveEqualizerPreset { name ->
                val gains = EqualizerManager.getAllGains()
                val preamp = EqualizerManager.getPreamp()
                viewModel.savePreset(name, gains, preamp)
            }
        }

        // Collect the live preset list and hand it straight to the adapter.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.presets.collect { presets ->
                    adapter.submitList(presets)
                }
            }
        }
    }

    /**
     * Applies [preset] to the live audio engine and persists all its values.
     * The parent fragment's [onPresetApplied] callback is also fired so it can update
     * its sliders without requiring a full ViewModel reload.
     */
    private fun applyPreset(preset: EqualizerPreset) {
        val gains = preset.getBandGains()

        // Push each band gain to the manager and persist it. The 'persist' flag on each
        // individual call is false here because we batch-write all gains at once below.
        EqualizerPreferences.setAllBandGains(gains)
        for (i in gains.indices) {
            EqualizerManager.setBandGain(i, gains[i], persist = false)
        }

        // Restore the preamp gain from the preset as well.
        EqualizerManager.setPreamp(preset.preampDb)

        onPresetApplied?.invoke(preset)
        dismissAllowingStateLoss()
    }

    /**
     * Offers a delete option for user-created presets via a long-press.
     * Built-in presets are left alone — they are here for a good reason.
     *
     * @param preset The preset the user long-pressed.
     */
    private fun offerDelete(preset: EqualizerPreset) {
        if (preset.isBuiltIn) return

        // A simple, no-frills "are you sure?" confirmation. The user long-pressed deliberately,
        // but an accidental long-press shouldn't nuke their favorite preset forever.
        AlertDialog.Builder(requireContext())
            .setMessage(getString(app.simple.felicity.R.string.are_you_sure))
            .setPositiveButton(getString(app.simple.felicity.R.string.yes)) { _, _ ->
                viewModel.deletePreset(preset)
            }
            .setNegativeButton(getString(app.simple.felicity.R.string.cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {

        const val TAG = "EqualizerPresets"

        fun newInstance(): EqualizerPresets = EqualizerPresets().apply {
            arguments = Bundle()
        }

        /**
         * Convenience extension that shows the presets dialog from any [FragmentManager]
         * without having to manually construct and show the fragment.
         *
         * @param onPresetApplied Optional callback fired when the user applies a preset.
         */
        fun FragmentManager.showEqualizerPresets(
                onPresetApplied: ((EqualizerPreset) -> Unit)? = null
        ) {
            val fragment = newInstance()
            fragment.onPresetApplied = onPresetApplied
            fragment.show(this, TAG)
        }
    }
}

