package app.simple.felicity.glide.transformation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.util.Util
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * This transformation adds padding intrinsically to the bitmap.
 * This is used to add a coloured border to the image, or create
 * transparent padding to prevent clipping when drawing shadows.
 */
@Suppress("unused")
class Padding : BitmapTransformation {
    private var paddingLeft: Int
    private var paddingRight: Int
    private var paddingTop: Int
    private var paddingBottom: Int
    private var colour: Int = Color.argb(0, 0, 0, 0)
    private var paddingRatio: Float? = null

    constructor(padding: Int) {
        paddingLeft = padding
        paddingRight = padding
        paddingTop = padding
        paddingBottom = padding
    }

    constructor() {
        paddingLeft = 0
        paddingRight = 0
        paddingTop = 0
        paddingBottom = 0
    }

    /**
     * Padding ration should be from 0 to 100 divided by 100.
     * Higher values will make the image disappear
     */
    constructor(paddingRatio: Float) {
        this.paddingRatio = paddingRatio
        paddingLeft = 0
        paddingRight = 0
        paddingTop = 0
        paddingBottom = 0
    }

    fun setPadding(left: Int, right: Int, top: Int, bottom: Int): Padding {
        paddingLeft = left
        paddingRight = right
        paddingTop = top
        paddingBottom = bottom
        return this
    }

    fun setColour(@ColorInt colour: Int): Padding {
        this.colour = colour
        return this
    }

    fun setColourRes(@ColorRes res: Int, context: Context): Padding {
        colour = context.resources.getColor(res, null)
        return this
    }

    override fun transform(pool: BitmapPool, source: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        if (paddingRatio != null) {
            paddingLeft = (source.width * paddingRatio!!).toInt()
            paddingRight = paddingLeft
            paddingTop = (source.height * paddingRatio!!).toInt()
            paddingBottom = paddingTop
        }

        val paddedWidth = 0.coerceAtLeast(source.width - (paddingLeft + paddingRight))
        val paddedHeight = 0.coerceAtLeast(source.height - (paddingTop + paddingBottom))
        val bitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val bitmapBounds = Rect(paddingLeft, paddingTop, paddedWidth + paddingLeft, paddedHeight + paddingTop)

        val paint = Paint()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.isDither = true

        val canvas = Canvas(bitmap)
        canvas.drawColor(colour)
        canvas.drawBitmap(source, null, bitmapBounds, paint)
        return bitmap
    }

    override fun equals(other: Any?): Boolean {
        if (other is Padding) {
            return paddingLeft == other.paddingLeft && paddingRight == other.paddingRight && paddingTop == other.paddingTop && paddingBottom == other.paddingBottom && colour == other.colour
        }
        return false
    }

    override fun hashCode(): Int {
        return Util.hashCode(ID.hashCode(),
                             Util.hashCode(paddingLeft,
                                           Util.hashCode(paddingRight,
                                                         Util.hashCode(paddingTop,
                                                                       Util.hashCode(paddingBottom,
                                                                                     Util.hashCode(colour))))))
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        val messages: ArrayList<ByteArray> = ArrayList()
        messages.add(ID_BYTES)
        messages.add(ByteBuffer.allocate(Integer.SIZE / java.lang.Byte.SIZE).putInt(paddingLeft).array())
        messages.add(ByteBuffer.allocate(Integer.SIZE / java.lang.Byte.SIZE).putInt(paddingRight).array())
        messages.add(ByteBuffer.allocate(Integer.SIZE / java.lang.Byte.SIZE).putInt(paddingTop).array())
        messages.add(ByteBuffer.allocate(Integer.SIZE / java.lang.Byte.SIZE).putInt(paddingBottom).array())
        messages.add(ByteBuffer.allocate(Integer.SIZE / java.lang.Byte.SIZE).putInt(colour).array())
        for (c in 0 until messages.size) {
            messageDigest.update(messages[c])
        }
    }

    companion object {
        private const val ID = "app.simple.inure.glide.transformations.Padding"
        private val ID_BYTES = ID.toByteArray()
    }
}
