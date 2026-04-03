package app.simple.felicity.decorations.miniplayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import app.simple.felicity.decoration.R

/**
 * Encapsulates the crossfading play/pause icon drawing for [MiniPlayer].
 *
 * Instead of morphing geometry, this drawer blends [R.drawable.ic_play] and
 * [R.drawable.ic_pause] by manipulating their alpha values according to [progress]:
 *
 * - `progress = 0f` → playing state: [R.drawable.ic_pause] fully visible
 * - `progress = 1f` → paused state: [R.drawable.ic_play] fully visible
 * - Values in between produce a smooth crossfade between both icons
 *
 * Call [updateGeometry] whenever the button zone size changes, then call [draw] inside
 * [android.view.View.onDraw].
 *
 * @param context Context used to load [R.drawable.ic_play] and [R.drawable.ic_pause].
 * @author Hamza417
 */
internal class MiniPlayerPlayPauseDrawer(context: Context) {

    /**
     * Tint color applied to both drawables.
     * Updating this immediately re-tints both icons.
     */
    @ColorInt
    var color: Int = 0
        set(value) {
            field = value
            playDrawable.setTint(value)
            pauseDrawable.setTint(value)
        }

    /**
     * Morph progress between the playing and paused icon states.
     * `0f` = playing (pause icon fully visible), `1f` = paused (play icon fully visible).
     * Drive this with a [android.animation.ValueAnimator] for smooth transitions.
     */
    var progress: Float = 1f

    /**
     * Slide-out fraction: `0f` = fully visible, `1f` = fully off-screen to the right.
     * Set this during a drag gesture to hide the button while the user scrolls.
     */
    var slideOut: Float = 0f

    /** The total horizontal width of the button zone; used to compute the slide-out offset. */
    var btnZoneWidth: Float = 0f

    /** Horizontal center of the button zone in view coordinate space. */
    var centerX: Float = 0f

    /** Vertical center of the button zone in view coordinate space. */
    var centerY: Float = 0f

    private val playDrawable: Drawable =
        ContextCompat.getDrawable(context, R.drawable.ic_play)!!.mutate()

    private val pauseDrawable: Drawable =
        ContextCompat.getDrawable(context, R.drawable.ic_pause)!!.mutate()

    private var iconHalfSize: Float = 0f

    /**
     * Recomputes icon bounds from the button square side [btnSize].
     * Must be called from [android.view.View.onSizeChanged].
     */
    fun updateGeometry(btnSize: Float) {
        iconHalfSize = btnSize * 0.35f
    }

    /**
     * Draws the crossfaded icon onto [canvas] at the pre-configured [centerX]/[centerY].
     * Returns immediately when the button is fully slid off screen.
     */
    fun draw(canvas: Canvas) {
        val buttonAlpha = ((1f - slideOut) * 255f).toInt().coerceIn(0, 255)
        if (buttonAlpha == 0) return

        val slideOffsetPx = slideOut * btnZoneWidth
        val half = iconHalfSize
        val l = (centerX + slideOffsetPx - half).toInt()
        val t = (centerY - half).toInt()
        val r = (centerX + slideOffsetPx + half).toInt()
        val b = (centerY + half).toInt()

        // At progress=0 (playing) → pause icon visible, play icon hidden.
        // At progress=1 (paused) → play icon visible, pause icon hidden.
        val pauseAlpha = ((1f - progress) * buttonAlpha).toInt().coerceIn(0, 255)
        val playAlpha = (progress * buttonAlpha).toInt().coerceIn(0, 255)

        if (pauseAlpha > 0) {
            pauseDrawable.setBounds(l, t, r, b)
            pauseDrawable.alpha = pauseAlpha
            pauseDrawable.draw(canvas)
        }

        if (playAlpha > 0) {
            playDrawable.setBounds(l, t, r, b)
            playDrawable.alpha = playAlpha
            playDrawable.draw(canvas)
        }
    }
}
