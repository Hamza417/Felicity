package app.simple.felicity.repository.loader

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.metadata.MetaDataHelper.extractMetadata
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.scanners.AudioScanner
import app.simple.felicity.shared.storage.RemovableStorageDetector
import app.simple.felicity.shared.utils.ProcessUtils.checkNotMainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max

@Singleton
@WorkerThread
class AudioDatabaseLoader @Inject constructor(private val context: Context) {

    companion object {
        private const val TAG = "AudioDatabaseLoader"
        private const val MIN_SEMAPHORE_PERMITS = 4

        data class IndexedFile(val lastModified: Long, val size: Long)
    }

    // Instance-specific indexed map (not shared across instances)
    private val indexedMap: HashMap<String, IndexedFile> = hashMapOf()

    // Track all active processing jobs for proper cancellation
    private val activeJobs = mutableListOf<Job>()

    // Supervisor job ensures that failure in one child doesn't cancel others
    private val supervisorJob = SupervisorJob()
    private val loaderScope = CoroutineScope(Dispatchers.IO + supervisorJob)

    // Mutex to prevent multiple parallel scans
    private val scanMutex = Mutex()

    // Use max() to ensure we use at least MIN_SEMAPHORE_PERMITS, even if fewer processors are available
    private val semaphore = Semaphore(max(MIN_SEMAPHORE_PERMITS, Runtime.getRuntime().availableProcessors()))

    private val audioDatabase: AudioDatabase by lazy {
        AudioDatabase.getInstance(context)
    }

    suspend fun processAudioFiles() {
        checkNotMainThread()

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

            Log.d(TAG, "Reconciling database with current storage state...")
            reconcileDatabase(storages)

            Log.d(TAG, "Indexing existing audio files in the database...")
            // Create index of existing audio files in the database
            dao?.getAllAudioList().let { audioList ->
                audioList?.forEach { audio ->
                    indexedMap[audio.path] = IndexedFile(audio.dateModified, audio.size)
                }
            }

            Log.d(TAG, "Indexing complete. Found ${indexedMap.size} existing audio files in the database.")

            // Clear any previous jobs and start fresh
            synchronized(activeJobs) {
                activeJobs.clear()
            }

            storages.forEach { storage ->
                // Check for cancellation before processing each storage
                loaderScope.coroutineContext.ensureActive()

                val audioFiles = AudioScanner().getAudioFiles(storage.path!!)
                Log.d(TAG, "Found ${audioFiles.size} audio files in ${storage.path}")

                audioFiles.forEach { file ->
                    // Check for cancellation before processing each file
                    loaderScope.coroutineContext.ensureActive()

                    if (shouldProcess(file)) {
                        val processingJob = loaderScope.launch {
                            semaphore.acquire()
                            try {
                                // Check for cancellation before starting metadata extraction
                                ensureActive()

                                Log.d(TAG, "Processing: ${file.absolutePath}")
                                val audio = file.extractMetadata()

                                // Check for cancellation after metadata extraction
                                ensureActive()

                                if (audio == null) {
                                    Log.w(TAG, "Failed to extract metadata for: ${file.name}")
                                } else {
                                    Log.d(TAG, "Metadata extracted for ${file.name}: size=${audio.size}, dateModified=${audio.dateModified}")
                                    dao?.insert(audio)
                                    indexedMap[file.absolutePath] = IndexedFile(audio.dateModified, audio.size)
                                    Log.d(TAG, "Inserted and indexed: ${file.name}")
                                }
                            } catch (e: CancellationException) {
                                // Coroutine was canceled - rethrow to propagate cancellation
                                Log.d(TAG, "Processing canceled for: ${file.name}")
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing ${file.absolutePath}", e)
                            } finally {
                                semaphore.release()
                            }
                        }

                        synchronized(activeJobs) {
                            activeJobs.add(processingJob)
                        }
                    }
                }
            }

            // Wait for all processing jobs to complete
            val jobsToWait = synchronized(activeJobs) { activeJobs.toList() }
            jobsToWait.joinAll()
            Log.d(TAG, "Audio file processing complete in ${(System.currentTimeMillis() - startTime) / 1000} seconds.")
        } catch (e: CancellationException) {
            // Scan was canceled - this is expected behavior
            Log.d(TAG, "Audio file processing canceled")
            throw e  // Rethrow to propagate cancellation
        } catch (e: Exception) {
            Log.e(TAG, "Error during audio file processing", e)
        } finally {
            // Clear active jobs after completion
            synchronized(activeJobs) {
                activeJobs.clear()
            }
            scanMutex.unlock()
        }
    }

    // TODO: check for .nomedia and hidden files during sanitization as well
    /**
     * Removes entries from the database that no longer exist on disk.
     * SAFELY handles removable storage by only checking files on currently mounted volumes.
     */
    private suspend fun reconcileDatabase(mountedStorages: List<RemovableStorageDetector.StorageInfo?>) {
        // Get all audio from DB
        val allAudio = audioDatabase.audioDao()?.getAllAudioList() ?: return

        // Get list of currently mounted paths (e.g., /storage/emulated/0)
        val mounts = mountedStorages.mapNotNull { it?.path }

        val toDelete = mutableListOf<Audio>()
        val toUpdate = mutableListOf<Audio>()

        allAudio.forEach { audio ->
            // Check if this audio file belongs to a volume that is currently mounted
            val isHostedOnMountedVolume = mounts.any { mount ->
                audio.path.startsWith(mount.path, ignoreCase = true)
            }

            when {
                isHostedOnMountedVolume -> {
                    // Volume IS mounted. We can check the file system safely.
                    val file = File(audio.path)

                    when {
                        !file.exists() -> {
                            // CASE 1: Hard Delete (User deleted file using a file manager)
                            Log.d(TAG, "File missing on mounted storage. Deleting: ${audio.path}")
                            toDelete.add(audio)
                            indexedMap.remove(audio.path)
                        }
                        !audio.isAvailable -> {
                            // CASE 2: Restore (User inserted?, file is found)
                            Log.d(TAG, "Restoring available file: ${audio.path}")
                            audio.isAvailable = true
                            toUpdate.add(audio)

                            // Re-add to index so we don't re-scan metadata
                            indexedMap[audio.path] = IndexedFile(audio.dateModified, audio.size)
                        }
                    }
                }
                else -> {
                    // CASE 3: Volume is NOT mounted (ejected?).
                    when {
                        audio.isAvailable -> {
                            // DB says available, but storage is gone. Mark as unavailable.
                            Log.d(TAG, "Marking unavailable (storage ejected): ${audio.path}")

                            // FIX: Use Setter instead of copy()
                            audio.isAvailable = false
                            toUpdate.add(audio)
                        }
                    }
                }
            }
        }

        // Apply Batched Changes
        if (toDelete.isNotEmpty()) {
            audioDatabase.audioDao()?.delete(toDelete)
        }

        if (toUpdate.isNotEmpty()) {
            audioDatabase.audioDao()?.update(toUpdate)
        }

        Log.d(TAG, "Reconcile complete: Deleted ${toDelete.size}, Updated status for ${toUpdate.size}")
    }

    /**
     * Check if a scan is currently in progress
     */
    fun isScanInProgress(): Boolean = scanMutex.isLocked

    /**
     * Cancel all ongoing operations and cleanup resources
     * This ensures no background processes are left running
     */
    fun cleanup() {
        Log.d(TAG, "Cleanup called - canceling all operations")

        // Cancel all active processing jobs
        synchronized(activeJobs) {
            Log.d(TAG, "Canceling ${activeJobs.size} active jobs")
            activeJobs.forEach { job ->
                if (job.isActive) {
                    job.cancel()
                }
            }
            activeJobs.clear()
        }

        // Cancel the supervisor job (this will cancel the entire scope)
        supervisorJob.cancel()

        // Clear the indexed map
        indexedMap.clear()

        // Unlock the mutex if it's locked (in case cleanup is called during a scan)
        if (scanMutex.isLocked) {
            try {
                scanMutex.unlock()
            } catch (_: IllegalStateException) {
                // Mutex wasn't locked by this thread, ignore
                Log.w(TAG, "Attempted to unlock mutex but it wasn't locked by this thread")
            }
        }

        Log.d(TAG, "Cleanup complete - all operations canceled")
    }

    private fun shouldProcess(file: File): Boolean {
        val existing = indexedMap[file.absolutePath]

        if (existing == null) {
            Log.d(TAG, "New file (not in index): ${file.name}")
            return true   // new file
        }

        val fileSize = file.length()
        val fileModified = file.lastModified()

        if (existing.size != fileSize) {
            Log.d(TAG, "Size changed for ${file.name}: DB=${existing.size}, File=$fileSize")
            return true          // changed
        }

        if (existing.lastModified != fileModified) {
            Log.d(TAG, "Modified time changed for ${file.name}: DB=${existing.lastModified}, File=$fileModified")
            return true
        }

        return false                                             // unchanged → skip
    }
}