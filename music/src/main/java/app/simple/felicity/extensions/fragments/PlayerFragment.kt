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
import app.simple.felicity.helpers.IntentHelper
import kotlinx.coroutines.launch

abstract class PlayerFragment : ScopedFragment() {

    private var serviceConnection: ServiceConnection? = null
    private var audioBroadcastReceiver: BroadcastReceiver? = null
    private val audioIntentFilter = IntentFilter()

    protected var currentSeekPosition = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        audioIntentFilter.addAction(app.simple.felicity.shared.constants.ServiceConstants.actionPrepared)
        audioIntentFilter.addAction(app.simple.felicity.shared.constants.ServiceConstants.actionQuitMusicService)
        audioIntentFilter.addAction(app.simple.felicity.shared.constants.ServiceConstants.actionMetaData)
        audioIntentFilter.addAction(app.simple.felicity.shared.constants.ServiceConstants.actionPause)
        audioIntentFilter.addAction(app.simple.felicity.shared.constants.ServiceConstants.actionPlay)
        audioIntentFilter.addAction(app.simple.felicity.shared.constants.ServiceConstants.actionBuffering)
        audioIntentFilter.addAction(app.simple.felicity.shared.constants.ServiceConstants.actionNext)
        audioIntentFilter.addAction(app.simple.felicity.shared.constants.ServiceConstants.actionPrevious)
        audioIntentFilter.addAction(app.simple.felicity.shared.constants.ServiceConstants.actionMediaError)

        audioBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    app.simple.felicity.shared.constants.ServiceConstants.actionPrepared -> {
                        onPrepared()
                    }

                    app.simple.felicity.shared.constants.ServiceConstants.actionMetaData -> {
                        onMetaData()
                        handler.post(progressRunnable)
                    }

                    app.simple.felicity.shared.constants.ServiceConstants.actionQuitMusicService -> {
                        onQuitMusicService()
                    }

                    app.simple.felicity.shared.constants.ServiceConstants.actionPlay -> {
                        onStateChanged(true)
                    }

                    app.simple.felicity.shared.constants.ServiceConstants.actionPause -> {
                        onStateChanged(false)
                    }

                    app.simple.felicity.shared.constants.ServiceConstants.actionNext -> {
                        onNext()
                    }

                    app.simple.felicity.shared.constants.ServiceConstants.actionPrevious -> {
                        onPrevious()
                    }

                    app.simple.felicity.shared.constants.ServiceConstants.actionBuffering -> {
                        onBuffering(intent.extras?.getInt(IntentHelper.INT_EXTRA)!!)
                    }

                    app.simple.felicity.shared.constants.ServiceConstants.actionMediaError -> {
                        onMediaError(intent.extras?.getString("stringExtra", "unknown_media_playback_error")!!)
                    }
                }
            }
        }

        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {

                onServiceConnected()
            }

            override fun onServiceDisconnected(name: ComponentName?) {

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

    }

    protected fun stopService() {
        kotlin.runCatching {
            goBack()
        }
    }

    protected val progressRunnable: Runnable = object : Runnable {
        override fun run() {

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
