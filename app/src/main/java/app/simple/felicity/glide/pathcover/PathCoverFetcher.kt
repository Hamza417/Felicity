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
import java.io.FileNotFoundException

class PathCoverFetcher internal constructor(private val model: PathCoverModel) : DataFetcher<Bitmap> {
    private var retriever = MediaMetadataRetriever()

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(model.path)
            val byteArray = retriever.embeddedPicture
            val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray?.size!!)
            callback.onDataReady(bitmap)
        } catch (_: IllegalArgumentException) {
        } catch (e: FileNotFoundException) {
            callback.onDataReady(R.drawable.ic_felicity.toBitmap(model.context, AppearancePreferences.getIconSize()))
        } finally {
            retriever.release()
            retriever.close()
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
}
