package app.simple.felicity.repository.factories.workers

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import app.simple.felicity.repository.workers.SyncWorker

class SyncWorkerFactory : WorkerFactory() {
    var syncWorker: SyncWorker? = null

    override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            SyncWorker::class.java.name -> {
                syncWorker = SyncWorker(appContext, workerParameters)
                syncWorker
            }
            else -> null
        }
    }
}