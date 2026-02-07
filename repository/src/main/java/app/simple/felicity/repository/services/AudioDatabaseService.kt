package app.simple.felicity.repository.services

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import app.simple.felicity.repository.loader.AudioDatabaseLoader
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AudioDatabaseService : Service() {

    @Inject
    lateinit var audioDatabaseLoader: AudioDatabaseLoader

    override fun onCreate() {
        super.onCreate()
        audioDatabaseLoader.processAudioFiles()
    }

    override fun onBind(intent: Intent?): IBinder {
        return AudioDatabaseBinder()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    fun refreshAudioFiles() {
        audioDatabaseLoader.processAudioFiles()
    }

    inner class AudioDatabaseBinder : Binder() {
        fun getService(): AudioDatabaseService = this@AudioDatabaseService
    }
}