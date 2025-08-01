package app.simple.felicity.glide.transformation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.get
import androidx.core.graphics.set
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

class BottomAlphaGradient : BitmapTransformation() {

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update("BottomAlphaGradientTransformation".toByteArray())
    }

    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val width = toTransform.width
        val height = toTransform.height
        val gradientHeight = (height * 0.50f).toInt()
        val gradientTop = height - gradientHeight

        val result = pool.get(width, height, Bitmap.Config.ARGB_8888)
        val mutableBitmap = toTransform.copy(Bitmap.Config.ARGB_8888, true)

        for (y in gradientTop until height) {
            val gradientAlpha = ((height - y).toFloat() / gradientHeight).coerceIn(0f, 1f)
            for (x in 0 until width) {
                val color = mutableBitmap[x, y]
                val origAlpha = Color.alpha(color)
                val newAlpha = (origAlpha * gradientAlpha).toInt().coerceIn(0, 255)
                val rgb = color and 0x00FFFFFF
                mutableBitmap[x, y] = (newAlpha shl 24) or rgb
            }
        }

        val canvas = Canvas(result)
        canvas.drawBitmap(mutableBitmap, 0f, 0f, null)
        return result
    }
}