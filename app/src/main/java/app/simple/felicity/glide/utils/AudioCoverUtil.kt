package app.simple.felicity.glide.utils

import android.graphics.Bitmap
import android.graphics.drawable.AnimatedVectorDrawable
import android.net.Uri
import android.widget.ImageView
import app.simple.felicity.R
import app.simple.felicity.glide.filedescriptorcover.DescriptorCoverModel
import app.simple.felicity.glide.modules.GlideApp
import app.simple.felicity.glide.transformation.BlurShadow
import app.simple.felicity.glide.transformation.Greyscale
import app.simple.felicity.glide.transformation.Padding
import app.simple.felicity.glide.uricover.UriCoverModel
import app.simple.felicity.preferences.AppearancePreferences
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

object AudioCoverUtil {
    /**
     * @param uri requires a valid file uri and not art uri else
     * error 0x80000000 will be thrown by the MediaMetadataRetriever
     *
     * Asynchronously load Album Arts for song files from their URIs using file descriptor
     */
    fun ImageView.loadFromFileDescriptor(uri: Uri) {
        GlideApp.with(this)
            .asBitmap()
            .transform(RoundedCorners(AppearancePreferences.getCornerRadius().div(2).toInt()),
                       Padding(BlurShadow.DEFAULT_SHADOW_SIZE.toInt()),
                       BlurShadow(context)
                           .setElevation(25F)
                           .setBlurRadius(BlurShadow.DEFAULT_SHADOW_SIZE))
            .load(DescriptorCoverModel(this.context, uri))
            .addListener(object : RequestListener<Bitmap> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Bitmap>?, isFirstResource: Boolean): Boolean {
                    this@loadFromFileDescriptor.setImageResource(R.drawable.ic_felicity).also {
                        if (this@loadFromFileDescriptor.drawable is AnimatedVectorDrawable) {
                            (this@loadFromFileDescriptor.drawable as AnimatedVectorDrawable).start()
                        }
                    }
                    return true
                }

                override fun onResourceReady(resource: Bitmap?, model: Any?, target: Target<Bitmap>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
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
    fun ImageView.loadFromFileDescriptorFullScreen(uri: Uri) {
        GlideApp.with(this)
            .asBitmap()
            .transform(CenterCrop())
            .load(DescriptorCoverModel(this.context, uri))
            .addListener(object : RequestListener<Bitmap> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Bitmap>?, isFirstResource: Boolean): Boolean {
                    this@loadFromFileDescriptorFullScreen.setImageResource(R.drawable.ic_felicity).also {
                        (this@loadFromFileDescriptorFullScreen.drawable as AnimatedVectorDrawable).start()
                    }
                    return true
                }

                override fun onResourceReady(resource: Bitmap?, model: Any?, target: Target<Bitmap>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
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
        GlideApp.with(this)
            .asBitmap()
            .transform(CenterCrop(), Greyscale())
            .load(DescriptorCoverModel(this.context, uri))
            .addListener(object : RequestListener<Bitmap> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Bitmap>?, isFirstResource: Boolean): Boolean {
                    this@loadFromFileDescriptorGreyscale.setImageResource(R.drawable.ic_felicity).also {
                        (this@loadFromFileDescriptorGreyscale.drawable as AnimatedVectorDrawable).start()
                    }
                    return true
                }

                override fun onResourceReady(resource: Bitmap?, model: Any?, target: Target<Bitmap>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                    return false
                }
            })
            .into(this)
    }

    /**
     * @param uri requires a valid art uri
     *
     * Asynchronously load Album Arts for song files from their URIs
     */
    fun ImageView.loadFromUri(uri: Uri) {
        GlideApp.with(this)
            .asBitmap()
            .dontTransform()
            //            .transform(RoundedCorners(AppearancePreferences.getCornerRadius().toInt().times(2)),
            //                       Padding(BlurShadow.DEFAULT_SHADOW_SIZE.toInt()),
            //                       BlurShadow(this.context)
            //                           .setElevation(25F)
            //                           .setBlurRadius(BlurShadow.DEFAULT_SHADOW_SIZE))
            .load(UriCoverModel(this.context, uri))
            .into(this)
    }
}