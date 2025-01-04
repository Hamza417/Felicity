package app.simple.felicity.repository

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Environment
import android.os.IBinder
import android.util.Log
import app.simple.felicity.core.tools.MovingAverage
import app.simple.felicity.core.utils.NumberUtils
import app.simple.felicity.core.utils.SDCard
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.loaders.JAudioMetadataLoader
import app.simple.felicity.repository.loaders.MediaMetadataLoader
import app.simple.felicity.repository.models.normal.Audio
import app.simple.felicity.repository.utils.LoaderUtils.isAudioFile
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
    private val filesLoaderJobs = mutableListOf<Job>()
    private val timeRemaining = MutableStateFlow(Pair(0L, ""))
    private val averageTime = MovingAverage(100)

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
        loadData()
    }

    private fun loadData() {
        val job = CoroutineScope(Dispatchers.IO).launch {
            val paths = arrayListOf(Environment.getExternalStorageDirectory(), SDCard.findSdCardPath(applicationContext))
            val audioDatabase = AudioDatabase.getInstance(applicationContext)

            ensureActive()

            val files = paths.flatMap {
                it?.walkTopDown()?.filter { file ->
                    file.isFile && file.isAudioFile()
                }?.toList() ?: listOf()
            }
            val fileCount = files.size
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

    override fun onDestroy() {
        super.onDestroy()
        filesLoaderJobs.forEach { it.cancel() }
    }

    companion object {
        private const val TAG = "SynchronizerService"
        private const val SEMAPHORE_PERMITS = 25
    }
}
