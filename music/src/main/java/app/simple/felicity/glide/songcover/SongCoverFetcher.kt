package app.simple.felicity.glide.songcover

import android.content.Context
import android.graphics.Bitmap
import app.simple.felicity.repository.models.Song
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher

class SongCoverFetcher internal constructor(private val context: Context, private val song: Song) : DataFetcher<Bitmap> {
    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {

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
