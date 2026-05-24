package app.simple.felicity.repository.repositories

import android.content.Context
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.models.AudioBookmark
import app.simple.felicity.repository.models.AudioWithBookmarks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single source of truth for all bookmark reads and writes.
 *
 * Every operation here talks directly to Room through [BookmarkDao], keeping
 * database concerns completely out of the manager and ViewModel layers above.
 *
 * The 1-second resolution rule — no two bookmarks within 1 000 ms of each other —
 * is enforced by [addBookmark] before it touches the database, so callers never
 * need to think about it.
 *
 * @author Hamza417
 */
@Singleton
class BookmarkRepository @Inject constructor(
        @ApplicationContext private val context: Context
) {

    private val dao get() = AudioDatabase.getInstance(context).bookmarkDao()

    /**
     * Returns a live stream of all bookmarks for the given audio hash, ordered by
     * timestamp. The flow emits a fresh list whenever a bookmark is added or removed.
     */
    fun getBookmarksFlow(audioHash: Long): Flow<List<AudioBookmark>> {
        return dao.getBookmarksForAudio(audioHash)
    }

    /**
     * Returns all bookmarks for the given audio hash as a one-shot snapshot.
     * Useful when you need the list synchronously inside a coroutine without
     * keeping a long-lived flow collector open.
     */
    suspend fun getBookmarksOnce(audioHash: Long): List<AudioBookmark> {
        return dao.getBookmarksForAudioOnce(audioHash)
    }

    /**
     * Adds a bookmark at [timestampMs] for the track identified by [audioHash],
     * but only if no existing bookmark sits within 1 000 ms of the requested position.
     *
     * Returns `true` when the bookmark was inserted, `false` when it was rejected
     * because another bookmark was too close.
     */
    suspend fun addBookmark(audioHash: Long, timestampMs: Long): Boolean {
        val existing = dao.getBookmarksForAudioOnce(audioHash)
        val tooClose = existing.any { kotlin.math.abs(it.timestampMs - timestampMs) < MINIMUM_GAP_MS }
        if (tooClose) return false
        dao.insertBookmark(AudioBookmark(audioHash = audioHash, timestampMs = timestampMs))
        return true
    }

    /**
     * Removes the bookmark closest to [timestampMs] for the given audio hash,
     * as long as it sits within a small tolerance window. Does nothing if no
     * bookmark is that close.
     */
    suspend fun removeBookmarkNear(audioHash: Long, timestampMs: Long) {
        val existing = dao.getBookmarksForAudioOnce(audioHash)
        val target = existing.minByOrNull { kotlin.math.abs(it.timestampMs - timestampMs) }
        if (target != null && kotlin.math.abs(target.timestampMs - timestampMs) < MINIMUM_GAP_MS) {
            dao.deleteBookmark(target)
        }
    }

    /** Removes a specific bookmark by its exact database row. */
    suspend fun removeBookmark(bookmark: AudioBookmark) {
        dao.deleteBookmark(bookmark)
    }

    /** Wipes all bookmarks for a given audio hash. */
    suspend fun clearBookmarks(audioHash: Long) {
        dao.deleteAllBookmarksForAudio(audioHash)
    }

    /**
     * Returns a live stream of all bookmarked tracks as [AudioWithBookmarks] pairs.
     *
     * Every time a bookmark is added or removed, the flow emits a fresh list.
     * Tracks whose audio row no longer exists in the library are silently dropped —
     * their bookmarks stay in the database (non-cascading) but are not shown in the panel
     * until the file is re-scanned.
     *
     * Note: because [getAudioByHash] is a suspend DAO query, the outer [map] must run
     * inside a coroutine context. Call [kotlinx.coroutines.flow.flowOn] with
     * [kotlinx.coroutines.Dispatchers.IO] in the ViewModel to keep database work off
     * the main thread.
     */
    fun getAllWithAudio(): Flow<List<AudioWithBookmarks>> {
        val audioDao = AudioDatabase.getInstance(context).audioDao()
        return dao.getAllBookmarks().map { allBookmarks ->
            allBookmarks
                .groupBy { bookmark -> bookmark.audioHash }
                .mapNotNull { (hash, bookmarks) ->
                    val audio = audioDao?.getAudioByHash(hash) ?: return@mapNotNull null
                    AudioWithBookmarks(
                            audio = audio,
                            bookmarks = bookmarks.sortedBy { b -> b.timestampMs }
                    )
                }
                .sortedBy { entry -> entry.audio.title?.lowercase() }
        }
    }

    companion object {
        /** Two bookmarks must be at least this many milliseconds apart. */
        private const val MINIMUM_GAP_MS = 1_000L
    }
}

