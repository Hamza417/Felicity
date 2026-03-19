package app.simple.felicity.extensions.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.decorations.ripple.DynamicRippleConstraintLayout
import app.simple.felicity.repository.listeners.MediaStateListener
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Audio

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
        setSelected(shouldBeSelected, true)
        if (shouldBeSelected) {
            // requestRecyclerViewToScrollToSelf()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        MediaManager.registerListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        MediaManager.unregisterListener(this)
    }

    private fun requestRecyclerViewToScrollToSelf() {
        var currentView: View = this
        var currentParent = parent

        while (currentParent != null) {
            if (currentParent is RecyclerView) {
                // We found the RV! 'currentView' is now the ViewHolder's root view.
                val position = currentParent.getChildAdapterPosition(currentView)

                if (position != RecyclerView.NO_POSITION) {
                    // Or if you want an instant snap instead of a smooth scroll:
                    currentParent.scrollToPosition(position)
                }
                break
            }

            // Move up the tree, keeping track of the immediate child
            if (currentParent is View) {
                currentView = currentParent
                currentParent = currentParent.parent
            } else {
                break // We hit the ViewRootImpl or a non-View parent
            }
        }
    }

    companion object {
        private const val ANIMATION_DURATION = 500L
    }
}