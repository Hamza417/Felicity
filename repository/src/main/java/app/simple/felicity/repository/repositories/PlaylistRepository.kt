package app.simple.felicity.repository.repositories

import android.content.Context
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Playlist
import app.simple.felicity.repository.models.PlaylistSongCrossRef
import app.simple.felicity.repository.models.PlaylistWithSongs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing playlists and their song associations.
 *
 * <p>All blocking database work is dispatched to [Dispatchers.IO]. Reactive results are
 * returned as [Flow] objects so that the UI layer can observe changes without polling.
 * The underlying DAO operations that return [Flow] are cold streams — Room re-emits
 * whenever the {@code playlists} or {@code playlist_song_cross_ref} tables change.</p>
 *
 * @author Hamza417
 */
@Singleton
class PlaylistRepository @Inject constructor(
        @param:ApplicationContext private val context: Context
) {

    private val database: AudioDatabase by lazy {
        AudioDatabase.getInstance(context)
    }

    private val dao get() = database.playlistDao()

    /**
     * Returns a reactive [Flow] of all playlists ordered alphabetically by name.
     */
    fun getAllPlaylists(): Flow<List<Playlist>> = dao.getAllPlaylists()

    /**
     * Returns a reactive [Flow] of all playlists with pinned ones at the top,
     * then the rest sorted alphabetically.
     */
    fun getAllPlaylistsPinned(): Flow<List<Playlist>> = dao.getAllPlaylistsPinned()

    /**
     * Returns a reactive [Flow] emitting every playlist paired with its full song list.
     * Song order within each playlist is not guaranteed here; use
     * [getSongsInPlaylistOrdered] for position-aware retrieval.
     */
    fun getAllPlaylistsWithSongs(): Flow<List<PlaylistWithSongs>> =
        dao.getAllPlaylistsWithSongs()

    /**
     * Returns a reactive [Flow] for a single playlist paired with its songs.
     *
     * @param playlistId The primary key of the target playlist.
     */
    fun getPlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongs?> =
        dao.getPlaylistWithSongs(playlistId)

    /**
     * Returns a reactive [Flow] of [Audio] tracks belonging to the playlist in the
     * user-defined manual order (sorted by {@code position ASC}).
     *
     * @param playlistId The primary key of the target playlist.
     */
    fun getSongsInPlaylistOrdered(playlistId: Long): Flow<List<Audio>> =
        dao.getSongsInPlaylistOrdered(playlistId)

    /**
     * Returns a reactive [Flow] emitting the current song count for the given playlist.
     *
     * @param playlistId The primary key of the target playlist.
     */
    fun getSongCount(playlistId: Long): Flow<Int> = dao.getSongCountFlow(playlistId)

    /**
     * Returns a reactive [Flow] for a single playlist's metadata.
     *
     * @param playlistId The primary key of the target playlist.
     */
    fun getPlaylistByIdFlow(playlistId: Long): Flow<Playlist?> =
        dao.getPlaylistByIdFlow(playlistId)

    /**
     * Creates a new playlist and returns its auto-generated id.
     *
     * @param name        The display name for the new playlist.
     * @param description Optional description text.
     * @return The Room-assigned primary key of the newly inserted playlist.
     */
    suspend fun createPlaylist(name: String, description: String? = null): Long =
        withContext(Dispatchers.IO) {
            dao.insertPlaylist(Playlist(name = name, description = description))
        }

    /**
     * Renames the playlist and updates its {@code date_modified} timestamp.
     *
     * @param playlist The playlist to update (its {@code id} must match an existing row).
     * @param newName  The new display name.
     */
    suspend fun renamePlaylist(playlist: Playlist, newName: String) =
        withContext(Dispatchers.IO) {
            dao.updatePlaylist(
                    playlist.copy(
                            name = newName,
                            dateModified = System.currentTimeMillis()
                    )
            )
        }

    /**
     * Persists a full [Playlist] object update (all mutable fields).
     *
     * @param playlist The updated playlist object.
     */
    suspend fun updatePlaylist(playlist: Playlist) = withContext(Dispatchers.IO) {
        dao.updatePlaylist(playlist)
    }

    /**
     * Deletes a playlist and all of its song associations (removed via cascade FK).
     *
     * @param playlist The playlist to delete.
     */
    suspend fun deletePlaylist(playlist: Playlist) = withContext(Dispatchers.IO) {
        dao.deletePlaylist(playlist)
    }

    /**
     * Stamps the {@code last_accessed} field with the current wall-clock time.
     * Should be called whenever the user opens a playlist.
     *
     * @param playlistId The primary key of the accessed playlist.
     */
    suspend fun markAccessed(playlistId: Long) = withContext(Dispatchers.IO) {
        dao.touchLastAccessed(playlistId, System.currentTimeMillis())
    }

    /**
     * Updates the per-playlist sort preference stored inside the playlist row and
     * stamps {@code date_modified}.
     *
     * @param playlistId The target playlist.
     * @param sortOrder  The sort-field constant ({@code BY_*} from
     *                   CommonPreferencesConstants, or {@code -1} for manual order).
     * @param sortStyle  The sort-direction constant ({@code ASCENDING} or {@code DESCENDING}).
     */
    suspend fun updateSortPreference(playlistId: Long, sortOrder: Int, sortStyle: Int) =
        withContext(Dispatchers.IO) {
            dao.updateSortPreference(playlistId, sortOrder, sortStyle)
            dao.touchModified(playlistId, System.currentTimeMillis())
        }

    /**
     * Appends a single [Audio] track to the end of the playlist and stamps
     * {@code date_modified}.
     *
     * @param playlistId The target playlist.
     * @param audioHash  The XXHash64 fingerprint of the audio track ({@code audio.hash}).
     */
    suspend fun addSong(playlistId: Long, audioHash: Long) = withContext(Dispatchers.IO) {
        val nextPos = dao.getMaxPosition(playlistId) + 1
        dao.addSongToPlaylist(
                PlaylistSongCrossRef(
                        playlistId = playlistId,
                        audioHash = audioHash,
                        position = nextPos
                )
        )
        dao.touchModified(playlistId, System.currentTimeMillis())
    }

    /**
     * Appends a batch of [Audio] tracks to the end of the playlist in the order
     * provided, then stamps {@code date_modified}.
     *
     * @param playlistId  The target playlist.
     * @param audioHashes Ordered list of XXHash64 fingerprints to add.
     */
    suspend fun addSongs(playlistId: Long, audioHashes: List<Long>) =
        withContext(Dispatchers.IO) {
            val startPos = dao.getMaxPosition(playlistId) + 1
            val crossRefs = audioHashes.mapIndexed { index, hash ->
                PlaylistSongCrossRef(
                        playlistId = playlistId,
                        audioHash = hash,
                        position = startPos + index
                )
            }
            dao.addSongsToPlaylist(crossRefs)
            dao.touchModified(playlistId, System.currentTimeMillis())
        }

    /**
     * Removes a single song from the playlist by its audio hash and stamps
     * {@code date_modified}.
     *
     * @param playlistId The target playlist.
     * @param audioHash  The XXHash64 fingerprint of the track to remove.
     */
    suspend fun removeSong(playlistId: Long, audioHash: Long) = withContext(Dispatchers.IO) {
        dao.removeSongFromPlaylist(playlistId, audioHash)
        dao.touchModified(playlistId, System.currentTimeMillis())
    }

    /**
     * Removes all songs from the playlist without deleting the playlist row, then
     * stamps {@code date_modified}.
     *
     * @param playlistId The target playlist.
     */
    suspend fun clearPlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        dao.removeAllSongsFromPlaylist(playlistId)
        dao.touchModified(playlistId, System.currentTimeMillis())
    }

    /**
     * Returns {@code true} if the given audio hash is already a member of the playlist.
     *
     * @param playlistId The target playlist.
     * @param audioHash  The XXHash64 fingerprint to check.
     */
    suspend fun isSongInPlaylist(playlistId: Long, audioHash: Long): Boolean =
        withContext(Dispatchers.IO) {
            dao.isSongInPlaylist(playlistId, audioHash)
        }

    /**
     * Updates the manual position of a single song inside the playlist and stamps
     * {@code date_modified}. Typically called after a single drag-and-drop reorder.
     *
     * @param playlistId  The target playlist.
     * @param audioHash   The track whose position should change.
     * @param newPosition The new zero-based position index.
     */
    suspend fun updateSongPosition(playlistId: Long, audioHash: Long, newPosition: Int) =
        withContext(Dispatchers.IO) {
            dao.updateSongPosition(playlistId, audioHash, newPosition)
            dao.touchModified(playlistId, System.currentTimeMillis())
        }

    /**
     * Replaces the entire ordered song list for the playlist in a single transaction.
     * Useful after a full drag-and-drop reorder where many positions change at once.
     *
     * @param playlistId    The target playlist.
     * @param orderedHashes The complete new ordering expressed as a list of audio hash values.
     */
    suspend fun reorderSongs(playlistId: Long, orderedHashes: List<Long>) =
        withContext(Dispatchers.IO) {
            dao.removeAllSongsFromPlaylist(playlistId)
            val crossRefs = orderedHashes.mapIndexed { index, hash ->
                PlaylistSongCrossRef(
                        playlistId = playlistId,
                        audioHash = hash,
                        position = index
                )
            }
            dao.addSongsToPlaylist(crossRefs)
            dao.touchModified(playlistId, System.currentTimeMillis())
        }
}