package app.simple.felicity.decorations.miniplayer

import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import androidx.annotation.ColorInt
import androidx.core.graphics.withTranslation
import com.google.android.material.math.MathUtils.lerp
import kotlin.math.sqrt

/**
 * Encapsulates the morphing play/pause icon geometry and drawing.
 *
 * The icon morphs between a play triangle (progress = 0) and two pause bars
 * (progress = 1), matching the geometry of FlipPlayPauseView exactly.
 *
 * Call [updateGeometry] whenever the button zone size changes, then call
 * [draw] inside [android.view.View.onDraw].
 */
internal class MiniPlayerPlayPauseDrawer {

    /** Tint color for the icon paths. */
    @ColorInt
    var color: Int = 0
        set(value) {
            field = value
            paint.color = value
        }

    /** 0f = play triangle, 1f = pause bars. Drive this with a [android.animation.ValueAnimator]. */
    var progress: Float = 1f

    /**
     * Slide-out fraction: 0f = normal, 1f = fully off-screen to the right.
     * Set this during drag to hide the button while the user scrolls.
     */
    var slideOut: Float = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        pathEffect = CornerPathEffect(10f)
    }

    private val leftPath = Path()
    private val rightPath = Path()

    // Geometry fields — set by updateGeometry()
    private var halfHeight = 0f
    private var barWidth = 0f
    private var barGap = 0f
    private var triHeight = 0f

    /** The total horizontal width of the button zone; used to compute the slide-out offset. */
    var btnZoneWidth: Float = 0f

    /** Horizontal center of the button zone in the view coordinate space. */
    var centerX: Float = 0f

    /** Vertical center of the button zone in the view coordinate space. */
    var centerY: Float = 0f

    /**
     * Recomputes all geometry from the button square side [btnSize].
     * Must be called from [android.view.View.onSizeChanged].
     */
    fun updateGeometry(btnSize: Float) {
        halfHeight = btnSize * 0.5f
        barWidth = halfHeight / 2.5f
        barGap = barWidth / 1.5f
        triHeight = (sqrt(3.0) / 2.0 * halfHeight).toFloat()
    }

    /**
     * Draws the icon onto [canvas] at the pre-configured [centerX]/[centerY].
     * Returns immediately if the icon is fully slid out (alpha would be 0).
     */
    fun draw(canvas: Canvas) {
        val h = halfHeight
        val bw = barWidth
        val gap = barGap
        val tri = triHeight
        val p = progress

        val buttonAlpha = ((1f - slideOut) * 255f).toInt().coerceIn(0, 255)
        if (buttonAlpha == 0) return

        leftPath.rewind()
        rightPath.rewind()

        // Right pause bar (fades out toward play)
        val rightBarX = bw + gap
        rightPath.moveTo(rightBarX, 0f)
        rightPath.lineTo(rightBarX + bw, 0f)
        rightPath.lineTo(rightBarX + bw, h)
        rightPath.lineTo(rightBarX, h)
        rightPath.close()

        // Left shape — morphs between bar (p=1) and triangle tip (p=0)
        if (p >= 0.9f) {
            leftPath.moveTo(0f, 0f)
            leftPath.lineTo(tri, h / 2f)
            leftPath.lineTo(0f, h)
            leftPath.close()
        } else {
            val tipX = lerp(bw, tri, p)
            val topY = lerp(0f, h / 2f, p)
            val bottomY = lerp(h, h / 2f, p)
            leftPath.moveTo(0f, 0f)
            leftPath.lineTo(tipX, topY)
            leftPath.lineTo(tipX, bottomY)
            leftPath.lineTo(0f, h)
            leftPath.close()
        }

        val slideOffsetPx = slideOut * btnZoneWidth
        val totalPauseWidth = bw * 2f + gap
        val offsetPause = -totalPauseWidth / 2f
        val offsetPlay = -tri / 2f + bw * 0.1f
        val offsetX = lerp(offsetPause, offsetPlay, p)

        canvas.withTranslation(centerX + slideOffsetPx, centerY) {
            withTranslation(offsetX, -h / 2f) {
                paint.alpha = buttonAlpha
                drawPath(leftPath, paint)

                if (p < 1f) {
                    paint.alpha = ((1f - p) * buttonAlpha).toInt().coerceIn(0, 255)
                    drawPath(rightPath, paint)
                }

                paint.alpha = 255
            }
        }
    }
}

