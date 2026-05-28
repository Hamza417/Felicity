package app.simple.felicity.glide.artistcover

import android.content.Context
import android.graphics.Bitmap
import app.simple.felicity.repository.models.Artist
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import java.io.File

class ArtistCoverLoader(private val context: Context) : ModelLoader<Artist, Bitmap> {
    override fun buildLoadData(model: Artist, width: Int, height: Int, options: Options): ModelLoader.LoadData<Bitmap> {
        /**
         * We mix the artist id with the saved image file's last-modified time so that
         * whenever the user picks a new image (which overwrites the file on disk), the
         * cache key changes and Glide fetches the fresh image instead of the old cached one.
         */
        val lastModified = artistImageFile(model.name)?.lastModified() ?: 0L
        val key = ObjectKey("${model.id}_$lastModified")
        return ModelLoader.LoadData(key, ArtistCoverFetcher(context, model))
    }

    /**
     * Returns the file handle for the artist's saved image, or null if the artist has no name.
     * This mirrors the path logic inside ArtistCover so the timestamps always match.
     */
    private fun artistImageFile(artistName: String?): File? {
        if (artistName.isNullOrBlank()) return null
        val sanitized = artistName.trim()
            .replace(' ', '_')
            .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            .ifEmpty { "unknown" }
        return File(File(context.filesDir, "artist_images"), "$sanitized.png")
    }

    fun getResourceFetcher(model: Artist): DataFetcher<Bitmap> {
        return ArtistCoverFetcher(context, model)
    }

    override fun handles(model: Artist): Boolean {
        return true
    }

    internal class Factory(private val context: Context) : ModelLoaderFactory<Artist, Bitmap> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Artist, Bitmap> {
            return ArtistCoverLoader(context)
        }

        override fun teardown() {}
    }
}
