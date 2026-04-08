package app.simple.felicity.repository.models

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * Composite read-only projection that pairs a {@link Playlist} with all of its
 * constituent {@link Audio} tracks.
 *
 * <p>Room populates the {@code songs} list automatically by joining
 * {@code playlists} → {@code playlist_song_cross_ref} → {@code audio} via the
 * shared {@code id} / {@code playlist_id} and {@code hash} / {@code audio_hash}
 * columns. This class is intentionally <em>not</em> a Room {@code @Entity} – it is
 * a plain read-only projection used exclusively as a DAO return type.</p>
 *
 * <p>Note: the list order returned by Room for a {@code @Relation} is not guaranteed.
 * To respect the manual {@code position} ordering, use
 * {@link app.simple.felicity.repository.database.dao.PlaylistDao#getSongsInPlaylistOrdered}
 * instead, which joins the tables explicitly and sorts by {@code position ASC}.</p>
 *
 * @author Hamza417
 */
data class PlaylistWithSongs(
        @Embedded
        val playlist: Playlist,

        @Relation(
                parentColumn = "id",
                entityColumn = "hash",
                associateBy = Junction(
                        value = PlaylistSongCrossRef::class,
                        parentColumn = "playlist_id",
                        entityColumn = "audio_hash"
                )
        )
        val songs: List<Audio>
)

