package app.simple.felicity.repository.helpers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import app.simple.felicity.core.utils.BitmapUtils.resizeToMaxSize
import app.simple.felicity.preferences.ConfigurationPreferences
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.images.Artwork
import java.io.File

object AlbumArtHelper {

    private const val MAX_SIZE = 500

    fun File.getAlbumArt(): Bitmap? {
        when (ConfigurationPreferences.getAlbumArtLoaderSource()) {
            ConfigurationPreferences.JAUDIO_TAG -> {
                val audioFile = AudioFileIO.read(this)
                val tag = audioFile.tag
                val artwork: Artwork? = tag?.firstArtwork

                return if (artwork != null) {
                    val imageData = artwork.binaryData
                    BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                            .resizeToMaxSize(MAX_SIZE)
                } else {
                    null
                }
            }

            else -> {
                val retriever = MediaMetadataRetriever()

                try {
                    retriever.setDataSource(this.path)
                    val byteArray = retriever.embeddedPicture
                    retriever.release()
                    retriever.close()
                    return if (byteArray != null) {
                        BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                    } else {
                        null
                    }
                } finally {
                    try {
                        retriever.release()
                        retriever.close()
                    } catch (ignored: Exception) {
                    }
                }
            }
        }
    }
}
