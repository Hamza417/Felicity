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

class AudioCoverLoader() : ModelLoader<Audio, Bitmap> {
    override fun buildLoadData(audio: Audio, width: Int, height: Int, options: Options): ModelLoader.LoadData<Bitmap> {
        return ModelLoader.LoadData(ObjectKey(audio), AudioCoverFetcher(audio))
    }

    fun getResourceFetcher(model: Audio): DataFetcher<Bitmap> {
        return AudioCoverFetcher(model)
    }

    override fun handles(model: Audio): Boolean {
        return true
    }

    internal class Factory(private val context: Context) : ModelLoaderFactory<Audio, Bitmap> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Audio, Bitmap> {
            return AudioCoverLoader()
        }

        override fun teardown() {}
    }
}