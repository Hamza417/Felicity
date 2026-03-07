package app.simple.felicity.decorations.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import app.simple.felicity.decoration.R
import app.simple.felicity.decorations.behaviors.OverScrollBehavior
import app.simple.felicity.decorations.corners.DynamicCornersNestedScrollView
import app.simple.felicity.decorations.ripple.DynamicRippleTextView
import app.simple.felicity.decorations.typeface.TypeFaceTextView
import app.simple.felicity.decorations.typeface.TypefaceStyle
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.theme.managers.ThemeManager
import com.google.android.material.shape.MaterialShapeDrawable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sign
import kotlin.math.sin

open class SharedScrollViewPopup @JvmOverloads constructor(
        private val container: ViewGroup,
        private val anchorView: View,
        private val menuItems: List<Int>, // String resource IDs
        private val menuIcons: List<Int>? = null, // Optional icons
        private val onMenuItemClick: (itemResId: Int) -> Unit, // Callback
        private val onDismiss: (() -> Unit)? = null
) {

    private lateinit var scrimView: View
    private lateinit var morphLayout: MorphLayout
    private lateinit var popupScrollView: DynamicCornersNestedScrollView
    private lateinit var linearLayout: LinearLayout

    private var backCallback: OnBackPressedCallback? = null
    private var isDismissing = false

    private var morphAnimator: ValueAnimator? = null
    private var scaleXAnimation: SpringAnimation? = null
    private var scaleYAnimation: SpringAnimation? = null
    private var translationXAnimation: SpringAnimation? = null
    private var translationYAnimation: SpringAnimation? = null

    // Rects captured once per show/dismiss
    private val anchorRect = Rect()
    private val finalRect = Rect()
    private val containerRect = Rect()

    companion object {
        private const val DURATION = 450L
        private const val MARGIN = 16 // dp
        private const val MAX_WIGGLE_THRESHOLD = 72F
        private const val MAX_FINGER_DISTANCE = 0.05f

        // Content starts fading in from this morph progress (0..1)
        private const val CONTENT_FADE_START = 0.30f

        // Peak elevation (dp) reached at the midpoint of the morph — the "cylinder top"
        private const val PEAK_ELEVATION_DP = 24f

        // Resting elevation once fully open
        private const val FINAL_ELEVATION_DP = 12f

        // Material 3 "emphasized" spring-like easing
        private val OPEN_INTERPOLATOR = PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f)
        private val CLOSE_INTERPOLATOR = PathInterpolator(0.3f, 0f, 1f, 1f)
        private val ARG_EVALUATOR = ArgbEvaluator()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        var initialX = 0F
        var initialY = 0F
        var isInitialTouch = true

        // ── 1. Capture anchor rect in container coordinates ───────────────────
        container.getGlobalVisibleRect(containerRect)
        anchorView.getGlobalVisibleRect(anchorRect)
        anchorRect.offset(-containerRect.left, -containerRect.top)

        // Resolve anchor background color — fall back to the app background color
        val anchorColor = resolveAnchorColor()
        val finalColor = ThemeManager.theme.viewGroupTheme.backgroundColor
        val accentColor = ThemeManager.accent.primaryAccentColor

        // Corner radius: anchor starts at half the final radius, so it "blooms" open
        val anchorCorner = AppearancePreferences.getCornerRadius()
        val finalCorner = AppearancePreferences.getCornerRadius()

        val density = container.resources.displayMetrics.density
        val peakElevationPx = PEAK_ELEVATION_DP * density
        val finalElevationPx = FINAL_ELEVATION_DP * density

        // ── 2. Build menu content ─────────────────────────────────────────────
        linearLayout = LinearLayout(container.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
        }

        menuItems.forEach { resId ->
            val tv = DynamicRippleTextView(container.context).apply {
                val hp = (8 * density).toInt()
                val vp = (12 * density).toInt()
                setPadding(hp, vp, hp * 2, vp)
                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setTypeFaceStyle(TypefaceStyle.BOLD.style)
                gravity = Gravity.CENTER_VERTICAL
                compoundDrawablePadding = (16 * density).toInt()
                setTextColor(ThemeManager.theme.textViewTheme.primaryTextColor)
                setText(resId)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    onMenuItemClick(resId)
                    dismiss()
                }
                val textSizePx = textSize * 1.3F
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

        // ── 3. Build scroll view — no background, MorphLayout owns it ─────────
        popupScrollView = DynamicCornersNestedScrollView(container.context).apply {
            background = null
            isFillViewport = true
            clipChildren = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_ALWAYS
            val pad = resources.getDimensionPixelSize(R.dimen.padding_10)
            setPadding(pad, pad, pad, pad)
            addView(linearLayout)

            setOnTouchListener { _, event ->
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
                        morphLayout.translationX = easedX
                        morphLayout.translationY = easedY
                        val intensity = max(nx, ny)
                        morphLayout.scaleX = 1f - (easeOutDecay(intensity) * 0.15f)
                        morphLayout.scaleY = 1f - (easeOutDecay(intensity) * 0.15f)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        translationXAnimation = startSpring(morphLayout, SpringAnimation.TRANSLATION_X, 0f, morphLayout.translationX)
                        translationYAnimation = startSpring(morphLayout, SpringAnimation.TRANSLATION_Y, 0f, morphLayout.translationY)
                        scaleXAnimation = startSpring(morphLayout, SpringAnimation.SCALE_X, 1f, morphLayout.scaleX)
                        scaleYAnimation = startSpring(morphLayout, SpringAnimation.SCALE_Y, 1f, morphLayout.scaleY)
                        isInitialTouch = true
                    }
                }
                false
            }
        }

        // ── 4. Compute final popup rect ───────────────────────────────────────
        val marginPx = (MARGIN * density).toInt()
        val windowInsets = ViewCompat.getRootWindowInsets(container)
        val systemBars = windowInsets?.getInsets(WindowInsetsCompat.Type.systemBars())
        val statusBarH = systemBars?.top ?: 0
        val navBarH = systemBars?.bottom ?: 0

        linearLayout.measure(
                View.MeasureSpec.makeMeasureSpec(container.width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.UNSPECIFIED
        )

        val pad2 = container.resources.getDimensionPixelSize(R.dimen.padding_10) * 2
        val naturalW = linearLayout.measuredWidth + pad2
        val naturalH = linearLayout.measuredHeight + pad2
        val maxH = (container.height * 2f / 3f).toInt()
        val finalW = naturalW
        val finalH = min(naturalH, maxH)

        var finalLeft = anchorRect.centerX() - finalW / 2
        var finalTop = anchorRect.centerY() - finalH / 2
        finalLeft = max(marginPx, min(finalLeft, container.width - finalW - marginPx))
        finalTop = max(marginPx + statusBarH, min(finalTop, container.height - finalH - marginPx - navBarH))
        finalRect.set(finalLeft, finalTop, finalLeft + finalW, finalTop + finalH)

        // ── 5. Build MorphLayout ──────────────────────────────────────────────
        // Use FrameLayout.LayoutParams for the child to avoid MarginLayoutParams crash
        morphLayout = MorphLayout(container.context).apply {
            elevation = 0f
            outlineAmbientShadowColor = accentColor
            outlineSpotShadowColor = accentColor
            // FrameLayout.LayoutParams avoids the ViewGroup.LayoutParams → MarginLayoutParams CCE
            addView(popupScrollView, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ))
            contentAlpha = 0f
            // Place it at anchor bounds to start
            setMorphState(anchorRect, anchorCorner, anchorColor)
        }

        // CoordinatorLayout.LayoutParams needed for OverScrollBehavior — but we must NOT
        // set width/height here to something that would trigger CoordinatorLayout's own
        // measure — use WRAP_CONTENT, the actual size is driven by layout() in setMorphState.
        morphLayout.layoutParams = CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.WRAP_CONTENT,
                CoordinatorLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            behavior = OverScrollBehavior(container.context, null)
        }

        // ── 6. Scrim ──────────────────────────────────────────────────────────
        scrimView = View(container.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            setOnClickListener { dismiss() }
        }

        container.addView(scrimView)
        container.addView(morphLayout)
        anchorView.visibility = View.INVISIBLE
        morphLayout.visibility = View.VISIBLE

        // ── 7. Animate: rect lerp + elevation arc + color + corner + content fade ──
        morphAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURATION
            interpolator = OPEN_INTERPOLATOR

            addUpdateListener { anim ->
                val p = anim.animatedValue as Float

                // Rect interpolation
                val l = lerp(anchorRect.left, finalRect.left, p)
                val t = lerp(anchorRect.top, finalRect.top, p)
                val r = lerp(anchorRect.right, finalRect.right, p)
                val b = lerp(anchorRect.bottom, finalRect.bottom, p)

                // Corner: full radius throughout (anchor is already at full radius)
                val corner = lerp(anchorCorner, finalCorner, p)

                // Color
                val color = ARG_EVALUATOR.evaluate(p, anchorColor, finalColor) as Int

                morphLayout.setMorphState(l, t, r, b, corner, color)

                // Elevation arc: rises like a cylinder lifting from the surface.
                // sin(p*π) peaks at p=0.5, settles at finalElevation at p=1.
                val elevArc = sin(p * Math.PI).toFloat()          // 0 → 1 → 0
                val elev = lerp(0f, finalElevationPx, p) + elevArc * (peakElevationPx - finalElevationPx)
                morphLayout.elevation = elev

                // Scrim
                scrimView.alpha = min(p / 0.5f, 1f) * 0.55f

                // Content fades in after CONTENT_FADE_START
                val cp = ((p - CONTENT_FADE_START) / (1f - CONTENT_FADE_START)).coerceIn(0f, 1f)
                morphLayout.contentAlpha = cp
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    morphLayout.elevation = finalElevationPx
                    morphLayout.contentAlpha = 1f
                    onPopupCreated(popupScrollView, linearLayout)
                    setupBackPressListener()
                }
            })

            start()
        }
    }

    fun dismiss() {
        if (isDismissing) return
        isDismissing = true
        morphAnimator?.cancel()
        backCallback?.remove()
        backCallback = null

        val anchorColor = resolveAnchorColor()
        val finalColor = ThemeManager.theme.viewGroupTheme.backgroundColor
        val anchorCorner = AppearancePreferences.getCornerRadius()
        val finalCorner = AppearancePreferences.getCornerRadius()
        val density = container.resources.displayMetrics.density
        val finalElevationPx = FINAL_ELEVATION_DP * density

        // Re-capture anchor in case it moved
        container.getGlobalVisibleRect(containerRect)
        anchorView.getGlobalVisibleRect(anchorRect)
        anchorRect.offset(-containerRect.left, -containerRect.top)

        morphAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (DURATION * 0.75f).roundToLong()
            interpolator = CLOSE_INTERPOLATOR

            addUpdateListener { anim ->
                val p = anim.animatedValue as Float

                val l = lerp(finalRect.left, anchorRect.left, p)
                val t = lerp(finalRect.top, anchorRect.top, p)
                val r = lerp(finalRect.right, anchorRect.right, p)
                val b = lerp(finalRect.bottom, anchorRect.bottom, p)

                val corner = lerp(finalCorner, anchorCorner, p)
                val color = ARG_EVALUATOR.evaluate(p, finalColor, anchorColor) as Int

                morphLayout.setMorphState(l, t, r, b, corner, color)

                // Elevation drops back to 0 as it returns to the anchor surface
                morphLayout.elevation = lerp(finalElevationPx, 0f, p)

                // Scrim and content fade out fast
                scrimView.alpha = (1f - p).coerceIn(0f, 1f) * 0.55f
                morphLayout.contentAlpha = (1f - p * 2.5f).coerceIn(0f, 1f)
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = cleanup()
                override fun onAnimationCancel(animation: Animator) = cleanup()
            })

            start()
        }
    }

    private fun resolveAnchorColor(): Int {
        return try {
            when (val bg = anchorView.background) {
                is ColorDrawable -> bg.color
                is MaterialShapeDrawable ->
                    bg.fillColor?.defaultColor
                        ?: ThemeManager.theme.viewGroupTheme.backgroundColor
                else -> ThemeManager.theme.viewGroupTheme.backgroundColor
            }
        } catch (_: Exception) {
            ThemeManager.theme.viewGroupTheme.backgroundColor
        }
    }

    private fun cleanup() {
        anchorView.visibility = View.VISIBLE
        if (morphLayout.parent != null) container.removeView(morphLayout)
        if (scrimView.parent != null) container.removeView(scrimView)
        onDismiss?.invoke()
        isDismissing = false
    }

    private fun setupBackPressListener() {
        val activity = container.context as? AppCompatActivity ?: return
        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isDismissing) dismiss()
            }
        }
        activity.onBackPressedDispatcher.addCallback(backCallback!!)
    }

    private fun startSpring(
            view: View,
            property: FloatPropertyCompat<View>,
            final: Float,
            start: Float
    ): SpringAnimation = SpringAnimation(view, property).apply {
        spring = SpringForce(final).apply {
            stiffness = SpringForce.STIFFNESS_VERY_LOW
            dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
        }
        setStartValue(start)
        start()
    }

    // lerp overloads for Int and Float
    private fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).toInt()
    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun easeOutDecay(normalized: Float): Float = 1f - (1f - normalized).pow(5)

    // Optional hook
    open fun onPopupCreated(scrollView: NestedScrollView, contentLayout: LinearLayout) {}
}