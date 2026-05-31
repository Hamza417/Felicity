package app.simple.felicity.dialogs.app

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import app.simple.felicity.databinding.DialogVolumeKnobBinding
import app.simple.felicity.decorations.knobs.FelicityKnobListener
import app.simple.felicity.dialogs.app.VolumeKnob.Companion.VOLUME_REPEAT_DELAY_MS
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
     * Using [MutableStateFlow] instead of launching a coroutine per [FelicityKnobListener.onRotate]
     * call prevents flooding the system with redundant [AudioManager.setStreamVolume] requests:
     * the collector only processes the latest distinct value, and the main-thread update is a
     * simple field assignment with no allocation or scheduling overhead.
     */
    private val volumeFlow = MutableStateFlow(-1)

    private var volumeListener: VolumeListener? = null

    private var isEventConsumed = false

    /**
     * When the user holds down a volume button, this runnable keeps firing at a fixed interval,
     * nudging the volume up or down one step at a time so the change feels gradual rather than
     * jumping straight to the maximum or minimum.
     *
     * It reschedules itself every [VOLUME_REPEAT_DELAY_MS] milliseconds until the key is released.
     */
    private var volumeRepeatRunnable: Runnable? = null
    private var closeRunnable: Runnable? = null

    private val volumeRepeatHandler = Handler(Looper.getMainLooper())
    private val closeHandler = Handler(Looper.getMainLooper())

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

        startCloseRunnable()

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
        binding.volumeKnob.setListener(object : FelicityKnobListener {
            override fun onIncrement(value: Float) {

            }

            override fun onRotate(value: Float) {
                // Map the 0..100 knob value to a stream index and emit — the collector deduplicates
                // and serializes the actual AudioManager calls on a background thread.
                volumeFlow.value = ((value / 100.0f) * maxVolume).roundToInt()
            }

            override fun onUserInteractionStart(value: Float) {
                requireContext().contentResolver.unregisterContentObserver(volumeObserver)
            }

            override fun onUserInteractionEnd(value: Float) {
                postDelayed(delayMillis = 1000) {
                    requireContext().contentResolver.registerContentObserver(
                            Settings.System.CONTENT_URI, true, volumeObserver)
                }
            }
        })

        binding.equalizer.setOnClickListener {
            volumeListener?.onEqualizerClicked().also {
                dismiss()
            }
        }

        // Hardware volume keys
        dialog?.setOnKeyListener { _, keyCode, event ->
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    when (event.action) {
                        KeyEvent.ACTION_DOWN -> {
                            stopCloseRunnable()

                            /**
                             * Only kick off the repeating runnable on the very first down event
                             * (repeatCount == 0). Subsequent hardware repeats are ignored here
                             * because our own handler takes over the pacing from that point on.
                             */
                            if (event.repeatCount == 0 && isEventConsumed.not()) {
                                isEventConsumed = true
                                startVolumeRepeat(raising = true)
                            }
                        }
                        KeyEvent.ACTION_UP -> {
                            isEventConsumed = false
                            stopVolumeRepeat()
                            startCloseRunnable()
                        }
                    }

                    true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    when (event.action) {
                        KeyEvent.ACTION_DOWN -> {
                            stopCloseRunnable()

                            /**
                             * Only kick off the repeating runnable on the very first down event
                             * (repeatCount == 0). Subsequent hardware repeats are ignored here
                             * because our own handler takes over the pacing from that point on.
                             */
                            if (event.repeatCount == 0 && isEventConsumed.not()) {
                                isEventConsumed = true
                                startVolumeRepeat(raising = false)
                            }
                        }
                        KeyEvent.ACTION_UP -> {
                            isEventConsumed = false
                            stopVolumeRepeat()
                            startCloseRunnable()
                        }
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

    /**
     * Fires the appropriate step function immediately, then re-posts itself every
     * [VOLUME_REPEAT_DELAY_MS] ms so the volume climbs (or falls) one step at a time
     * for as long as the hardware button stays pressed.
     */
    private fun startVolumeRepeat(raising: Boolean) {
        stopVolumeRepeat()
        volumeRepeatRunnable = object : Runnable {
            override fun run() {
                if (raising) raiseByFivePercent() else lowerByFivePercent()
                volumeRepeatHandler.postDelayed(this, VOLUME_REPEAT_DELAY_MS)
            }
        }
        volumeRepeatRunnable?.run()
    }

    private fun startCloseRunnable() {
        stopCloseRunnable()
        closeRunnable = Runnable { dismiss() }
        closeHandler.postDelayed(closeRunnable!!, VOLUME_CLOSE_DELAY_MS)
    }

    private fun stopCloseRunnable() {
        closeRunnable?.let { closeHandler.removeCallbacks(it) }
        closeRunnable = null
    }

    private fun stopVolumeRepeat() {
        volumeRepeatRunnable?.let { volumeRepeatHandler.removeCallbacks(it) }
        volumeRepeatRunnable = null
    }

    private fun raiseByFivePercent() {
        withUnregisteredVolumeObserver {
            val currentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 0f
            val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 1f
            val newVolume = (currentVolume + 0.05f * maxVolume).coerceAtMost(maxVolume)
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume.roundToInt(), 0)
            setVolumeKnobPosition()
        }
    }

    private fun lowerByFivePercent() {
        withUnregisteredVolumeObserver {
            val currentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 0f
            val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 1f
            val newVolume = (currentVolume - 0.05f * maxVolume).coerceAtLeast(0f)
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume.roundToInt(), 0)
            setVolumeKnobPosition()
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
        stopVolumeRepeat()
        stopCloseRunnable()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    fun setVolumeListener(listener: VolumeListener) {
        this.volumeListener = listener
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

        interface VolumeListener {

            fun onEqualizerClicked()
        }

        const val TAG = "VolumeKnob"

        /** How long to wait between each automatic volume step while the button is held down. */
        private const val VOLUME_REPEAT_DELAY_MS = 250L
        private const val VOLUME_CLOSE_DELAY_MS = 3000L
    }
}
