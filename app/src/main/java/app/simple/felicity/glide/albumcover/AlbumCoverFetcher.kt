package app.simple.felicity.glide.albumcover

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import app.simple.felicity.R
import app.simple.felicity.core.utils.BitmapUtils.toBitmap
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import java.io.FileNotFoundException

class AlbumCoverFetcher internal constructor(private val albumCoverModel: AlbumCoverModel) : DataFetcher<Bitmap> {

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        try {
            val artUri = Uri.withAppendedPath(ALBUM_ART_URI, albumCoverModel.albumId.toString())
            albumCoverModel.context.contentResolver.openInputStream(artUri).use {
                callback.onDataReady(BitmapFactory.decodeStream(it))
            }
        } catch (_: IllegalArgumentException) {
        } catch (e: FileNotFoundException) {
            callback.onDataReady(R.drawable.ic_felicity.toBitmap(albumCoverModel.context, app.simple.felicity.preferences.AppearancePreferences.getIconSize()))
        }
    }

    override fun cleanup() {
        // Cleared
    }

    override fun cancel() {
        // Probably already cleared
    }

    override fun getDataClass(): Class<Bitmap> {
        return Bitmap::class.java
    }

    override fun getDataSource(): DataSource {
        return DataSource.LOCAL
    }

    companion object {
        private const val TAG = "AlbumCoverFetcher"
        private val ALBUM_ART_URI = Uri.parse("content://media/external/audio/albumart")
    }
}
