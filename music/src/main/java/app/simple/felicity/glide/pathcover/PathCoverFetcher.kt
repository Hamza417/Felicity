package app.simple.felicity.glide.pathcover

import android.graphics.Bitmap
import app.simple.felicity.repository.helpers.AlbumArtHelper.getAlbumArt
import app.simple.felicity.utils.FileUtils.toFile
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher

class PathCoverFetcher internal constructor(private val model: PathCoverModel) : DataFetcher<Bitmap> {

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        val art = model.path.toFile().getAlbumArt()
        callback.onDataReady(art ?: throw NullPointerException("No album art found for ${model.path}"))
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
