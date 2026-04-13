package app.simple.felicity.engine.managers

import android.content.Context
import android.util.Log
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.PlaybackQueueEntry
import app.simple.felicity.repository.models.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages persistence and restoration of playback state.
 *
 * <p>The current queue is stored as individual {@link PlaybackQueueEntry} rows so that
 * SQLite's cascade-delete mechanism automatically prunes any song removed from the
 * library. Scalar state (index, seek position, repeat mode) is kept in a single-row
 * {@link PlaybackState} record. The active song's hash is also stored so that
 * {@link #getAudiosFromQueueIDs} can resolve the correct queue index even when
 * cascade deletions have shifted positions.</p>
 *
 * @author Hamza417
 */
object PlaybackStateManager {

    private const val TAG = "PlaybackStateManager"

    /**
     * Saves the current playback state from [MediaPlaybackManager] to the database.
     *
     * @param context  The application context.
     * @param logTag   Optional tag for logging (defaults to TAG).
     * @return {@code true} if state was saved successfully, {@code false} otherwise.
     */
    suspend fun saveCurrentPlaybackState(context: Context, logTag: String = TAG): Boolean {
        val songs = MediaPlaybackManager.getSongs()
        if (songs.isEmpty()) {
            Log.w(logTag, "Songs list is empty, skipping state save")
            return false
        }

        var seek = 0L
        var position = 0

        withContext(Dispatchers.Main) {
            seek = MediaPlaybackManager.getSeekPosition()
            position = MediaPlaybackManager.getCurrentSongPosition()
        }

        // We intentionally allow seek == 0 here. When the user swipes to a new song
        // while the player is paused, the seek position of the newly loaded song is
        // naturally 0ms (it just got there). Skipping the save in that case would
        // mean the queue index is never written to the database, so restarting the
        // app would take the user right back to the song they were on 20 swipes ago.
        // The only time we truly have nothing worth saving is when the queue is empty,
        // and that is already guarded by the songs.isEmpty() check above.

        return try {
            val audioDatabase = AudioDatabase.getInstance(context)
            savePlaybackState(
                    db = audioDatabase,
                    queueHash = songs.map { it.hash },
                    index = position,
                    position = seek,
                    shuffle = false,
                    repeat = 0
            )
            Log.d(logTag, "Playback state saved: position=$position, seek=$seek, queueSize=${songs.size}")
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
     * stale slots from a prior session.</p>
     *
     * @param db        The open [AudioDatabase] instance.
     * @param queueHash Ordered list of audio hashes representing the queue.
     * @param index     Index of the currently active song within [queueHash].
     * @param position  Seek position in milliseconds.
     * @param shuffle   Whether shuffle mode was active.
     * @param repeat    Repeat mode constant.
     */
    suspend fun savePlaybackState(
            db: AudioDatabase,
            queueHash: List<Long>,
            index: Int,
            position: Long,
            shuffle: Boolean,
            repeat: Int
    ) {
        if (queueHash.isEmpty()) return

        val currentHash = queueHash.getOrElse(index) { 0L }

        val state = PlaybackState(
                index = index,
                position = position,
                shuffle = shuffle,
                repeatMode = repeat,
                updatedAt = System.currentTimeMillis(),
                currentHash = currentHash
        )

        val entries = queueHash.mapIndexed { pos, hash ->
            PlaybackQueueEntry(queuePos = pos, audioHash = hash)
        }

        db.playbackQueueDao().clear()
        db.playbackQueueDao().insertAll(entries)
        db.playbackStateDao().save(state)
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
     * Returns the restored queue as an ordered list of [Audio] objects.
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

