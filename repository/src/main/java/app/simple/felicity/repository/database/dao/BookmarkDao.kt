package app.simple.felicity.repository.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.simple.felicity.repository.models.AudioBookmark
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for bookmark records stored in the {@code audio_bookmarks} table.
 *
 * <p>Bookmarks are keyed by the audio content hash, so they are not tied to a specific
 * library row — they survive library rescans and track deletions.</p>
 *
 * <p>The 1-second resolution rule (no two bookmarks within 1000 ms of each other) is
 * enforced at the application layer in {@link BookmarkRepository} before calling
 * {@link #insertBookmark}. The unique index on {@code (audioHash, timestampMs)}
 * is an additional safety net that silently replaces duplicates at the exact same
 * millisecond.</p>
 */
@Dao
interface BookmarkDao {

    /**
     * Returns all bookmarks for the given audio hash, ordered from first to last.
     */
    @Query("SELECT * FROM audio_bookmarks WHERE audioHash = :audioHash ORDER BY timestampMs ASC")
    fun getBookmarksForAudio(audioHash: Long): Flow<List<AudioBookmark>>

    /**
     * Returns all bookmarks for the given audio hash as a plain list, useful for
     * one-shot reads inside a coroutine.
     */
    @Query("SELECT * FROM audio_bookmarks WHERE audioHash = :audioHash ORDER BY timestampMs ASC")
    suspend fun getBookmarksForAudioOnce(audioHash: Long): List<AudioBookmark>

    /**
     * Inserts a bookmark. If a bookmark at the exact same millisecond already exists for
     * this track the new row replaces the old one (same effective result).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: AudioBookmark)

    @Delete
    suspend fun deleteBookmark(bookmark: AudioBookmark)

    /** Removes all bookmarks for a given audio hash in one shot. */
    @Query("DELETE FROM audio_bookmarks WHERE audioHash = :audioHash")
    suspend fun deleteAllBookmarksForAudio(audioHash: Long)

    /**
     * Returns every bookmark in the table, across all tracks, ordered by creation time.
     * Used by the Bookmarks panel to show all bookmarked songs at a glance.
     */
    @Query("SELECT * FROM audio_bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<AudioBookmark>>

    /** Wipes the entire bookmarks table. */
    @Query("DELETE FROM audio_bookmarks")
    suspend fun deleteAllBookmarks()
}

