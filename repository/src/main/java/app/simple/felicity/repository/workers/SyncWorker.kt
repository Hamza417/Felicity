package app.simple.felicity.repository.workers

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.simple.felicity.core.tools.MovingAverage
import app.simple.felicity.core.utils.NumberUtils
import app.simple.felicity.core.utils.ProcessUtils.mainThread
import app.simple.felicity.core.utils.SDCard
import app.simple.felicity.repository.R
import app.simple.felicity.repository.SynchronizerService
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.loaders.JAudioMetadataLoader
import app.simple.felicity.repository.loaders.MediaMetadataLoader
import app.simple.felicity.repository.models.normal.Audio
import app.simple.felicity.repository.utils.LoaderUtils.isAudioFile
import app.simple.felicity.shared.constants.ServiceConstants
import app.simple.felicity.shared.utils.ServiceUtils.createNotificationAction
import app.simple.felicity.shared.utils.ServiceUtils.createNotificationChannel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jaudiotagger.audio.exceptions.CannotReadException
import java.io.File
import kotlin.system.measureTimeMillis

class SyncWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val semaphore = Semaphore(SEMAPHORE_PERMITS)
    private val filesLoaderJobs = mutableListOf<Job>()
    private val timeRemaining = MutableStateFlow(Pair(0L, ""))
    private val averageTime = MovingAverage(100)

    private val notificationManager: NotificationManager by lazy {
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val notificationBuilder: NotificationCompat.Builder by lazy {
        NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
    }

    private lateinit var notification: Notification

    override suspend fun doWork(): Result {
        try {
            loadData()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure()
        }

        postSyncCompletedNotification()
        return Result.success()
    }

    private suspend fun loadData() = coroutineScope {
        val paths = arrayListOf(Environment.getExternalStorageDirectory(), SDCard.findSdCardPath(applicationContext))
        val audioDatabase = AudioDatabase.getInstance(applicationContext)

        mainThread {
            createNotification()
        }

        ensureActive()

        val files = paths.flatMap {
            it?.walkTopDown()?.filter { file ->
                file.isFile && file.isAudioFile()
            }?.toList() ?: listOf()
        }
        val fileCount = files.size
        var count = 0
        val startTime = System.currentTimeMillis()
        postProgressNotification(fileCount, count)

        ensureActive()

        val deferredResults = files.map { file ->
            async {
                ensureActive()

                semaphore.withPermit {
                    ensureActive()
                    count++
                    val remaining = fileCount - count
                    val processingTime = measureTimeMillis {
                        processFile(file, audioDatabase)
                    }

                    ensureActive()

                    synchronized(timeRemaining) {
                        synchronized(averageTime) {
                            timeRemaining.value = Pair((averageTime.next(processingTime) * remaining).toLong(), "$count/$fileCount")
                            postProgressNotification(fileCount, count)
                        }
                    }
                }
            }
        }

        deferredResults.awaitAll()

        Log.d(TAG, "loadData: Time taken: ${NumberUtils.getFormattedTime(System.currentTimeMillis() - startTime)} s")
    }

    private fun postProgressNotification(max: Int, progress: Int) {
        notificationBuilder.setProgress(max, progress, false)
        notificationBuilder.setContentText("$progress/$max")
        if (ActivityCompat.checkSelfPermission(
                        applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
        } else {
            throw IllegalStateException("Permission denied")
        }
    }

    private suspend fun processFile(file: File, audioDatabase: AudioDatabase?) = coroutineScope {
        try {
            ensureActive()
            val audio = Audio()
            val retriever = JAudioMetadataLoader(file)
            retriever.setAudioMetadata(audio)
            audioDatabase?.audioDao()?.insert(audio)
            Log.d(TAG, "Successfully read file using JAudioMetadataLoader: ${file.absolutePath}")
        } catch (e: CannotReadException) {
            e.printStackTrace()
            Log.d(TAG, "loadData: Cannot read file: ${file.absolutePath}")
            ensureActive()

            val audio = Audio()
            val retriever = MediaMetadataLoader(file)
            retriever.setAudioMetadata(audio)

            audioDatabase?.audioDao()?.insert(audio)
            Log.d(TAG, "Successfully read file using MediaMetadataLoader: ${file.absolutePath}")
        }
    }

    private fun createNotification() {
        applicationContext.createNotificationChannel(NOTIFICATION_CHANNEL_ID, applicationContext.getString(R.string.sync))

        notificationBuilder.setContentTitle(applicationContext.getString(R.string.scanning))
            .setSmallIcon(R.drawable.ic_sync)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setShowWhen(true)
            .setSilent(true)
            .setOngoing(true)
            .addAction(applicationContext.createNotificationAction(
                    app.simple.felicity.shared.R.drawable.ic_cancel,
                    applicationContext.getString(R.string.close),
                    ServiceConstants.ACTION_CANCEL,
                    SynchronizerService::class.java))
            .setProgress(100, 0, false)

        notification = notificationBuilder.build()
        notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT

        if (ActivityCompat.checkSelfPermission(
                        applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun postSyncCompletedNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
        notificationBuilder.setContentTitle(applicationContext.getString(R.string.sync_completed))
            .setSmallIcon(R.drawable.ic_sync)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setShowWhen(true)
            .setSilent(true)
            .setOngoing(false)
            .setProgress(0, 0, false)
            .setAutoCancel(true)

        notification = notificationBuilder.build()
        notification.flags = notification.flags and Notification.FLAG_ONGOING_EVENT.inv()

        if (ActivityCompat.checkSelfPermission(
                        applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun getTimeRemaining(): StateFlow<Pair<Long, String>> = timeRemaining

    companion object {
        const val TAG = "SyncWorker"
        private const val SEMAPHORE_PERMITS = 25
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "synchronizer_channel"
    }
}