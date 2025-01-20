package app.simple.felicity.glide.modules

import android.content.Context
import android.graphics.Bitmap
import app.simple.felicity.glide.albumcover.AlbumCoverLoader
import app.simple.felicity.glide.albumcover.AlbumCoverModel
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.LibraryGlideModule

@GlideModule
class AlbumCoverModule : LibraryGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.append(AlbumCoverModel::class.java, Bitmap::class.java, AlbumCoverLoader.Factory())
    }
}
