package app.simple.felicity.repository.loader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.R
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
import java.util.concurrent.atomic.AtomicInteger
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
        private const val BATCH_SIZE = 50 // Number of audio items to accumulate before inserting to database

        /** Notification channel ID — stays constant so the system only creates it once. */
        private const val SCAN_CHANNEL_ID = "felicity_library_scanner"

        /** A stable notification ID so each new scan updates the same notification. */
        private const val SCAN_NOTIFICATION_ID = 1001

        /** How often (in files processed) the progress notification text is refreshed. */
        private const val NOTIFICATION_UPDATE_INTERVAL = 25

        data class IndexedFile(val lastModified: Long, val size: Long, val id: Long = 0L, val isFavorite: Boolean = false, val alwaysSkip: Boolean = false)
    }

    // Instance-specific indexed map (not shared across instances)
    private val indexedMap: HashMap<String, IndexedFile> = hashMapOf()

    // Track all active processing jobs for proper cancellation
    private val activeJobs = mutableListOf<Job>()

    // Scope is recreated for every scan so a canceled/dead scope never blocks future scans
    private var scanScope: CoroutineScope = newScanScope()

    // Mutex to prevent multiple parallel scans
    private val scanMutex = Mutex()

    private fun newScanScope() = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Use max() to ensure we use at least MIN_SEMAPHORE_PERMITS, even if fewer processors are available
    private val semaphore = Semaphore(max(MIN_SEMAPHORE_PERMITS, Runtime.getRuntime().availableProcessors()))

    private val audioDatabase: AudioDatabase by lazy {
        AudioDatabase.getInstance(context)
    }

    /** The system notification manager — used to post and update the scan progress card. */
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** Counts how many files have been fully processed during the current scan. */
    private val processedFileCount = AtomicInteger(0)

    suspend fun processAudioFiles() {
        checkNotMainThread()

        // Use mutex to prevent multiple scans from running in parallel
        if (!scanMutex.tryLock()) {
            Log.w(TAG, "Scan already in progress, skipping...")
            return
        }

        // Ensure the scope is alive – it may have been canceled by a prior cleanup() call
        if (!scanScope.coroutineContext[Job]!!.isActive) {
            Log.d(TAG, "Recreating dead scanScope before new scan")
            scanScope = newScanScope()
        }

        setupScannerNotificationChannel()
        processedFileCount.set(0)
        postScannerNotification(0)

        try {
            val startTime = System.currentTimeMillis()
            Log.d(TAG, "Starting audio file processing...")
            val storages = RemovableStorageDetector.getAllStorageVolumes(context)
            val dao = audioDatabase.audioDao()

            Log.d(TAG, "Reconciling database with current storage state...")
            reconcileDatabase(storages)

            Log.d(TAG, "Indexing existing audio files in the database...")
            // Single pass: build both the change-detection index and the path→id lookup map.
            // pathToStoredId lets us reuse the stable DB primary key when updating a row whose
            // content hash (= id) changed after tag editing – no extra per-file DB query needed.
            val pathToStoredId = HashMap<String, Long>()
            dao?.getAllAudioListAll().let { audioList ->
                audioList?.forEach { audio ->
                    indexedMap[audio.path] = IndexedFile(audio.dateModified, audio.size, audio.id, audio.isFavorite, audio.isAlwaysSkip)
                    pathToStoredId[audio.path] = audio.id
                }
            }

            Log.d(TAG, "Indexing complete. Found ${indexedMap.size} existing audio files in the database.")


            // Clear any previous jobs and start fresh
            synchronized(activeJobs) {
                activeJobs.clear()
            }

            // Batch accumulators – inserts for new files, updates for changed existing files
            val insertBatchList = mutableListOf<Audio>()
            val updateBatchList = mutableListOf<Audio>()
            val batchMutex = Mutex()

            storages.forEach { storage ->
                // Check for cancellation before processing each storage
                scanScope.coroutineContext.ensureActive()

                val audioFiles = AudioScanner().getAudioFiles(storage.path!!)
                Log.d(TAG, "Found ${audioFiles.size} audio files in ${storage.path}")

                audioFiles.forEach { file ->
                    // Check for cancellation before processing each file
                    scanScope.coroutineContext.ensureActive()

                    if (shouldProcess(file)) {
                        val processingJob = scanScope.launch {
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

                                    batchMutex.lock()
                                    try {
                                        val storedId = pathToStoredId[file.absolutePath]

                                        if (storedId != null && storedId != 0L) {
                                            // File already has a DB row – reuse the stable PK so
                                            // Room's @Update matches the right row and no orphan
                                            // row (old id, same path) is left behind.
                                            audio.id = storedId
                                            // Preserve user-controlled state flags from the stored record
                                            val stored = indexedMap[file.absolutePath]
                                            audio.setFavorite(stored?.isFavorite ?: false)
                                            audio.setAlwaysSkip(stored?.alwaysSkip ?: false)
                                            Log.d(TAG, "Updating existing entry (id=$storedId) for: ${file.name}")
                                            updateBatchList.add(audio)
                                        } else {
                                            insertBatchList.add(audio)
                                        }

                                        indexedMap[file.absolutePath] = IndexedFile(audio.dateModified, audio.size)

                                        // Flush insert batch when it reaches the batch size
                                        if (insertBatchList.size >= BATCH_SIZE) {
                                            val batchToInsert = insertBatchList.toList()
                                            insertBatchList.clear()
                                            Log.d(TAG, "Inserting batch of ${batchToInsert.size} audio items")
                                            dao?.insertBatch(batchToInsert)
                                        }

                                        // Flush update batch when it reaches the batch size
                                        if (updateBatchList.size >= BATCH_SIZE) {
                                            val batchToUpdate = updateBatchList.toList()
                                            updateBatchList.clear()
                                            Log.d(TAG, "Updating batch of ${batchToUpdate.size} audio items")
                                            dao?.update(batchToUpdate)
                                        }
                                    } finally {
                                        batchMutex.unlock()
                                    }
                                }
                            } catch (e: CancellationException) {
                                // Coroutine was canceled - rethrow to propagate cancellation
                                Log.d(TAG, "Processing canceled for: ${file.name}")
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing ${file.absolutePath}", e)
                            } finally {
                                semaphore.release()

                                // Bump the counter and refresh the notification every
                                // NOTIFICATION_UPDATE_INTERVAL files so the user can
                                // see the scan is actually making progress.
                                val count = processedFileCount.incrementAndGet()
                                if (count % NOTIFICATION_UPDATE_INTERVAL == 0) {
                                    postScannerNotification(count)
                                }
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

            // Insert/update any remaining items in the batches
            if (insertBatchList.isNotEmpty()) {
                Log.d(TAG, "Inserting final batch of ${insertBatchList.size} audio items")
                dao?.insertBatch(insertBatchList)
                insertBatchList.clear()
            }

            if (updateBatchList.isNotEmpty()) {
                Log.d(TAG, "Updating final batch of ${updateBatchList.size} audio items")
                dao?.update(updateBatchList)
                updateBatchList.clear()
            }

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

            dismissScannerNotification()

            try {
                scanMutex.unlock()
            } catch (_: IllegalStateException) {
                Log.w(TAG, "Mutex unlock skipped in finally block – not locked by current owner")
            }
        }
    }

    /**
     * Creates the notification channel for scanner progress cards.
     * Android only creates the channel once; subsequent calls are harmless no-ops.
     * The channel is low-importance and completely silent so it never interrupts playback.
     */
    private fun setupScannerNotificationChannel() {
        val channel = NotificationChannel(
                SCAN_CHANNEL_ID,
                "Library Scanner",
                NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress while your music library is being scanned"
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Posts (or updates) the scan progress notification.
     *
     * When [filesProcessed] is 0 the progress bar is indeterminate, which is the right
     * look for the initial reconcile phase when we don't have a total yet.
     * Once files start coming in, the text updates to show how many have been handled.
     *
     * @param filesProcessed The number of audio files processed so far this scan.
     */
    private fun postScannerNotification(filesProcessed: Int) {
        val isIndeterminate = filesProcessed == 0
        val contentText = if (isIndeterminate) {
            "Scanning your music library…"
        } else {
            "$filesProcessed files indexed so far"
        }

        val notification = NotificationCompat.Builder(context, SCAN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_felicity_full)
            .setContentTitle("Scanning Library")
            .setContentText(contentText)
            .setProgress(0, 0, isIndeterminate)
            .setSilent(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(SCAN_NOTIFICATION_ID, notification)
    }

    /**
     * Removes the scan progress notification once the scan finishes (or is cancelled).
     * Safe to call multiple times.
     */
    private fun dismissScannerNotification() {
        notificationManager.cancel(SCAN_NOTIFICATION_ID)
    }

    /**
     * Removes entries from the database that no longer exist on disk, or are excluded by the
     * current filter preferences (.nomedia, hidden files, hidden folders).
     * SAFELY handles removable storage by only checking files on currently mounted volumes.
     */
    private suspend fun reconcileDatabase(mountedStorages: List<RemovableStorageDetector.StorageInfo?>) {
        val dao = audioDatabase.audioDao() ?: return

        // CASE 0: Purge any duplicate rows that share the same path.
        // These can accumulate when a tagger app changes file content (hash changes → new PK)
        // before this fix was applied. Run it unconditionally so existing bad data is cleaned up.
        dao.deleteStalePathDuplicates()
        Log.d(TAG, "Stale path-duplicate purge complete.")

        // Get all audio from DB (include unavailable rows so they are also evaluated below)
        val allAudio = dao.getAllAudioListAll()

        // Get list of currently mounted paths (e.g., /storage/emulated/0)
        val mounts = mountedStorages.mapNotNull { it?.path }

        // Snapshot current filter preferences once for the whole reconcile pass
        val skipNomedia = LibraryPreferences.isSkipNomedia()
        val skipHiddenFiles = LibraryPreferences.isSkipHiddenFiles()
        val skipHiddenFolders = LibraryPreferences.isSkipHiddenFolders()

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
                            if (audio.isAvailable) {
                                // CASE 1: The file was a real, known track that the user deleted
                                // using a file manager or some other means. We go ahead and remove
                                // it from the database because there is nothing left to play.
                                Log.d(TAG, "File missing on mounted storage. Deleting: ${audio.path}")
                                toDelete.add(audio)
                                indexedMap.remove(audio.path)
                            } else {
                                // CASE 1b: The file doesn't exist, but the entry is already marked
                                // unavailable — this is a ghost/placeholder that was created when an
                                // M3U playlist was imported and the referenced track wasn't on the
                                // device yet. We leave it alone so the playlist doesn't lose that slot.
                                Log.d(TAG, "Ghost entry (file missing, already unavailable) — keeping: ${audio.path}")
                            }
                        }
                        isExcludedByFilter(file, skipNomedia, skipHiddenFiles, skipHiddenFolders) -> {
                            // CASE 2: File now excluded by current filter prefs (e.g., user added .nomedia
                            //         or toggled the preference). Remove it from the database.
                            Log.d(TAG, "File excluded by current filter settings. Removing: ${audio.path}")
                            toDelete.add(audio)
                            indexedMap.remove(audio.path)
                        }
                        !audio.isAvailable -> {
                            // CASE 3: Restore (file is found and no longer excluded)
                            Log.d(TAG, "Restoring available file: ${audio.path}")
                            audio.isAvailable = true
                            toUpdate.add(audio)

                            // Re-add to index so we don't re-scan metadata
                            indexedMap[audio.path] = IndexedFile(audio.dateModified, audio.size)
                        }
                    }
                }
                else -> {
                    // CASE 4: Volume is NOT mounted (ejected?).
                    when {
                        audio.isAvailable -> {
                            // DB says available, but storage is gone. Mark as unavailable.
                            Log.d(TAG, "Marking unavailable (storage ejected): ${audio.path}")
                            audio.isAvailable = false
                            toUpdate.add(audio)
                        }
                    }
                }
            }
        }

        // Apply Batched Changes
        if (toDelete.isNotEmpty()) {
            dao.delete(toDelete)
        }

        if (toUpdate.isNotEmpty()) {
            dao.update(toUpdate)
        }

        Log.d(TAG, "Reconcile complete: Deleted ${toDelete.size}, Updated status for ${toUpdate.size}")
    }

    /**
     * Returns true if the file should be excluded from the library based on current filter preferences.
     * Walks up the directory tree to check every ancestor for .nomedia and hidden-folder rules.
     */
    private fun isExcludedByFilter(
            file: File,
            skipNomedia: Boolean,
            skipHiddenFiles: Boolean,
            skipHiddenFolders: Boolean
    ): Boolean {
        // Check the file itself for the hidden-file rule
        if (skipHiddenFiles && file.name.startsWith(".")) {
            Log.d(TAG, "Excluded (hidden file): ${file.absolutePath}")
            return true
        }

        // Walk up the directory tree from the file's parent
        var dir = file.parentFile
        while (dir != null) {
            if (skipNomedia && File(dir, ".nomedia").exists()) {
                Log.d(TAG, "Excluded (.nomedia in ${dir.absolutePath}): ${file.absolutePath}")
                return true
            }
            if (skipHiddenFolders && dir.name.startsWith(".")) {
                Log.d(TAG, "Excluded (hidden folder ${dir.absolutePath}): ${file.absolutePath}")
                return true
            }
            dir = dir.parentFile
        }
        return false
    }

    /**
     * Check if a scan is currently in progress
     */
    fun isScanInProgress(): Boolean = scanMutex.isLocked

    /**
     * Cancel any in-flight scan and immediately start a new one.
     * This is the safe path for "refresh on resume" – it never silently drops the request.
     */
    suspend fun cancelAndRestartScan() {
        Log.d(TAG, "cancelAndRestartScan: tearing down current scan")
        cancelCurrentScan()
        Log.d(TAG, "cancelAndRestartScan: starting fresh scan")
        processAudioFiles()
    }

    /**
     * Cancel the currently running scan and reset all state so a new scan can be started.
     * Unlike cleanup(), this method leaves the loader fully operational.
     */
    private fun cancelCurrentScan() {
        // Cancel every child job first
        synchronized(activeJobs) {
            Log.d(TAG, "Canceling ${activeJobs.size} active jobs")
            activeJobs.forEach { it.cancel() }
            activeJobs.clear()
        }

        // Cancel and replace the scope – this kills any stragglers launched directly on scanScope
        scanScope.coroutineContext[Job]?.cancel()
        scanScope = newScanScope()

        // Clear stale index so the new scan re-evaluates every file
        indexedMap.clear()

        // Release the mutex if it is still held by the canceled scan
        if (scanMutex.isLocked) {
            try {
                scanMutex.unlock()
                Log.d(TAG, "Mutex released after cancel")
            } catch (_: IllegalStateException) {
                Log.w(TAG, "Mutex unlock skipped – not locked by current owner")
            }
        }
    }

    /**
     * Cancel all ongoing operations and cleanup resources.
     * After this call the loader is still usable – a new scan can be started.
     */
    fun cleanup() {
        Log.d(TAG, "Cleanup called - canceling all operations")
        cancelCurrentScan()
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