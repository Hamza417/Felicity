package app.simple.felicity.repository.loader

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import app.simple.felicity.preferences.LibraryPreferences
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max

/**
 * Drives the full audio library scanning pipeline — finding audio files on
 * device storage, extracting their metadata, and keeping the local Room
 * database in sync with what is actually on disk.
 *
 * All notification concerns live in [LoaderNotification] so this class can
 * stay focused on the actual scanning work without getting distracted.
 *
 * @author Hamza417
 */
@Singleton
@WorkerThread
class AudioDatabaseLoader @Inject constructor(private val context: Context) {

    companion object {
        private const val TAG = "AudioDatabaseLoader"
        private const val MIN_SEMAPHORE_PERMITS = 4

        /** How many audio items we accumulate before flushing them to the database in one go. */
        private const val BATCH_SIZE = 50

        /**
         * A lightweight snapshot of a file's identity in the database index.
         * We use size and last-modified time as a quick "has this file changed?" check —
         * no need to re-read the full metadata if these two numbers still match.
         */
        data class IndexedFile(
                val lastModified: Long,
                val size: Long,
                val id: Long = 0L,
                val isFavorite: Boolean = false,
                val alwaysSkip: Boolean = false
        )
    }

    /** Per-instance index of paths we have already seen in the database. */
    private val indexedMap: HashMap<String, IndexedFile> = hashMapOf()

    /**
     * Each scan gets its own scope. When a scan is canceled, we swap in a fresh
     * scope so the next scan can start clean without inheriting a dead Job.
     */
    private var scanScope: CoroutineScope = newScanScope()

    /**
     * Guards against two scans running at the same time. We use an AtomicBoolean
     * instead of a coroutine Mutex because a Mutex can only be unlocked by the
     * coroutine that locked it — trying to unlock from a different coroutine
     * (e.g. during a forced cancel) throws silently and leaves the lock stuck
     * forever, which is exactly the bug that caused the first-launch scan to stop.
     * An AtomicBoolean has no such restriction and can be reset from anywhere.
     */
    private val isScanRunning = AtomicBoolean(false)

    /** Limits how many files we process in parallel so we don't overwhelm the CPU. */
    private val semaphore = Semaphore(max(MIN_SEMAPHORE_PERMITS, Runtime.getRuntime().availableProcessors()))

    /** Handles all the "Scanning Library" notification UI so we don't have to. */
    private val notification = LoaderNotification(context)

    private val audioDatabase: AudioDatabase by lazy {
        AudioDatabase.getInstance(context)
    }

    private fun newScanScope() = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun processAudioFiles() {
        checkNotMainThread()

        if (!isScanRunning.compareAndSet(false, true)) {
            Log.w(TAG, "Scan already in progress, skipping...")
            return
        }

        if (!scanScope.coroutineContext[Job]!!.isActive) {
            Log.d(TAG, "Recreating dead scanScope before new scan")
            scanScope = newScanScope()
        }

        // begin() bumps the generation counter — this is what prevents the old scan's
        // finally block from accidentally dismissing our brand-new notification later.
        val generation = notification.begin()

        try {
            val startTime = System.currentTimeMillis()
            Log.d(TAG, "Starting audio file processing...")

            val storages = RemovableStorageDetector.getAllStorageVolumes(context)
            val dao = audioDatabase.audioDao()

            Log.d(TAG, "Reconciling database with current storage state...")
            reconcileDatabase(storages)

            Log.d(TAG, "Indexing existing audio files in the database...")

            // Build the change-detection index and the path→id lookup map so we can
            // tell whether an audio file already has a row in the database and, if so,
            // reuse its stable primary key instead of creating a duplicate row.
            val pathToStoredId = HashMap<String, Long>()

            dao?.getAllAudioListAll()?.forEach { audio ->
                indexedMap[audio.path] = IndexedFile(audio.dateModified, audio.size, audio.id, audio.isFavorite, audio.isAlwaysSkip)
                pathToStoredId[audio.path] = audio.id
            }

            Log.d(TAG, "Indexing complete. Found ${indexedMap.size} existing audio files in the database.")

            val insertBatchList = mutableListOf<Audio>()
            val updateBatchList = mutableListOf<Audio>()
            val batchMutex = Mutex()

            // Phase 1 — collect every audio file from all storage volumes.
            // We do this upfront so we know the grand total and can show a proper
            // fill-level progress bar instead of an infinite spinner.
            val allAudioFiles = mutableListOf<File>()
            storages.forEach { storage ->
                scanScope.coroutineContext.ensureActive()
                val files = AudioScanner().getAudioFiles(storage.path!!)
                Log.d(TAG, "Found ${files.size} audio files in ${storage.path}")
                allAudioFiles.addAll(files)
            }

            notification.setTotal(allAudioFiles.size)
            Log.d(TAG, "Total audio files to evaluate: ${allAudioFiles.size}")

            /**
             * This counter tracks how many files have actually *finished* being
             * processed (metadata extracted, DB written), not just how many times
             * the loop has ticked. The old approach used the loop index, which
             * races way ahead of the parallel coroutines and made the notification
             * show "1200 files done" while the library was still loading its 200th
             * song — pretty confusing for anyone watching the progress bar.
             */
            val processedCount = AtomicInteger(0)

            /**
             * A plain local list of every job we launch during Phase 2. No locking
             * needed here — only the sequential loop below ever touches this list
             * (the launched coroutines never write to it), so there is no contention.
             * Previously this was a shared field with synchronized blocks on every
             * iteration, which caused a 252ms monitor stall and "Canceling 1180 jobs"
             * at cancel time. Jobs no longer need individual cancellation because
             * cancelling [scanScope] already cancels every child automatically.
             */
            val pendingJobs = mutableListOf<Job>()

            // Phase 2 — process each file. The loop is sequential; the heavy lifting
            // (metadata extraction) happens inside parallel coroutines on the scanScope.
            allAudioFiles.forEach { file ->
                scanScope.coroutineContext.ensureActive()

                if (shouldProcess(file)) {
                    val processingJob = scanScope.launch {
                        semaphore.acquire()
                        try {
                            ensureActive()

                            Log.d(TAG, "Processing: ${file.absolutePath}")
                            val audio = file.extractMetadata()

                            ensureActive()

                            if (audio == null) {
                                Log.w(TAG, "Failed to extract metadata for: ${file.name}")
                            } else {
                                Log.d(TAG, "Metadata extracted for ${file.name}: size=${audio.size}, dateModified=${audio.dateModified}")

                                batchMutex.lock()
                                try {
                                    val storedId = pathToStoredId[file.absolutePath]

                                    // Read the stored record BEFORE overwriting the index entry
                                    // so we can recover user-set flags like isFavorite.
                                    val stored = indexedMap[file.absolutePath]

                                    if (storedId != null && storedId != 0L) {
                                        // The file already has a row in the database — reuse its
                                        // stable primary key so Room's @Update hits the right row
                                        // and no orphaned entry is left behind.
                                        audio.id = storedId
                                        audio.isFavorite = stored?.isFavorite ?: false
                                        audio.isAlwaysSkip = stored?.alwaysSkip ?: false
                                        Log.d(TAG, "Updating existing entry (id=$storedId) for: ${file.name}")
                                        updateBatchList.add(audio)
                                        indexedMap[file.absolutePath] = IndexedFile(audio.dateModified, audio.size)
                                    } else {
                                        // Brand-new file — each file path produces its own unique hash
                                        // (content hash XOR path hash), so even exact audio copies at
                                        // different locations get their own row and show up in the library.
                                        insertBatchList.add(audio)
                                        indexedMap[file.absolutePath] = IndexedFile(audio.dateModified, audio.size)
                                    }

                                    if (insertBatchList.size >= BATCH_SIZE) {
                                        val batchToInsert = insertBatchList.toList()
                                        insertBatchList.clear()
                                        Log.d(TAG, "Inserting batch of ${batchToInsert.size} audio items")
                                        dao?.insertBatch(batchToInsert)
                                    }

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

                            // Bump the real progress counter and refresh the notification.
                            // We do this here — inside the coroutine, after the heavy work —
                            // so the count reflects files that are genuinely done, not just
                            // files whose coroutine was *launched* (those two can be far apart).
                            val done = processedCount.incrementAndGet()
                            if (done % LoaderNotification.NOTIFICATION_UPDATE_INTERVAL == 0) {
                                notification.updateProgress(done)
                            }
                        } catch (e: CancellationException) {
                            Log.d(TAG, "Processing canceled for: ${file.name}")
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing ${file.absolutePath}", e)
                        } finally {
                            semaphore.release()
                        }
                    }

                    pendingJobs.add(processingJob)
                }
            }

            pendingJobs.joinAll()

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

            // Push one last update so the bar shows 100% before it disappears.
            notification.updateProgress(allAudioFiles.size)

            Log.d(TAG, "Audio file processing complete in ${(System.currentTimeMillis() - startTime) / 1000} seconds.")
        } catch (e: CancellationException) {
            Log.d(TAG, "Audio file processing canceled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error during audio file processing", e)
        } finally {
            // Pass our generation token — if a newer scan has already started, this
            // is a no-op and the new scan's notification stays on screen. If we are
            // the most recent scan, we properly clean up after ourselves.
            notification.dismiss(generation)

            // Only force-dismiss if the notification is still marked as active after
            // the generation-aware dismiss above. Without this guard the old scan's
            // finally block would blow away the new scan's notification even when the
            // generation check correctly decided to leave it alone.
            if (notification.isShowing()) {
                notification.dismissForce()
            }

            // Signal that the scan is done. Any coroutine can flip this safely
            // because AtomicBoolean has no ownership concept — unlike Mutex, which
            // would throw if the wrong coroutine tried to unlock it (the root cause
            // of first-launch scans silently dropping).
            isScanRunning.set(false)
        }
    }

    /**
     * Removes entries from the database that no longer exist on disk, or are excluded by the
     * current filter preferences (.nomedia, hidden files, hidden folders).
     * Safely handles removable storage by only checking files on currently mounted volumes.
     */
    private suspend fun reconcileDatabase(mountedStorages: List<RemovableStorageDetector.StorageInfo?>) {
        val dao = audioDatabase.audioDao() ?: return

        // Clean up any duplicate rows that share the same path — these can build up
        // when a tag editor changes file content (which shifts the hash) before the
        // dedup logic in this loader was in place.
        dao.deleteStalePathDuplicates()
        Log.d(TAG, "Stale path-duplicate purge complete.")

        val allAudio = dao.getAllAudioListAll()
        val mounts = mountedStorages.mapNotNull { it?.path }

        val skipNomedia = LibraryPreferences.isSkipNomedia()
        val skipHiddenFiles = LibraryPreferences.isSkipHiddenFiles()
        val skipHiddenFolders = LibraryPreferences.isSkipHiddenFolders()
        val excludedFolders = LibraryPreferences.getExcludedFolders()

        val toDelete = mutableListOf<Audio>()
        val toUpdate = mutableListOf<Audio>()

        allAudio.forEach { audio ->
            val isHostedOnMountedVolume = mounts.any { mount ->
                audio.path.startsWith(mount.path, ignoreCase = true)
            }

            when {
                isHostedOnMountedVolume -> {
                    val file = File(audio.path)

                    when {
                        !file.exists() -> {
                            if (audio.isAvailable) {
                                // The file is genuinely gone — delete it from the library.
                                Log.d(TAG, "File missing on mounted storage. Deleting: ${audio.path}")
                                toDelete.add(audio)
                                indexedMap.remove(audio.path)
                            } else {
                                // Ghost entry from a playlist import — leave it alone.
                                Log.d(TAG, "Ghost entry (file missing, already unavailable) — keeping: ${audio.path}")
                            }
                        }
                        isExcludedByFilter(file, skipNomedia, skipHiddenFiles, skipHiddenFolders) ||
                                isInExcludedFolder(file, excludedFolders) -> {
                            // The user toggled a filter (like .nomedia) — respect their wishes.
                            Log.d(TAG, "File excluded by current filter settings. Removing: ${audio.path}")
                            toDelete.add(audio)
                            indexedMap.remove(audio.path)
                        }
                        !audio.isAvailable -> {
                            // File is back! Welcome home, little song.
                            Log.d(TAG, "Restoring available file: ${audio.path}")
                            audio.isAvailable = true
                            toUpdate.add(audio)
                            indexedMap[audio.path] = IndexedFile(audio.dateModified, audio.size)
                        }
                    }
                }
                else -> {
                    if (audio.isAvailable) {
                        // Storage volume is not mounted — mark the track as temporarily unavailable.
                        Log.d(TAG, "Marking unavailable (storage ejected): ${audio.path}")
                        audio.isAvailable = false
                        toUpdate.add(audio)
                    }
                }
            }
        }

        if (toDelete.isNotEmpty()) dao.delete(toDelete)
        if (toUpdate.isNotEmpty()) dao.update(toUpdate)

        Log.d(TAG, "Reconcile complete: Deleted ${toDelete.size}, Updated status for ${toUpdate.size}")
    }

    /**
     * Returns true if the file lives inside any of the folders the user has
     * explicitly added to the excluded (blacklist) folders setting.
     * Uses a simple path-prefix check — if the file's path starts with an
     * excluded folder's path, it doesn't belong in the library.
     */
    private fun isInExcludedFolder(file: File, excludedFolders: Set<String>): Boolean {
        if (excludedFolders.isEmpty()) return false
        return excludedFolders.any { excluded ->
            file.absolutePath.startsWith(excluded).also { hit ->
                if (hit) Log.d(TAG, "Excluded (user blacklist '$excluded'): ${file.absolutePath}")
            }
        }
    }

    /**
     * Returns true if the file should be excluded from the library based on the
     * current filter preferences. Walks up the directory tree to check every
     * ancestor folder for .nomedia and hidden-folder rules.
     */
    private fun isExcludedByFilter(
            file: File,
            skipNomedia: Boolean,
            skipHiddenFiles: Boolean,
            skipHiddenFolders: Boolean
    ): Boolean {
        if (skipHiddenFiles && file.name.startsWith(".")) {
            Log.d(TAG, "Excluded (hidden file): ${file.absolutePath}")
            return true
        }

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
     * Returns true when a file needs fresh metadata extraction — either because
     * it is brand new to the library, or because its size or last-modified timestamp
     * changed since we last saw it (indicating the user may have re-tagged it).
     */
    private fun shouldProcess(file: File): Boolean {
        val existing = indexedMap[file.absolutePath] ?: run {
            Log.d(TAG, "New file (not in index): ${file.name}")
            return true
        }

        val fileSize = file.length()
        val fileModified = file.lastModified()

        if (existing.size != fileSize) {
            Log.d(TAG, "Size changed for ${file.name}: DB=${existing.size}, File=$fileSize")
            return true
        }

        if (existing.lastModified != fileModified) {
            Log.d(TAG, "Modified time changed for ${file.name}: DB=${existing.lastModified}, File=$fileModified")
            return true
        }

        return false
    }

    /** Returns true when a scan is currently running. */
    fun isScanInProgress(): Boolean = isScanRunning.get()

    /**
     * Cancels whatever scan is in progress and immediately kicks off a fresh one.
     * This is the safe path for "refresh on resume" — it never silently drops the request.
     */
    suspend fun cancelAndRestartScan() {
        Log.d(TAG, "cancelAndRestartScan: tearing down current scan")
        cancelCurrentScan()
        Log.d(TAG, "cancelAndRestartScan: starting fresh scan")
        processAudioFiles()
    }

    /**
     * Cancels the currently running scan and resets all state so a new one can
     * start cleanly. Unlike [cleanup], this leaves the loader fully operational.
     */
    private fun cancelCurrentScan() {
        // Cancelling the scope automatically cancels every coroutine child that was
        // launched within it — no need to track or cancel individual jobs. The old
        // approach kept a synchronized list of all 1000+ jobs and iterated over it,
        // which caused a noticeable main-thread monitor stall when cancel was called.
        Log.d(TAG, "Canceling scanScope (all child coroutines will be cancelled automatically)")
        scanScope.coroutineContext[Job]?.cancel()
        scanScope = newScanScope()

        indexedMap.clear()

        // Reset the running flag so the next scan can start immediately.
        // This is safe to call from any coroutine — AtomicBoolean has no
        // owner restriction, which is exactly why we chose it over Mutex here.
        isScanRunning.set(false)

        notification.dismissForce()
    }

    /**
     * Cancels all ongoing operations, clears the notification immediately, and
     * resets the loader. After this call the loader is still usable — a new
     * scan can be started whenever you are ready.
     */
    fun cleanup() {
        Log.d(TAG, "Cleanup called - canceling all operations")
        // Force-dismiss first so there is no zombie notification lingering
        // after the service shuts down, regardless of any in-flight finally blocks.
        notification.dismissForce()
        cancelCurrentScan()
        Log.d(TAG, "Cleanup complete - all operations canceled")
    }
}