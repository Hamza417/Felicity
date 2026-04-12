package app.simple.felicity.ui.panels

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.AdapterEqualizerPresets
import app.simple.felicity.databinding.FragmentEqualizerPresetsBinding
import app.simple.felicity.databinding.HeaderEqualizerPresetsBinding
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.decorations.views.PopupMenuItem
import app.simple.felicity.decorations.views.SharedScrollViewPopup
import app.simple.felicity.dialogs.player.SaveEqualizerPreset
import app.simple.felicity.dialogs.player.SaveEqualizerPreset.Companion.showSaveEqualizerPreset
import app.simple.felicity.engine.managers.EqualizerManager
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.preferences.EqualizerPreferences
import app.simple.felicity.repository.models.EqualizerPreset
import app.simple.felicity.viewmodels.dialogs.EqualizerPresetsViewModel
import kotlinx.coroutines.launch

/**
 * A full-screen panel that lists all saved equalizer presets — both the ones bundled
 * with the app and any the user has created themselves.
 *
 * This is modeled after [LyricsSearch] so it fits naturally into the existing panel
 * navigation stack: tapping the Presets button in the EQ screen pushes this fragment
 * onto the back stack, and pressing back pops it off. No bottom-sheets, no dialogs
 * floating over dialogs — just clean, predictable navigation.
 *
 * Tapping a preset applies it immediately and returns to the EQ screen so the user
 * can see the sliders snap to the preset curve in real time.
 *
 * Long-pressing a preset shows a [SharedScrollPopupView] with two options:
 *  - "Apply" — same as a regular tap, applies the preset and goes back.
 *  - "Delete" — asks for confirmation via [withSureDialog] and removes the preset
 *    from the database. This option is hidden from the popup for built-in presets
 *    since those cannot (and should not) be deleted.
 *
 * The "Save Preset" button in the header captures the current EQ state into a named
 * preset via [SaveEqualizerPreset], without leaving this screen.
 *
 * @author Hamza417
 */
class EqualizerPresets : MediaFragment() {

    private lateinit var binding: FragmentEqualizerPresetsBinding
    private lateinit var headerBinding: HeaderEqualizerPresetsBinding

    private val viewModel: EqualizerPresetsViewModel by viewModels()
    private var adapter: AdapterEqualizerPresets? = null

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        binding = FragmentEqualizerPresetsBinding.inflate(inflater, container, false)
        headerBinding = HeaderEqualizerPresetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireHiddenMiniPlayer()

        // Plug the header into the AppHeader so it behaves like every other panel header —
        // it scrolls away as the user scrolls down the preset list.
        binding.appHeader.setContentView(headerBinding.root)
        binding.appHeader.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        setupAdapter()
        setupSaveButton()
        observePresets()
    }

    override fun onDestroyView() {
        adapter = null
        super.onDestroyView()
    }

    /**
     * Wires up the adapter and hands it the two interaction callbacks.
     * The adapter is created once and recycled across list updates.
     */
    private fun setupAdapter() {
        adapter = AdapterEqualizerPresets(
                onPresetClicked = ::applyPreset,
                onOptionClicked = ::showPresetOptions
        )
        binding.recyclerView.adapter = adapter
    }

    /**
     * Opens the [SaveEqualizerPreset] bottom-sheet so the user can snapshot their
     * current EQ settings into a named preset right from this screen.
     */
    private fun setupSaveButton() {
        headerBinding.savePreset.setOnClickListener {
            childFragmentManager.showSaveEqualizerPreset { name ->
                val gains = EqualizerManager.getAllGains()
                val preamp = EqualizerManager.getPreamp()
                viewModel.savePreset(name, gains, preamp)
            }
        }
    }

    /**
     * Starts collecting the live preset list from the database and feeds each update
     * straight into the adapter. Room handles the heavy lifting — we just watch.
     */
    private fun observePresets() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.presets.collect { presets ->
                    adapter?.submitList(presets)
                }
            }
        }
    }

    /**
     * Applies [preset] to the live audio engine, persists all band gains and the preamp
     * to [EqualizerPreferences], and then navigates back to the EQ screen so the user
     * can immediately see the sliders jump to the preset positions. One tap, done.
     *
     * @param preset The preset to apply.
     */
    private fun applyPreset(preset: EqualizerPreset) {
        val gains = preset.getBandGains()

        // Write all gains to SharedPreferences first, then push them to the audio engine.
        // Doing it in this order means the values are safe even if the app is killed mid-update.
        EqualizerPreferences.setAllBandGains(gains)
        for (i in gains.indices) {
            EqualizerManager.setBandGain(i, gains[i], persist = false)
        }
        EqualizerManager.setPreamp(preset.preampDb)

        goBack()
    }

    /**
     * Shows a [SharedScrollPopupView] anchored to [anchorView] with context actions for
     * the given [preset]. "Apply" is always present; "Delete" is only offered for
     * presets that the user created (not the built-in factory ones).
     *
     * @param preset     The preset the user long-pressed.
     * @param anchorView The view that was long-pressed — used as the popup anchor.
     */
    private fun showPresetOptions(preset: EqualizerPreset, anchorView: View) {
        SharedScrollViewPopup(
                container = requireContainerView(),
                anchorView = anchorView,
                menuItems = listOf(
                        PopupMenuItem(title = R.string.apply_preset),
                        PopupMenuItem(title = R.string.delete),
                ),
                onMenuItemClick = {
                    val effect = when (it) {
                        R.string.apply_preset -> applyPreset(preset)
                        R.string.delete -> confirmAndDelete(preset)
                        else -> return@SharedScrollViewPopup
                    }
                },
                onDismiss = {}
        ).show()
    }

    /**
     * Shows a [withSureDialog] confirmation before permanently deleting [preset].
     * The dialog lives in the parent panel rather than a nested bottom-sheet, which
     * keeps the interaction hierarchy clean and simple.
     *
     * @param preset The user-created preset to delete.
     */
    private fun confirmAndDelete(preset: EqualizerPreset) {
        withSureDialog { confirmed ->
            if (confirmed) {
                viewModel.deletePreset(preset)
            }
        }
    }

    companion object {

        const val TAG = "EqualizerPresets"

        /**
         * Creates a fresh instance of [EqualizerPresets] ready to be pushed onto the
         * fragment back stack via [app.simple.felicity.extensions.fragments.ScopedFragment.openFragment].
         */
        fun newInstance(): EqualizerPresets {
            return EqualizerPresets().apply {
                arguments = Bundle()
            }
        }
    }
}

