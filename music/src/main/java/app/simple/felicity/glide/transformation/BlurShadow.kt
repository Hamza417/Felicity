package app.simple.felicity.glide.transformation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.IntDef
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import app.simple.felicity.blur.GPUBlur
import app.simple.felicity.preferences.AppearancePreferences
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.util.Util
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.cos
import kotlin.math.sin

/**
 * This transformation applies a shadow intrinsically to the bitmap.
 * This is useful for images with complex shapes where Android does
 * not support elevation shadows. The color of the shadow, its blur
 * radius, and offset from the image can all be configured.
 *
 *
 * Images should be padded with transparent pixels by at least the
 * blur radius plus the elevation in order for the drawn shadow to
 * display properly without clipping. See: [Padding]
 */
@Suppress("unused")

/**
 * Default constructor.
 * The shadow is set at 0 elevation and 0 blur, with black color at 50%
 * opacity, by default.
 *
 * @param context current context
 */
class BlurShadow(private val context: Context) : BitmapTransformation() {
    private var blurRadius = 0f
    private var elevation = 0f
    private var angle = 0f
    private var colour: Int

    /**
     * Snapshot of [AppearancePreferences.isShadowEffectOn] taken at construction time.
     *
     * Capturing it here ensures that the rendered output and the disk/memory cache key are always
     * derived from the same value — preventing a stale cache hit when the preference is toggled
     * between two requests.
     */
    private var shadowEffectEnabled: Boolean = false

    @IntDef(EAST, NORTHEAST, NORTH, NORTHWEST, WEST, SOUTHWEST, SOUTH, SOUTHEAST)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Direction

    /**
     * Sets the blur radius of the shadow.
     * It is advised to pad the image by at least this amount plus the
     * elevation to prevent clipping of the shadow.
     *
     * @param  blurRadius  elevation in pixels
     * @return      returns self
     */
    fun setBlurRadius(blurRadius: Float): BlurShadow {
        this.blurRadius = blurRadius
        return this
    }

    /**
     * Sets the elevation, or how much the shadow is offset from the image.
     * It is advised to pad the image by at least this amount plus the
     * blur radius to prevent clipping of the shadow.
     *
     * @param  elevation  elevation in pixels
     * @return      returns self
     */
    fun setElevation(elevation: Float): BlurShadow {
        this.elevation = elevation
        return this
    }

    /**
     * Sets the angle in which the shadow is offset from the image.
     * Zero degrees indicates due west, and angles progress counter-clockwise.
     * Angles larger than 360° or smaller than 0° simply indicate wraps around the circle.
     *
     * @param  angle  the angle in degrees
     * @return      returns self
     */
    fun setAngle(angle: Float): BlurShadow {
        this.angle = angle
        return this
    }

    /**
     * Sets the cardinal direction in which the shadow is offset from the image.
     *
     * @param d the cardinal direction as a @Direction
     * @return returns self
     */
    fun setDirection(@Direction d: Int): BlurShadow {
        angle = getAngle(d)
        return this
    }

    /**
     * Sets the shadow's color.
     * Shadow is drawn black with 50% opacity by default.
     *
     * @param colour the color as a @ColorInt
     * @return returns self
     */
    fun setShadowColour(@ColorInt colour: Int): BlurShadow {
        this.colour = colour
        return this
    }

    /**
     * Sets the shadow's color by color resource.
     * Shadow is drawn black with 50% opacity by default.
     *
     * @param  res  the color resource as a @ColorRes
     * @return      returns self
     */
    fun setShadowColourRes(@ColorRes res: Int): BlurShadow {
        colour = ContextCompat.getColor(context, res)
        return this
    }

    private fun getAngle(@Direction d: Int): Float {
        return when (d) {
            EAST -> 0f
            NORTHEAST -> 45f
            NORTH -> 90f
            NORTHWEST -> 135f
            WEST -> 180f
            SOUTHWEST -> 225f
            SOUTH -> 270f
            SOUTHEAST -> 315f
            else -> throw IllegalArgumentException("Invalid Direction")
        }
    }

    override fun transform(pool: BitmapPool, source: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val bitmap = createBitmap(source.width, source.height)
        val shadow: Bitmap

        //Calculate Shadow Offset
        val shadowX = elevation * cos(Math.toRadians(angle.toDouble())).toFloat()
        val shadowY = -(elevation * sin(Math.toRadians(angle.toDouble())).toFloat())

        //Create Shadow Paint
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = if (shadowEffectEnabled) {
                ColorMatrixColorFilter(ColorMatrix().apply {
                    setScale(SHADOW_SCALE_RGB, SHADOW_SCALE_RGB, SHADOW_SCALE_RGB, SHADOW_SCALE_ALPHA)
                })
            } else {
                PorterDuffColorFilter(colour, PorterDuff.Mode.SRC_IN)
            }
            isAntiAlias = true
        }

        if (blurRadius <= MAX_BLUR_RADIUS) {
            //Apply Blur
            shadow = if (shadowEffectEnabled) {
                GPUBlur.blur(source, blurRadius)
            } else {
                createBitmap(source.width, source.height)
            }

            //Draw to Canvas
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            canvas.drawBitmap(shadow, 0F, 0F, shadowPaint)
            canvas.drawBitmap(source, 0f, 0f, null)
        } else {
            // The GPU pipeline inside GPUBlur handles large radii transparently
            // by downscaling internally, so we can treat this branch the same way
            // as the small-radius case and let the native side do the heavy lifting.
            shadow = if (shadowEffectEnabled) {
                GPUBlur.blur(source, blurRadius)
            } else {
                createBitmap(source.width, source.height)
            }

            //Draw to Canvas
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            canvas.drawBitmap(shadow, shadowX, shadowY, shadowPaint)
            canvas.drawBitmap(source, 0f, 0f, null)
        }

        //Output
        shadow.recycle()
        return bitmap
    }

    override fun equals(other: Any?): Boolean {
        if (other is BlurShadow) {
            return blurRadius == other.blurRadius
                    && elevation == other.elevation
                    && angle == other.angle
                    && colour == other.colour
                    && shadowEffectEnabled == other.shadowEffectEnabled
        }
        return false
    }

    override fun hashCode(): Int {
        return Util.hashCode(ID.hashCode(),
                             Util.hashCode(blurRadius,
                                           Util.hashCode(elevation,
                                                         Util.hashCode(angle,
                                                                       Util.hashCode(colour,
                                                                                     Util.hashCode(if (shadowEffectEnabled) 1 else 0))))))
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        val messages = ArrayList<ByteArray>()
        messages.add(ID_BYTES)
        messages.add(ByteBuffer.allocate(java.lang.Float.SIZE / java.lang.Byte.SIZE).putFloat(blurRadius).array())
        messages.add(ByteBuffer.allocate(java.lang.Float.SIZE / java.lang.Byte.SIZE).putFloat(elevation).array())
        messages.add(ByteBuffer.allocate(java.lang.Float.SIZE / java.lang.Byte.SIZE).putFloat(angle).array())
        messages.add(ByteBuffer.allocate(Integer.SIZE / java.lang.Byte.SIZE).putInt(colour).array())
        messages.add(ByteBuffer.allocate(1).put(if (shadowEffectEnabled) 1.toByte() else 0.toByte()).array())
        for (c in messages.indices) {
            messageDigest.update(messages[c])
        }
    }

    companion object {
        private const val ID = "app.simple.inure.glide.transformations.Shadow"
        private val ID_BYTES = ID.toByteArray()
        const val MAX_BLUR_RADIUS = 25.0f
        const val DEFAULT_SHADOW_SIZE = 20.0F
        private const val SHADOW_SCALE_RGB = 0.85f
        private const val SHADOW_SCALE_ALPHA = 0.8f
        const val EAST = 0
        const val NORTHEAST = 1
        const val NORTH = 2
        const val NORTHWEST = 3
        const val WEST = 4
        const val SOUTHWEST = 5
        const val SOUTH = 6
        const val SOUTHEAST = 7
    }

    init {
        colour = Color.argb(60, 0, 0, 0)
        shadowEffectEnabled = AppearancePreferences.isShadowEffectOn()
    }
}
