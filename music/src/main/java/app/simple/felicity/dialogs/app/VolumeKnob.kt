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
import app.simple.felicity.databinding.DialogVolumeKnobBinding
import app.simple.felicity.decorations.knobs.RotaryKnobListener
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
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
                postDelayed(delayMillis = 1000) {
                    requireContext().contentResolver.registerContentObserver(
                            Settings.System.CONTENT_URI, true, volumeObserver)
                }
            }
        })

        binding.equalizer.setOnClickListener {

        }

        // Hardware volume keys
        dialog?.setOnKeyListener { _, keyCode, _ ->
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    withUnregisteredVolumeObserver {
                        audioManager?.adjustStreamVolume(
                                /* streamType = */ AudioManager.STREAM_MUSIC,
                                /* direction = */ AudioManager.ADJUST_RAISE,
                                /* flags = */ AudioManager.FLAG_VIBRATE)
                        setVolumeKnobPosition()
                    }
                    true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    withUnregisteredVolumeObserver {
                        audioManager?.adjustStreamVolume(
                                /* streamType = */ AudioManager.STREAM_MUSIC,
                                /* direction = */ AudioManager.ADJUST_LOWER,
                                /* flags = */ AudioManager.FLAG_VIBRATE)
                        setVolumeKnobPosition()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun withUnregisteredVolumeObserver(action: () -> Unit) {
        requireContext().contentResolver.unregisterContentObserver(volumeObserver)
        try {
            action()
        } finally {
            postDelayed(delayMillis = 1000) {
                requireContext().contentResolver.registerContentObserver(
                        Settings.System.CONTENT_URI, true, volumeObserver)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        requireContext().contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI, true, volumeObserver)
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

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {

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

        private fun FragmentManager.isVolumeKnobShowing(): Boolean = findFragmentByTag(TAG) != null

        const val TAG = "VolumeKnob"
    }
}
