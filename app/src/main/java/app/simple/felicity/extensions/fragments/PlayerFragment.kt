package app.simple.felicity.extensions.fragments

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import app.simple.felicity.constants.ServiceConstants
import app.simple.felicity.helpers.IntentHelper
import app.simple.felicity.models.Audio
import app.simple.felicity.preferences.MusicPreferences
import app.simple.felicity.services.AudioService
import kotlinx.coroutines.launch

abstract class PlayerFragment : ScopedFragment() {

    protected var audioService: AudioService? = null
    protected var serviceConnection: ServiceConnection? = null
    private var audioBroadcastReceiver: BroadcastReceiver? = null
    private val audioIntentFilter = IntentFilter()
    protected var audios = ArrayList<Audio>()

    protected var currentSeekPosition = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        audioIntentFilter.addAction(ServiceConstants.actionPrepared)
        audioIntentFilter.addAction(ServiceConstants.actionQuitMusicService)
        audioIntentFilter.addAction(ServiceConstants.actionMetaData)
        audioIntentFilter.addAction(ServiceConstants.actionPause)
        audioIntentFilter.addAction(ServiceConstants.actionPlay)
        audioIntentFilter.addAction(ServiceConstants.actionBuffering)
        audioIntentFilter.addAction(ServiceConstants.actionNext)
        audioIntentFilter.addAction(ServiceConstants.actionPrevious)
        audioIntentFilter.addAction(ServiceConstants.actionMediaError)

        audioBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    ServiceConstants.actionPrepared -> {
                        onPrepared()
                    }
                    ServiceConstants.actionMetaData -> {
                        onMetaData()
                        handler.post(progressRunnable)
                    }
                    ServiceConstants.actionQuitMusicService -> {
                        onQuitMusicService()
                    }
                    ServiceConstants.actionPlay -> {
                        onStateChanged(true)
                    }
                    ServiceConstants.actionPause -> {
                        onStateChanged(false)
                    }
                    ServiceConstants.actionNext -> {
                        onNext()
                    }
                    ServiceConstants.actionPrevious -> {
                        onPrevious()
                    }
                    ServiceConstants.actionBuffering -> {
                        onBuffering(intent.extras?.getInt(IntentHelper.INT_EXTRA)!!)
                    }
                    ServiceConstants.actionMediaError -> {
                        onMediaError(intent.extras?.getString("stringExtra", "unknown_media_playback_error")!!)
                    }
                }
            }
        }

        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                audioService = (service as AudioService.AudioBinder).getService()
                audioService?.setCurrentPosition(MusicPreferences.getMusicPosition())
                onServiceConnected()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                audioService = null
                onServiceDisconnected()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                startService()
            }
        }
    }

    private fun startService() {
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(audioBroadcastReceiver!!) // Just to be safe
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(audioBroadcastReceiver!!, audioIntentFilter)
        val intent = AudioService.getIntent(requireActivity()) // Activity context will keep the foreground service alive
        requireContext().startService(intent)
        serviceConnection?.let { requireContext().bindService(intent, it, Context.BIND_AUTO_CREATE) }
    }

    protected fun stopService() {
        kotlin.runCatching {
            requireContext().unbindService(serviceConnection!!)
            requireContext().stopService(AudioService.getIntent(requireActivity().applicationContext))
            goBack()
        }
    }

    protected val progressRunnable: Runnable = object : Runnable {
        override fun run() {
            if (audioService != null) {
                val progress = audioService!!.getProgress()
                val duration = audioService!!.getDuration()
                onProgress(progress, duration)
            }

            handler.postDelayed(this, 1000L)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(progressRunnable)

        try {
            requireContext().unbindService(serviceConnection!!)
        } catch (_: IllegalArgumentException) {
        } catch (_: IllegalStateException) {
        }

        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(audioBroadcastReceiver!!)
    }

    abstract fun onServiceConnected()
    abstract fun onServiceDisconnected()
    abstract fun onPrepared()
    abstract fun onMetaData()
    abstract fun onQuitMusicService()
    abstract fun onStateChanged(isPlaying: Boolean)
    abstract fun onNext()
    abstract fun onPrevious()
    abstract fun onBuffering(progress: Int)
    abstract fun onMediaError(error: String)
    abstract fun onProgress(progress: Int, duration: Int)
}