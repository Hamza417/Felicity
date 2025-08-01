// MediaStoreRepository.kt
package app.simple.felicity.repository.repositories

import android.content.Context
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

class MediaStoreRepository(private val context: Context) {

    fun getAllAudio(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA
        )
        val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, null
        )
        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (it.moveToNext()) {
                val id = it.getLong(idCol).toString()
                val title = it.getString(titleCol)
                val artist = it.getString(artistCol)
                val uri = it.getString(dataCol)
                items.add(
                        MediaItem.Builder()
                            .setMediaId(id)
                            .setUri(uri)
                            .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(title)
                                        .setArtist(artist)
                                        .build()
                            ).build()
                )
            }
        }
        return items
    }

    fun getItemById(mediaId: String): MediaItem? {
        return getAllAudio().find { it.mediaId == mediaId }
    }
}