package app.simple.felicity.repository

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

class SynchronizerService : Service() {

    private val semaphore = Semaphore(1)
    private val filesLoaderJobs = mutableListOf<Job>()

    inner class SynchronizerBinder : Binder() {
        fun getService(): SynchronizerService {
            return this@SynchronizerService
        }
    }

    override fun onBind(p0: Intent?): IBinder {
        return SynchronizerBinder()
    }

    override fun onCreate() {
        super.onCreate()

    }

    private fun loadFiles() {
        val job = CoroutineScope(Dispatchers.IO).launch {
            
        }
    }
}
