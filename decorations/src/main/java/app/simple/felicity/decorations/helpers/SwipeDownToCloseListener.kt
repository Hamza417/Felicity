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
}