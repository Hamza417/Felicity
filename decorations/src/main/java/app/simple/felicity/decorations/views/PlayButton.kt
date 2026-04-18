package app.simple.felicity.decorations.views

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.os.BundleCompat
import app.simple.felicity.decoration.R
import app.simple.felicity.decorations.ripple.DynamicRippleImageButton

class PlayButton : DynamicRippleImageButton {

    private val DURATION = 100L
    private var WAS_PLAYING = false

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        setImageResource(R.drawable.ic_play)
    }

    fun setPlaying(isPlaying: Boolean, animate: Boolean = true) {
        if (isPlaying != WAS_PLAYING) {
            setAppropriateIcon(isPlaying, animate)
            WAS_PLAYING = isPlaying
        }
    }

    fun setPlaying() = setPlaying(isPlaying = true, animate = true)
    fun setPaused() = setPlaying(isPlaying = false, animate = true)

    private fun setAppropriateIcon(isPlaying: Boolean, animate: Boolean) {
        if (animate) {
            clearAnimation()

            animate()
                .alpha(0f)
                .setDuration(DURATION)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    if (isPlaying) {
                        setImageResource(R.drawable.ic_pause)
                    } else {
                        setImageResource(R.drawable.ic_play)
                    }
                    animate()
                        .alpha(1f)
                        .setDuration(DURATION)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }.start()
        } else {
            if (isPlaying) {
                setImageResource(R.drawable.ic_pause)
            } else {
                setImageResource(R.drawable.ic_play)
            }
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle()
        bundle.putParcelable(KEY_SUPER_STATE, super.onSaveInstanceState())
        bundle.putBoolean(KEY_IS_PLAYING, WAS_PLAYING)
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            val fav = state.getBoolean(KEY_IS_PLAYING)
            super.onRestoreInstanceState(
                    BundleCompat.getParcelable(state, KEY_SUPER_STATE, Parcelable::class.java),
            )
            setPlaying(fav, animate = false)
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        clearAnimation()
    }

    companion object {
        private const val KEY_SUPER_STATE = "superState"
        private const val KEY_IS_PLAYING = "isPlaying"
    }
}
