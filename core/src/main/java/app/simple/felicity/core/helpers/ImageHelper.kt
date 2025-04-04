package app.simple.felicity.core.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.annotation.FloatRange
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import app.simple.felicity.core.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.InputStream

object ImageHelper {
    /**
     * Converts image uri to bitmap
     *
     * Recommended to run inside a background thread
     */
    fun getBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            bitmap
        } catch (e: FileNotFoundException) {
            null
        }
    }

    fun Bitmap.resizeBitmap(width: Int, height: Int): Bitmap {
        return Bitmap.createScaledBitmap(this, width, height, false)
    }

    /**
     * Converts vector drawable to bitmap
     */
    @JvmStatic
    fun Int.toBitmap(context: Context): Bitmap {
        val drawable = ContextCompat.getDrawable(context, this)
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable?.setBounds(0, 0, canvas.width, canvas.height)
        drawable?.draw(canvas)
        return bitmap
    }

    /**
     * Converts vector drawable to bitmap
     */
    @JvmStatic
    fun Int.toBitmap(context: Context, size: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(context, this)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable?.setBounds(0, 0, canvas.width, canvas.height)
        drawable?.draw(canvas)
        return bitmap
    }

    /**
     * Converts vector to bitmap drawable
     *
     * @return [BitmapDrawable]
     */
    fun Int.toBitmapDrawable(context: Context): BitmapDrawable {
        return BitmapDrawable(context.resources, this.toBitmap(context))
    }

    /**
     * Converts vector to bitmap drawable
     *
     * @return [BitmapDrawable]
     */
    fun Int.toBitmapDrawable(context: Context, size: Int): BitmapDrawable {
        return BitmapDrawable(context.resources, this.toBitmap(context, size))
    }

    /**
     * @param bitmap takes the bitmap value and makes the corners rounded
     * by [bitmap] * 0.2f
     *
     * TODO - add shadow to bitmap
     */
    @Deprecated("Use Glide and RenderScript Toolkit")
    fun getRoundedCornerBitmap(bitmap: Bitmap, @FloatRange(from = 0.1, to = 0.5) radius: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val roundPx = bitmap.height * radius

        paint.isAntiAlias = true

        paint.color = -0xbdbdbe
        canvas.drawRoundRect(RectF(rect), roundPx, roundPx, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)

        return output
    }

    @Deprecated("Use Glide and RenderScript Toolkit")
    private fun addShadow(bitmap: Bitmap, cornerRadius: Float, padding: Int): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val paddedWidth = 0.coerceAtLeast(output.width - (padding * 2))
        val paddedHeight = 0.coerceAtLeast(output.height - (padding * 2))
        val canvas = Canvas(output)
        val rect = Rect(padding, padding, paddedWidth + padding, paddedHeight + padding)
        val shadowPaint = Paint()

        shadowPaint.isAntiAlias = true
        shadowPaint.color = Color.DKGRAY
        shadowPaint.maskFilter = BlurMaskFilter(padding * 0.2f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawRoundRect(RectF(rect), cornerRadius, cornerRadius, shadowPaint)
        shadowPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        canvas.drawBitmap(output, rect, rect, Paint())

        return output
    }

    private fun bitmapOverlayToCenter(bitmap1: Bitmap, overlayBitmap: Bitmap): Bitmap {
        val bitmap1Width = bitmap1.width
        val bitmap1Height = bitmap1.height
        val bitmap2Width = overlayBitmap.width
        val bitmap2Height = overlayBitmap.height
        val marginLeft = (bitmap1Width * 0.5 - bitmap2Width * 0.5).toFloat()
        val marginTop = (bitmap1Height * 0.5 - bitmap2Height * 0.5).toFloat()
        val finalBitmap = Bitmap.createBitmap(bitmap1Width, bitmap1Height, bitmap1.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBitmap)
        canvas.drawBitmap(bitmap1, Matrix(), null)
        canvas.drawBitmap(overlayBitmap, marginLeft, marginTop, null)
        return finalBitmap
    }

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

}
