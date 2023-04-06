package app.simple.inure.glide.uricover

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.simple.felicity.R
import app.simple.felicity.glide.uricover.UriCoverModel
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.utils.BitmapHelper.toBitmap
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import java.io.FileNotFoundException

class UriCoverFetcher internal constructor(private val uriCoverModel: UriCoverModel) : DataFetcher<Bitmap> {
    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        try {
            uriCoverModel.context.contentResolver.openInputStream(uriCoverModel.artUri).use {
                callback.onDataReady(BitmapFactory.decodeStream(it))
            }
        } catch (_: IllegalArgumentException) {
        } catch (e: FileNotFoundException) {
            callback.onDataReady(R.drawable.ic_felicity.toBitmap(uriCoverModel.context, AppearancePreferences.getIconSize()))
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
}