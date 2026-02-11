// repository/models/normal/Song.kt
package app.simple.felicity.repository.models

import android.net.Uri
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Deprecated("Use Audio model instead of Song for better consistency and flexibility. Song will be removed in future versions.")
@Parcelize
@Entity(tableName = "songs")
data class Song(
        @PrimaryKey val id: Long,
        val title: String?,
        val artist: String?,
        val album: String?,
        val albumId: Long,
        val artistId: Long,
        val uri: Uri?,
        val path: String,
        val duration: Long,
        val size: Long,
        val dateAdded: Long,
        val dateModified: Long,
        val genre: String?,         // Song genre
        val trackNumber: Int?,      // Track number in album
        val composer: String?,      // Composer name
        val year: Int?,             // Year of release
        val bitrate: Int?,          // Audio bitrate
        val isMusic: Boolean = true // Is this file music
) : Parcelable
