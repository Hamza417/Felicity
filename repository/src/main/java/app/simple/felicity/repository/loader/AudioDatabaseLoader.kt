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
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.io.File
import javax.inject.Inject
import kotlin.math.max

@WorkerThread
class AudioDatabaseLoader @Inject constructor(private val context: Context) {

    companion object {
        private const val TAG = "AudioDatabaseLoader"
        private const val MIN_SEMAPHORE_PERMITS = 4

        data class IndexedFile(val lastModified: Long, val size: Long)

        private val indexedMap: HashMap<String, IndexedFile> = hashMapOf()
    }

    private val loaderJobs: MutableSet<Job> = mutableSetOf()

    // Use max() to ensure we use at least MIN_SEMAPHORE_PERMITS, even if fewer processors are available
    private val semaphore = Semaphore(max(MIN_SEMAPHORE_PERMITS, Runtime.getRuntime().availableProcessors()))

    private val audioDatabase: AudioDatabase by lazy {
        AudioDatabase.getInstance(context)!!
    }

    fun processAudioFiles() {
        val job = CoroutineScope(Dispatchers.IO).launch {
            val startTime = System.currentTimeMillis()
            Log.d(TAG, "Starting audio file processing...")
            val storages = RemovableStorageDetector.getAllStorageVolumes(context)
            val dao = audioDatabase.audioDao()

            Log.d(TAG, "Indexing existing audio files in the database...")
            // Create index of existing audio files in the database
            dao?.getAllAudioList().let { audioList ->
                audioList?.forEach { audio ->
                    indexedMap[audio.path] = IndexedFile(audio.dateModified, audio.size)
                }
            }

            Log.d(TAG, "Indexing complete. Found ${indexedMap.size} existing audio files in the database.")

            // Collect all processing jobs
            val processingJobs = mutableListOf<Job>()

            storages.forEach { storage ->
                val audioFiles = AudioScanner().getAudioFiles(storage.path!!)
                Log.d(TAG, "Found ${audioFiles.size} audio files in ${storage.path}")
                audioFiles.forEach { file ->
                    if (shouldProcess(file, indexedMap)) {
                        val processingJob = launch {
                            semaphore.acquire()
                            try {
                                Log.d(TAG, "Processing: ${file.absolutePath}")
                                val audio = file.extractMetadata()
                                audio?.let {
                                    dao?.insert(it)
                                    Log.d(TAG, "Inserted: ${file.absolutePath}")
                                }
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
        }

        loaderJobs.add(job)
    }

    private fun shouldProcess(file: File, index: Map<String, IndexedFile>): Boolean {
        val existing = index[file.absolutePath] ?: return true   // new file

        if (existing.size != file.length()) return true          // changed
        if (existing.lastModified != file.lastModified()) return true

        return false                                             // unchanged → skip
    }
}