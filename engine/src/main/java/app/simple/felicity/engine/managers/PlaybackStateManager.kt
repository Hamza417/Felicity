package app.simple.felicity.engine.managers

import android.content.Context
import android.util.Log
import app.simple.felicity.preferences.ShufflePreferences
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.PlaybackQueueEntry
import app.simple.felicity.repository.models.PlaybackState
import app.simple.felicity.repository.models.SavedQueueEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages persistence and restoration of playback state across five independent
 * queue slots.
 *
 * <p>The active (currently playing) queue is stored as individual
 * {@link PlaybackQueueEntry} rows in {@code playback_queue} — exactly as before —
 * so that every other part of the app continues to read the queue from the same
 * place without any refactoring. Scalar state (index, seek position, repeat mode,
 * active queue ID) is kept in a single-row {@link PlaybackState} record.</p>
 *
 * <p>When the user switches queues, the current queue is first archived into its
 * slot in {@code saved_queue}, then the target queue is loaded from its slot and
 * written into {@code playback_queue}. The active queue ID in {@code playback_state}
 * is updated so the correct queue is restored on the next app launch.</p>
 *
 * <p>Queue slot IDs range from 0 to 4 (five total). Slot 0 is the default and
 * represents the original single-queue behavior.</p>
 *
 * @author Hamza417
 */
object PlaybackStateManager {

    private const val TAG = "PlaybackStateManager"

    /** Number of independent queue slots available to the user. */
    const val QUEUE_COUNT = 10

    /**
     * Bundles a restored queue together with the playback position the user was at
     * when they last left that queue, so [MediaPlaybackManager] can resume exactly
     * where they left off instead of resetting to song 0 every time.
     */
    data class QueueSwitchResult(
            val songs: List<Audio>,
            val lastPosition: Int,
            val lastSeek: Long
    )

    /**
     * Saves the current playback state from [MediaPlaybackManager] to the database.
     *
     * <p>This writes two things atomically:</p>
     * <ol>
     *   <li>The active queue (as {@link PlaybackQueueEntry} rows) and scalar state
     *       (index, seek, shuffle) into their primary tables so the app can restore
     *       on next launch.</li>
     *   <li>The same queue into the {@code saved_queue} slot matching the currently
     *       active queue ID, so the archive stays up-to-date for queue switching.</li>
     * </ol>
     *
     * @param context  The application context.
     * @param logTag   Optional tag for logging (defaults to TAG).
     * @return {@code true} if state was saved successfully, {@code false} otherwise.
     */
    suspend fun saveCurrentPlaybackState(context: Context, logTag: String = TAG): Boolean {
        // Always persist the original (unshuffled) queue so we can restore and optionally
        // re-shuffle on the next launch rather than saving an already-shuffled order.
        val songs = MediaPlaybackManager.getSongs()
        if (songs.isEmpty()) {
            Log.w(logTag, "Songs list is empty, skipping state save")
            return false
        }

        var seek = 0L
        var position = 0

        withContext(Dispatchers.Main) {
            seek = MediaPlaybackManager.getSeekPosition()
            // When shuffle is on the active queue is shuffled, but the index we want to
            // restore is the one matching the current song inside the ORIGINAL queue.
            val currentSong = MediaPlaybackManager.getCurrentSong()
            position = if (currentSong != null) {
                songs.indexOfFirst { it.id == currentSong.id }.coerceAtLeast(0)
            } else {
                MediaPlaybackManager.getCurrentSongPosition()
            }
        }

        return try {
            val audioDatabase = AudioDatabase.getInstance(context)
            val activeQueueId = MediaPlaybackManager.getActiveQueueId()
            savePlaybackState(
                    db = audioDatabase,
                    queueHash = songs.map { it.hash },
                    index = position,
                    position = seek,
                    shuffle = ShufflePreferences.isShuffleEnabled(),
                    repeat = 0,
                    activeQueueId = activeQueueId
            )
            Log.d(
                    logTag, "Playback state saved: position=$position, seek=$seek, " +
                    "queueSize=${songs.size}, shuffle=${ShufflePreferences.isShuffleEnabled()}, " +
                    "activeQueue=$activeQueueId"
            )
            true
        } catch (e: Exception) {
            Log.e(logTag, "Error saving playback state", e)
            false
        }
    }

    /**
     * Persists the given queue and scalar playback state to the database.
     *
     * <p>The previous queue rows are deleted and replaced atomically so there are never
     * stale slots from a prior session. The queue is also archived into its matching
     * {@code saved_queue} slot so queue-switching always sees the latest state.</p>
     *
     * @param db            The open [AudioDatabase] instance.
     * @param queueHash     Ordered list of audio hashes representing the queue.
     * @param index         Index of the currently active song within [queueHash].
     * @param position      Seek position in milliseconds.
     * @param shuffle       Whether shuffle mode was active.
     * @param repeat        Repeat mode constant.
     * @param activeQueueId Which queue slot (0–4) this state belongs to.
     */
    suspend fun savePlaybackState(
            db: AudioDatabase,
            queueHash: List<Long>,
            index: Int,
            position: Long,
            shuffle: Boolean,
            repeat: Int,
            activeQueueId: Int = 0
    ) {
        if (queueHash.isEmpty()) return

        val currentHash = queueHash.getOrElse(index) { 0L }

        val state = PlaybackState(
                index = index,
                position = position,
                shuffle = shuffle,
                repeatMode = repeat,
                updatedAt = System.currentTimeMillis(),
                currentHash = currentHash,
                activeQueueId = activeQueueId
        )

        val entries = queueHash.mapIndexed { pos, hash ->
            PlaybackQueueEntry(queuePos = pos, audioHash = hash)
        }

        db.playbackQueueDao().clear()
        db.playbackQueueDao().insertAll(entries)
        db.playbackStateDao().save(state)

        // Mirror the queue into the saved_queue archive so queue-switching always
        // has an up-to-date copy of the active slot, including the current playback
        // position so we can resume at the right song later.
        saveQueueToSlot(db, activeQueueId, queueHash, index, position)
    }

    /**
     * Archives a queue into the {@code saved_queue} table under the given slot ID.
     *
     * <p>Any previous contents for this slot are cleared first so the archive
     * never contains stale remnants from an older version of the same queue.</p>
     *
     * <p>The current [position] (song index) and [seek] (milliseconds into that
     * song) are stored on every row so the restore position survives even when
     * individual songs are cascade-deleted — the first surviving row still carries
     * the correct values.</p>
     *
     * @param db        The open [AudioDatabase] instance.
     * @param queueId   The slot to save into (0–4).
     * @param queueHash Ordered list of audio hashes.
     * @param position  The song index that is currently playing.
     * @param seek      The seek offset in milliseconds within the current song.
     */
    suspend fun saveQueueToSlot(
            db: AudioDatabase,
            queueId: Int,
            queueHash: List<Long>,
            position: Int = 0,
            seek: Long = 0L
    ) {
        if (queueHash.isEmpty()) return
        val entries = queueHash.mapIndexed { pos, hash ->
            SavedQueueEntry(
                    queueId = queueId,
                    queuePos = pos,
                    audioHash = hash,
                    lastPosition = position,
                    lastSeek = seek
            )
        }
        db.savedQueueDao().clear(queueId)
        db.savedQueueDao().insertAll(entries)
    }

    /**
     * Loads a previously archived queue from the {@code saved_queue} table.
     *
     * <p>Only tracks still present in the library are returned — any song that has
     * been deleted since the last save is silently omitted thanks to the INNER JOIN
     * against the {@code audio} table.</p>
     *
     * @param db      The open [AudioDatabase] instance.
     * @param queueId The slot to load from (0–4).
     * @return The restored queue as a list of [Audio], or {@code null} if the
     *         slot is empty or all its songs have been removed from the library.
     */
    suspend fun loadQueueFromSlot(
            db: AudioDatabase,
            queueId: Int
    ): MutableList<Audio>? {
        val audios = db.savedQueueDao().getQueuedAudios(queueId)
        return if (audios.isEmpty()) null else audios.toMutableList()
    }

    /**
     * Switches the active playback queue to the given slot.
     *
     * <p>This performs three steps atomically:</p>
     * <ol>
     *   <li>Saves the currently active queue into its own {@code saved_queue} slot
     *       (so nothing is lost), including the current playback position.</li>
     *   <li>Loads the target queue from {@code saved_queue} and writes it into
     *       {@code playback_queue} so the rest of the app sees it.</li>
     *   <li>Updates {@code playback_state.active_queue_id} so the correct slot
     *       is restored on the next cold launch.</li>
     * </ol>
     *
     * <p>The returned [QueueSwitchResult] carries both the loaded songs and the
     * last-known playback position for that queue so the caller can seek to the
     * exact song and offset the user left off at.</p>
     *
     * @param context         The application context.
     * @param currentQueueId  The queue ID we are switching FROM (captured before
     *                        the caller updates its in-memory state).
     * @param targetQueueId   The slot to switch to (0–4).
     * @param currentPosition The song index currently playing (captured on main
     *                        thread before entering IO context).
     * @param currentSeek     The seek offset currently playing (captured on main
     *                        thread before entering IO context).
     * @return A [QueueSwitchResult] with the loaded songs and the last playback
     *         position/seek for the target queue.
     */
    suspend fun switchToQueue(
            context: Context,
            currentQueueId: Int,
            targetQueueId: Int,
            currentPosition: Int = 0,
            currentSeek: Long = 0L
    ): QueueSwitchResult {
        val db = AudioDatabase.getInstance(context)

        // Step 1: Archive the currently active queue into its saved slot,
        //         including the current playback position so we can resume
        //         exactly where we left off when switching back later.
        val currentSongs = MediaPlaybackManager.getSongs()
        if (currentSongs.isNotEmpty()) {
            saveQueueToSlot(db, currentQueueId, currentSongs.map { it.hash }, currentPosition, currentSeek)
            Log.d(TAG, "switchToQueue: archived queue $currentQueueId (${currentSongs.size} songs) " +
                    "at position=$currentPosition, seek=$currentSeek")
        }

        // Step 2: Load the target queue from its saved slot.
        val targetSongs = loadQueueFromSlot(db, targetQueueId)

        // Read the last playback position stored for this queue.
        // The values are denormalized across all rows of the same queue so
        // the first entry (lowest queue_pos) carries the correct restore data.
        val firstEntry = db.savedQueueDao().getQueue(targetQueueId).firstOrNull()
        val restoredPosition = firstEntry?.lastPosition ?: 0
        val restoredSeek = firstEntry?.lastSeek ?: 0L

        // Step 3: Update the active queue ID in playback_state so the correct
        //         slot is restored on the next cold launch.
        val currentState = fetchPlaybackState(db)
        if (currentState != null) {
            db.playbackStateDao().save(currentState.copy(activeQueueId = targetQueueId))
        } else {
            // No state row exists yet — create a minimal one with just the queue ID.
            db.playbackStateDao().save(
                    PlaybackState(activeQueueId = targetQueueId, updatedAt = System.currentTimeMillis())
            )
        }

        if (targetSongs.isNullOrEmpty()) {
            Log.d(TAG, "switchToQueue: queue $targetQueueId is empty, returning empty list")
            return QueueSwitchResult(emptyList(), 0, 0L)
        }

        // Clamp the restored position to the valid range — cascade-deleted songs
        // may have shrunk the queue since the last save.
        val clampedPosition = restoredPosition.coerceIn(0, (targetSongs.size - 1).coerceAtLeast(0))

        // Write the loaded queue into playback_queue so every other part of the
        // app that reads from there (BaseActivity restore, widget, etc.) sees it.
        val entries = targetSongs.mapIndexed { pos, audio ->
            PlaybackQueueEntry(queuePos = pos, audioHash = audio.hash)
        }
        db.playbackQueueDao().clear()
        db.playbackQueueDao().insertAll(entries)

        Log.d(
                TAG, "switchToQueue: switched from queue $currentQueueId to $targetQueueId " +
                "(${targetSongs.size} songs loaded), " +
                "restoredPosition=$clampedPosition, restoredSeek=$restoredSeek"
        )
        return QueueSwitchResult(targetSongs, clampedPosition, restoredSeek)
    }

    /**
     * Returns the ID of the queue that was active when playback state was last saved.
     *
     * @param db The open [AudioDatabase] instance.
     * @return The active queue ID (0–4), defaulting to 0 if no state exists.
     */
    suspend fun getActiveQueueId(db: AudioDatabase): Int {
        return fetchPlaybackState(db)?.activeQueueId ?: 0
    }

    /**
     * Returns the last saved [PlaybackState], or {@code null} if none exists.
     *
     * @param db The open [AudioDatabase] instance.
     */
    suspend fun fetchPlaybackState(db: AudioDatabase): PlaybackState? {
        return db.playbackStateDao().get()
    }

    /**
     * Returns the restored queue as an ordered list of [Audio] objects from the
     * active {@code playback_queue} table.
     *
     * <p>Songs that were cascade-deleted since the last save are absent from the
     * result automatically — no stale entries are ever returned.</p>
     *
     * @param db The open [AudioDatabase] instance.
     * @return The queue, or {@code null} if no queue was saved.
     */
    suspend fun getAudiosFromQueueIDs(db: AudioDatabase): MutableList<Audio>? {
        val audios = db.playbackQueueDao().getQueuedAudios()
        return if (audios.isEmpty()) null else audios.toMutableList()
    }
}

