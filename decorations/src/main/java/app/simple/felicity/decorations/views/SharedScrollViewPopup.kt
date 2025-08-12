package app.simple.felicity.decorations.views

import android.annotation.SuppressLint
import android.graphics.Color
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.setPadding
import androidx.core.widget.NestedScrollView
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.transition.Transition
import androidx.transition.TransitionManager
import app.simple.felicity.decoration.R
import app.simple.felicity.decorations.behaviors.OverScrollBehavior
import app.simple.felicity.decorations.corners.DynamicCornersNestedScrollView
import app.simple.felicity.decorations.ripple.DynamicRippleTextView
import com.google.android.material.transition.MaterialContainerTransform
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sign

abstract class SharedScrollViewPopup @JvmOverloads constructor(
        private val container: ViewGroup,
        private val anchorView: View,
        private val menuItems: List<Int>, // String resource IDs
        private val onMenuItemClick: (itemResId: Int) -> Unit, // Callback
        private val onDismiss: (() -> Unit)? = null
) {

    private lateinit var scrimView: View
    private lateinit var popupContainer: DynamicCornersNestedScrollView
    private var backCallback: OnBackPressedCallback? = null
    private var isDismissing = false

    private var scaleXAnimation: SpringAnimation? = null
    private var scaleYAnimation: SpringAnimation? = null
    private var translationXAnimation: SpringAnimation? = null
    private var translationYAnimation: SpringAnimation? = null

    companion object {
        private const val TRANSITION_NAME = "shared_element_popup_transition"
        private const val DURATION = 350L
        private const val END_ELEVATION = 0f
        private const val MARGIN = 16 // in dp
        private const val MAX_WIGGLE_THRESHOLD = 72F
        private const val MAX_FINGER_DISTANCE = 0.05f
        private val INTERPOLATOR = DecelerateInterpolator(1.5F)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        var initialX = 0F
        var initialY = 0F

        var isInitialTouch = true

        val popupScrollView = DynamicCornersNestedScrollView(container.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isFillViewport = false
            elevation = END_ELEVATION
            ViewCompat.setTransitionName(this, TRANSITION_NAME)
            clipChildren = true
            clipToPadding = false
            clipToOutline = true
            visibility = View.INVISIBLE
            setPadding(resources.getDimensionPixelSize(R.dimen.padding_10))

            fun easeOutDecay(normalized: Float): Float {
                // Cubic decay example
                return 1f - (1f - normalized).pow(5)
            }

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_MOVE -> {
                        if (isInitialTouch) {
                            initialX = event.rawX
                            initialY = event.rawY
                            isInitialTouch = false
                        }

                        val dx = event.rawX - initialX
                        val dy = event.rawY - initialY

                        // Sensitivity dampening
                        val dampX = dx * MAX_FINGER_DISTANCE
                        val dampY = dy * MAX_FINGER_DISTANCE

                        // Normalize to [0,1] by max wiggle threshold
                        val nx = (abs(dampX) / MAX_WIGGLE_THRESHOLD).coerceAtMost(1f)
                        val ny = (abs(dampY) / MAX_WIGGLE_THRESHOLD).coerceAtMost(1f)

                        // Your existing decay easing function
                        val easedX = easeOutDecay(nx) * MAX_WIGGLE_THRESHOLD * sign(dampX)
                        val easedY = easeOutDecay(ny) * MAX_WIGGLE_THRESHOLD * sign(dampY)

                        // Apply eased wiggle translation
                        v.translationX = easedX
                        v.translationY = easedY

                        // Calculate scale adjustment based on the total wiggle intensity
                        // For example, take the max of nx and ny as overall intensity
                        val intensity = max(nx, ny)

                        // Define scale range (e.g., shrink to 0.95 and grow to 1.05)
                        val minScale = 0.85f
                        val maxScale = 1.15f

                        // Map intensity 0..1 to scale range using easing (can reuse easeOutDecay)
                        // When intensity is 0, scale = 1f (normal), when 1 -> scale near minScale or maxScale
                        val scaleRange = maxScale - minScale

                        // Example: scale varies between 1 and minScale (a squeeze)
                        val easedScaleFactor = 1f - (easeOutDecay(intensity) * (1f - minScale))
                        // or if you want a slight oscillation around 1, you could do:
                        // val easedScaleFactor = minScale + scaleRange * easeOutDecay(intensity)

                        v.scaleX = easedScaleFactor
                        v.scaleY = easedScaleFactor
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        translationXAnimation?.cancel()
                        translationYAnimation?.cancel()
                        scaleXAnimation?.cancel()
                        scaleYAnimation?.cancel()

                        translationXAnimation = SpringAnimation(v, SpringAnimation.TRANSLATION_X)
                        translationXAnimation?.spring = SpringForce(0f).apply {
                            stiffness = SpringForce.STIFFNESS_LOW
                            dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
                        }
                        translationXAnimation?.setStartValue(v.translationX)
                        translationXAnimation?.start()

                        translationYAnimation = SpringAnimation(v, SpringAnimation.TRANSLATION_Y)
                        translationYAnimation?.spring = SpringForce(0f).apply {
                            stiffness = SpringForce.STIFFNESS_LOW
                            dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
                        }
                        translationYAnimation?.setStartValue(v.translationY)
                        translationYAnimation?.start()

                        scaleXAnimation = SpringAnimation(v, SpringAnimation.SCALE_X)
                        scaleXAnimation?.spring = SpringForce(1f).apply {
                            stiffness = SpringForce.STIFFNESS_LOW
                            dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
                        }
                        scaleXAnimation?.setStartValue(v.scaleX)
                        scaleXAnimation?.start()

                        scaleYAnimation = SpringAnimation(v, SpringAnimation.SCALE_Y)
                        scaleYAnimation?.spring = SpringForce(1f).apply {
                            stiffness = SpringForce.STIFFNESS_LOW
                            dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
                        }
                        scaleYAnimation?.setStartValue(v.scaleY)
                        scaleYAnimation?.start()

                        isInitialTouch = true
                    }
                }

                false
            }
        }

        // Menu list container
        val linearLayout = LinearLayout(container.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
        }

        // Create menu items dynamically
        menuItems.forEach { resId ->
            val tv = DynamicRippleTextView(container.context).apply {
                val padding = (16 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)
                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setText(resId)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    onMenuItemClick(resId)
                    dismiss()
                }
            }
            linearLayout.addView(tv)
        }

        popupScrollView.addView(linearLayout)

        // Transparent scrim
        scrimView = View(container.context).apply {
            setBackgroundColor("#80000000".toColorInt())
            layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            setOnClickListener { dismiss() }
            alpha = 0f
            animate().alpha(1f).setInterpolator(INTERPOLATOR).setDuration(DURATION).start()
        }

        container.addView(scrimView)

        // Positioning
        val anchorLocation = IntArray(2)
        anchorView.getLocationInWindow(anchorLocation)
        val containerLocation = IntArray(2)
        container.getLocationInWindow(containerLocation)

        val marginPx = (MARGIN * container.resources.displayMetrics.density).toInt()
        val anchorX = anchorLocation[0] - containerLocation[0]
        val anchorY = anchorLocation[1] - containerLocation[1]
        val anchorWidth = anchorView.width
        val anchorHeight = anchorView.height

        popupScrollView.measure(
                View.MeasureSpec.makeMeasureSpec(container.width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.UNSPECIFIED
        )

        val popupWidth = popupScrollView.measuredWidth
        val popupHeight = popupScrollView.measuredHeight

        // Define max height as container height * 2/3 (or full container height if you want)
        val maxHeight = (container.height * 2f / 3f).toInt()

        val finalHeight = if (popupHeight > maxHeight) {
            maxHeight
        } else {
            CoordinatorLayout.LayoutParams.WRAP_CONTENT
        }

        var leftMargin = anchorX + anchorWidth / 2 - popupWidth / 2
        var topMargin = anchorY + anchorHeight / 2 - min(popupHeight, maxHeight) / 2

        leftMargin = max(marginPx, min(leftMargin, container.width - popupWidth - marginPx))
        topMargin = max(marginPx, min(topMargin, container.height - min(popupHeight, maxHeight) - marginPx))

        val params = CoordinatorLayout.LayoutParams(
                popupWidth,
                finalHeight
        ).apply {
            this.leftMargin = leftMargin
            this.topMargin = topMargin
            behavior = OverScrollBehavior(container.context, null)
        }

        popupScrollView.layoutParams = params

        // Add directly to container
        popupContainer = popupScrollView
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

        onPopupCreated(popupScrollView, linearLayout)
        setupBackPressListener()
    }

    fun dismiss() {
        if (isDismissing) return
        isDismissing = true
        backCallback?.remove()
        backCallback = null

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

        reverseTransform.addListener(object : Transition.TransitionListener {
            override fun onTransitionEnd(transition: Transition) {
                anchorView.visibility = View.VISIBLE
                container.removeView(popupContainer)
                scrimView.clearAnimation()
                container.removeView(scrimView)
                onDismiss?.invoke()
                reverseTransform.removeListener(this)
                isDismissing = false
            }

            override fun onTransitionStart(t: Transition) {
                scrimView.alpha = 1f
                scrimView.animate()
                    .alpha(0f)
                    .setDuration((DURATION / 1.5F).roundToLong())
                    .start()
            }

            override fun onTransitionCancel(t: Transition) {
                anchorView.visibility = View.VISIBLE
                container.removeView(popupContainer)
                scrimView.clearAnimation()
                container.removeView(scrimView)
                onDismiss?.invoke()
                reverseTransform.removeListener(this)
                isDismissing = false
            }

            override fun onTransitionPause(t: Transition) {}
            override fun onTransitionResume(t: Transition) {}
        })

        TransitionManager.beginDelayedTransition(container, reverseTransform)
        popupContainer.visibility = View.INVISIBLE
    }

    private fun setupBackPressListener() {
        val activity = container.context as? AppCompatActivity ?: return
        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isDismissing) {
                    dismiss()
                }
            }
        }
        activity.onBackPressedDispatcher.addCallback(backCallback!!)
    }

    // Optional hook
    abstract fun onPopupCreated(scrollView: NestedScrollView, contentLayout: LinearLayout)
}