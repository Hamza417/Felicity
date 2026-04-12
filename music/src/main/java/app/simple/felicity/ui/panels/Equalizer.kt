package app.simple.felicity.ui.panels

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.simple.felicity.R
import app.simple.felicity.databinding.FragmentEqualizerBinding
import app.simple.felicity.decorations.knobs.RotaryKnobListener
import app.simple.felicity.decorations.seekbars.FelicityEqualizerSliders
import app.simple.felicity.decorations.toggles.FelicityButtonGroup.Companion.Button
import app.simple.felicity.decorations.utils.TextViewUtils.setTextWithFade
import app.simple.felicity.dialogs.player.SaveEqualizerPreset.Companion.showSaveEqualizerPreset
import app.simple.felicity.engine.managers.EqualizerManager
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.preferences.EqualizerPreferences
import app.simple.felicity.ui.subpanels.EqualizerPresets
import app.simple.felicity.viewmodels.panels.EqualizerViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fragment that presents all equalizer controls: the 10-band graphic EQ sliders, balance,
 * stereo widening, and tape saturation. Each control persists its state via
 * [EqualizerPreferences] which is observed by the player service for immediate processor
 * updates. The 10-band sliders also drive [EqualizerManager] directly so the hardware
 * [android.media.audiofx.Equalizer] effect is updated in real-time.
 *
 * All initial preference reads are offloaded to the IO thread through [EqualizerViewModel]
 * so that the main thread is never blocked during fragment startup. Speaker and reverb panel
 * knobs are configured lazily the first time each panel is shown, avoiding any work for
 * panels the user never opens in a given session.
 *
 * Band gains are kept in sync with [EqualizerManager.bandGainsFlow] so any external change
 * (e.g., a future preset loader) is reflected in the UI automatically.
 *
 * The preset name label between the "Save Preset" and "Reset" buttons always reflects the
 * active preset, or "Custom" when the current EQ curve does not match any saved preset.
 * The ViewModel does that comparison in the background whenever the gains change.
 *
 * @author Hamza417
 */
class Equalizer : MediaFragment() {

    private lateinit var binding: FragmentEqualizerBinding
    private val viewModel: EqualizerViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentEqualizerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireHiddenMiniPlayer()

        setupViewFlipper(savedInstanceState)

        // Wire up the EQ switch eagerly — it has no preference read on the hot path
        // because the ViewModel will deliver the real value via initialState below.
        binding.equalizerScreen.equalizerSwitch.setOnCheckedChangeListener { _, isChecked ->
            EqualizerPreferences.setEqEnabled(isChecked)
            updateEqualizerEnabledState(isChecked)
        }

        binding.equalizerScreen.reset.setOnClickListener {
            withSureDialog { sure ->
                if (sure) {
                    EqualizerManager.resetAllBands()
                    EqualizerPreferences.setPreampDb(0f)
                }
            }
        }

        // Show the save dialog, then let the ViewModel handle the actual DB work —
        // including the duplicate check that guards against accidental overwrites.
        binding.equalizerScreen.savePreset.setOnClickListener {
            childFragmentManager.showSaveEqualizerPreset { name ->
                viewModel.savePreset(
                        name = name,
                        gains = EqualizerManager.getAllGains(),
                        preampDb = EqualizerManager.getPreamp(),
                        onDuplicate = { /* show warning */ },
                        onSuccess = { /* the preset list updates automatically via Room */ }
                )
            }
        }

        // Open the presets panel so the user can browse, apply, or save EQ snapshots.
        // It's a full-screen panel pushed onto the back stack — clean and simple.
        binding.equalizerScreen.presetsButton.setOnClickListener {
            openFragment(EqualizerPresets.newInstance(), EqualizerPresets.TAG)
        }

        // Register live-update observers for the EQ sliders. Initial values are applied
        // once the ViewModel delivers its state below.
        setupEqualizerSliderObservers()

        // Keep the preset name label in sync whenever the ViewModel figures out a match.
        observeCurrentPresetName()

        // Collect the ViewModel's initial state (loaded on the IO thread) to populate
        // all visible controls without blocking the main thread.
        viewLifecycleOwner.lifecycleScope.launch {
            val state = viewModel.initialState.filterNotNull().first()
            applyInitialState(state)
        }
    }

    /**
     * Applies the fully loaded [EqualizerViewModel.EqualizerInitialState] to the UI.
     *
     * Panel 0 (EQ screen) is set up immediately since it is always the default visible
     * panel. Panel 1 (speaker) and panel 2 (reverb) are set up here only when the user
     * has already swiped to them before the ViewModel state arrived; otherwise they are
     * deferred until the first swipe via the ViewFlipper listener.
     */
    private fun applyInitialState(state: EqualizerViewModel.EqualizerInitialState) {
        binding.equalizerScreen.equalizerSwitch.isChecked = state.isEqEnabled
        updateEqualizerEnabledState(state.isEqEnabled, animate = false)
        setupEqualizerSliders(state)
        setupEqKnobs(state)
        setupSpeakerKnobs(state)
        setupReverbKnobs(state)
    }

    fun setupViewFlipper(savedInstanceState: Bundle?) {
        val initialScreen = savedInstanceState?.getInt(SCREEN_STATE_KEY) ?: 0
        binding.viewFlipper.displayedChild = initialScreen

        binding.panelGroup.setButtons(
                listOf(
                        Button(iconResId = R.drawable.ic_tune_16dp),
                        Button(iconResId = R.drawable.ic_knob_16dp),
                        Button(iconResId = R.drawable.ic_speaker_16dp)
                )
        )

        binding.panelGroup.setSelectedIndex(initialScreen, animate = false, notifyListener = false)
        setHeaderTitle(initialScreen)

        binding.panelGroup.setOnButtonSelectedListener { index ->
            binding.viewFlipper.displayedChild = index
        }

        binding.viewFlipper.setOnScreenChangedListener { index ->
            binding.panelGroup.setSelectedIndex(index, animate = true, notifyListener = false)
            setHeaderTitle(index)
        }
    }

    // -------------------------------------------------------------------------
    // 10-band EQ sliders
    // -------------------------------------------------------------------------

    /**
     * Registers the live-update flow observers for the EQ sliders. Initial values are NOT
     * applied here — they come from the ViewModel's [EqualizerViewModel.initialState].
     * This split avoids any synchronous preference read on the main thread.
     */
    private fun setupEqualizerSliderObservers() {
        binding.equalizerScreen.equalizerSliders.setOnBandChangedListener { bandIndex, gain, fromUser ->
            if (fromUser) {
                Log.d(TAG, "Band $bandIndex changed to ${gain}dB by user")
                if (bandIndex == FelicityEqualizerSliders.PREAMP_BAND_INDEX) {
                    EqualizerManager.setPreamp(gain)
                } else {
                    EqualizerManager.setBandGain(bandIndex, gain)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                EqualizerManager.bandGainsFlow.collect { gains ->
                    binding.equalizerScreen.equalizerSliders.setAllGains(gains, animate = true)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                EqualizerManager.preampFlow.collect { db ->
                    binding.equalizerScreen.equalizerSliders.setPreampGain(db, animate = true)
                }
            }
        }
    }

    /**
     * Applies the initial slider positions from [state]. Called once after the ViewModel
     * delivers its state, keeping the restore animation disabled so the UI is ready
     * before the user sees it.
     */
    private fun setupEqualizerSliders(state: EqualizerViewModel.EqualizerInitialState) {
        binding.equalizerScreen.equalizerSliders.setAllGains(state.bandGains, animate = false)
        binding.equalizerScreen.equalizerSliders.setPreampGain(state.preampDb, animate = false)
    }

    private fun updateEqualizerEnabledState(isEnabled: Boolean, animate: Boolean = true) {
        if (animate) {
            binding.equalizerScreen.equalizerSliders
                .animate()
                .alpha(if (isEnabled) 1f else 0.5f)
                .setDuration(300)
                .start()

            binding.equalizerScreen.equalizerSliders.isEnabled = isEnabled
        } else {
            binding.equalizerScreen.equalizerSliders.alpha = if (isEnabled) 1f else 0.5f
            binding.equalizerScreen.equalizerSliders.isEnabled = isEnabled
        }
    }

    /**
     * Collects [EqualizerViewModel.currentPresetName] and keeps the preset name label
     * between the Save and Reset buttons up to date. The fade animation makes the
     * transition between "Custom" and a named preset look smooth rather than jarring.
     */
    private fun observeCurrentPresetName() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentPresetName.collect { name ->
                    binding.equalizerScreen.presetName.setTextWithFade(name)
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // EQ screen knobs (bass, treble)
    // -------------------------------------------------------------------------

    /**
     * Wires up the bass and treble knobs on the EQ screen using initial values from
     * [state]. These knobs are part of panel 0 so they are configured eagerly alongside
     * the EQ sliders.
     */
    private fun setupEqKnobs(state: EqualizerViewModel.EqualizerInitialState) {
        // Bass knob (low-shelf at 250 Hz).
        // Knob value 0-100 maps to gain -12 dB (full cut) ... 0 dB (center) ... +12 dB (full boost).
        binding.equalizerScreen.bassKnob.centerSnapEnabled = true
        binding.equalizerScreen.bassKnob.setTickTexts("-12", "+12")
        binding.equalizerScreen.bassKnob.divisionCount = 48 * 2
        binding.equalizerScreen.bassKnob.setKnobPosition(bassDbToKnobValue(state.bassDb), animate = false)
        binding.equalizerScreen.bassKnob.setListener(object : RotaryKnobListener {
            override fun onIncrement(value: Float) {}

            override fun onRotate(value: Float) {
                val db = knobValueToBassDb(value)
                EqualizerPreferences.setBassDb(db)
                Log.d(TAG, "Bass gain updated: ${db}dB")
            }

            override fun onLabel(value: Float): String {
                val db = knobValueToBassDb(value)
                return when {
                    db > 0.05f -> "+${"%.1f".format(db)} dB"
                    db < -0.05f -> "${"%.1f".format(db)} dB"
                    else -> "0 dB"
                }
            }
        })

        // Treble knob (high-shelf at 4000 Hz).
        // Knob value 0-100 maps to gain -12 dB (full cut) ... 0 dB (center) ... +12 dB (full boost).
        binding.equalizerScreen.trebleKnob.centerSnapEnabled = true
        binding.equalizerScreen.trebleKnob.setTickTexts("-12", "+12")
        binding.equalizerScreen.trebleKnob.divisionCount = 48 * 2
        binding.equalizerScreen.trebleKnob.setKnobPosition(trebleDbToKnobValue(state.trebleDb), animate = false)
        binding.equalizerScreen.trebleKnob.setListener(object : RotaryKnobListener {
            override fun onIncrement(value: Float) {}

            override fun onRotate(value: Float) {
                val db = knobValueToTrebleDb(value)
                EqualizerPreferences.setTrebleDb(db)
                Log.d(TAG, "Treble gain updated: ${db}dB")
            }

            override fun onLabel(value: Float): String {
                val db = knobValueToTrebleDb(value)
                return when {
                    db > 0.05f -> "+${"%.1f".format(db)} dB"
                    db < -0.05f -> "${"%.1f".format(db)} dB"
                    else -> "0 dB"
                }
            }
        })
    }

    // -------------------------------------------------------------------------
    // Speaker screen knobs (balance, stereo widening, tape saturation)
    // -------------------------------------------------------------------------

    /**
     * Wires up the three knobs on the speaker screen. Called lazily the first time
     * the user swipes to panel 1.
     */
    private fun setupSpeakerKnobs(state: EqualizerViewModel.EqualizerInitialState) {
        // Balance knob (constant-power panning).
        // Knob value 0-100 maps to pan -1 (full left) ... 0 (center) ... +1 (full right).
        binding.speakerScreen.balanceKnob.centerSnapEnabled = true
        binding.speakerScreen.balanceKnob.setTickTexts("L", "R")
        binding.speakerScreen.balanceKnob.setKnobPosition(panToKnobValue(state.balance), animate = false)
        binding.speakerScreen.balanceKnob.setListener(object : RotaryKnobListener {
            override fun onIncrement(value: Float) {}

            override fun onRotate(value: Float) {
                val pan = knobValueToPan(value)
                EqualizerPreferences.setBalance(pan)
                Log.d(TAG, "Balance updated: pan=$pan")
            }

            override fun onLabel(value: Float): String {
                val pan = knobValueToPan(value)
                return when {
                    pan < -0.02f -> "L ${"%.0f".format(-pan * 100)}%"
                    pan > 0.02f -> "R ${"%.0f".format(pan * 100)}%"
                    else -> "C"
                }
            }
        })

        // Stereo widening knob (mid/side matrix).
        // Knob value 0-100 maps to width 0.0 (mono) ... 1.0 (normal) ... 2.0 (max wide).
        binding.speakerScreen.stereoWideningKnob.centerSnapEnabled = true
        binding.speakerScreen.stereoWideningKnob.setTickTexts("M", "W")
        binding.speakerScreen.stereoWideningKnob.setKnobPosition(widthToKnobValue(state.stereoWidth), animate = false)
        binding.speakerScreen.stereoWideningKnob.divisionCount = 10 * 10
        binding.speakerScreen.stereoWideningKnob.setListener(object : RotaryKnobListener {
            override fun onIncrement(value: Float) {}

            override fun onRotate(value: Float) {
                val width = knobValueToWidth(value)
                EqualizerPreferences.setStereoWidth(width)
                Log.d(TAG, "Stereo width updated: width=$width")
            }

            override fun onLabel(value: Float): String {
                val width = knobValueToWidth(value)
                return when {
                    width < 0.02f -> "Mono"
                    width in 0.98f..1.02f -> getString(R.string.normal)
                    width > 1.0f -> "+${"%.0f".format((width - 1f) * 100)}%"
                    else -> "-${"%.0f".format((1f - width) * 100)}%"
                }
            }
        })

        // Tape saturation knob (algebraic soft-clip drive).
        // Knob value 0-100 maps to drive 0.0 (clean/off) ... 4.0 (maximum saturation).
        binding.speakerScreen.tapeSaturationKnob.setTickTexts("0", "4")
        binding.speakerScreen.tapeSaturationKnob.setKnobPosition(driveToKnobValue(state.tapeSaturationDrive), animate = false)
        binding.speakerScreen.tapeSaturationKnob.divisionCount = 4 * 10
        binding.speakerScreen.tapeSaturationKnob.setListener(object : RotaryKnobListener {
            override fun onIncrement(value: Float) {}

            override fun onRotate(value: Float) {
                val drive = knobValueToDrive(value)
                EqualizerPreferences.setTapeSaturationDrive(drive)
                Log.d(TAG, "Tape saturation drive updated: drive=$drive")
            }

            override fun onLabel(value: Float): String {
                val drive = knobValueToDrive(value)
                return if (drive < 0.05f) "Off" else "%.1f".format(drive)
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(SCREEN_STATE_KEY, binding.viewFlipper.displayedChild)
    }

    // -------------------------------------------------------------------------
    // Reverb screen knobs (mix, fade, size, damp)
    // -------------------------------------------------------------------------

    /**
     * Wires up the four reverb knobs on the reverb screen. Called lazily the first time
     * the user swipes to panel 2.
     *
     * Each knob writes to [EqualizerPreferences], which is observed by the player service
     * and forwarded to the native DSP engine in real-time.
     */
    private fun setupReverbKnobs(state: EqualizerViewModel.EqualizerInitialState) {
        // Mix knob — wet/dry ratio.
        // Knob value 0-100 maps to mix 0.0 (dry) ... 1.0 (fully wet).
        binding.reverbScreen.reverbMixKnob.setTickTexts("0%", "100%")
        binding.reverbScreen.reverbMixKnob.divisionCount = 100
        binding.reverbScreen.reverbMixKnob.setKnobPosition(
                reverbMixToKnob(state.reverbMix), animate = false)
        binding.reverbScreen.reverbMixKnob.setListener(object : RotaryKnobListener {
            override fun onIncrement(value: Float) {}

            override fun onRotate(value: Float) {
                val mix = knobToReverbMix(value)
                EqualizerPreferences.setReverbMix(mix)
                Log.d(TAG, "Reverb mix updated: ${(mix * 100).toInt()}%")
            }

            override fun onLabel(value: Float): String {
                val mix = knobToReverbMix(value)
                return if (mix < 0.005f) "Off" else "${"%.0f".format(mix * 100)}%"
            }
        })

        // Fade knob — reverb decay time.
        // Knob value 0-100 maps to decay 0.0 (very short) ... 1.0 (very long hall).
        binding.reverbScreen.reverbFadeKnob.setTickTexts("S", "L")
        binding.reverbScreen.reverbFadeKnob.divisionCount = 100
        binding.reverbScreen.reverbFadeKnob.setKnobPosition(
                reverbDecayToKnob(state.reverbDecay), animate = false)
        binding.reverbScreen.reverbFadeKnob.setListener(object : RotaryKnobListener {
            override fun onIncrement(value: Float) {}

            override fun onRotate(value: Float) {
                val decay = knobToReverbDecay(value)
                EqualizerPreferences.setReverbDecay(decay)
                Log.d(TAG, "Reverb decay updated: ${"%.2f".format(decay)}")
            }

            override fun onLabel(value: Float): String {
                val decay = knobToReverbDecay(value)
                return when {
                    decay < 0.05f -> "Short"
                    decay > 0.95f -> "Long"
                    else -> "${"%.0f".format(decay * 100)}%"
                }
            }
        })

        // Size knob — room size.
        // Knob value 0-100 maps to size 0.0 (small room) ... 1.0 (large hall).
        binding.reverbScreen.reverbSizeKnob.setTickTexts("S", "L")
        binding.reverbScreen.reverbSizeKnob.divisionCount = 100
        binding.reverbScreen.reverbSizeKnob.setKnobPosition(
                reverbSizeToKnob(state.reverbSize), animate = false)
        binding.reverbScreen.reverbSizeKnob.setListener(object : RotaryKnobListener {
            override fun onIncrement(value: Float) {}

            override fun onRotate(value: Float) {
                val size = knobToReverbSize(value)
                EqualizerPreferences.setReverbSize(size)
                Log.d(TAG, "Reverb size updated: ${"%.2f".format(size)}")
            }

            override fun onLabel(value: Float): String {
                val size = knobToReverbSize(value)
                return when {
                    size < 0.05f -> "S"
                    size > 0.95f -> "L"
                    else -> "${"%.0f".format(size * 100)}%"
                }
            }
        })

        // Damp knob — high-frequency damping, independent of the Fade (decay) knob.
        // Knob value 0-100 maps to damp 0.0 (brightest tail) ... 1.0 (darkest tail).
        // Decoupled from decay so long-but-dark and short-but-bright rooms are both possible.
        binding.reverbScreen.reverbDampKnob.setTickTexts("B", "D")
        binding.reverbScreen.reverbDampKnob.divisionCount = 100
        binding.reverbScreen.reverbDampKnob.setKnobPosition(
                reverbDampToKnob(state.reverbDamp), animate = false)
        binding.reverbScreen.reverbDampKnob.setListener(object : RotaryKnobListener {
            override fun onIncrement(value: Float) {}

            override fun onRotate(value: Float) {
                val damp = knobToReverbDamp(value)
                EqualizerPreferences.setReverbDamp(damp)
                Log.d(TAG, "Reverb damp updated: ${"%.2f".format(damp)}")
            }

            override fun onLabel(value: Float): String {
                val damp = knobToReverbDamp(value)
                return when {
                    damp < 0.05f -> "Bright"
                    damp > 0.95f -> "Dark"
                    else -> "${"%.0f".format(damp * 100)}%"
                }
            }
        })
    }

    private fun setHeaderTitle(position: Int) {
        val title = when (position) {
            0 -> getString(R.string.equalizer)
            1 -> getString(R.string.output)
            2 -> getString(R.string.reverb)
            else -> ""
        }

        binding.eqTitle.setTextWithFade(title)
    }

    override val wantsMiniPlayerVisible: Boolean
        get() = false

    companion object {
        fun newInstance(): Equalizer {
            val args = Bundle()
            val fragment = Equalizer()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "Equalizer"

        /** Maps knob position [0..100] to pan [-1..1]. */
        fun knobValueToPan(knobValue: Float): Float = ((knobValue - 50f) / 50f).coerceIn(-1f, 1f)

        /** Maps pan [-1..1] to knob position [0..100]. */
        fun panToKnobValue(pan: Float): Float = ((pan * 50f) + 50f).coerceIn(0f, 100f)

        /** Maps knob position [0..100] to stereo width [0..2]. */
        fun knobValueToWidth(knobValue: Float): Float = (knobValue / 50f).coerceIn(0f, 2f)

        /** Maps stereo width [0..2] to knob position [0..100]. */
        fun widthToKnobValue(width: Float): Float = (width * 50f).coerceIn(0f, 100f)

        /** Maps knob position [0..100] to tape saturation drive [0..4]. */
        fun knobValueToDrive(knobValue: Float): Float = (knobValue / 100f * 4f).coerceIn(0f, 4f)

        /** Maps tape saturation drive [0..4] to knob position [0..100]. */
        fun driveToKnobValue(drive: Float): Float = (drive / 4f * 100f).coerceIn(0f, 100f)

        /** Maps knob position [0..100] to bass/treble gain [-12..+12] dB. */
        fun knobValueToBassDb(knobValue: Float): Float = ((knobValue - 50f) / 50f * 12f).coerceIn(-12f, 12f)

        /** Maps bass gain [-12..+12] dB to knob position [0..100]. */
        fun bassDbToKnobValue(db: Float): Float = ((db / 12f * 50f) + 50f).coerceIn(0f, 100f)

        /** Maps knob position [0..100] to treble gain [-12..+12] dB. */
        fun knobValueToTrebleDb(knobValue: Float): Float = ((knobValue - 50f) / 50f * 12f).coerceIn(-12f, 12f)

        /** Maps treble gain [-12..+12] dB to knob position [0..100]. */
        fun trebleDbToKnobValue(db: Float): Float = ((db / 12f * 50f) + 50f).coerceIn(0f, 100f)

        /** Maps knob position [0..100] to reverb wet/dry mix [0..1]. */
        fun knobToReverbMix(knobValue: Float): Float = (knobValue / 100f).coerceIn(0f, 1f)

        /** Maps reverb wet/dry mix [0..1] to knob position [0..100]. */
        fun reverbMixToKnob(mix: Float): Float = (mix * 100f).coerceIn(0f, 100f)

        /** Maps knob position [0..100] to reverb decay parameter [0..1]. */
        fun knobToReverbDecay(knobValue: Float): Float = (knobValue / 100f).coerceIn(0f, 1f)

        /** Maps reverb decay parameter [0..1] to knob position [0..100]. */
        fun reverbDecayToKnob(decay: Float): Float = (decay * 100f).coerceIn(0f, 100f)

        /** Maps knob position [0..100] to reverb room-size parameter [0..1]. */
        fun knobToReverbSize(knobValue: Float): Float = (knobValue / 100f).coerceIn(0f, 1f)

        /** Maps reverb room-size parameter [0..1] to knob position [0..100]. */
        fun reverbSizeToKnob(size: Float): Float = (size * 100f).coerceIn(0f, 100f)

        /** Maps knob position [0..100] to reverb high-frequency damping [0..1]. */
        fun knobToReverbDamp(knobValue: Float): Float = (knobValue / 100f).coerceIn(0f, 1f)

        /** Maps reverb high-frequency damping [0..1] to knob position [0..100]. */
        fun reverbDampToKnob(damp: Float): Float = (damp * 100f).coerceIn(0f, 100f)

        private const val SCREEN_STATE_KEY = "screen_state"
    }
}