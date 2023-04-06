package app.simple.felicity.glide.modules

import android.content.Context
import android.graphics.Bitmap
import app.simple.felicity.glide.uricover.UriCoverModel
import app.simple.inure.glide.uricover.UriCoverLoader
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.LibraryGlideModule

@GlideModule
class UriCoverModule : LibraryGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.append(UriCoverModel::class.java, Bitmap::class.java, UriCoverLoader.Factory())
    }
}