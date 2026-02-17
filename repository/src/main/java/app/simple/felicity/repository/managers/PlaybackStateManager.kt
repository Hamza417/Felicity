package app.simple.felicity.repository.managers

import android.content.Context
import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.PlaybackState
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.reflect.Type

object PlaybackStateManager {

    private const val TAG = "PlaybackStateManager"
    val type: Type = object : TypeToken<List<Long>>() {}.type

    /**
     * Saves the current playback state from MediaManager to the database.
     * This is a convenience function that handles all the common logic for saving playback state.
     *
     * @param context The application context
     * @param logTag Optional tag for logging (defaults to TAG)
     * @return true if state was saved successfully, false otherwise
     */
    suspend fun saveCurrentPlaybackState(context: Context, logTag: String = TAG): Boolean {
        val songs = MediaManager.getSongs()
        if (songs.isEmpty()) {
            Log.w(logTag, "Songs list is empty, skipping state save")
            return false
        }

        var seek = 0L
        var position = 0

        withContext(Dispatchers.Main) {
            seek = MediaManager.getSeekPosition()
            position = MediaManager.getCurrentPosition()
        }

        if (seek == 0L) {
            Log.w(logTag, "Seek position is zero, skipping state save")
            return false
        }

        return try {
            val audioDatabase = AudioDatabase.getInstance(context)
            savePlaybackState(
                    db = audioDatabase,
                    queueIds = songs.map { it.id },
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

    suspend fun savePlaybackState(
            db: AudioDatabase,
            queueIds: List<Long>,
            index: Int,
            position: Long,
            shuffle: Boolean,
            repeat: Int
    ) {
        if (queueIds.isEmpty()) return

        val state = PlaybackState(
                queue = Gson().toJson(queueIds),
                index = index,
                position = position,
                shuffle = shuffle,
                repeatMode = repeat,
                updatedAt = System.currentTimeMillis()
        )

        db.playbackStateDao().save(state)
    }

    suspend fun fetchPlaybackState(db: AudioDatabase): PlaybackState? {
        return db.playbackStateDao().get()
    }

    suspend fun fetchQueueIds(db: AudioDatabase): List<Long>? {
        val state = db.playbackStateDao().get() ?: return null
        return Gson().fromJson<List<Long>>(state.queue, type)
    }

    suspend fun getAudiosFromQueueIDs(db: AudioDatabase): MutableList<Audio>? {
        val queueIds = fetchQueueIds(db) ?: return null
        if (queueIds.isEmpty()) return null

        // Build ORDER BY CASE statement to preserve the order of IDs
        val orderByCase = queueIds.mapIndexed { index, id ->
            "WHEN id = $id THEN $index"
        }.joinToString(" ")

        val query = SimpleSQLiteQuery(
                "SELECT * FROM audio WHERE id IN (${queueIds.joinToString(",")}) ORDER BY CASE $orderByCase END"
        )

        return db.audioDao()?.getAudioByIDs(query)
    }
}