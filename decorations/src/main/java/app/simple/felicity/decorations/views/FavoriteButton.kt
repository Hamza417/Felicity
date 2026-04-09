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
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.models.Theme

class FavoriteButton : DynamicRippleImageButton {

    private val DURATION = 250L
    private var WAS_FAVORITE = false
    private var isFavorite = false

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        setImageResource(R.drawable.ic_favorite_border)
    }

    fun setFavorite(isFavorite: Boolean, animate: Boolean) {
        this.isFavorite = isFavorite

        if (WAS_FAVORITE != isFavorite) {
            setAppropriateIcon(isFavorite, animate)
            WAS_FAVORITE = isFavorite
        }

        updateTint(animate, isFavorite)
    }

    private fun setAppropriateIcon(isFavorite: Boolean, animate: Boolean) {
        if (animate) {
            clearAnimation()

            animate()
                .scaleX(0f)
                .scaleY(0f)
                .alpha(0f)
                .setDuration(150)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    if (isFavorite) {
                        setImageResource(R.drawable.ic_favorite_filled)
                    } else {
                        setImageResource(R.drawable.ic_favorite_border)
                    }
                    animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(DURATION)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }.start()
        } else {
            if (isFavorite) {
                setImageResource(R.drawable.ic_favorite_filled)
            } else {
                setImageResource(R.drawable.ic_favorite_border)
            }
        }
    }

    private fun updateTint(animate: Boolean, isFavorite: Boolean) {
        if (isFavorite) {
            setTint(ThemeManager.accent.primaryAccentColor, animate)
        } else {
            setTint(ThemeManager.theme.iconTheme.regularIconColor, animate)
        }
    }

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        updateTint(animate, isFavorite)
    }

    override fun onAccentChanged(accent: Accent) {
        updateTint(true, isFavorite)
    }

    override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle()
        bundle.putParcelable(KEY_SUPER_STATE, super.onSaveInstanceState())
        bundle.putBoolean(KEY_IS_FAVORITE, isFavorite)
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            val fav = state.getBoolean(KEY_IS_FAVORITE)
            super.onRestoreInstanceState(
                    BundleCompat.getParcelable(state, KEY_SUPER_STATE, Parcelable::class.java),
            )
            setFavorite(fav, animate = false)
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
        private const val KEY_IS_FAVORITE = "isFavorite"
    }
}
