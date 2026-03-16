package app.simple.felicity.ui.panels

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.simple.felicity.R
import app.simple.felicity.databinding.FragmentEqualizerBinding
import app.simple.felicity.decorations.knobs.RotaryKnobListener
import app.simple.felicity.decorations.seekbars.FelicityEqualizerSliders
import app.simple.felicity.engine.managers.EqualizerManager
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.preferences.EqualizerPreferences
import kotlinx.coroutines.launch

/**
 * Fragment that presents all equalizer controls: the 10-band graphic EQ sliders, balance,
 * stereo widening, and tape saturation. Each control persists its state via
 * [EqualizerPreferences] which is observed by the player service for immediate processor
 * updates. The 10-band sliders also drive [EqualizerManager] directly so the hardware
 * [android.media.audiofx.Equalizer] effect is updated in real-time.
 *
 * Band gains are loaded from preferences on view creation and kept in sync with
 * [EqualizerManager.bandGainsFlow] so any external change (e.g., a future preset loader)
 * is reflected in the UI automatically.
 *
 * @author Hamza417
 */
class Equalizer : MediaFragment() {

    private lateinit var binding: FragmentEqualizerBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentEqualizerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireHiddenMiniPlayer()

        binding.equalizerScreen.equalizerSwitch.isChecked = EqualizerPreferences.isEqEnabled()
        updateEqualizerEnabledState(EqualizerPreferences.isEqEnabled(), false)

        binding.equalizerScreen.equalizerSwitch.setOnCheckedChangeListener { _, isChecked ->
            EqualizerPreferences.setEqEnabled(isChecked)
            updateEqualizerEnabledState(isChecked)
        }

        binding.equalizerScreen.reset.setOnClickListener {
            withSureDialog {
                EqualizerManager.resetAllBands()
                EqualizerPreferences.setPreampDb(0f)
            }
        }

        setupEqualizerSliders()
        setupKnobs()

        binding.viewFlipper.displayedChild = savedInstanceState?.getInt(SCREEN_STATE_KEY) ?: 0
        when (binding.viewFlipper.displayedChild) {
            0 -> binding.equalizer.isChecked = true
            1 -> binding.speaker.isChecked = true
        }

        binding.panelGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.equalizer -> binding.viewFlipper.displayedChild = 0
                    R.id.speaker -> binding.viewFlipper.displayedChild = 1
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // 10-band EQ sliders
    // -------------------------------------------------------------------------

    private fun setupEqualizerSliders() {
        // Restore persisted band gains immediately — no animation so UI is ready before
        // the user sees it.
        binding.equalizerScreen.equalizerSliders.setAllGains(EqualizerPreferences.getAllBandGains(), animate = false)
        binding.equalizerScreen.equalizerSliders.setPreampGain(EqualizerPreferences.getPreampDb(), animate = false)

        // Forward every user drag to EqualizerManager which persists the value and
        // applies it to the hardware Equalizer in real-time.
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

        // Observe band-gains flow so any externally driven change (preset load,
        // reset-all, etc.) is immediately reflected in the slider positions.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                EqualizerManager.bandGainsFlow.collect { gains ->
                    binding.equalizerScreen.equalizerSliders.setAllGains(gains, animate = true)
                }
            }
        }

        // Observe preamp flow independently so a future preset loader or reset
        // that changes only the preamp is reflected without a full gains update.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                EqualizerManager.preampFlow.collect { db ->
                    binding.equalizerScreen.equalizerSliders.setPreampGain(db, animate = true)
                }
            }
        }
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

    // -------------------------------------------------------------------------
    // Rotary knobs (balance, stereo widening, tape saturation)
    // -------------------------------------------------------------------------

    private fun setupKnobs() {
        // Bass knob (low-shelf at 250 Hz).
        // Knob value 0-100 maps to gain -12 dB (full cut) … 0 dB (center) … +12 dB (full boost).
        binding.equalizerScreen.bassKnob.centerSnapEnabled = true
        binding.equalizerScreen.bassKnob.setTickTexts("-12", "+12")
        binding.equalizerScreen.bassKnob.divisionCount = 24 * 2
        binding.equalizerScreen.bassKnob.setKnobPosition(bassDbToKnobValue(EqualizerPreferences.getBassDb()), animate = false)
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
        // Knob value 0-100 maps to gain -12 dB (full cut) … 0 dB (center) … +12 dB (full boost).
        binding.equalizerScreen.trebleKnob.centerSnapEnabled = true
        binding.equalizerScreen.trebleKnob.setTickTexts("-12", "+12")
        binding.equalizerScreen.trebleKnob.divisionCount = 24 * 2
        binding.equalizerScreen.trebleKnob.setKnobPosition(trebleDbToKnobValue(EqualizerPreferences.getTrebleDb()), animate = false)
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

        // Balance knob (constant-power panning).
        // Knob value 0-100 maps to pan -1 (full left) … 0 (center) … +1 (full right).
        binding.speakerScreen.balanceKnob.centerSnapEnabled = true
        binding.speakerScreen.balanceKnob.setTickTexts("L", "R")
        binding.speakerScreen.balanceKnob.setKnobPosition(panToKnobValue(EqualizerPreferences.getBalance()), animate = false)
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
        // Knob value 0-100 maps to width 0.0 (mono) … 1.0 (normal) … 2.0 (max wide).
        binding.speakerScreen.stereoWideningKnob.centerSnapEnabled = true
        binding.speakerScreen.stereoWideningKnob.setTickTexts("M", "W")
        binding.speakerScreen.stereoWideningKnob.setKnobPosition(widthToKnobValue(EqualizerPreferences.getStereoWidth()), animate = false)
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
        // Knob value 0-100 maps to drive 0.0 (clean/off) … 4.0 (maximum saturation).
        binding.speakerScreen.tapeSaturationKnob.setTickTexts("0", "4")
        binding.speakerScreen.tapeSaturationKnob.setKnobPosition(driveToKnobValue(EqualizerPreferences.getTapeSaturationDrive()), animate = false)
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

    companion object {
        fun newInstance(): Equalizer {
            val args = Bundle()
            val fragment = Equalizer()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "Equalizer"

        /** Maps knob position [0..100] → pan [-1..1]. */
        fun knobValueToPan(knobValue: Float): Float = ((knobValue - 50f) / 50f).coerceIn(-1f, 1f)

        /** Maps pan [-1..1] → knob position [0..100]. */
        fun panToKnobValue(pan: Float): Float = ((pan * 50f) + 50f).coerceIn(0f, 100f)

        /** Maps knob position [0..100] → stereo width [0..2]. */
        fun knobValueToWidth(knobValue: Float): Float = (knobValue / 50f).coerceIn(0f, 2f)

        /** Maps stereo width [0..2] → knob position [0..100]. */
        fun widthToKnobValue(width: Float): Float = (width * 50f).coerceIn(0f, 100f)

        /** Maps knob position [0..100] → tape saturation drive [0..4]. */
        fun knobValueToDrive(knobValue: Float): Float = (knobValue / 100f * 4f).coerceIn(0f, 4f)

        /** Maps tape saturation drive [0..4] → knob position [0..100]. */
        fun driveToKnobValue(drive: Float): Float = (drive / 4f * 100f).coerceIn(0f, 100f)

        /** Maps knob position [0..100] → bass/treble gain [-12..+12] dB. */
        fun knobValueToBassDb(knobValue: Float): Float = ((knobValue - 50f) / 50f * 12f).coerceIn(-12f, 12f)

        /** Maps bass gain [-12..+12] dB → knob position [0..100]. */
        fun bassDbToKnobValue(db: Float): Float = ((db / 12f * 50f) + 50f).coerceIn(0f, 100f)

        /** Maps knob position [0..100] → treble gain [-12..+12] dB. */
        fun knobValueToTrebleDb(knobValue: Float): Float = ((knobValue - 50f) / 50f * 12f).coerceIn(-12f, 12f)

        /** Maps treble gain [-12..+12] dB → knob position [0..100]. */
        fun trebleDbToKnobValue(db: Float): Float = ((db / 12f * 50f) + 50f).coerceIn(0f, 100f)

        private const val SCREEN_STATE_KEY = "screen_state"
    }
}