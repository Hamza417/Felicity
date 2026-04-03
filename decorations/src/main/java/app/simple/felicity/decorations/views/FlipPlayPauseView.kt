package app.simple.felicity.decorations.views

import android.content.Context
import android.graphics.drawable.TransitionDrawable
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import app.simple.felicity.decoration.R
import app.simple.felicity.decorations.theme.ThemeImageButton

/**
 * A play/pause button that crossfades between [R.drawable.ic_play] and [R.drawable.ic_pause].
 *
 * Extends [ThemeImageButton] so all `app:buttonTintType` attributes and theme-change
 * listeners work automatically without any additional wiring. The crossfade is driven
 * by a [TransitionDrawable] with cross-fade enabled, meaning the outgoing icon fades
 * out at the same time the incoming icon fades in.
 *
 * @author Hamza417
 */
class FlipPlayPauseView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : ThemeImageButton(context, attrs, defStyleAttr) {

    /**
     * Duration of the crossfade animation in milliseconds.
     */
    var animDuration: Long = 300L

    /**
     * Current logical playback state.
     * `true` = playing (pause icon visible), `false` = paused (play icon visible).
     */
    private var isPlaying = false

    private val transitionDrawable: TransitionDrawable

    init {
        val playDrawable = ContextCompat.getDrawable(context, R.drawable.ic_play)!!.mutate()
        val pauseDrawable = ContextCompat.getDrawable(context, R.drawable.ic_pause)!!.mutate()
        transitionDrawable = TransitionDrawable(arrayOf(playDrawable, pauseDrawable)).apply {
            isCrossFadeEnabled = true
        }
        setImageDrawable(transitionDrawable)
        isClickable = true
        setOnClickListener { toggle() }
    }

    /**
     * Toggles between the play and pause states with animation.
     */
    fun toggle() {
        setPlaying(!isPlaying, animate = true)
    }

    /**
     * Sets the current playback state.
     *
     * When [playing] is `true` the pause icon is shown (user can pause).
     * When [playing] is `false` the play icon is shown (user can play).
     *
     * @param playing Target state.
     * @param animate Whether to crossfade the icon transition.
     */
    fun setPlaying(playing: Boolean, animate: Boolean = true) {
        if (isPlaying == playing) return
        isPlaying = playing
        val duration = if (animate) animDuration.toInt() else 0
        if (playing) {
            transitionDrawable.startTransition(duration)
        } else {
            transitionDrawable.reverseTransition(duration)
        }
    }

    /**
     * Transitions to the paused state, showing the play icon.
     *
     * @param animate Whether to crossfade the icon transition.
     */
    fun paused(animate: Boolean = true) {
        setPlaying(false, animate)
    }

    /**
     * Transitions to the playing state, showing the pause icon.
     *
     * @param animate Whether to crossfade the icon transition.
     */
    fun playing(animate: Boolean = true) {
        setPlaying(true, animate)
    }

    override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle()
        bundle.putParcelable(KEY_SUPER_STATE, super.onSaveInstanceState())
        bundle.putBoolean(KEY_IS_PLAYING, isPlaying)
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            isPlaying = state.getBoolean(KEY_IS_PLAYING)
            if (isPlaying) {
                transitionDrawable.startTransition(0)
            } else {
                transitionDrawable.reverseTransition(0)
            }
            super.onRestoreInstanceState(
                    BundleCompat.getParcelable(state, KEY_SUPER_STATE, Parcelable::class.java)
            )
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    private companion object {
        private const val KEY_SUPER_STATE = "superState"
        private const val KEY_IS_PLAYING = "isPlaying"
    }
}