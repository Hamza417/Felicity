package app.simple.felicity.glide.transformation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest
import kotlin.math.max

/**
 * Draws the image as a stack of cards in a single bitmap, so a collection (playlist, mix)
 * can be shown with one image view and one Glide request instead of several layered views.
 *
 * The sharp image sits at the bottom of the bitmap and smaller, blurred and faded copies of it
 * peek out above it. Blurring is done by scaling the image down to a tiny bitmap and drawing it
 * back up with filtering, which needs no extra libraries and almost no memory.
 *
 * Apply it after cropping and rounding the corners so the copies share the same shape.
 *
 * @param layers Number of blurred copies drawn behind the main image.
 * @param blurDownscale How many times the image is shrunk before being drawn back up; higher is blurrier.
 * @author Hamza417
 */
class StackTransformation(
        private val layers: Int = 2,
        private val blurDownscale: Int = 12
) : BitmapTransformation() {

    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val size = max(toTransform.width, toTransform.height).toFloat()
        val output = pool.get(toTransform.width, toTransform.height, Bitmap.Config.ARGB_8888)
        output.setHasAlpha(true)
        output.eraseColor(Color.TRANSPARENT)

        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val blurred = createBlurred(pool, toTransform)

        // Back layers are drawn first (smallest and highest), then the sharp image on top.
        for (layer in layers downTo 1) {
            val scale = 1f - LAYER_SCALE_STEP * (layer + 1)
            val width = size * scale
            val top = size * LAYER_PEEK * (layers - layer)
            val left = (size - width) / 2f
            paint.alpha = (255 * (1f - LAYER_FADE * layer)).toInt()
            canvas.drawBitmap(blurred, null, RectF(left, top, left + width, top + width), paint)
        }

        val frontWidth = size * (1f - LAYER_SCALE_STEP)
        val frontLeft = (size - frontWidth) / 2f
        paint.alpha = 255
        canvas.drawBitmap(
                toTransform, null,
                RectF(frontLeft, size - frontWidth, frontLeft + frontWidth, size), paint)

        pool.put(blurred)
        return output
    }

    /**
     * Shrinks [source] and stretches it back to its original size with bilinear filtering,
     * which gives a soft blur without allocating more than one extra full size bitmap.
     */
    private fun createBlurred(pool: BitmapPool, source: Bitmap): Bitmap {
        val smallWidth = max(1, source.width / blurDownscale)
        val smallHeight = max(1, source.height / blurDownscale)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val small = pool.get(smallWidth, smallHeight, Bitmap.Config.ARGB_8888)
        small.eraseColor(Color.TRANSPARENT)
        Canvas(small).drawBitmap(source, null, RectF(0f, 0f, smallWidth.toFloat(), smallHeight.toFloat()), paint)

        val blurred = pool.get(source.width, source.height, Bitmap.Config.ARGB_8888)
        blurred.eraseColor(Color.TRANSPARENT)
        Canvas(blurred).drawBitmap(small, null, RectF(0f, 0f, source.width.toFloat(), source.height.toFloat()), paint)

        pool.put(small)
        return blurred
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update("$ID-$layers-$blurDownscale".toByteArray(CHARSET))
    }

    override fun equals(other: Any?): Boolean {
        return other is StackTransformation && layers == other.layers && blurDownscale == other.blurDownscale
    }

    override fun hashCode(): Int {
        return 31 * (31 * ID.hashCode() + layers) + blurDownscale
    }

    companion object {
        private const val ID = "app.simple.felicity.glide.transformation.StackTransformation"

        /** Width lost by each successive layer, as a fraction of the full size. */
        private const val LAYER_SCALE_STEP = 0.12f

        /** Vertical distance between two neighboring layers, as a fraction of the full size. */
        private const val LAYER_PEEK = 0.05f

        /** Alpha lost by each successive layer. */
        private const val LAYER_FADE = 0.3f
    }
}
