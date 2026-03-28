package app.simple.felicity.glide.audiocover

import android.content.Context
import android.graphics.Bitmap
import app.simple.felicity.repository.models.Audio
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey

/**
 * Glide [ModelLoader] for [Audio] → [Bitmap] that wires a [Context] into
 * [AudioCoverFetcher] so it can query the MediaStore album art cache.
 *
 * @author Hamza417
 */
class AudioCoverLoader(private val context: Context) : ModelLoader<Audio, Bitmap> {
    override fun buildLoadData(audio: Audio, width: Int, height: Int, options: Options): ModelLoader.LoadData<Bitmap> {
        return ModelLoader.LoadData(ObjectKey(audio), AudioCoverFetcher(context, audio))
    }

    fun getResourceFetcher(model: Audio): DataFetcher<Bitmap> {
        return AudioCoverFetcher(context, model)
    }

    override fun handles(model: Audio): Boolean {
        return true
    }

    internal class Factory(private val context: Context) : ModelLoaderFactory<Audio, Bitmap> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Audio, Bitmap> {
            return AudioCoverLoader(context)
        }

        override fun teardown() {}
    }
}