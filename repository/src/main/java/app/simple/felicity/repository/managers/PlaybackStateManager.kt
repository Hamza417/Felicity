package app.simple.felicity.repository.managers

import androidx.sqlite.db.SimpleSQLiteQuery
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.PlaybackState
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

object PlaybackStateManager {

    val type: Type = object : TypeToken<List<Long>>() {}.type

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