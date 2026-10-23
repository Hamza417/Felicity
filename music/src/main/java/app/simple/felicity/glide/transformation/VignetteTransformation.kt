package app.simple.felicity.glide.transformation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.graphics.toColorInt
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest
import kotlin.math.max

class VignetteTransformation(
        // Default to a 70% opacity black vignette
        private val vignetteColor: Int = "#B3000000".toColorInt(),
        // Controls how far out the gradient stretches
        private val radiusMultiplier: Float = 1.2f
) : BitmapTransformation() {

    override fun transform(
            pool: BitmapPool,
            toTransform: Bitmap,
            outWidth: Int,
            outHeight: Int
    ): Bitmap {
        val width = toTransform.width
        val height = toTransform.height

        // Obtain a new bitmap from Glide's pool to prevent modifying the original
        val bitmap = pool.get(width, height, toTransform.config ?: Bitmap.Config.ARGB_8888)
        bitmap.setHasAlpha(true)

        val canvas = Canvas(bitmap)

        // Draw the original image first
        canvas.drawBitmap(toTransform, 0f, 0f, null)

        val centerX = width / 2f
        val centerY = height / 2f

        // Use the longer side to ensure the gradient reaches the corners smoothly
        val radius = max(width, height) / 2f * radiusMultiplier

        // The gradient stays transparent for the first 40% of the radius, then fades to the vignette color
        val gradient = RadialGradient(
                centerX, centerY, radius,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, vignetteColor),
                floatArrayOf(0f, 0.4f, 1f),
                Shader.TileMode.CLAMP
        )

        val paint = Paint().apply {
            shader = gradient
            isAntiAlias = true
        }

        // Draw the vignette overlay
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        return bitmap
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        // Unique key so Glide knows how to cache this specific transformation
        messageDigest.update(("vignette_transformation_{$vignetteColor}_$radiusMultiplier").toByteArray())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VignetteTransformation) return false
        return vignetteColor == other.vignetteColor && radiusMultiplier == other.radiusMultiplier
    }

    override fun hashCode(): Int {
        var result = vignetteColor
        result = 31 * result + radiusMultiplier.hashCode()
        return result
    }
}