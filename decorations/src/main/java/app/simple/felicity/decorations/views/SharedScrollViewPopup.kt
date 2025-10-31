package app.simple.felicity.decorations.views

import android.animation.ValueAnimator
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

open class SharedScrollViewPopup @JvmOverloads constructor(
        private val container: ViewGroup,
        private val anchorView: View,
        private val menuItems: List<Int>, // String resource IDs
        private val menuIcons: List<Int>? = null, // Optional icons
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

    // Wiggle/interaction state (composed with idle float)
    private var wiggleTx = 0f
    private var wiggleTy = 0f
    private var wiggleScale = 1f

    // Idle floating state
    private var idleTy = 0f
    private var idleAnimator: ValueAnimator? = null
    private var idleStartRunnable: Runnable? = null

    // Public speed control for idle float: 1.0 = normal, >1 = faster, <1 = slower
    var idleFloatSpeed: Float = 0.6f
        set(value) {
            field = value.coerceIn(0.25f, 4f)
            // If running, restarts with new speed for immediate effect
            if (idleAnimator != null) restartIdleFloat()
        }

    // Custom properties to spring wiggle values (not raw view properties)
    private val WIGGLE_TRANSLATION_X = object : FloatPropertyCompat<View>("wiggle_tx") {
        override fun getValue(view: View): Float = wiggleTx
        override fun setValue(view: View, value: Float) {
            wiggleTx = value
            applyCombinedTransform(view)
        }
    }

    private val WIGGLE_TRANSLATION_Y = object : FloatPropertyCompat<View>("wiggle_ty") {
        override fun getValue(view: View): Float = wiggleTy
        override fun setValue(view: View, value: Float) {
            wiggleTy = value
            applyCombinedTransform(view)
        }
    }

    private val WIGGLE_SCALE = object : FloatPropertyCompat<View>("wiggle_scale") {
        override fun getValue(view: View): Float = wiggleScale
        override fun setValue(view: View, value: Float) {
            wiggleScale = value
            applyCombinedTransform(view)
        }
    }

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
                    MotionEvent.ACTION_DOWN -> {
                        // Pauses idle float immediately on interaction
                        stopIdleFloat()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isInitialTouch) {
                            initialX = event.rawX
                            initialY = event.rawY
                            isInitialTouch = false

                            translationXAnimation?.cancel()
                            translationYAnimation?.cancel()
                            scaleXAnimation?.cancel()
                            scaleYAnimation?.cancel()

                            // Pauses idle float as interaction begins
                            stopIdleFloat()
                        }

                        val dx = event.rawX - initialX
                        val dy = event.rawY - initialY

                        // Sensitivity damping
                        val dampX = dx * MAX_FINGER_DISTANCE
                        val dampY = dy * MAX_FINGER_DISTANCE

                        // Normalizes to [0,1] by max wiggle threshold
                        val nx = (abs(dampX) / MAX_WIGGLE_THRESHOLD).coerceAtMost(1f)
                        val ny = (abs(dampY) / MAX_WIGGLE_THRESHOLD).coerceAtMost(1f)

                        // Decay easing function
                        val easedX = easeOutDecay(nx) * MAX_WIGGLE_THRESHOLD * sign(dampX)
                        val easedY = easeOutDecay(ny) * MAX_WIGGLE_THRESHOLD * sign(dampY)

                        // Applies eased wiggle translation (composed with idle)
                        wiggleTx = easedX
                        wiggleTy = easedY

                        // Calculates scale adjustment based on overall wiggle intensity
                        // Uses max(nx, ny) as overall intensity
                        val intensity = max(nx, ny)
                        val minScale = 0.85f
                        val easedScaleFactor = 1f - (easeOutDecay(intensity) * (1f - minScale))
                        wiggleScale = easedScaleFactor

                        applyCombinedTransform(v)
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // Animates wiggle back to rest; idle resumes after a short delay
                        translationXAnimation = startSpringAnimation(v, WIGGLE_TRANSLATION_X, 0f, wiggleTx)
                        translationYAnimation = startSpringAnimation(v, WIGGLE_TRANSLATION_Y, 0f, wiggleTy)
                        scaleXAnimation = startSpringAnimation(v, WIGGLE_SCALE, 1f, wiggleScale)
                        scaleYAnimation = scaleXAnimation // mirrors scale value

                        isInitialTouch = true

                        scheduleIdleFloatRestart()
                    }
                }

                false
            }
        }

        // Menu container
        val linearLayout = LinearLayout(container.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
        }

        // Creates menu items dynamically
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
                    dismiss()
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

        // Transparent scrim
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

        // Defines max height as container height * 2/3 (or full container height)
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

        // Adds directly to container
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

        // Starts idle float after the opening transform completes
        transform.addListener(object : Transition.TransitionListener {
            override fun onTransitionEnd(transition: Transition) {
                // Prepares a restart runnable tied to this view
                idleStartRunnable = Runnable { startIdleFloat() }
                // Adds a small delay for a natural feel
                popupContainer.postDelayed({ startIdleFloat() }, 250L)
                transform.removeListener(this)
            }

            override fun onTransitionStart(transition: Transition) {}
            override fun onTransitionCancel(transition: Transition) {}
            override fun onTransitionPause(transition: Transition) {}
            override fun onTransitionResume(transition: Transition) {}
        })

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

        // Ensures idle float is stopped and reset
        stopIdleFloat(reset = true)

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

    // Composes wiggle and idle transforms for a single, clean effect
    private fun applyCombinedTransform(view: View) {
        view.translationX = wiggleTx
        view.translationY = wiggleTy + idleTy
        view.scaleX = wiggleScale
        view.scaleY = wiggleScale
    }

    private fun scheduleIdleFloatRestart(delay: Long = 1200L) {
        if (!::popupContainer.isInitialized) return
        idleStartRunnable?.let { popupContainer.removeCallbacks(it) }
        val runnable = Runnable { startIdleFloat() }
        idleStartRunnable = runnable
        popupContainer.postDelayed(runnable, delay)
    }

    private fun startIdleFloat() {
        if (!::popupContainer.isInitialized) return
        if (isDismissing) return
        if (idleAnimator?.isRunning == true) return

        // Smooth sine-wave float: independent amplitude and speed controls
        val density = popupContainer.resources.displayMetrics.density
        val amplitudePx = (1.5f + (Math.random().toFloat() * 1.5f)) * density // ~1.5dp..3dp
        val baseDurationMs = (900L + (Math.random() * 400L)).toLong() // ~0.9s..1.3s
        val speed = idleFloatSpeed.coerceIn(0.25f, 4f)
        val durationMs = (baseDurationMs / speed).toLong().coerceAtLeast(250L)

        // Phase goes 0..2π, linear; value = sin(phase) * amplitude
        idleAnimator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
            this.duration = durationMs
            interpolator = android.view.animation.LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { animator ->
                val phase = animator.animatedValue as Float
                idleTy = kotlin.math.sin(phase.toDouble()).toFloat() * amplitudePx
                applyCombinedTransform(popupContainer)
            }
            start()
        }
    }

    private fun stopIdleFloat(reset: Boolean = false) {
        idleStartRunnable?.let { runnable ->
            if (::popupContainer.isInitialized) popupContainer.removeCallbacks(runnable)
        }
        idleAnimator?.cancel()
        idleAnimator = null
        if (reset) {
            idleTy = 0f
            if (::popupContainer.isInitialized) applyCombinedTransform(popupContainer)
        }
    }

    private fun restartIdleFloat() {
        idleAnimator?.cancel()
        idleAnimator = null
        // Resets offset to avoid any visual jump
        idleTy = 0f
        if (::popupContainer.isInitialized) applyCombinedTransform(popupContainer)
        startIdleFloat()
    }

    // Optional hook
    open fun onPopupCreated(scrollView: NestedScrollView, contentLayout: LinearLayout) {

    }
}