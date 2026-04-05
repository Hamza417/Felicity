package app.simple.felicity.decorations.views

import android.annotation.SuppressLint
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.setPadding
import androidx.core.widget.NestedScrollView
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.transition.Transition
import androidx.transition.TransitionManager
import app.simple.felicity.decoration.R
import app.simple.felicity.decorations.behaviors.OverScrollBehavior
import app.simple.felicity.decorations.corners.DynamicCornersNestedScrollView
import app.simple.felicity.decorations.ripple.DynamicRippleTextView
import app.simple.felicity.decorations.typeface.TypeFaceTextView
import app.simple.felicity.decorations.typeface.TypefaceStyle
import app.simple.felicity.theme.managers.ThemeManager
import com.google.android.material.transition.MaterialContainerTransform
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sign

/**
 * A popup menu that morphs from an anchor view using a shared-element container
 * transform animation. All layout positions and sizes are pre-calculated before
 * the animation begins, so the transform always travels from the anchor's exact
 * source rect to the popup's exact target rect with no midair resizing.
 *
 * @param container The root [ViewGroup] that hosts both the scrim and the popup.
 * @param anchorView The view the popup expands from and collapses back to.
 * @param menuItems String resource IDs for each menu entry.
 * @param menuIcons Optional drawable resource IDs aligned by index with [menuItems].
 * @param onMenuItemClick Invoked with the resource ID of the tapped item.
 * @param onDismiss Optional callback fired after the popup has fully collapsed.
 * @param backProgression When true (default), a back press or a scrim tap dismisses
 *   the popup. Set to false for dialogs that require an explicit action to close.
 *
 * @author Hamza417
 */
open class SharedScrollViewPopup @JvmOverloads constructor(
        private val container: ViewGroup,
        private val anchorView: View,
        private val menuItems: List<Int>,
        private val menuIcons: List<Int>? = null,
        private val onMenuItemClick: (itemResId: Int) -> Unit,
        private val onDismiss: (() -> Unit)? = null,
        private val backProgression: Boolean = true
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

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_MOVE -> {
                        if (isInitialTouch) {
                            initialX = event.rawX
                            initialY = event.rawY
                            isInitialTouch = false

                            translationXAnimation?.cancel()
                            translationYAnimation?.cancel()
                            scaleXAnimation?.cancel()
                            scaleYAnimation?.cancel()
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
                        // val maxScale = 1.15f

                        // Map intensity 0..1 to scale range using easing (can reuse easeOutDecay)
                        // When intensity is 0, scale = 1f (normal), when 1 -> scale near minScale or maxScale
                        // val scaleRange = maxScale - minScale

                        // Example: scale varies between 1 and minScale (a squeeze)
                        val easedScaleFactor = 1f - (easeOutDecay(intensity) * (1f - minScale))
                        // or if you want a slight oscillation around 1, you could do:
                        // val easedScaleFactor = minScale + scaleRange * easeOutDecay(intensity)

                        v.scaleX = easedScaleFactor
                        v.scaleY = easedScaleFactor
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        translationXAnimation = startSpringAnimation(v, SpringAnimation.TRANSLATION_X, 0f, v.translationX)
                        translationYAnimation = startSpringAnimation(v, SpringAnimation.TRANSLATION_Y, 0f, v.translationY)
                        scaleXAnimation = startSpringAnimation(v, SpringAnimation.SCALE_X, 1f, v.scaleX)
                        scaleYAnimation = startSpringAnimation(v, SpringAnimation.SCALE_Y, 1f, v.scaleY)

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
                val horizontalPadding = (8 * resources.displayMetrics.density).toInt()
                val verticalPadding = (12 * resources.displayMetrics.density).toInt()

                setPadding(
                        /* left = */ horizontalPadding,
                        /* top = */ verticalPadding,
                        /* right = */ horizontalPadding + horizontalPadding,
                        /* bottom = */ verticalPadding)

                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setTypeFaceStyle(TypefaceStyle.BOLD.style)
                gravity = Gravity.CENTER_VERTICAL
                compoundDrawablePadding = (16 * resources.displayMetrics.density).toInt()
                setTextColor(ThemeManager.theme.textViewTheme.primaryTextColor)
                setText(resId)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    onMenuItemClick(resId)
                    if (backProgression) {
                        dismiss()
                    } else {
                        // Simply removed the dialog without the transition
                        simplyDismiss()
                    }
                }

                val textSizePx = textSize.times(1.3F)
                val drawableResId = menuIcons?.getOrNull(menuItems.indexOf(resId)) ?: 0
                val drawable = if (drawableResId != 0) {
                    ContextCompat.getDrawable(context, drawableResId)?.apply {
                        setBounds(0, 0, textSizePx.toInt(), textSizePx.toInt())
                    }
                } else null

                setCompoundDrawables(drawable, null, null, null)
                setDrawableTineMode(TypeFaceTextView.DRAWABLE_ACCENT)
            }

            linearLayout.addView(tv)
        }

        popupScrollView.addView(linearLayout)

        // Transparent scrim — only dismisses when backProgression is enabled
        scrimView = View(container.context).apply {
            setBackgroundColor("#80000000".toColorInt())
            layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    container.height
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

        // Get system bar insets (status bar and navigation bar heights)
        val windowInsets = ViewCompat.getRootWindowInsets(container)
        val systemBarsInsets = windowInsets?.getInsets(WindowInsetsCompat.Type.systemBars())
        val statusBarHeight = systemBarsInsets?.top ?: 0
        val navigationBarHeight = systemBarsInsets?.bottom ?: 0

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

        // Use the exact measured pixel height so the target rect is fully resolved
        // before the animation starts, ensuring consistent source-to-target morphing.
        val finalHeight = if (popupHeight > maxHeight) maxHeight else popupHeight

        var leftMargin = anchorX + anchorWidth / 2 - popupWidth / 2
        var topMargin = anchorY + anchorHeight / 2 - finalHeight / 2

        leftMargin = max(marginPx, min(leftMargin, container.width - popupWidth - marginPx))

        // Include status bar height for top margin and navigation bar height for bottom margin
        val topMarginMin = marginPx + statusBarHeight
        val bottomMarginMin = marginPx + navigationBarHeight
        topMargin = max(topMarginMin, min(topMargin, container.height - finalHeight - bottomMarginMin))

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

        // Wait until the popup is fully laid out at its final position before
        // beginning the transition. This guarantees the framework reads the
        // correct source rect (anchor) and target rect (popup) with no
        // intermediate sizes — producing a clean origin-to-target morph.
        popupContainer.doOnLayout {
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

            // beginDelayedTransition must be called first so it captures the
            // before-state (anchor visible, popup invisible). The visibility
            // changes below are the scene change that drives the animation.
            TransitionManager.beginDelayedTransition(container, transform)
            anchorView.visibility = View.INVISIBLE
            popupContainer.visibility = View.VISIBLE
        }

        onPopupCreated(popupScrollView, linearLayout)
        setupBackPressListener()
    }

    fun simplyDismiss() {
        isDismissing = false
        container.removeView(popupContainer)
        scrimView.clearAnimation()
        anchorView.visibility = View.VISIBLE
        container.removeView(scrimView)
        backCallback?.remove()
        backCallback = null
        onDismiss?.invoke()
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
                // The anchor is already visible from the scene change below;
                // just clean up the popup and scrim overlay.
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

        // Capture the before-state (popup visible, anchor invisible) first,
        // then apply scene changes so the framework has a concrete target rect
        // to morph back to — the anchor's exact bounds.
        TransitionManager.beginDelayedTransition(container, reverseTransform)
        popupContainer.visibility = View.INVISIBLE
        anchorView.visibility = View.VISIBLE
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

    private fun startSpringAnimation(
            view: View,
            property: FloatPropertyCompat<View>,
            finalPosition: Float,
            startValue: Float
    ): SpringAnimation {
        return SpringAnimation(view, property).apply {
            spring = SpringForce(finalPosition).apply {
                stiffness = SpringForce.STIFFNESS_VERY_LOW
                dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            }
            setStartValue(startValue)
            start()
        }
    }

    fun easeOutDecay(normalized: Float): Float {
        return 1f - (1f - normalized).pow(5)
    }

    /** Optional hook called after the popup view tree is fully constructed. */
    open fun onPopupCreated(scrollView: NestedScrollView, contentLayout: LinearLayout) {

    }
}