package app.simple.felicity.repository.loader

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.metadata.MetaDataHelper.extractMetadata
import app.simple.felicity.repository.scanners.AudioScanner
import app.simple.felicity.shared.storage.RemovableStorageDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
@WorkerThread
class AudioDatabaseLoader @Inject constructor(private val context: Context) {

    companion object {
        private const val TAG = "AudioDatabaseLoader"
        private const val MIN_SEMAPHORE_PERMITS = 4

        data class IndexedFile(val lastModified: Long, val size: Long)

        private val indexedMap: HashMap<String, IndexedFile> = hashMapOf()
    }

    // Supervisor job ensures that failure in one child doesn't cancel others
    private val supervisorJob = SupervisorJob()
    private val loaderScope = CoroutineScope(Dispatchers.IO + supervisorJob)

    // Mutex to prevent multiple parallel scans
    private val scanMutex = Mutex()

    // Use max() to ensure we use at least MIN_SEMAPHORE_PERMITS, even if fewer processors are available
    private val semaphore = Semaphore(max(MIN_SEMAPHORE_PERMITS, Runtime.getRuntime().availableProcessors()))

    private val audioDatabase: AudioDatabase by lazy {
        AudioDatabase.getInstance(context)!!
    }

    suspend fun processAudioFiles() {
        // Use mutex to prevent multiple scans from running in parallel
        if (!scanMutex.tryLock()) {
            Log.w(TAG, "Scan already in progress, skipping...")
            return
        }

        try {
            val startTime = System.currentTimeMillis()
            Log.d(TAG, "Starting audio file processing...")
            val storages = RemovableStorageDetector.getAllStorageVolumes(context)
            val dao = audioDatabase.audioDao()

            Log.d(TAG, "Indexing existing audio files in the database...")
            // Clear and rebuild index of existing audio files in the database
            withContext(Dispatchers.IO) {
                indexedMap.clear() // Clear stale entries from previous scans
                dao?.getAllAudioList().let { audioList ->
                    audioList?.forEach { audio ->
                        indexedMap[audio.path] = IndexedFile(audio.dateModified, audio.size)
                    }
                }
            }

            Log.d(TAG, "Indexing complete. Found ${indexedMap.size} existing audio files in the database.")

            // Collect all processing jobs
            val processingJobs = mutableListOf<Job>()

            storages.forEach { storage ->
                val audioFiles = AudioScanner().getAudioFiles(storage.path!!)
                Log.d(TAG, "Found ${audioFiles.size} audio files in ${storage.path}")
                audioFiles.forEach { file ->
                    if (shouldProcess(file)) {
                        val processingJob = loaderScope.launch {
                            semaphore.acquire()
                            try {
                                Log.d(TAG, "Processing: ${file.absolutePath}")
                                val audio = file.extractMetadata()
                                audio?.let {
                                    dao?.insert(it)
                                    Log.d(TAG, "Inserted: ${file.absolutePath}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing ${file.absolutePath}", e)
                            } finally {
                                semaphore.release()
                            }
                        }

                        processingJobs.add(processingJob)
                    }
                }
            }

            // Wait for all processing jobs to complete
            processingJobs.joinAll()
            Log.d(TAG, "Audio file processing complete in ${(System.currentTimeMillis() - startTime) / 1000} seconds.")
        } catch (e: Exception) {
            Log.e(TAG, "Error during audio file processing", e)
        } finally {
            scanMutex.unlock()
        }
    }

    /**
     * Check if a scan is currently in progress
     */
    fun isScanInProgress(): Boolean = scanMutex.isLocked

    /**
     * Cancel all ongoing operations and cleanup resources
     */
    fun cleanup() {
        supervisorJob.cancel()
        indexedMap.clear()
    }

    private fun shouldProcess(file: File): Boolean {
        val existing = indexedMap[file.absolutePath] ?: return true   // new file

        if (existing.size != file.length()) return true          // changed
        if (existing.lastModified != file.lastModified()) return true

        return false                                             // unchanged → skip
    }
}