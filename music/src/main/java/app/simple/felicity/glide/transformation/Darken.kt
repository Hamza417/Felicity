package app.simple.felicity.glide.transformation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.bumptech.glide.load.Key
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

class Darken(private val factor: Float = 0.6f) : BitmapTransformation() {

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(("DarkenTransformation-$factor").toByteArray(Key.CHARSET))
    }

    override fun transform(
            pool: BitmapPool,
            toTransform: Bitmap,
            outWidth: Int,
            outHeight: Int
    ): Bitmap {
        val width = toTransform.width
        val height = toTransform.height
        val bitmap = pool.get(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        val colorMatrix = android.graphics.ColorMatrix().apply {
            setScale(factor, factor, factor, 1f) // Darken
            val saturationBoost = 1f // change it to add saturation
            val saturationMatrix = android.graphics.ColorMatrix()
            saturationMatrix.setSaturation(saturationBoost)
            postConcat(saturationMatrix)
        }
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(toTransform, 0f, 0f, paint)
        return bitmap
    }
}