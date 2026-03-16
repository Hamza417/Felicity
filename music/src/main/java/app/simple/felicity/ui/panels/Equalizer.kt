package app.simple.felicity.ui.panels

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.R
import app.simple.felicity.databinding.FragmentEqualizerBinding
import app.simple.felicity.decorations.knobs.RotaryKnobListener
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.preferences.EqualizerPreferences

/**
 * Fragment that presents all equalizer controls: balance, stereo widening, tape saturation,
 * karaoke mode, and night mode. Each control persists its state via [EqualizerPreferences]
 * which is observed by the player service for immediate processor updates.
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

        // Balance knob (constant-power panning).
        // Knob value 0-100 maps to pan -1 (full left) … 0 (center, default) … +1 (full right).
        // Center (50) = no change. Center-snap enabled so the knob snaps back to 50 when
        // released close to it, and a divider line shows which side it's leaning toward.
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
        // Center (50) = width 1.0 = natural stereo passthrough (no processing).
        // Center-snap ensures the knob easily returns to the neutral position.
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
        // No center-snap — this is a one-directional effect; more = more saturation.
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

        /**
         * Maps knob position [0..100] → pan [-1..1].
         * 50 = center = 0, 0 = full left = -1, 100 = full right = +1.
         */
        fun knobValueToPan(knobValue: Float): Float = ((knobValue - 50f) / 50f).coerceIn(-1f, 1f)

        /**
         * Maps pan [-1..1] → knob position [0..100].
         * 0 (center) → 50, -1 (full left) → 0, +1 (full right) → 100.
         */
        fun panToKnobValue(pan: Float): Float = ((pan * 50f) + 50f).coerceIn(0f, 100f)

        /**
         * Maps knob position [0..100] → stereo width [0..2].
         * 0 = mono (0.0), 50 = natural stereo (1.0), 100 = max wide (2.0).
         */
        fun knobValueToWidth(knobValue: Float): Float = (knobValue / 50f).coerceIn(0f, 2f)

        /**
         * Maps stereo width [0..2] → knob position [0..100].
         * 0.0 (mono) → 0, 1.0 (natural) → 50, 2.0 (max wide) → 100.
         */
        fun widthToKnobValue(width: Float): Float = (width * 50f).coerceIn(0f, 100f)

        /**
         * Maps knob position [0..100] → tape saturation drive [0..4].
         * 0 = off (0.0), 100 = maximum drive (4.0).
         */
        fun knobValueToDrive(knobValue: Float): Float = (knobValue / 100f * 4f).coerceIn(0f, 4f)

        /**
         * Maps tape saturation drive [0..4] → knob position [0..100].
         * 0.0 (off) → 0, 4.0 (max drive) → 100.
         */
        fun driveToKnobValue(drive: Float): Float = (drive / 4f * 100f).coerceIn(0f, 100f)
    }
}