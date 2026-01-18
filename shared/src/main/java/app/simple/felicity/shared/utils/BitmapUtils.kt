package app.simple.felicity.shared.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.BlurMaskFilter.Blur
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Matrix.ScaleToFit
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import androidx.core.content.ContextCompat

object BitmapUtils {
    private const val shadowColor = -4671304

    /**
     * Converts drawable to bitmap
     */
    fun Int.toBitmap(context: Context, size: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(context, this)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable?.setBounds(0, 0, canvas.width, canvas.height)
        drawable?.draw(canvas)
        return bitmap
    }

    fun Bitmap.addShadow(dstHeight: Int, dstWidth: Int, size: Int, dx: Float, dy: Float): Bitmap {
        val mask = Bitmap.createBitmap(dstWidth, dstHeight, Bitmap.Config.ARGB_8888)
        val scaleToFit = Matrix()
        val src = RectF(0F, 0F, width.toFloat(), height.toFloat())
        val dst = RectF(0F, 0F, dstWidth - dx, dstHeight - dy)
        scaleToFit.setRectToRect(src, dst, ScaleToFit.CENTER)
        val dropShadow = Matrix(scaleToFit)
        dropShadow.postTranslate(dx, dy)
        val maskCanvas = Canvas(mask)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        maskCanvas.drawBitmap(this, scaleToFit, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OUT)
        maskCanvas.drawBitmap(this, dropShadow, paint)
        val filter = BlurMaskFilter(size.toFloat(), Blur.NORMAL)
        paint.reset()
        paint.isAntiAlias = true
        paint.color = shadowColor
        paint.maskFilter = filter
        paint.isFilterBitmap = true
        val finalBitmap = Bitmap.createBitmap(dstWidth, dstHeight, Bitmap.Config.ARGB_8888)
        val retCanvas = Canvas(finalBitmap)
        retCanvas.drawBitmap(mask, 0F, 0F, paint)
        retCanvas.drawBitmap(this, scaleToFit, null)
        mask.recycle()
        return finalBitmap
    }

    //Convert Picture to Bitmap
    fun Picture.toBitmap(): Bitmap {
        val pd = PictureDrawable(this)
        val bitmap =
            Bitmap.createBitmap(pd.intrinsicWidth, pd.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawPicture(pd.picture)
        return bitmap
    }

    fun getBitmapFromDrawable(drawable: Drawable): Bitmap {
        val bmp = Bitmap.createBitmap(
                drawable.intrinsicWidth,
                drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bmp
    }

    fun Drawable.toBitmap(dimension: Int = 400): Bitmap {
        val bitmap = Bitmap.createBitmap(dimension, dimension, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, dimension, dimension)
        draw(canvas)
        return bitmap
    }

    /**
     * Convert vector resource into Bitmap
     *
     * @param context [Context]
     * @param incrementFactor Resolution/Dimension of the output bitmap in multiples of the original size
     * @param alpha 0 - 255 opacity of output bitmap
     * @return [Bitmap]
     */
    fun Int.toBitmapKeepingSize(context: Context, incrementFactor: Int, alpha: Int = 255): Bitmap {
        val drawable = ContextCompat.getDrawable(context, this)
        val intrinsicWidth = drawable!!.intrinsicWidth * incrementFactor
        val intrinsicHeight = drawable.intrinsicHeight * incrementFactor
        drawable.setBounds(0, 0, intrinsicWidth, intrinsicHeight)
        val bitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.alpha = alpha
        drawable.draw(canvas)
        return bitmap
    }

    fun Bitmap.addLinearGradient(array: IntArray): Bitmap {
        val width = width
        val height = height
        val updatedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(updatedBitmap)
        canvas.drawBitmap(this, 0f, 0f, null)
        val paint = Paint()
        val shader =
            LinearGradient(0f, 0f, 0f, height.toFloat(), array[0], array[1], Shader.TileMode.CLAMP)
        paint.shader = shader
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return updatedBitmap
    }

    /**
     * Convert bitmap to grayscale
     */
    fun Bitmap.toGrayscale(): Bitmap {
        val width = width
        val height = height
        val bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmpGrayscale)
        val paint = Paint()
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0F)
        val f = ColorMatrixColorFilter(colorMatrix)
        paint.colorFilter = f
        canvas.drawBitmap(this, 0F, 0F, paint)
        return bmpGrayscale
    }

    fun Bitmap.resizeToMaxSize(maxSize: Int = 1000): Bitmap {
        val width = this.width
        val height = this.height

        return if (width == height) {
            Bitmap.createScaledBitmap(this, maxSize, maxSize, true)
        } else {
            val aspectRatio = width.toFloat() / height.toFloat()
            if (width > height) {
                val newHeight = (maxSize / aspectRatio).toInt()
                Bitmap.createScaledBitmap(this, maxSize, newHeight, true)
            } else {
                val newWidth = (maxSize * aspectRatio).toInt()
                Bitmap.createScaledBitmap(this, newWidth, maxSize, true)
            }
        }
    }
}
