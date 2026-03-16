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

        binding.back.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        setupEqualizerSliders()
        setupKnobs()
    }

    // -------------------------------------------------------------------------
    // 10-band EQ sliders
    // -------------------------------------------------------------------------

    private fun setupEqualizerSliders() {
        // Restore persisted band gains immediately — no animation so UI is ready before
        // the user sees it.
        binding.equalizerSliders.setAllGains(EqualizerPreferences.getAllBandGains(), animate = false)
        binding.equalizerSliders.setPreampGain(EqualizerPreferences.getPreampDb(), animate = false)

        // Forward every user drag to EqualizerManager which persists the value and
        // applies it to the hardware Equalizer in real-time.
        binding.equalizerSliders.setOnBandChangedListener { bandIndex, gain, fromUser ->
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
                    binding.equalizerSliders.setAllGains(gains, animate = true)
                }
            }
        }

        // Observe preamp flow independently so a future preset loader or reset
        // that changes only the preamp is reflected without a full gains update.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                EqualizerManager.preampFlow.collect { db ->
                    binding.equalizerSliders.setPreampGain(db, animate = true)
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Rotary knobs (balance, stereo widening, tape saturation)
    // -------------------------------------------------------------------------

    private fun setupKnobs() {
        // Balance knob (constant-power panning).
        // Knob value 0-100 maps to pan -1 (full left) … 0 (center) … +1 (full right).
        binding.balanceKnob.centerSnapEnabled = true
        binding.balanceKnob.setTickTexts("L", "R")
        binding.balanceKnob.setKnobPosition(panToKnobValue(EqualizerPreferences.getBalance()), animate = false)
        binding.balanceKnob.setListener(object : RotaryKnobListener {
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
        binding.stereoWideningKnob.centerSnapEnabled = true
        binding.stereoWideningKnob.setTickTexts("M", "W")
        binding.stereoWideningKnob.setKnobPosition(widthToKnobValue(EqualizerPreferences.getStereoWidth()), animate = false)
        binding.stereoWideningKnob.divisionCount = 10 * 10
        binding.stereoWideningKnob.setListener(object : RotaryKnobListener {
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
        binding.tapeSaturationKnob.setTickTexts("0", "4")
        binding.tapeSaturationKnob.setKnobPosition(driveToKnobValue(EqualizerPreferences.getTapeSaturationDrive()), animate = false)
        binding.tapeSaturationKnob.divisionCount = 4 * 10
        binding.tapeSaturationKnob.setListener(object : RotaryKnobListener {
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
    }
}