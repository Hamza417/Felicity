import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.transition.TransitionManager
import androidx.viewbinding.ViewBinding
import app.simple.felicity.core.maths.Number.half
import com.google.android.material.transition.MaterialContainerTransform
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

open class SharedElementPopup<VB : ViewBinding> @JvmOverloads constructor(
        private val container: ViewGroup,
        private val anchorView: View,
        private val inflateBinding: (LayoutInflater, ViewGroup?, Boolean) -> VB,
        private val onPopupInflated: (VB) -> Unit = {},
        private val onDismiss: (() -> Unit)? = null
) {

    private lateinit var scrimView: View
    private lateinit var popupContainer: FrameLayout
    private lateinit var binding: VB

    companion object {
        private const val TRANSITION_NAME = "shared_element_popup_transition"
        private const val DURATION = 350L
        private const val END_ELEVATION = 0f
        private val INTERPOLATOR = DecelerateInterpolator(1.5F)
    }

    fun show() {
        binding = inflateBinding(LayoutInflater.from(container.context), null, false)

        scrimView = View(container.context).apply {
            setBackgroundColor("#80000000".toColorInt())
            layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            setOnClickListener { dismiss() }
            alpha = 0f
            animate().alpha(1f).setDuration(DURATION).start()
        }

        container.addView(scrimView)

        val anchorLocation = IntArray(2)
        anchorView.getLocationInWindow(anchorLocation)
        val containerLocation = IntArray(2)
        container.getLocationInWindow(containerLocation)

        val anchorX = anchorLocation[0] - containerLocation[0]
        val anchorY = anchorLocation[1] - containerLocation[1]
        val anchorWidth = anchorView.width
        val anchorHeight = anchorView.height

        binding.root.measure(
                View.MeasureSpec.makeMeasureSpec(container.width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.UNSPECIFIED
        )
        val popupWidth = binding.root.measuredWidth
        val popupHeight = binding.root.measuredHeight

        var leftMargin = anchorX + anchorWidth.half() - popupWidth.half()
        var topMargin = anchorY + anchorHeight.half() - popupHeight.half()

        // Clamp margins to keep popup inside container
        leftMargin = max(0, min(leftMargin, container.width - popupWidth))
        topMargin = max(0, min(topMargin, container.height - popupHeight))

        popupContainer = FrameLayout(container.context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            background = null
            elevation = END_ELEVATION
            ViewCompat.setTransitionName(this, TRANSITION_NAME)
            layoutParams = CoordinatorLayout.LayoutParams(
                    popupWidth,
                    CoordinatorLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                this.leftMargin = leftMargin
                this.topMargin = topMargin
            }
            clipChildren = false
            clipToPadding = false

            visibility = View.INVISIBLE
            addView(binding.root)
        }

        container.addView(popupContainer)

        ViewCompat.setTransitionName(anchorView, TRANSITION_NAME)
        anchorView.visibility = View.INVISIBLE

        val transform = MaterialContainerTransform().apply {
            startView = anchorView
            endView = popupContainer
            addTarget(popupContainer)
            duration = DURATION
            scrimColor = Color.TRANSPARENT
            containerColor = Color.TRANSPARENT
            fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
            startElevation = END_ELEVATION
            endElevation = END_ELEVATION
            interpolator = INTERPOLATOR
        }

        popupContainer.post {
            popupContainer.visibility = View.VISIBLE
            TransitionManager.beginDelayedTransition(container, transform)
        }

        onPopupInflated(binding)
    }

    fun dismiss() {
        val reverseTransform = MaterialContainerTransform().apply {
            startView = popupContainer
            endView = anchorView
            addTarget(popupContainer)
            duration = DURATION
            scrimColor = Color.TRANSPARENT
            containerColor = Color.TRANSPARENT
            fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
            startElevation = END_ELEVATION
            endElevation = END_ELEVATION
            interpolator = INTERPOLATOR
        }

        reverseTransform.addListener(object : androidx.transition.Transition.TransitionListener {
            override fun onTransitionEnd(transition: androidx.transition.Transition) {
                anchorView.visibility = View.VISIBLE
                container.removeView(popupContainer)
                scrimView.clearAnimation()
                container.removeView(scrimView)
                onDismiss?.invoke()
                reverseTransform.removeListener(this)
            }

            override fun onTransitionStart(t: androidx.transition.Transition) {
                scrimView.alpha = 1f
                scrimView.animate()
                    .alpha(0f)
                    .setDuration(DURATION.div(1.5F).roundToLong())
                    .start()
            }

            override fun onTransitionCancel(t: androidx.transition.Transition) {}
            override fun onTransitionPause(t: androidx.transition.Transition) {}
            override fun onTransitionResume(t: androidx.transition.Transition) {}
        })

        TransitionManager.beginDelayedTransition(container, reverseTransform)
        popupContainer.visibility = View.INVISIBLE
    }
}