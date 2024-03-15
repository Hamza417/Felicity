package app.simple.felicity.glide.modules

import android.content.Context
import android.graphics.Bitmap
import app.simple.felicity.glide.artistcover.ArtistCoverLoader
import app.simple.felicity.glide.artistcover.ArtistCoverModel
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.LibraryGlideModule

@GlideModule
class ArtistCoverModule : LibraryGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.append(ArtistCoverModel::class.java, Bitmap::class.java, ArtistCoverLoader.Factory())
    }
}
