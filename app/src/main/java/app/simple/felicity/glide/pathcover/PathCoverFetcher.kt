package app.simple.felicity.glide.pathcover

import android.graphics.Bitmap
import app.simple.felicity.R
import app.simple.felicity.core.utils.BitmapHelper.toBitmap
import app.simple.felicity.helpers.AlbumArtHelper.getAlbumArt
import app.simple.felicity.utils.FileUtils.toFile
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher

class PathCoverFetcher internal constructor(private val model: PathCoverModel) : DataFetcher<Bitmap> {

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        kotlin.runCatching {
            //            retriever.setDataSource(model.path)
            //            val byteArray = retriever.embeddedPicture
            //            val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray?.size!!)

            val art = model.path.toFile().getAlbumArt()
            callback.onDataReady(art ?: throw NullPointerException("No album art found for ${model.path}"))
        }.getOrElse {
            val icon = R.drawable.ic_felicity.toBitmap(model.context, ICON_SIZE)
            callback.onDataReady(icon)
        }
    }

    override fun cleanup() {
        /* no-op */
    }

    override fun cancel() {
        /* no-op */
    }

    override fun getDataClass(): Class<Bitmap> {
        return Bitmap::class.java
    }

    override fun getDataSource(): DataSource {
        return DataSource.LOCAL
    }

    companion object {
        private const val ICON_SIZE = 512
        private const val TAG = "PathCoverFetcher"
    }
}
