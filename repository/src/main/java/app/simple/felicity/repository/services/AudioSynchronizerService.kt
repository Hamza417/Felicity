package app.simple.felicity.repository.services

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
import app.simple.felicity.core.tools.MovingAverage
import app.simple.felicity.core.utils.NumberUtils
import app.simple.felicity.core.utils.ProcessUtils.mainThread
import app.simple.felicity.core.utils.SDCard
import app.simple.felicity.preferences.SharedPreferences
import app.simple.felicity.repository.R
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.loaders.JAudioMetadataLoader
import app.simple.felicity.repository.loaders.MediaMetadataLoader
import app.simple.felicity.repository.models.normal.Audio
import app.simple.felicity.repository.repositories.AudioRepository
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
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.exceptions.CannotReadException
import java.io.File
import kotlin.system.measureTimeMillis

class AudioSynchronizerService : Service() {

    private val semaphore = Semaphore(SEMAPHORE_PERMITS)
    private val filesLoaderJobs = mutableSetOf<Job>()
    private val averageTime = MovingAverage(100)

    private val timeRemaining = MutableStateFlow(Pair(0L, ""))
    private val isCompleted = MutableStateFlow(false)
    private val currentFileName = MutableStateFlow("")

    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var notification: Notification
    private var audioRepository: AudioRepository? = null

    inner class SynchronizerBinder : Binder() {
        fun getService(): AudioSynchronizerService {
            return this@AudioSynchronizerService
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
        audioRepository = AudioRepository(AudioDatabase.getInstance(applicationContext)?.audioDao()!!)
        initDataLoader()
    }

    fun initDataLoader() {
        Log.i(TAG, "initDataLoader: Initializing data loader")
        loadData()
    }

    private fun loadData() {
        val job = CoroutineScope(Dispatchers.IO).launch {
            val startTime = System.currentTimeMillis()

            // Step 1: Prepare paths and load existing audio map
            val paths = listOfNotNull(
                    Environment.getExternalStorageDirectory(),
                    SDCard.findSdCardPath(applicationContext)
            )
            val hashMap = audioRepository?.getHashMapForIDByPath() ?: HashMap()

            mainThread { createNotification() }
            ensureActive()

            // Step 2: Collect all audio files
            val files = withContext(Dispatchers.IO) {
                paths.flatMap { dir ->
                    dir.walkTopDown()
                        .filter { it.isFile && it.isAudioFile() }
                        .toList()
                }
            }

            // Step 3: Filter files that need processing
            val filesToProcess = files.filter { file ->
                // TODO - find why 14 songs keep skipping here
                !hashMap.containsKey(file.absolutePath)
            }

            val fileCount = filesToProcess.size
            var count = 0
            postProgressNotification(fileCount, count)
            ensureActive()

            // Step 4: Process files concurrently with semaphore
            val deferredResults = filesToProcess.map { file ->
                async {
                    ensureActive()
                    semaphore.withPermit {
                        ensureActive()
                        val processingTime = measureTimeMillis {
                            processFile(file, audioRepository!!)
                            currentFileName.value = file.name
                        }
                        count++
                        updateProgress(count, fileCount, processingTime)
                    }
                }
            }
            deferredResults.awaitAll()

            Log.v(TAG, "Time taken: ${NumberUtils.getFormattedTime(System.currentTimeMillis() - startTime)} for $fileCount files")
            postSyncCompletedNotification()
        }

        filesLoaderJobs.add(job)
    }

    private fun updateProgress(count: Int, total: Int, lastTime: Long) {
        val remaining = total - count
        synchronized(timeRemaining) {
            synchronized(averageTime) {
                timeRemaining.value = Pair((averageTime.next(lastTime) * remaining).toLong(), "$count/$total")
                postProgressNotification(total, count)
            }
        }
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

    private suspend fun processFile(file: File, audioRepository: AudioRepository) = coroutineScope {
        try {
            ensureActive()
            val audio = Audio()
            val retriever = JAudioMetadataLoader(file)
            retriever.setAudioMetadata(audio)
            audioRepository.insertAudio(audio)
            Log.d(TAG, "Successfully read file using JAudioMetadataLoader: ${file.absolutePath}")
        } catch (e: CannotReadException) {
            e.printStackTrace()
            Log.d(TAG, "loadData: Cannot read file: ${file.absolutePath}")
            ensureActive()

            val audio = Audio()
            val retriever = MediaMetadataLoader(file)
            retriever.setAudioMetadata(audio)

            audioRepository.insertAudio(audio)
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
                    AudioSynchronizerService::class.java))
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
            return Intent(context, AudioSynchronizerService::class.java)
        }
    }
}
