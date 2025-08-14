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
import androidx.core.view.ViewCompat
import androidx.core.view.setPadding
import androidx.core.widget.NestedScrollView
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.transition.Transition
import androidx.transition.TransitionManager
import app.simple.felicity.core.maths.Number.half
import app.simple.felicity.core.maths.Number.twice
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
import kotlin.math.sign

abstract class SharedScrollViewPopupNonContainer @JvmOverloads constructor(
        private val container: ViewGroup,
        private val anchorView: View,
        private val menuItems: List<Int>, // String resource IDs
        private val menuIcons: List<Int>? = null, // Optional icons
        private val onMenuItemClick: (itemResId: Int) -> Unit, // Callback
        private val onDismiss: (() -> Unit)? = null
) {

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
        val context = container.context
        val marginPx = (MARGIN * context.resources.displayMetrics.density).toInt()

        // Create the scrollable popup container
        val popupScrollView = DynamicCornersNestedScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isFillViewport = false
            elevation = 48F
            ViewCompat.setTransitionName(this, TRANSITION_NAME)
            clipChildren = true
            clipToPadding = false
            clipToOutline = true
            visibility = View.INVISIBLE
            setPadding(context.resources.getDimensionPixelSize(R.dimen.padding_10))
            setOnTouchListener(createWiggleTouchListener())
        }

        // Create a vertical LinearLayout to hold menu items inside scroll view
        val linearLayout = LinearLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
        }

        // Add menu items as text views with optional icons
        menuItems.forEachIndexed { i, resId ->
            val tv = DynamicRippleTextView(context).apply {
                val hPad = (8 * resources.displayMetrics.density).toInt()
                val vPad = (12 * resources.displayMetrics.density).toInt()
                setPadding(hPad, vPad, hPad * 2, vPad)
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
                    dismiss()
                }
                val drawableResId = menuIcons?.getOrNull(i) ?: 0
                val textSizePx = textSize.times(1.3F)
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

        // Add the linear layout to the scroll view
        popupScrollView.addView(linearLayout)

        // Calculate anchor and container locations relative to window
        val anchorLocation = IntArray(2)
        anchorView.getLocationInWindow(anchorLocation)
        val containerLocation = IntArray(2)
        container.getLocationInWindow(containerLocation)

        val anchorX = anchorLocation[0] - containerLocation[0]
        val anchorY = anchorLocation[1] - containerLocation[1]
        val anchorWidth = anchorView.width
        val anchorHeight = anchorView.height

        // Measure popupScrollView with max width = container width, height unspecified (wrap content)
        popupScrollView.measure(
                View.MeasureSpec.makeMeasureSpec(container.width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.UNSPECIFIED
        )

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val popupWidth = popupScrollView.measuredWidth
        val popupHeight = popupScrollView.measuredHeight
        val maxHeight = (screenHeight * 2f / 3f).toInt()

        val finalHeight = if (popupHeight > maxHeight) maxHeight else CoordinatorLayout.LayoutParams.WRAP_CONTENT

        val popupCenterX = anchorX + anchorWidth.half() - popupWidth.half()
        val maxLeft = screenWidth - popupWidth - marginPx.twice()
        val leftMargin = max(marginPx, min(popupCenterX, maxLeft))

        val popupVisibleHeight = min(popupHeight, maxHeight)
        val popupCenterY = anchorY + anchorHeight.half() - popupVisibleHeight.half()
        val maxTop = screenHeight - popupVisibleHeight - marginPx
        val topMargin = max(marginPx, min(popupCenterY, maxTop))

        popupScrollView.layoutParams = CoordinatorLayout.LayoutParams(popupWidth, finalHeight).apply {
            this.leftMargin = leftMargin
            this.topMargin = topMargin
            behavior = OverScrollBehavior(context, null)
        }

        popupContainer = popupScrollView
        container.addView(popupContainer)

        // Setup shared element transition name
        ViewCompat.setTransitionName(anchorView, TRANSITION_NAME)

        // Hide the anchor view while popup is showing
        anchorView.visibility = View.INVISIBLE

        // Setup MaterialContainerTransform for morph animation
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

        // Callback after popup creation for any additional setup
        onPopupCreated(popupScrollView, linearLayout)

        setupBackPressListener()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createWiggleTouchListener(): View.OnTouchListener {
        var initialX = 0F
        var initialY = 0F
        var isInitialTouch = true

        return View.OnTouchListener { v, event ->
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
                    val dampX = dx * MAX_FINGER_DISTANCE
                    val dampY = dy * MAX_FINGER_DISTANCE
                    val nx = (abs(dampX) / MAX_WIGGLE_THRESHOLD).coerceAtMost(1f)
                    val ny = (abs(dampY) / MAX_WIGGLE_THRESHOLD).coerceAtMost(1f)
                    val easedX = easeOutDecay(nx) * MAX_WIGGLE_THRESHOLD * sign(dampX)
                    val easedY = easeOutDecay(ny) * MAX_WIGGLE_THRESHOLD * sign(dampY)
                    v.translationX = easedX
                    v.translationY = easedY
                    val intensity = max(nx, ny)
                    val minScale = 0.85f
                    val easedScaleFactor = 1f - (easeOutDecay(intensity) * (1f - minScale))
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

    fun dismiss() {
        if (isDismissing) return
        isDismissing = true
        backCallback?.remove()
        backCallback = null

        val reverseTransform = MaterialContainerTransform().apply {
            startView = popupContainer
            endView = anchorView
            addTarget(popupContainer)
            setDuration(DURATION)
            scrimColor = Color.TRANSPARENT
            containerColor = Color.TRANSPARENT
            fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
            startElevation = END_ELEVATION
            endElevation = END_ELEVATION
            setInterpolator(INTERPOLATOR)
        }

        reverseTransform.addListener(object : Transition.TransitionListener {
            override fun onTransitionEnd(transition: Transition) {
                anchorView.visibility = View.VISIBLE
                container.removeView(popupContainer)
                onDismiss?.invoke()
                reverseTransform.removeListener(this)
                isDismissing = false
            }

            override fun onTransitionStart(t: Transition) {
            }

            override fun onTransitionCancel(t: Transition) {
                anchorView.visibility = View.VISIBLE
                container.removeView(popupContainer)
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

    // Optional hook
    abstract fun onPopupCreated(scrollView: NestedScrollView, contentLayout: LinearLayout)
}