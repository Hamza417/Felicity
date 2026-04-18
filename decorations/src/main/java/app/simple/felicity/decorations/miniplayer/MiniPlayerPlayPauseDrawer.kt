package app.simple.felicity.decorations.miniplayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import app.simple.felicity.decoration.R

/**
 * Draws the play/pause icon for the mini player using the same [R.drawable.ic_play] and
 * [R.drawable.ic_pause] resources that the rest of the app uses, so the button style stays
 * consistent across every screen.
 *
 * The [MiniPlayer] cross-fades between states by animating [alpha] to 0, flipping [isPlaying],
 * then animating [alpha] back to 255 — quick, clean, and obviously intentional.
 *
 * A circular highlight is drawn behind the icon. Its radius is set via [circleRadius] to
 * match the card's own corner radius, so the two curves feel like they belong together
 * rather than competing for attention.
 *
 * @author Hamza417
 */
internal class MiniPlayerPlayPauseDrawer(private val context: Context) {

    /** Tint color applied to whichever drawable is currently shown. */
    @ColorInt
    var color: Int = Color.WHITE
        set(value) {
            field = value
            retintDrawables()
        }

    /**
     * The radius of the circular highlight in pixels.
     * Set this to the card's [MiniPlayer.cornerRadiusPx] so the two curves look related —
     * a small radius matches a subtly-rounded card, a large one a pill-shaped card.
     */
    var circleRadius: Float = 0f

    /**
     * Whether the player is currently in the playing state.
     * Flip this while [alpha] is 0 so the drawable swap is invisible to the user.
     */
    var isPlaying: Boolean = false

    /**
     * Overall opacity of the button (icon + highlight) in [0, 255].
     * Driven externally for both swipe-gesture fades and play/pause state transitions.
     */
    var alpha: Int = 255

    // Reusable bounds rect so we don't allocate inside draw().
    private val iconBounds = Rect()

    // Load both drawables once and keep them tinted — never allocate inside draw().
    private val icPlay = ContextCompat.getDrawable(context, R.drawable.ic_play)!!.mutate()
    private val icPause = ContextCompat.getDrawable(context, R.drawable.ic_pause)!!.mutate()

    /** Geometry — populated by [updateGeometry], read only inside [draw]. */
    private var halfHeight = 0f

    /** Horizontal center of the button zone in view coordinates. */
    var centerX: Float = 0f

    /** Vertical center of the button zone in view coordinates. */
    var centerY: Float = 0f

    init {
        retintDrawables()
    }

    /**
     * Updates the icon layout geometry from the button square's side length.
     * Call this from [android.view.View.onSizeChanged] whenever the view resizes.
     *
     * @param btnSize side length of the button zone square in pixels
     */
    fun updateGeometry(btnSize: Float) {
        halfHeight = btnSize * 0.5f
    }

    /** Applies the current [color] tint to both drawables so [draw] never has to allocate. */
    private fun retintDrawables() {
        for (d in listOf(icPlay, icPause)) {
            DrawableCompat.setTint(DrawableCompat.wrap(d), color)
        }
    }

    /**
     * Draws the highlight circle and the current icon onto [canvas].
     * Nothing is drawn when [alpha] is 0 — saves a GPU round-trip on every frame.
     */
    fun draw(canvas: Canvas) {
        if (alpha == 0) return

        // Pick the drawable for the current state and set its bounds centered on the button zone.
        val drawable = if (isPlaying) icPause else icPlay
        val r = halfHeight.toInt()
        iconBounds.set(
                (centerX - r).toInt(),
                (centerY - r).toInt(),
                (centerX + r).toInt(),
                (centerY + r).toInt()
        )
        drawable.bounds = iconBounds
        drawable.alpha = alpha
        drawable.draw(canvas)
    }
}
