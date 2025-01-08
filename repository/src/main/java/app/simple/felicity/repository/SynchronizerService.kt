package app.simple.felicity.repository

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
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
import app.simple.felicity.shared.R as SharedR

class SynchronizerService : Service() {

    private val semaphore = Semaphore(SEMAPHORE_PERMITS)
    private val filesLoaderJobs = mutableListOf<Job>()
    private val timeRemaining = MutableStateFlow(Pair(0L, ""))
    private val averageTime = MovingAverage(100)

    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var notification: Notification

    val timeRemainingFlow: StateFlow<Pair<Long, String>> = timeRemaining

    inner class SynchronizerBinder : Binder() {
        fun getService(): SynchronizerService {
            return this@SynchronizerService
        }

        fun getTimeRemaining(): StateFlow<Pair<Long, String>> {
            return timeRemainingFlow
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
            notificationBuilder.setProgress(fileCount, 0, false)
            var count = 0
            val startTime = System.currentTimeMillis()

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
                                notificationBuilder
                            }
                        }
                    }
                }
            }

            deferredResults.awaitAll()

            Log.d(TAG, "loadData: Time taken: ${NumberUtils.getFormattedTime(System.currentTimeMillis() - startTime)} s")
        }

        filesLoaderJobs.add(job)
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
        createNotificationChannel(NOTIFICATION_CHANNEL_ID, getString(R.string.sync))

        notificationBuilder.setContentTitle(getString(R.string.scanning))
            .setSmallIcon(R.drawable.ic_sync)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setShowWhen(true)
            .setSilent(true)
            .setOngoing(true)
            .addAction(applicationContext.createNotificationAction(
                    SharedR.drawable.ic_cancel,
                    getString(R.string.close),
                    ServiceConstants.ACTION_CANCEL,
                    SynchronizerService::class.java))
            .setProgress(100, 0, false)

        notification = notificationBuilder.build()
        notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT

        if (ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        notificationManager.notify(NOTIFICATION_ID, notification)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        filesLoaderJobs.forEach { it.cancel() }
    }

    companion object {
        private const val TAG = "SynchronizerService"

        private const val SEMAPHORE_PERMITS = 25
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "synchronizer_channel"
    }
}
