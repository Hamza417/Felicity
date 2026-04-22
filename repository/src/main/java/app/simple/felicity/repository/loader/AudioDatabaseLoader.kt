package app.simple.felicity.repository.loader

import android.content.Context
import android.provider.DocumentsContract
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import app.simple.felicity.repository.database.dao.AudioDao
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.loader.MediaStorePaths.buildMediaStorePathMap
import app.simple.felicity.repository.metadata.MetaDataHelper.extractMetadata
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.notifications.LoaderNotification
import app.simple.felicity.repository.scanners.AudioScanner
import app.simple.felicity.repository.scanners.SAFFile
import app.simple.felicity.shared.utils.ProcessUtils.checkNotMainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.ConcurrentHashMap
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
 * All notification concerns live in [app.simple.felicity.repository.notifications.LoaderNotification] so this class can
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

    /**
     * Carries a parsed audio object from a producer coroutine to the single
     * database-writer consumer. Using a sealed class makes the insert/update
     * distinction explicit without needing a separate boolean flag.
     */
    private sealed class PendingWrite {
        abstract val audio: Audio
        abstract val path: String
        abstract val indexedFile: IndexedFile

        data class Insert(
                override val audio: Audio,
                override val path: String,
                override val indexedFile: IndexedFile
        ) : PendingWrite()

        data class Update(
                override val audio: Audio,
                override val path: String,
                override val indexedFile: IndexedFile
        ) : PendingWrite()
    }

    /** Per-instance index of paths we have already seen in the database. */
    private val indexedMap: ConcurrentHashMap<String, IndexedFile> = ConcurrentHashMap()

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

    /**
     * Returns the set of SAF tree URIs the user has actively granted access to,
     * reading directly from the system's persisted permission list rather than
     * a separate preferences store — this way the list always reflects reality.
     */
    private fun getGrantedTreeUriStrings(): Set<String> {
        return context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri.toString() }
            .toSet()
    }

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

        val generation = notification.begin()

        try {
            val startTime = System.currentTimeMillis()
            Log.d(TAG, "Starting audio file processing...")

            val dao = audioDatabase.audioDao()

            val treeUriStrings = getGrantedTreeUriStrings()
            if (treeUriStrings.isEmpty()) {
                Log.w(TAG, "No SAF tree URIs granted. Nothing to scan — ask the user to pick a folder first.")
                return
            }

            Log.d(TAG, "Reconciling database with current SAF grants...")
            reconcileDatabase(treeUriStrings)

            Log.d(TAG, "Indexing existing audio files in the database...")

            val pathToStoredId = HashMap<String, Long>()
            dao?.getAllAudioListAll()?.forEach { audio ->
                indexedMap[audio.uri] = IndexedFile(audio.dateModified, audio.size, audio.id, audio.isFavorite, audio.isAlwaysSkip)
                pathToStoredId[audio.uri] = audio.id
            }

            Log.d(TAG, "Indexing complete. Found ${indexedMap.size} existing entries in the database.")

            // Phase 1 — collect every audio file from all SAF tree URIs.
            val allSAFFiles = mutableListOf<SAFFile>()
            val scanner = AudioScanner()
            for (uriStr in treeUriStrings) {
                scanScope.coroutineContext.ensureActive()
                val treeUri = uriStr.toUri()
                val collectStart = System.currentTimeMillis()
                val files = scanner.getAudioFiles(context, treeUri)
                Log.d(TAG, "Found ${files.size} audio files in $uriStr (collected in ${System.currentTimeMillis() - collectStart}ms)")
                allSAFFiles.addAll(files)
            }

            notification.setTotal(allSAFFiles.size)
            Log.d(TAG, "Total audio files to evaluate: ${allSAFFiles.size}")


            val processedCount = AtomicInteger(0)

            val dbChannel = Channel<PendingWrite>(capacity = Channel.BUFFERED)

            val consumerJob = scanScope.launch {
                val insertBatch = mutableListOf<Audio>()
                val updateBatch = mutableListOf<Audio>()

                for (write in dbChannel) {
                    indexedMap[write.path] = write.indexedFile

                    when (write) {
                        is PendingWrite.Insert -> {
                            insertBatch.add(write.audio)
                            if (insertBatch.size >= BATCH_SIZE) {
                                val batch = insertBatch.toList()
                                insertBatch.clear()
                                Log.d(TAG, "Inserting batch of ${batch.size} audio items")
                                dao?.insertBatch(batch)
                            }
                        }
                        is PendingWrite.Update -> {
                            updateBatch.add(write.audio)
                            if (updateBatch.size >= BATCH_SIZE) {
                                val batch = updateBatch.toList()
                                updateBatch.clear()
                                Log.d(TAG, "Updating batch of ${batch.size} audio items")
                                dao?.update(batch)
                            }
                        }
                    }
                }

                if (insertBatch.isNotEmpty()) {
                    Log.d(TAG, "Inserting final batch of ${insertBatch.size} audio items")
                    dao?.insertBatch(insertBatch)
                }
                if (updateBatch.isNotEmpty()) {
                    Log.d(TAG, "Updating final batch of ${updateBatch.size} audio items")
                    dao?.update(updateBatch)
                }
            }

            // Phase 2 — process each SAFFile in parallel coroutines.
            val pendingJobs = mutableListOf<Job>()

            allSAFFiles.forEach { safFile ->
                scanScope.coroutineContext.ensureActive()

                val uriKey = safFile.uri.toString()
                if (shouldProcess(safFile)) {
                    val producerJob = scanScope.launch {
                        semaphore.acquire()
                        try {
                            ensureActive()

                            Log.d(TAG, "Processing: $uriKey")
                            val audio = safFile.extractMetadata(context)

                            ensureActive()

                            if (audio == null) {
                                Log.w(TAG, "Failed to extract metadata for: ${safFile.name}")
                                return@launch
                            }


                            val storedId = pathToStoredId[uriKey]
                            val stored = indexedMap[uriKey]
                            val newIndexEntry = IndexedFile(audio.dateModified, audio.size)

                            val write = if (storedId != null && storedId != 0L) {
                                audio.id = storedId
                                audio.isFavorite = stored?.isFavorite ?: false
                                audio.isAlwaysSkip = stored?.alwaysSkip ?: false
                                Log.d(TAG, "Updating existing entry (id=$storedId) for: ${safFile.name}")
                                PendingWrite.Update(audio, uriKey, newIndexEntry)
                            } else {
                                PendingWrite.Insert(audio, uriKey, newIndexEntry)
                            }

                            dbChannel.send(write)

                            val done = processedCount.incrementAndGet()
                            if (done % LoaderNotification.NOTIFICATION_UPDATE_INTERVAL == 0) {
                                notification.updateProgress(done)
                            }
                        } catch (e: CancellationException) {
                            Log.d(TAG, "Processing canceled for: ${safFile.name}")
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing $uriKey", e)
                        } finally {
                            semaphore.release()
                        }
                    }

                    pendingJobs.add(producerJob)
                }
            }

            pendingJobs.joinAll()
            dbChannel.close()
            consumerJob.join()

            notification.updateProgress(allSAFFiles.size)

            // Post-processing: fill in real filesystem paths for any audio rows that are missing one.
            // We query each content URI directly for the DATA column — same way MediaStoreCover
            // fetches album IDs — and batch-update the rows in one final write.
            Log.d(TAG, "Resolving filesystem paths from MediaStore...")
            resolveAndUpdatePaths(dao)

            Log.d(TAG, "Audio file processing complete in ${(System.currentTimeMillis() - startTime) / 1000} seconds.")
        } catch (e: CancellationException) {
            Log.d(TAG, "Audio file processing canceled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error during audio file processing", e)
        } finally {
            notification.dismiss(generation)
            isScanRunning.set(false)
        }
    }

    /**
     * Removes entries from the database that no longer exist on disk or have been excluded
     * by the current filter preferences. Works with SAF content URIs stored as paths.
     *
     * For each audio row in the database:
     * - If its path is a content URI, we check whether the document still exists.
     * - If the SAF tree that granted us access has been revoked entirely, we remove
     *   everything that lived inside that tree so the library doesn't ghost.
     */
    private suspend fun reconcileDatabase(grantedTreeUriStrings: Set<String>) {
        val dao = audioDatabase.audioDao() ?: return

        dao.deleteStalePathDuplicates()
        Log.d(TAG, "Stale path-duplicate purge complete.")

        val allAudio = dao.getAllAudioListAll()

        val toDelete = mutableListOf<Audio>()
        val toUpdate = mutableListOf<Audio>()

        allAudio.forEach { audio ->
            val path = audio.uri

            if (path.startsWith("content://")) {
                val docFile = DocumentFile.fromSingleUri(context, path.toUri())

                // Check if the tree that was covering this file is still in our granted list.
                // We compare the tree document IDs directly using DocumentsContract so that
                // encoding differences (e.g. %3A vs :) can never cause a false mismatch.
                val isTreeStillGranted = grantedTreeUriStrings.any { treeUriStr ->
                    try {
                        val docTreeId = DocumentsContract.getTreeDocumentId(path.toUri())
                        val grantedTreeId = DocumentsContract.getTreeDocumentId(treeUriStr.toUri())
                        docTreeId == grantedTreeId
                    } catch (e: Exception) {
                        false
                    }
                }

                when {
                    !isTreeStillGranted -> {
                        Log.d(TAG, "SAF tree revoked, removing: $path")
                        toDelete.add(audio)
                        indexedMap.remove(path)
                    }
                    docFile == null || !docFile.exists() -> {
                        if (audio.isAvailable) {
                            Log.d(TAG, "SAF document gone, deleting: $path")
                            toDelete.add(audio)
                            indexedMap.remove(path)
                        }
                    }
                    !audio.isAvailable -> {
                        Log.d(TAG, "Restoring available SAF file: $path")
                        audio.isAvailable = true
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
     * Goes through every audio row that has no filesystem path yet and fills it in by
     * matching title + artist + album against a single bulk MediaStore query.
     * Rows that already have a path are skipped so subsequent scans stay fast.
     */
    private suspend fun resolveAndUpdatePaths(dao: AudioDao?) {
        if (dao == null) return

        // One MediaStore query covers the whole library — no per-file ContentResolver calls.
        val pathMap = context.buildMediaStorePathMap()
        if (pathMap.isEmpty()) {
            Log.w(TAG, "MediaStore path map is empty — READ_MEDIA_AUDIO may not be granted yet.")
            return
        }

        val allAudio = dao.getAllAudioListAll()
        val toUpdate = mutableListOf<Audio>()

        allAudio.forEach { audio ->
            if (audio.path == null) {
                // Lower-case all three tags to match the case-insensitive map keys.
                val key = Triple(
                        audio.rawTitle?.lowercase() ?: "",
                        audio.artist?.lowercase() ?: "",
                        audio.album?.lowercase() ?: ""
                )
                val resolved = pathMap[key]
                if (resolved != null) {
                    audio.path = resolved
                    toUpdate.add(audio)
                }
            }
        }

        if (toUpdate.isNotEmpty()) {
            Log.d(TAG, "Resolved paths for ${toUpdate.size} audio entries")
            dao.update(toUpdate)
        } else {
            Log.d(TAG, "All paths already resolved, nothing to update")
        }
    }

    /**
     * Returns true when a [SAFFile] needs fresh metadata extraction — either because
     * it's brand new to the library, or because its size or last-modified timestamp
     * changed since we last indexed it. Because [SAFFile] already carries size and
     * lastModified from the bulk scan query, this check costs zero extra IPC calls.
     */
    private fun shouldProcess(safFile: SAFFile): Boolean {
        val key = safFile.uri.toString()
        val existing = indexedMap[key] ?: run {
            Log.d(TAG, "New SAF file (not in index): ${safFile.name}")
            return true
        }

        if (existing.size != safFile.size) {
            Log.d(TAG, "Size changed for ${safFile.name}: DB=${existing.size}, File=${safFile.size}")
            return true
        }

        if (existing.lastModified != safFile.lastModified) {
            Log.d(TAG, "Modified time changed for ${safFile.name}: DB=${existing.lastModified}, File=${safFile.lastModified}")
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
        Log.d(TAG, "Canceling scanScope (all child coroutines will be cancelled automatically)")
        scanScope.coroutineContext[Job]?.cancel()
        scanScope = newScanScope()

        indexedMap.clear()

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
        notification.dismissForce()
        cancelCurrentScan()
        Log.d(TAG, "Cleanup complete - all operations canceled")
    }
}