package app.simple.felicity.extensions.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import app.simple.felicity.decorations.corners.LayoutBackground
import app.simple.felicity.decorations.ripple.DynamicRippleConstraintLayout
import app.simple.felicity.repository.listeners.MediaStateListener
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.ColorUtils
import app.simple.felicity.theme.managers.ThemeManager

/**
 * A [DynamicRippleConstraintLayout] that automatically registers itself with
 * [MediaManager] to reflect the currently playing song state. When the playing
 * song changes, the selection highlight transitions smoothly via a color animation.
 *
 * Call [setAudioID] once per bind cycle to associate a song ID with this view.
 * All subsequent highlight updates are handled internally without adapter callbacks.
 *
 * @author Hamza417
 */
class MediaAwareRippleConstraintLayout @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null
) : DynamicRippleConstraintLayout(context, attrs), MediaStateListener {

    private var audioID: Long = -1L
    private var selectionAnimator: ValueAnimator? = null

    /**
     * Binds the given [audioID] to this view. The initial selection state is applied
     * instantly (no animation) so the recycled view reflects the correct state immediately.
     *
     * @param audioID the ID of the audio item this view represents.
     */
    fun setAudioID(audioID: Long) {
        if (audioID == -1L) {
            return
        }
        selectionAnimator?.cancel()
        selectionAnimator = null
        this.audioID = audioID
        isSelected = audioID == MediaManager.getCurrentSongId()
    }

    /**
     * Called by [MediaManager] on the main thread whenever the playing song changes.
     * Smoothly animates the background tint between the transparent and selected states.
     *
     * @param audio the newly playing [Audio], or null if playback stopped.
     */
    override fun onAudioChange(audio: Audio?) {
        val shouldBeSelected = audio?.id == audioID
        if (isSelected == shouldBeSelected) return
        animateSelectionChange(shouldBeSelected)
    }

    /**
     * Animates the background tint between the unselected (transparent) and selected
     * (accent color with reduced alpha) states over a short duration.
     *
     * @param shouldSelect true when transitioning to the selected state, false otherwise.
     */
    private fun animateSelectionChange(shouldSelect: Boolean) {
        selectionAnimator?.cancel()
        selectionAnimator = null

        val accentColor = ThemeManager.accent.primaryAccentColor
        val selectedColor = ColorUtils.changeAlpha(accentColor, 25)
        val transparentColor = ColorUtils.changeAlpha(accentColor, 0)

        if (shouldSelect) {
            LayoutBackground.setBackground(context, this, null, radius)
            setBackgroundTintList(ColorStateList.valueOf(transparentColor))

            selectionAnimator = ValueAnimator.ofArgb(transparentColor, selectedColor).apply {
                duration = ANIMATION_DURATION
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    setBackgroundTintList(ColorStateList.valueOf(it.animatedValue as Int))
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        isSelected = true
                    }
                })
                start()
            }
        } else {
            val fromColor = backgroundTintList?.defaultColor ?: selectedColor

            selectionAnimator = ValueAnimator.ofArgb(fromColor, transparentColor).apply {
                duration = ANIMATION_DURATION
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    setBackgroundTintList(ColorStateList.valueOf(it.animatedValue as Int))
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        isSelected = false
                    }
                })
                start()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        MediaManager.registerListener(this)
    }

    override fun onDetachedFromWindow() {
        selectionAnimator?.cancel()
        selectionAnimator = null
        super.onDetachedFromWindow()
        MediaManager.unregisterListener(this)
    }

    companion object {
        private const val ANIMATION_DURATION = 300L
    }
}