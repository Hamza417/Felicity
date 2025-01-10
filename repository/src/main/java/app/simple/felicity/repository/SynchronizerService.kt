package app.simple.felicity.repository

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import app.simple.felicity.core.logger.Debug.logDebug
import app.simple.felicity.core.tools.MovingAverage
import app.simple.felicity.core.utils.NumberUtils
import app.simple.felicity.core.utils.ProcessUtils.mainThread
import app.simple.felicity.core.utils.SDCard
import app.simple.felicity.preferences.SharedPreferences
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.loaders.JAudioMetadataLoader
import app.simple.felicity.repository.loaders.MediaMetadataLoader
import app.simple.felicity.repository.models.normal.Audio
import app.simple.felicity.repository.utils.LoaderUtils.isAudioFile
import app.simple.felicity.shared.constants.ServiceConstants
import app.simple.felicity.shared.utils.ServiceUtils.createNotificationAction
import app.simple.felicity.shared.utils.ServiceUtils.createNotificationChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jaudiotagger.audio.exceptions.CannotReadException
import java.io.File
import kotlin.system.measureTimeMillis

class SynchronizerService : Service() {

    private val semaphore = Semaphore(SEMAPHORE_PERMITS)
    private val filesLoaderJobs = mutableSetOf<Job>()
    private val averageTime = MovingAverage(100)

    private val timeRemaining = MutableStateFlow(Pair(0L, ""))
    private val isCompleted = MutableStateFlow(false)
    private val currentFileName = MutableStateFlow("")

    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var notification: Notification

    inner class SynchronizerBinder : Binder() {
        fun getService(): SynchronizerService {
            return this@SynchronizerService
        }

        fun getTimeRemaining(): StateFlow<Pair<Long, String>> {
            return timeRemaining
        }

        fun isCompleted(): StateFlow<Boolean> {
            return isCompleted
        }

        fun getCurrentFileName(): StateFlow<String> {
            return currentFileName
        }
    }

    override fun onBind(p0: Intent?): IBinder {
        return SynchronizerBinder()
    }

    override fun onCreate() {
        super.onCreate()
        SharedPreferences.init(applicationContext)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationBuilder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
        initDataLoader()
    }

    fun initDataLoader() {
        logDebug("initDataLoader")
        loadData()
    }

    private fun loadData() {
        val job = CoroutineScope(Dispatchers.IO).launch {
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
                            currentFileName.value = file.name
                        }

                        ensureActive()

                        synchronized(timeRemaining) {
                            synchronized(averageTime) {
                                timeRemaining.value =
                                    Pair((averageTime.next(processingTime) * remaining).toLong(), "$count/$fileCount")
                                postProgressNotification(fileCount, count)
                            }
                        }
                    }
                }
            }

            deferredResults.awaitAll()

            logDebug("loadData: Time taken: " +
                    "${NumberUtils.getFormattedTime(System.currentTimeMillis() - startTime)} for $fileCount files")

            postSyncCompletedNotification()
        }

        filesLoaderJobs.add(job)
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
        isCompleted.value = true
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
                        applicationContext, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return
        }

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        filesLoaderJobs.forEach { it.cancel() }.also {
            filesLoaderJobs.clear()
        }
    }

    companion object {
        const val TAG = "SynchronizerService"

        private const val SEMAPHORE_PERMITS = 25
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "synchronizer_channel"

        fun getSyncServiceIntent(context: Context): Intent {
            return Intent(context, SynchronizerService::class.java)
        }
    }
}
