package app.simple.felicity.glide.yearcover

import android.graphics.Bitmap
import android.util.Log
import app.simple.felicity.repository.covers.YearCover
import app.simple.felicity.repository.models.YearGroup
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import java.io.FileNotFoundException

class YearCoverFetcher internal constructor(
        private val yearGroup: YearGroup
) : DataFetcher<Bitmap> {

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        try {
            val bitmap = YearCover.load(yearGroup)
                ?: throw FileNotFoundException("Could not find artwork for year: ${yearGroup.year}")
            callback.onDataReady(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cover for year: ${yearGroup.year}", e)
            callback.onLoadFailed(e)
        }
    }

    override fun cleanup() {}

    override fun cancel() {}

    override fun getDataClass(): Class<Bitmap> = Bitmap::class.java

    override fun getDataSource(): DataSource = DataSource.LOCAL

    companion object {
        private const val TAG = "YearCoverFetcher"
    }
}

