package app.simple.felicity.glide.transformation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

/**
 * Draws a smooth bottom-to-top black-to-alpha gradient over the image to ensure
 * text overlaid at the bottom is perfectly legible.
 *
 * Uses a cubic ease-in-out (Bézier) curve to generate a multi-stop gradient. This avoids
 * the harsh visual banding that occurs with standard 2-stop linear gradients.
 *
 * @param heightFraction What fraction of the image height the gradient should cover (0.0 to 1.0).
 *                       Default is 0.8 (bottom 80% of the image).
 */
class BezierGradientTransformation(
        private val heightFraction: Float = 0.8f
) : BitmapTransformation() {

    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val width = toTransform.width
        val height = toTransform.height

        // Create the output bitmap
        val output = pool.get(width, height, toTransform.config ?: Bitmap.Config.ARGB_8888)
        output.setHasAlpha(true)

        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Draw the original image first
        canvas.drawBitmap(toTransform, 0f, 0f, paint)

        // The gradient starts at the very bottom (opaque black) and goes up to the fade start (transparent).
        val startY = height.toFloat()
        val endY = height * (1f - heightFraction)

        // Sample the cubic Bézier easing curve at GRADIENT_STEPS evenly-spaced positions
        val positions = FloatArray(GRADIENT_STEPS) { i -> i.toFloat() / (GRADIENT_STEPS - 1) }

        val colors = IntArray(GRADIENT_STEPS) { i ->
            val t = i.toFloat() / (GRADIENT_STEPS - 1)
            // Evaluates a cubic ease-out Bézier alpha: alpha = (1 - t)³
            val invT = 1f - t
            val alpha = ((invT * invT * invT) * 255f).toInt().coerceIn(0, 255)
            Color.argb(alpha, 0, 0, 0)
        }

        // Draw the gradient
        val gradientPaint = Paint().apply {
            shader = LinearGradient(
                    /* x0 = */ 0f,
                    /* y0 = */ startY,
                    /* x1 = */ 0f,
                    /* y1 = */ endY,
                    /* colors = */ colors,
                    /* positions = */ positions,
                    /* tile = */ Shader.TileMode.CLAMP
            )
        }

        canvas.drawRect(0f, endY, width.toFloat(), startY, gradientPaint)

        return output
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update((ID + heightFraction).toByteArray(CHARSET))
    }

    override fun equals(other: Any?): Boolean {
        return other is BezierGradientTransformation && heightFraction == other.heightFraction
    }

    override fun hashCode(): Int {
        return ID.hashCode() + (heightFraction * 1000).toInt()
    }

    companion object {
        private const val ID = "app.simple.felicity.glide.transformation.BezierGradientTransformation"
        private const val GRADIENT_STEPS = 16
    }
}