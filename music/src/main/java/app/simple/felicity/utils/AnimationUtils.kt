package app.simple.felicity.utils

import android.view.View

object AnimationUtils {

    /**
     * Animate the current view to 0 scale and 0 alpha.
     * @param duration The duration of the animation.
     * @param delay The delay of the animation.
     * @param action The action to perform after the animation.
     * @param T The type of the view to animate.
     * @see View.animate
     * @see View.setAlpha
     * @see View.setScaleX
     * @see View.setScaleY
     */
    inline fun <reified T : View> T.animateToZeroScale(duration: Long = 500L, delay: Long = 0L, crossinline action: () -> Unit) {
        animate()
            .alpha(0f)
            .scaleX(0f)
            .scaleY(0f)
            .setDuration(duration)
            .setStartDelay(delay)
            .withEndAction {
                action()
            }
    }
}