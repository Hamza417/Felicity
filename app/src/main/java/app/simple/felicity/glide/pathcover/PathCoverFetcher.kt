package app.simple.felicity.glide.pathcover

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import app.simple.felicity.R
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.utils.BitmapHelper.toBitmap
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.images.Artwork
import java.io.File

class PathCoverFetcher internal constructor(private val model: PathCoverModel) : DataFetcher<Bitmap> {
    private var retriever = MediaMetadataRetriever()

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        retriever = MediaMetadataRetriever()

        kotlin.runCatching {
            //            retriever.setDataSource(model.path)
            //            val byteArray = retriever.embeddedPicture
            //            val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray?.size!!)

            val art = getAlbumArt(model.path)
            callback.onDataReady(art ?: throw NullPointerException("No album art found for ${model.path}"))
        }.getOrElse {
            callback.onDataReady(R.drawable.ic_felicity.toBitmap(model.context, AppearancePreferences.getIconSize()))
        }
    }

    private fun getAlbumArt(path: String): Bitmap? {
        val audioFile = AudioFileIO.read(File(path))
        val tag = audioFile.tag
        val artwork: Artwork? = tag?.firstArtwork

        return if (artwork != null) {
            val imageData = artwork.binaryData
            BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
        } else {
            null
        }
    }

    override fun cleanup() {
        try {
            retriever.release()
            retriever.close()
        } catch (_: Exception) {
        }
    }

    override fun cancel() {
        try {
            retriever.release()
            retriever.close()
        } catch (_: Exception) {
        }
    }

    override fun getDataClass(): Class<Bitmap> {
        return Bitmap::class.java
    }

    override fun getDataSource(): DataSource {
        return DataSource.LOCAL
    }

    companion object {
        private const val TAG = "PathCoverFetcher"
    }
}
