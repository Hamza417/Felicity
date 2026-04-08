package app.simple.felicity.decorations.drawables

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import kotlin.math.min

/**
 * A reusable [Drawable] that renders the fader-style pill thumb shared across
 * [app.simple.felicity.decorations.seekbars.FelicityEqualizerSliders],
 * [app.simple.felicity.decorations.fastscroll.SlideFastScroller], and
 * [app.simple.felicity.decorations.seekbars.FelicitySeekbar], unifying their thumb designs.
 *
 * The pill consists of three layers drawn from back to front:
 *  1. A solid filled rounded-rectangle body (with optional shadow glow).
 *  2. A ring stroke drawn inset from the body edge.
 *  3. Three grip lines centered on the pill, drawn perpendicular to the pill's long axis.
 *
 * Bounds may be set with floating-point precision via [setBoundsF] to avoid
 * sub-pixel rounding artefacts. The [orientation] property controls whether the pill's
 * long axis runs vertically ([Orientation.VERTICAL]) or horizontally ([Orientation.HORIZONTAL]).
 *
 * @author Hamza417
 */
class ThumbPillDrawable(
        var orientation: Orientation = Orientation.VERTICAL
) : Drawable() {

    /**
     * Controls the long axis of the pill.
     *
     * [VERTICAL] – tall pill (narrow width, large height); used by the equalizer slider
     *              and the fast scroller.
     * [HORIZONTAL] – wide pill (large width, narrow height); used by the horizontal seekbar.
     */
    enum class Orientation {
        VERTICAL,
        HORIZONTAL
    }

    /** Fill color of the pill body. Defaults to white. */
    @ColorInt
    var fillColor: Int = Color.WHITE

    /** Color of the ring stroke drawn around the pill body. Defaults to white. */
    @ColorInt
    var ringColor: Int = Color.WHITE

    /** Color of the grip lines drawn on the pill. Defaults to white. */
    @ColorInt
    var gripColor: Int = Color.WHITE

    /** Alpha for the grip lines in the range [0, 255]. Defaults to 130. */
    var gripAlpha: Int = 130

    /** Stroke width of the ring in pixels. Defaults to 3px. */
    var ringStrokePx: Float = 3f

    /** Stroke width of each grip line in pixels. Defaults to 1.5px. */
    var gripLineStrokePx: Float = 1.5f

    /**
     * Fraction of the pill's narrow-axis half-dimension used as the half-length of each grip
     * line. Default 0.42 (42 % of the narrow axis half-dimension on each side of center).
     */
    var gripLineHalfLengthFraction: Float = 0.42f

    /**
     * Fraction of the pill's long-axis half-dimension used as the spacing between adjacent grip
     * lines. Default 0.22.
     */
    var gripLineSpacingFraction: Float = 0.22f

    /**
     * Glow shadow radius in pixels applied to the fill paint. Set to 0 to disable the glow.
     * The owning view must use a hardware layer (API 28+) for the glow to composite correctly.
     */
    var shadowRadius: Float = 0f

    /** Color of the glow shadow. Only relevant when [shadowRadius] is greater than 0. */
    @ColorInt
    var shadowColor: Int = Color.TRANSPARENT

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val gripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val boundsF = RectF()
    private val pillRect = RectF()

    /**
     * Sets the pill bounds with floating-point precision, bypassing the integer truncation of
     * the standard [setBounds] overload.
     *
     * @param left   Left edge of the pill in view coordinates.
     * @param top    Top edge of the pill in view coordinates.
     * @param right  Right edge of the pill in view coordinates.
     * @param bottom Bottom edge of the pill in view coordinates.
     */
    fun setBoundsF(left: Float, top: Float, right: Float, bottom: Float) {
        boundsF.set(left, top, right, bottom)
        setBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
    }

    override fun draw(canvas: Canvas) {
        val b: RectF = if (!boundsF.isEmpty) {
            boundsF
        } else {
            val ib = bounds
            if (ib.isEmpty) return
            RectF(ib)
        }

        val cx = b.centerX()
        val cy = b.centerY()
        val halfW = b.width() / 2f
        val halfH = b.height() / 2f
        val cornerR = min(halfW, halfH)

        if (shadowRadius > 0f) {
            fillPaint.setShadowLayer(shadowRadius, 0f, 0f, shadowColor)
        } else {
            fillPaint.clearShadowLayer()
        }
        fillPaint.color = fillColor
        pillRect.set(b)
        canvas.drawRoundRect(pillRect, cornerR, cornerR, fillPaint)

        val ringInset = ringStrokePx / 2f
        ringPaint.color = ringColor
        ringPaint.strokeWidth = ringStrokePx
        pillRect.inset(ringInset, ringInset)
        canvas.drawRoundRect(
                pillRect,
                (cornerR - ringInset).coerceAtLeast(0f),
                (cornerR - ringInset).coerceAtLeast(0f),
                ringPaint
        )
        pillRect.inset(-ringInset, -ringInset)

        gripPaint.color = gripColor
        gripPaint.alpha = gripAlpha
        gripPaint.strokeWidth = gripLineStrokePx

        when (orientation) {
            Orientation.VERTICAL -> {
                val gripHalfLen = halfW * gripLineHalfLengthFraction
                val gripSpacing = halfH * gripLineSpacingFraction
                for (i in -1..1) {
                    val lineY = cy + i * gripSpacing
                    canvas.drawLine(cx - gripHalfLen, lineY, cx + gripHalfLen, lineY, gripPaint)
                }
            }
            Orientation.HORIZONTAL -> {
                val gripHalfLen = halfH * gripLineHalfLengthFraction
                val gripSpacing = halfW * gripLineSpacingFraction
                for (i in -1..1) {
                    val lineX = cx + i * gripSpacing
                    canvas.drawLine(lineX, cy - gripHalfLen, lineX, cy + gripHalfLen, gripPaint)
                }
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        ringPaint.alpha = alpha
        gripPaint.alpha = (gripAlpha * (alpha / 255f)).toInt().coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        ringPaint.colorFilter = colorFilter
        gripPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

