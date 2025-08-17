// repository/models/normal/Song.kt
package app.simple.felicity.repository.models

import android.net.Uri
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import app.simple.felicity.repository.database.coverters.UriTypeConverter
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "songs")
@TypeConverters(UriTypeConverter::class)
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
) : Parcelable
