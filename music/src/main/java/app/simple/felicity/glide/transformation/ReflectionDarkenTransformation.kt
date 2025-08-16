package app.simple.felicity.glide.transformation

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

class ReflectionDarkenTransformation(
        private val darkenPercent: Float = 25f,      // 0–100, default 25%
        private val darkenOverlayAlpha: Int = 64,    // Overlay alpha (0–255)
        private val reflectionAlpha: Int = 128       // **WHOLE reflection alpha (0–255)**
) : BitmapTransformation() {

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        val idString = "reflection_darken_${darkenPercent}_${darkenOverlayAlpha}_refalpha_${reflectionAlpha}_fade"
        messageDigest.update(idString.toByteArray())
    }

    @SuppressLint("UseKtx")
    override fun transform(
            pool: BitmapPool,
            toTransform: Bitmap,
            outWidth: Int,
            outHeight: Int
    ): Bitmap {
        val width = toTransform.width
        val height = toTransform.height
        val reflectionHeight = height / 2

        val output = Bitmap.createBitmap(width, reflectionHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Create reflection bitmap
        val matrix = Matrix().apply { preScale(1f, -1f) }
        val reflection = Bitmap.createBitmap(
                toTransform,
                0,
                height - reflectionHeight,
                width,
                reflectionHeight,
                matrix,
                false
        )

        // Draw the reflection with adjustable overall alpha
        val reflectionPaint = Paint()
        reflectionPaint.alpha = reflectionAlpha.coerceIn(0, 255)
        canvas.drawBitmap(reflection, 0f, 0f, reflectionPaint)

        // Adjustable darken overlay
        val darkenPaint = Paint()
        val darkness = (darkenPercent.coerceIn(0f, 100f) / 100f)
        val darkenAlpha = (darkenOverlayAlpha * darkness).toInt().coerceIn(0, 255)
        darkenPaint.color = Color.argb(darkenAlpha, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), reflectionHeight.toFloat(), darkenPaint)

        // Fade effect
        val fadePaint = Paint()
        fadePaint.shader = LinearGradient(
                0f, 0f, 0f, reflectionHeight.toFloat(),
                Color.BLACK, Color.TRANSPARENT,
                Shader.TileMode.CLAMP
        )
        fadePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawRect(0f, 0f, width.toFloat(), reflectionHeight.toFloat(), fadePaint)

        reflection.recycle()
        return output
    }
}

