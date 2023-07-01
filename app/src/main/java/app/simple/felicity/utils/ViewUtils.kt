package app.simple.felicity.utils

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.*
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import app.simple.felicity.R
import app.simple.felicity.constants.Misc
import app.simple.felicity.preferences.AccessibilityPreferences
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.preferences.BehaviourPreferences

object ViewUtils {

    const val LEFT = -1
    const val RIGHT = 1

    /**
     * Dim the background when PopupWindow shows
     * Should be called from showAsDropDown function
     * because this is when container's parent is
     * initialized
     */
    fun dimBehind(contentView: View) {
        val container = contentView.rootView
        val windowManager =
            contentView.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val layoutParams = container.layoutParams as WindowManager.LayoutParams

        if (BehaviourPreferences.isDimmingOn()) {
            layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            layoutParams.dimAmount = getDimValue(contentView.context)
        }

        if (BehaviourPreferences.isBlurringOn()) {
            layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                layoutParams.blurBehindRadius = Misc.blurRadius.toInt()
            }
        }

        windowManager.updateViewLayout(container, layoutParams)
    }

    fun View.setMargins(marginLeft: Int, marginTop: Int, marginRight: Int, marginBottom: Int) {
        val params: LinearLayout.LayoutParams =
            LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            )
        params.setMargins(marginLeft, marginTop, marginRight, marginBottom)
        this.layoutParams = params
    }

    private fun createGradientBackground(
            @ColorInt startColor: Int,
            @ColorInt endColor: Int,
            YOUR_COLOR: Int
    ) =
        GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(startColor, endColor)
        ).also {
            it.shape = GradientDrawable.RECTANGLE
            it.cornerRadius = AppearancePreferences.getCornerRadius()
            it.setStroke(1, YOUR_COLOR)
        }

    /**
     * Get screen dim value based on interface theme
     */
    fun getDimValue(context: Context): Float {
        val outValue = TypedValue()
        context.resources.getValue(R.dimen.screen_dim, outValue, true)
        return outValue.float
    }

    /**
     * Adds outline shadows to the view using the accent color
     * of the app
     *
     * @param contentView [View] that needs to be elevated with colored
     *                    shadow
     */
    fun addShadow(contentView: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && BehaviourPreferences.areColoredShadowsOn()) {
            contentView.outlineAmbientShadowColor = AppearancePreferences.getAccentColor()
            contentView.outlineSpotShadowColor = AppearancePreferences.getAccentColor()
        }
    }

    /**
     * Makes the view go away
     */
    fun View.gone() {
        clearAnimation()
        this.visibility = View.GONE
    }

    fun View.gone(animate: Boolean) {
        if (animate) {
            clearAnimation()
            this.animate()
                .scaleY(0F)
                .scaleX(0F)
                .alpha(0F)
                .setInterpolator(AccelerateInterpolator())
                .setDuration(this.resources.getInteger(R.integer.animation_duration).toLong())
                .setListener(object : Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {
                        /* no-op */
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        this@gone.visibility = View.GONE
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        /* no-op */
                    }

                    override fun onAnimationRepeat(animation: Animator) {
                        /* no-op */
                    }
                })
                .start()
        } else {
            this.visibility = View.GONE
        }
    }

    /**
     * Makes the view go away
     *
     * @param animate adds animation to the process
     */
    fun View.invisible(animate: Boolean) {
        if (animate) {
            clearAnimation()
            this.animate()
                .scaleY(0F)
                .scaleX(0F)
                .alpha(0F)
                .setInterpolator(AccelerateInterpolator())
                .setDuration(this.resources.getInteger(R.integer.animation_duration).toLong())
                .setListener(object : Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {
                        /* no-op */
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        this@invisible.visibility = View.INVISIBLE
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        /* no-op */
                    }

                    override fun onAnimationRepeat(animation: Animator) {
                        /* no-op */
                    }
                })
                .start()
        } else {
            this.visibility = View.INVISIBLE
        }
    }

    /**
     * Makes the view come back
     *
     * @param animate adds animation to the process
     */
    fun View.visible(animate: Boolean) {
        if (visibility == View.VISIBLE) return

        if (animate) {
            clearAnimation()

            this.animate()
                .scaleX(1F)
                .scaleY(1F)
                .alpha(1F)
                .setInterpolator(LinearOutSlowInInterpolator())
                .setDuration(this.resources.getInteger(R.integer.animation_duration).toLong())
                .setListener(object : Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {
                        this@visible.visibility = View.VISIBLE
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        /* no-op */
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        /* no-op */
                    }

                    override fun onAnimationRepeat(animation: Animator) {
                        /* no-op */
                    }
                })
                .start()
        } else {
            this.visibility = View.VISIBLE
        }
    }

    // ViewExtensions

    fun View.fadeOutAnimation(
            duration: Long = 300,
            visibility: Int = View.INVISIBLE,
            completion: (() -> Unit)? = null
    ) {
        animate()
            .alpha(0f)
            .setDuration(duration)
            .withEndAction {
                this.visibility = visibility
                completion?.let {
                    it()
                }
            }
    }

    fun View.fadeInAnimation(duration: Long = 300, completion: (() -> Unit)? = null) {
        alpha = 0f
        visibility = View.VISIBLE
        animate()
            .alpha(1f)
            .setDuration(duration)
            .withEndAction {
                completion?.let {
                    it()
                }
            }
    }

    fun View.slideOutAnimation(duration: Long = 300, delay: Long = 0L, direction: Int, visibility: Int = View.INVISIBLE, completion: (() -> Unit)? = null) {
        animate()
            .translationX(direction * 50F)
            .alpha(0f)
            .setDuration(duration)
            .setStartDelay(delay)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                this.visibility = visibility
                completion?.let {
                    it()
                }
            }
    }

    fun View.slideInAnimation(duration: Long = 300, delay: Long = 0L, direction: Int, completion: (() -> Unit)? = null) {
        translationX = 50F * -direction
        visibility = View.VISIBLE
        animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(duration)
            .setStartDelay(delay)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                completion?.let {
                    it()
                }
            }
    }

    fun View.animateElevation(elevation: Float): ValueAnimator? {
        val valueAnimator = ValueAnimator.ofFloat(0F, elevation)
        valueAnimator.interpolator = LinearOutSlowInInterpolator()
        valueAnimator.duration = 5000
        valueAnimator.addUpdateListener { animation ->
            this.elevation = animation.animatedValue as Float
        }
        valueAnimator.start()
        return valueAnimator
    }

    fun <T : View> T.onDimensions(function: (Int, Int) -> Unit) {
        if (isLaidOut && height != 0 && width != 0) {
            function(width, height)
        } else {
            if (height == 0 || width == 0) {
                var onLayoutChangeListener: View.OnLayoutChangeListener? = null
                val onGlobalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener?

                onGlobalLayoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        if (isShown) {
                            removeOnLayoutChangeListener(onLayoutChangeListener)
                            viewTreeObserver.removeOnGlobalLayoutListener(this)
                            function(width, height)
                        }
                    }
                }

                onLayoutChangeListener = object : View.OnLayoutChangeListener {
                    override fun onLayoutChange(
                            v: View?,
                            left: Int,
                            top: Int,
                            right: Int,
                            bottom: Int,
                            oldLeft: Int,
                            oldTop: Int,
                            oldRight: Int,
                            oldBottom: Int
                    ) {
                        val width = v?.width ?: 0
                        val height = v?.height ?: 0
                        if (width > 0 && height > 0) {
                            // remove after finish
                            viewTreeObserver.removeOnGlobalLayoutListener(onGlobalLayoutListener)
                            v?.removeOnLayoutChangeListener(this)
                            function(width, height)
                        }
                    }
                }

                viewTreeObserver.addOnGlobalLayoutListener(onGlobalLayoutListener)
                addOnLayoutChangeListener(onLayoutChangeListener)
            } else {
                function(width, height)
            }
        }
    }

    /**
     * Animate the view on mouse hover
     */
    fun View.triggerHover(event: MotionEvent) {
        if (isClickable) {
            if (!AccessibilityPreferences.isAnimationReduced()) {
                if (event.action == MotionEvent.ACTION_HOVER_ENTER) {
                    animate()
                        .scaleX(Misc.hoverAnimationScaleOnHover)
                        .scaleY(Misc.hoverAnimationScaleOnHover)
                        .setDuration(Misc.hoverAnimationDuration)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                } else if (event.action == MotionEvent.ACTION_HOVER_EXIT) {
                    animate()
                        .scaleX(Misc.hoverAnimationScaleOnUnHover)
                        .scaleY(Misc.hoverAnimationScaleOnUnHover)
                        .setDuration(Misc.hoverAnimationDuration)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            }
        }
    }
}