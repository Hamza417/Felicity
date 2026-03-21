package app.simple.felicity.decorations.helpers

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import androidx.fragment.app.Fragment

class SwipeDownToCloseListener(
        private val fragment: Fragment,
        private val view: View
) : View.OnTouchListener {

    private var initialY = 0f
    private val dismissThreshold = 300f // How far down they need to swipe
    private var isDragging = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialY = event.rawY
                isDragging = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false
                val deltaY = event.rawY - initialY

                // Only allow swiping downwards
                if (deltaY > 0) {
                    view.translationY = deltaY

                    // Add a slight scale effect for that "predictive" feel
                    val scale = 1f - (deltaY / view.height) * 0.2f
                    view.scaleX = scale
                    view.scaleY = scale
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                val deltaY = event.rawY - initialY

                if (deltaY > dismissThreshold) {
                    // Threshold met: Animate off-screen and close fragment
                    view.animate()
                        .translationY(view.height.toFloat())
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction {
                            fragment.parentFragmentManager.popBackStack()
                        }
                        .start()
                } else {
                    // Threshold not met: Snap back to original position
                    view.animate()
                        .translationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .start()
                }
                return true
            }
        }
        return false
    }

    fun passOnTouchEvent(event: MotionEvent): Boolean {
        return onTouch(view, event)
    }

    /**
     * Passes a continuous [MotionEvent.ACTION_MOVE] event that originates from a child view's
     * vertical-drag delegate (e.g. [app.simple.felicity.decorations.pager.FelicityPager]).
     *
     * Because [MotionEvent.ACTION_DOWN] is consumed by the child, this listener never receives
     * it directly. This method bootstraps [initialY] and [isDragging] on the first call so
     * that subsequent move events are processed correctly.
     *
     * @param event            The current [MotionEvent.ACTION_MOVE] event.
     * @param gestureStartRawY The raw screen Y coordinate at which the drag gesture began.
     *                         Pass `event.rawY - totalDeltaY` when calling from an
     *                         [app.simple.felicity.decorations.pager.FelicityPager.OnVerticalDragListener].
     */
    fun passExternalDrag(event: MotionEvent, gestureStartRawY: Float) {
        if (!isDragging) {
            initialY = gestureStartRawY
            isDragging = true
        }
        onTouch(view, event)
    }

    /**
     * Finalizes an externally-driven drag gesture, applying the same dismiss-or-snap-back
     * logic that would normally be triggered by [MotionEvent.ACTION_UP].
     *
     * Call this from
     * [app.simple.felicity.decorations.pager.FelicityPager.OnVerticalDragListener.onVerticalDragEnd]
     * so that releasing the finger over the pager region correctly closes or restores the fragment.
     *
     * @param finalRawY The raw screen Y coordinate at the moment the finger was lifted.
     */
    fun endExternalDrag(finalRawY: Float) {
        isDragging = false
        val deltaY = finalRawY - initialY

        if (deltaY > dismissThreshold) {
            view.animate()
                .translationY(view.height.toFloat())
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    fragment.parentFragmentManager.popBackStack()
                }
                .start()
        } else {
            view.animate()
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .start()
        }
    }
}