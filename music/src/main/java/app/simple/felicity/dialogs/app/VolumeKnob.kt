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
import app.simple.felicity.databinding.DialogVolumeKnobBinding
import app.simple.felicity.decorations.knobs.simple.RotaryKnobListener
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.preferences.PlayerPreferences
import kotlin.math.roundToInt

class VolumeKnob : ScopedBottomSheetFragment() {

    private var audioManager: AudioManager? = null
    private lateinit var binding: DialogVolumeKnobBinding

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

        // ── Volume knob ──────────────────────────────────────────────────────────
        setVolumeKnobPosition()
        binding.volumeKnob.setTickTexts("0", "100")
        binding.volumeKnob.setListener(object : RotaryKnobListener {
            override fun onIncrement(value: Float) {
                Log.d(TAG, "Increment: $value")
            }

            override fun onRotate(value: Float) {
                val index = ((value / 100.0f) * audioManager!!.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).roundToInt()
                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
            }

            override fun onUserInteractionStart() {
                requireContext().contentResolver.unregisterContentObserver(volumeObserver)
            }

            override fun onUserInteractionEnd() {
                requireContext().contentResolver
                    .registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver)
            }
        })

        // ── Balance knob (constant-power panning) ───────────────────────────────
        // Knob value 0-100 maps to pan -1 (full left) … 0 (centre, default) … +1 (full right).
        // Centre (50) = no change. Centre-snap enabled so the knob snaps back to 50 when
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

        // ── Hardware volume keys ─────────────────────────────────────────────────
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
         * 50 = centre = 0, 0 = full left = -1, 100 = full right = +1.
         */
        fun knobValueToPan(knobValue: Float): Float = ((knobValue - 50f) / 50f).coerceIn(-1f, 1f)

        /**
         * Maps pan [-1..1] → knob position [0..100].
         * 0 (centre) → 50, -1 (full left) → 0, +1 (full right) → 100.
         */
        fun panToKnobValue(pan: Float): Float = ((pan * 50f) + 50f).coerceIn(0f, 100f)

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
