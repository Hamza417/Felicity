package app.simple.felicity.glide.utils

import android.graphics.Bitmap
import android.graphics.drawable.AnimatedVectorDrawable
import android.net.Uri
import android.widget.ImageView
import app.simple.felicity.R
import app.simple.felicity.glide.filedescriptorcover.DescriptorCoverModel
import app.simple.felicity.glide.pathcover.PathCoverModel
import app.simple.felicity.glide.transformation.Blur
import app.simple.felicity.glide.transformation.Greyscale
import app.simple.felicity.glide.uricover.UriCoverModel
import com.bumptech.glide.GenericTransitionOptions
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions.withCrossFade
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

object AudioCoverUtil {
    /**
     * @param uri requires a valid file uri and not art uri else
     * error 0x80000000 will be thrown by the MediaMetadataRetriever
     *
     * Asynchronously load Album Arts for song files from their URIs using file descriptor
     */
    fun ImageView.loadFromFileDescriptorFullScreen(uri: Uri) {
        Glide.with(this)
            .asBitmap()
            .transform(CenterCrop())
            .load(DescriptorCoverModel(this.context, uri))
            .addListener(object : RequestListener<Bitmap> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Bitmap>, isFirstResource: Boolean): Boolean {
                    this@loadFromFileDescriptorFullScreen.setImageResource(R.drawable.ic_felicity).also {
                        kotlin.runCatching {
                            (this@loadFromFileDescriptorFullScreen.drawable as AnimatedVectorDrawable).start()
                        }
                    }
                    return true
                }

                override fun onResourceReady(resource: Bitmap, model: Any, target: Target<Bitmap>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                    return false
                }
            })
            .into(this)
    }

    /**
     * @param uri requires a valid file uri and not art uri else
     * error 0x80000000 will be thrown by the MediaMetadataRetriever
     *
     * Asynchronously load Album Arts for song files from their URIs using file descriptor
     */
    fun ImageView.loadFromFileDescriptorGreyscale(uri: Uri) {
        Glide.with(this)
            .asBitmap()
            .transform(CenterCrop(), Greyscale())
            .load(DescriptorCoverModel(this.context, uri))
            .addListener(object : RequestListener<Bitmap> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Bitmap>, isFirstResource: Boolean): Boolean {
                    this@loadFromFileDescriptorGreyscale.setImageResource(R.drawable.ic_felicity).also {
                        (this@loadFromFileDescriptorGreyscale.drawable as AnimatedVectorDrawable).start()
                    }
                    return true
                }

                override fun onResourceReady(resource: Bitmap, model: Any, target: Target<Bitmap>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                    return false
                }
            })
            .into(this)
    }

    fun ImageView.loadFromPath(path: String) {
        Glide.with(this)
            .asBitmap()
            .load(PathCoverModel(this.context, path))
            .dontTransform()
            .centerCrop()
            .into(this)
    }

    fun ImageView.loadFromUriWithAnimation(uri: Uri) {
        Glide.with(this)
            .asBitmap()
            .transition(GenericTransitionOptions.with(R.anim.zoom_in))
            .dontTransform()
            .load(UriCoverModel(this.context, uri))
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(this)
    }

    fun ImageView.loadBlurredBackground(uri: Uri) {
        Glide.with(this)
            .asBitmap()
            .transition(withCrossFade())
            .transform(CenterCrop(), Blur(25))
            .load(DescriptorCoverModel(this.context, uri))
            .into(this)
    }
}
