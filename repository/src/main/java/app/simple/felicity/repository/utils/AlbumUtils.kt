package app.simple.felicity.repository.utils

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.net.toUri
import app.simple.felicity.repository.models.Album
import app.simple.felicity.shared.R
import app.simple.felicity.shared.utils.ConditionUtils.isNotZero

object AlbumUtils {
    fun getAlbumCover(context: Context, albumId: Long): Uri? {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            android.content.ContentUris.withAppendedId(
                    MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                    albumId
            )
        } else {
            var artPath: String? = null
            val cursor = context.contentResolver.query(
                    MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Audio.Albums.ALBUM_ART),
                    "${MediaStore.Audio.Albums._ID}=?",
                    arrayOf(albumId.toString()),
                    null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    artPath = it.getString(0)
                }
            }

            artPath?.toUri()
        }
    }

    fun AppCompatTextView.setAlbumFlag(album: Album) {
        text = buildString {
            append(resources.getQuantityString(R.plurals.number_of_songs, album.songCount, album.songCount))

            if (album.firstYear.isNotZero()) {
                append(" | ")
                append(album.firstYear)
            }

            if (album.lastYear.isNotZero() && album.firstYear != album.lastYear) {
                append(" | ")
                append(album.lastYear)
            }
        }
    }
}