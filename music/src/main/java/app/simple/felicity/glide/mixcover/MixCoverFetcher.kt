package app.simple.felicity.glide.mixcover

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import app.simple.felicity.repository.covers.AudioCover
import app.simple.felicity.viewmodels.panels.MixesViewModel.Mix
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import java.io.FileNotFoundException
import kotlin.random.Random

class MixCoverFetcher internal constructor(
        private val context: Context,
        private val mix: Mix,
        private val width: Int,
        private val height: Int
) : DataFetcher<Bitmap> {

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        try {
            val targetImageCount = Random.nextInt(4, 7)
            val numImages = minOf(mix.songs.size, targetImageCount)

            if (numImages == 0) {
                throw FileNotFoundException("No songs in mix to generate cover.")
            }

            // Load individual song covers synchronously using the standard audio cover pipeline.
            val bitmaps = mix.songs.take(numImages).mapNotNull { audio ->
                try {
                    AudioCover.load(context, audio)?.let { rawBitmap ->
                        // Downscale raw bitmaps so they don't consume too much memory when compositing
                        val scale = maxOf(width / 2f / rawBitmap.width, height / 2f / rawBitmap.height)

                        if (scale < 1f) {
                            val scaled = Bitmap.createScaledBitmap(rawBitmap, (rawBitmap.width * scale).toInt(), (rawBitmap.height * scale).toInt(), true)

                            if (scaled != rawBitmap) {
                                rawBitmap.recycle()
                            }

                            scaled
                        } else {
                            rawBitmap
                        }
                    }
                } catch (_: Exception) {
                    null
                }
            }

            if (bitmaps.isEmpty()) {
                throw FileNotFoundException("Could not load any audio covers for mix.")
            }

            val collage = MixCoverGenerator.createDynamicCollage(bitmaps, width, height)

            // Recycle intermediate downscaled bitmaps as they are no longer needed
            bitmaps.forEach {
                it.recycle()
            }

            callback.onDataReady(collage)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating mix collage", e)
            callback.onLoadFailed(e)
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
        private const val TAG = "MixCoverFetcher"
    }
}
