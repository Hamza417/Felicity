package app.simple.felicity.repository.services

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import app.simple.felicity.repository.loader.AudioDatabaseLoader

class AudioDatabaseService : Service() {

    override fun onCreate() {
        super.onCreate()
        AudioDatabaseLoader(this).processAudioFiles()
    }

    override fun onBind(intent: Intent?): IBinder {
        return AudioDatabaseBinder()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    inner class AudioDatabaseBinder : Binder() {
        fun getService(): AudioDatabaseService = this@AudioDatabaseService
    }
}