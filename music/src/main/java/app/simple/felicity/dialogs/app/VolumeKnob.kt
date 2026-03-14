package app.simple.felicity.dialogs.app

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import app.simple.felicity.R
import app.simple.felicity.databinding.DialogVolumeKnobBinding
import app.simple.felicity.decorations.knobs.RotaryKnobListener
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.preferences.PlayerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class VolumeKnob : ScopedBottomSheetFragment() {

    private var audioManager: AudioManager? = null
    private lateinit var binding: DialogVolumeKnobBinding

    /**
     * Holds the latest target volume index (0..maxVolume). A value of -1 is the
     * sentinel used before the first rotation event so the collector skips it.
     *
     * Using [MutableStateFlow] instead of launching a coroutine per [RotaryKnobListener.onRotate]
     * call prevents flooding the system with redundant [AudioManager.setStreamVolume] requests:
     * the collector only processes the latest distinct value, and the main-thread update is a
     * simple field assignment with no allocation or scheduling overhead.
     */
    private val volumeFlow = MutableStateFlow(-1)

    /** Cached stream maximum so it is not queried inside every rotation callback. */
    private val maxVolume: Int by lazy {
        audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
    }

    private val volumeObserver by lazy {
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                setVolumeKnobPosition()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogVolumeKnobBinding.inflate(inflater, container, false)

        requireActivity().volumeControlStream = AudioManager.STREAM_MUSIC
        audioManager = requireActivity().getSystemService(Context.AUDIO_SERVICE) as AudioManager?

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Single background collector — only the latest distinct index reaches the audio manager.
        // Launched on Dispatchers.IO because setStreamVolume is a synchronous binder (IPC) call
        // that must not block the main thread. Note: flowOn() only shifts upstream operators;
        // the collect block itself runs on whichever dispatcher the coroutine was launched on.
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            volumeFlow
                .filter { it >= 0 }
                .distinctUntilChanged()
                .collect { index ->
                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
                }
        }

        // Volume Knob
        setVolumeKnobPosition()
        binding.volumeKnob.setTickTexts("0", "100")
        binding.volumeKnob.setListener(object : RotaryKnobListener {
            override fun onIncrement(value: Float) {
                Log.d(TAG, "Increment: $value")
            }

            override fun onRotate(value: Float) {
                // Map the 0..100 knob value to a stream index and emit — the collector deduplicates
                // and serializes the actual AudioManager calls on a background thread.
                volumeFlow.value = ((value / 100.0f) * maxVolume).roundToInt()
            }

            override fun onUserInteractionStart() {
                requireContext().contentResolver.unregisterContentObserver(volumeObserver)
            }

            override fun onUserInteractionEnd() {
                requireContext().contentResolver
                    .registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver)
            }
        })

        // Balance knob (constant-power panning)
        // Knob value 0-100 maps to pan -1 (full left) … 0 (center, default) … +1 (full right).
        // Center (50) = no change. Center-snap enabled so the knob snaps back to 50 when
        // released close to it, and a divider line shows which side it's leaning toward.
        binding.balanceKnob.centerSnapEnabled = true
        binding.balanceKnob.setTickTexts("L", "R")
        binding.balanceKnob.setKnobPosition(panToKnobValue(PlayerPreferences.getBalance()), animate = false)
        binding.balanceKnob.setListener(object : RotaryKnobListener {
            override fun onIncrement(value: Float) {}

            override fun onRotate(value: Float) {
                val pan = knobValueToPan(value)
                PlayerPreferences.setBalance(pan)
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
        binding.stereoWideningKnob.setKnobPosition(widthToKnobValue(PlayerPreferences.getStereoWidth()), animate = false)
        binding.stereoWideningKnob.divisionCount = 10 * 10
        binding.stereoWideningKnob.setListener(object : RotaryKnobListener {
            override fun onIncrement(value: Float) {}

            override fun onRotate(value: Float) {
                val width = knobValueToWidth(value)
                PlayerPreferences.setStereoWidth(width)
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
        binding.tapeSaturationKnob.setKnobPosition(driveToKnobValue(PlayerPreferences.getTapeSaturationDrive()), animate = false)
        binding.tapeSaturationKnob.divisionCount = 4 * 10
        binding.tapeSaturationKnob.setListener(object : RotaryKnobListener {
            override fun onIncrement(value: Float) {}

            override fun onRotate(value: Float) {
                val drive = knobValueToDrive(value)
                PlayerPreferences.setTapeSaturationDrive(drive)
                Log.d(TAG, "Tape saturation drive updated: drive=$drive")
            }

            override fun onLabel(value: Float): String {
                val drive = knobValueToDrive(value)
                return if (drive < 0.05f) "Off" else "%.1f".format(drive)
            }
        })

        // Hardware volume keys
        dialog?.setOnKeyListener { _, keyCode, _ ->
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_VIBRATE)
                    setVolumeKnobPosition()
                    true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_VIBRATE)
                    setVolumeKnobPosition()
                    true
                }
                else -> false
            }
        }
    }

    override fun onStart() {
        super.onStart()
        requireContext().contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver)
    }

    override fun onStop() {
        super.onStop()
        requireContext().contentResolver.unregisterContentObserver(volumeObserver)
    }

    private fun setVolumeKnobPosition() {
        val current = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 0f
        val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 1f
        binding.volumeKnob.setKnobPosition((current / max) * 100f)
    }

    companion object {
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

        fun newInstance(): VolumeKnob {
            val fragment = VolumeKnob()
            fragment.arguments = Bundle()
            return fragment
        }

        fun AppCompatActivity.showVolumeKnob(): VolumeKnob {
            if (!supportFragmentManager.isVolumeKnobShowing()) {
                val dialog = newInstance()
                dialog.show(supportFragmentManager, TAG)
                return dialog
            }
            return supportFragmentManager.findFragmentByTag(TAG) as VolumeKnob
        }

        fun FragmentManager.showVolumeKnob(): VolumeKnob {
            if (!isVolumeKnobShowing()) {
                val dialog = newInstance()
                dialog.show(this, TAG)
                return dialog
            }
            return findFragmentByTag(TAG) as VolumeKnob
        }

        private fun FragmentManager.isVolumeKnobShowing(): Boolean = findFragmentByTag(TAG) != null

        const val TAG = "VolumeKnob"
    }
}
