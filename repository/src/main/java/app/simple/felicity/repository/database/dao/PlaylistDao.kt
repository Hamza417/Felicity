package app.simple.felicity.repository.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Playlist
import app.simple.felicity.repository.models.PlaylistSongCrossRef
import app.simple.felicity.repository.models.PlaylistWithSongs
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the {@code playlists} and {@code playlist_song_cross_ref} tables.
 *
 * <p>All queries that return live data expose a Kotlin {@link Flow} so that the UI
 * automatically re-renders whenever the underlying tables change — consistent with the
 * rest of the DAO layer in this module.</p>
 *
 * @author Hamza417
 */
@Dao
interface PlaylistDao {

    /**
     * Returns all playlists ordered alphabetically by name as a reactive [Flow].
     */
    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE ASC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    /**
     * Returns all playlists with pinned ones at the top, then the rest alphabetically.
     */
    @Query("SELECT * FROM playlists ORDER BY is_pinned DESC, name COLLATE NOCASE ASC")
    fun getAllPlaylistsPinned(): Flow<List<Playlist>>

    /**
     * Returns a single playlist by primary key, or {@code null} if not found.
     *
     * @param playlistId The playlist's auto-generated id.
     */
    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: Long): Playlist?

    /**
     * Returns a single playlist by primary key as a reactive [Flow].
     *
     * @param playlistId The playlist's auto-generated id.
     */
    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    fun getPlaylistByIdFlow(playlistId: Long): Flow<Playlist?>

    /**
     * Inserts a new playlist row and returns the auto-generated row id.
     *
     * @param playlist The playlist to insert.
     * @return The Room-assigned primary key of the newly created row.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    /**
     * Updates an existing playlist row matched by primary key.
     *
     * @param playlist The playlist containing updated values.
     */
    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    /**
     * Deletes a playlist row. All associated {@link PlaylistSongCrossRef} rows are
     * removed automatically by the cascade-delete foreign key.
     *
     * @param playlist The playlist to delete.
     */
    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    /**
     * Stamps the {@code date_modified} column with the given timestamp.
     *
     * @param playlistId The target playlist id.
     * @param timestamp  Epoch-millisecond value to store.
     */
    @Query("UPDATE playlists SET date_modified = :timestamp WHERE id = :playlistId")
    suspend fun touchModified(playlistId: Long, timestamp: Long)

    /**
     * Stamps the {@code last_accessed} column with the given timestamp.
     *
     * @param playlistId The target playlist id.
     * @param timestamp  Epoch-millisecond value to store.
     */
    @Query("UPDATE playlists SET last_accessed = :timestamp WHERE id = :playlistId")
    suspend fun touchLastAccessed(playlistId: Long, timestamp: Long)

    /**
     * Updates the per-playlist sort preference without touching any other column.
     *
     * @param playlistId The target playlist id.
     * @param sortOrder  The sort-field constant, or {@code -1} for manual order.
     * @param sortStyle  The sort-direction constant (ascending / descending).
     */
    @Query("UPDATE playlists SET sort_order = :sortOrder, sort_style = :sortStyle WHERE id = :playlistId")
    suspend fun updateSortPreference(playlistId: Long, sortOrder: Int, sortStyle: Int)

    /**
     * Returns every [PlaylistWithSongs] as a reactive [Flow], ordered alphabetically.
     * Room automatically populates the nested song list via the junction table.
     */
    @Transaction
    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE ASC")
    fun getAllPlaylistsWithSongs(): Flow<List<PlaylistWithSongs>>

    /**
     * Returns the [PlaylistWithSongs] for the given playlist as a reactive [Flow].
     *
     * @param playlistId The target playlist id.
     */
    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun getPlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongs?>

    /**
     * Returns the songs of a playlist in their manually-stored position order
     * as a reactive [Flow]. Backed by an explicit JOIN so order is guaranteed.
     *
     * @param playlistId The target playlist id.
     */
    @Query("""
        SELECT a.* FROM audio a
        INNER JOIN playlist_song_cross_ref cr ON a.hash = cr.audio_hash
        WHERE cr.playlist_id = :playlistId
        ORDER BY cr.position ASC
    """)
    fun getSongsInPlaylistOrdered(playlistId: Long): Flow<List<Audio>>

    /**
     * Returns the cross-ref rows for a playlist sorted by position, as a reactive [Flow].
     *
     * @param playlistId The target playlist id.
     */
    @Query("SELECT * FROM playlist_song_cross_ref WHERE playlist_id = :playlistId ORDER BY position ASC")
    fun getCrossRefsByPlaylistId(playlistId: Long): Flow<List<PlaylistSongCrossRef>>

    /**
     * Inserts a single [PlaylistSongCrossRef] row. If the song is already in the
     * playlist the row is replaced (idempotent).
     *
     * @param crossRef The junction row to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    /**
     * Inserts a batch of [PlaylistSongCrossRef] rows. Existing rows sharing the same
     * composite key are replaced.
     *
     * @param crossRefs The list of junction rows to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongsToPlaylist(crossRefs: List<PlaylistSongCrossRef>)

    /**
     * Removes a single song from a playlist by deleting its junction row.
     *
     * @param playlistId The target playlist id.
     * @param audioHash  The XXHash64 fingerprint of the song to remove.
     */
    @Query("DELETE FROM playlist_song_cross_ref WHERE playlist_id = :playlistId AND audio_hash = :audioHash")
    suspend fun removeSongFromPlaylist(playlistId: Long, audioHash: Long)

    /**
     * Removes every song from a playlist without deleting the playlist row itself.
     *
     * @param playlistId The target playlist id.
     */
    @Query("DELETE FROM playlist_song_cross_ref WHERE playlist_id = :playlistId")
    suspend fun removeAllSongsFromPlaylist(playlistId: Long)

    /**
     * Returns a reactive [Flow] emitting the current song count for the given playlist.
     *
     * @param playlistId The target playlist id.
     */
    @Query("SELECT COUNT(*) FROM playlist_song_cross_ref WHERE playlist_id = :playlistId")
    fun getSongCountFlow(playlistId: Long): Flow<Int>

    /**
     * Returns the highest {@code position} value currently stored in the playlist,
     * or {@code -1} if the playlist is empty. Used to append songs at the end.
     *
     * @param playlistId The target playlist id.
     * @return The maximum position value, or {@code -1} when the playlist is empty.
     */
    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_song_cross_ref WHERE playlist_id = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int

    /**
     * Returns {@code true} if the given audio hash is already a member of the playlist.
     *
     * @param playlistId The target playlist id.
     * @param audioHash  The XXHash64 fingerprint to check.
     */
    @Query("SELECT COUNT(*) > 0 FROM playlist_song_cross_ref WHERE playlist_id = :playlistId AND audio_hash = :audioHash")
    suspend fun isSongInPlaylist(playlistId: Long, audioHash: Long): Boolean

    /**
     * Updates the manual position of a single song within a playlist.
     *
     * @param playlistId  The target playlist id.
     * @param audioHash   The track whose position should change.
     * @param newPosition The new zero-based position index.
     */
    @Query("UPDATE playlist_song_cross_ref SET position = :newPosition WHERE playlist_id = :playlistId AND audio_hash = :audioHash")
    suspend fun updateSongPosition(playlistId: Long, audioHash: Long, newPosition: Int)

    /**
     * Deletes all playlist rows. All cross-ref rows are also removed automatically
     * by the cascade-delete foreign key on {@code playlist_id}.
     */
    @Query("DELETE FROM playlists")
    suspend fun nukeAllPlaylists()
}

