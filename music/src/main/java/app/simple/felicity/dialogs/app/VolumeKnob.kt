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
import kotlin.math.roundToInt

class VolumeKnob : ScopedBottomSheetFragment() {

    private var audioManager: AudioManager? = null
    private lateinit var binding: DialogVolumeKnobBinding

    private val volumeObserver by lazy {
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                setKnobPosition()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogVolumeKnobBinding.inflate(inflater, container, false)

        requireActivity().volumeControlStream = AudioManager.STREAM_MUSIC
        audioManager = requireActivity().getSystemService(Context.AUDIO_SERVICE) as AudioManager?

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setKnobPosition()
        binding.volumeKnob.setTickTexts("0", "100")

        binding.volumeKnob.setListener(object : RotaryKnobListener {
            override fun onIncrement(value: Float) {
                Log.d(TAG, "Increment: $value")
            }

            override fun onRotate(value: Float) {
                val index = ((value / 100.0f) * audioManager!!.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).roundToInt()
                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0);
            }
        })

        dialog?.setOnKeyListener { _, keyCode, _ ->
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    Log.d(TAG, "Volume key pressed: $keyCode")
                    audioManager?.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_RAISE,
                            AudioManager.FLAG_VIBRATE);
                    setKnobPosition()
                    true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    Log.d(TAG, "Volume key pressed: $keyCode")
                    audioManager?.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_LOWER,
                            AudioManager.FLAG_VIBRATE);
                    setKnobPosition()
                    true
                }
                else -> false
            }
        }
    }

    override fun onStart() {
        super.onStart()
        requireContext().contentResolver
            .registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver)
    }

    override fun onStop() {
        super.onStop()
        requireContext().contentResolver.unregisterContentObserver(volumeObserver)
    }

    private fun setKnobPosition() {
        val current = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 0f
        val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 1f
        Log.d(TAG, "setKnobPosition: current=$current, max=$max (percentage=${(current / max) * 100f})")
        binding.volumeKnob.setKnobPosition((current / max) * 100f)
    }

    companion object {
        fun newInstance(): VolumeKnob {
            val args = Bundle()
            val fragment = VolumeKnob()
            fragment.arguments = args
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

        private fun FragmentManager.isVolumeKnobShowing(): Boolean {
            return findFragmentByTag(TAG) != null
        }

        const val TAG = "VolumeKnob"
    }
}
