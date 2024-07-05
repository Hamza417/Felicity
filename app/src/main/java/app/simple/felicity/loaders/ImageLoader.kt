package app.simple.felicity.loaders

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import app.simple.felicity.R
import app.simple.felicity.preferences.ConfigurationPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.images.Artwork
import java.io.File

object ImageLoader {
    fun loadImage(resourceValue: Int, imageView: ImageView, delay: Long = 0L) {
        val drawable = if (resourceValue != 0) imageView.context.resources?.let {
            ResourcesCompat.getDrawable(it, resourceValue, imageView.context.theme)
        }!! else null

        val animOut: Animation = AnimationUtils.loadAnimation(imageView.context, R.anim.image_out)
        val animIn: Animation = AnimationUtils.loadAnimation(imageView.context, R.anim.image_in)

        animIn.startOffset = delay
        animOut.startOffset = delay

        animOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation?) {
                imageView.setImageDrawable(drawable)
                animIn.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {
                        /* no-op */
                    }

                    override fun onAnimationEnd(animation: Animation?) {
                        /* no-op */
                    }

                    override fun onAnimationRepeat(animation: Animation?) {
                        /* no-op */
                    }

                })
                imageView.startAnimation(animIn)
            }

            override fun onAnimationRepeat(animation: Animation?) {
                /* no-op */
            }
        })

        imageView.startAnimation(animOut)
    }

    fun loadImageResourcesWithoutAnimation(
            resourceValue: Int,
            imageView: ImageView,
            context: Context
    ) {
        CoroutineScope(Dispatchers.Default).launch {
            val drawable = if (resourceValue != 0) context.resources?.let {
                ResourcesCompat.getDrawable(it, resourceValue, context.theme)
            }!! else null

            withContext(Dispatchers.Main) {
                try {
                    imageView.setImageDrawable(drawable)
                } catch (ignored: NullPointerException) {
                }
            }
        }
    }

    fun setImage(resourceValue: Int, imageView: ImageView, context: Context, delay: Int) {
        val drawable = if (resourceValue != 0) context.resources?.let {
            ResourcesCompat.getDrawable(it, resourceValue, context.theme)
        }!! else null

        val animOut: Animation = AnimationUtils.loadAnimation(context, R.anim.image_out)
        val animIn: Animation = AnimationUtils.loadAnimation(context, R.anim.image_in)

        animIn.startOffset = delay.toLong()
        animOut.startOffset = delay.toLong()

        animOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation?) {
                imageView.setImageDrawable(drawable)
                animIn.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {
                        /* no-op */
                    }

                    override fun onAnimationEnd(animation: Animation?) {
                        /* no-op */
                    }

                    override fun onAnimationRepeat(animation: Animation?) {
                        /* no-op */
                    }

                })
                imageView.startAnimation(animIn)
            }

            override fun onAnimationRepeat(animation: Animation?) {
                /* no-op */
            }
        })
        imageView.startAnimation(animOut)
    }

    fun File.getAlbumArt(): Bitmap? {
        when (ConfigurationPreferences.getAlbumArtLoaderSource()) {
            ConfigurationPreferences.JAUDIO_TAG -> {
                val audioFile = AudioFileIO.read(this)
                val tag = audioFile.tag
                val artwork: Artwork? = tag?.firstArtwork

                return if (artwork != null) {
                    val imageData = artwork.binaryData
                    BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                } else {
                    null
                }
            }

            else -> {
                val retriever = MediaMetadataRetriever()

                try {
                    retriever.setDataSource(this.path)
                    val byteArray = retriever.embeddedPicture
                    retriever.release()
                    retriever.close()
                    return if (byteArray != null) {
                        BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                    } else {
                        null
                    }
                } finally {
                    try {
                        retriever.release()
                        retriever.close()
                    } catch (ignored: Exception) {
                    }
                }
            }
        }
    }
}
