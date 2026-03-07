package app.simple.felicity.decorations.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
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
import kotlin.math.pow
import kotlin.math.sign

open class SharedScrollViewPopup @JvmOverloads constructor(
        private val container: ViewGroup,
        private val anchorView: View,
        private val menuItems: List<Int>,
        private val menuIcons: List<Int>? = null,
        private val onMenuItemClick: (itemResId: Int) -> Unit,
        private val onDismiss: (() -> Unit)? = null
) {

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

    private val anchorRect = Rect()
    private val finalRect = Rect()
    private val containerRect = Rect()

    companion object {
        private const val OPEN_DURATION = 500L
        private const val CLOSE_DURATION = 600L
        private const val MARGIN = 16  // dp

        private const val MAX_WIGGLE_THRESHOLD = 72F
        private const val MAX_FINGER_DISTANCE = 0.05f

        private const val PEAK_Z_DP = 28f
        private const val FINAL_Z_DP = 14f
        private const val MAX_SCRIM = 0.25f   // max background dim opacity

        private val OPEN_INTERPOLATOR = DecelerateInterpolator(2.5f)
        private val CLOSE_INTERPOLATOR = DecelerateInterpolator(1.8f)
        private val ARG_EVALUATOR = ArgbEvaluator()
    }

    // ── Anchor bitmap capture ────────────────────────────────────────────────────
    private fun captureAnchorBitmap(): Bitmap? {
        if (anchorView.width <= 0 || anchorView.height <= 0) return null
        return try {
            val bmp = createBitmap(anchorView.width, anchorView.height)
            val canvas = Canvas(bmp)
            anchorView.background?.draw(canvas)
            anchorView.draw(canvas)
            bmp
        } catch (_: Exception) {
            null
        }
    }

    // ── Show ─────────────────────────────────────────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        var initialX = 0F
        var initialY = 0F
        var isInitialTouch = true

        // 1. Snapshot anchor BEFORE hiding it
        container.getGlobalVisibleRect(containerRect)
        anchorView.getGlobalVisibleRect(anchorRect)
        anchorRect.offset(-containerRect.left, -containerRect.top)

        val anchorBmp = captureAnchorBitmap()
        val anchorColor = resolveAnchorColor()
        val finalColor = ThemeManager.theme.viewGroupTheme.backgroundColor
        val accentColor = ThemeManager.accent.primaryAccentColor
        val cornerRadius = AppearancePreferences.getCornerRadius()
        val density = container.resources.displayMetrics.density
        val peakZPx = PEAK_Z_DP * density
        val finalZPx = FINAL_Z_DP * density

        // 2. Menu items
        linearLayout = LinearLayout(container.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
            orientation = LinearLayout.VERTICAL
        }
        menuItems.forEach { resId ->
            val tv = DynamicRippleTextView(container.context).apply {
                val hp = (8 * density).toInt()
                val vp = (12 * density).toInt()
                setPadding(hp, vp, hp * 2, vp)
                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                setTypeFaceStyle(TypefaceStyle.BOLD.style)
                gravity = Gravity.CENTER_VERTICAL
                compoundDrawablePadding = (16 * density).toInt()
                setTextColor(ThemeManager.theme.textViewTheme.primaryTextColor)
                setText(resId)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                isClickable = true
                isFocusable = true
                setOnClickListener { onMenuItemClick(resId); dismiss() }
                val iconResId = menuIcons?.getOrNull(menuItems.indexOf(resId)) ?: 0
                if (iconResId != 0) {
                    val sz = (textSize * 1.3f).toInt()
                    ContextCompat.getDrawable(context, iconResId)
                        ?.apply { setBounds(0, 0, sz, sz) }
                        ?.let { setCompoundDrawables(it, null, null, null) }
                }
                setDrawableTineMode(TypeFaceTextView.DRAWABLE_ACCENT)
            }
            linearLayout.addView(tv)
        }

        // 3. Scroll view
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
                            initialX = event.rawX; initialY = event.rawY
                            isInitialTouch = false
                            translationXAnimation?.cancel()
                            translationYAnimation?.cancel()
                            scaleXAnimation?.cancel()
                            scaleYAnimation?.cancel()
                        }
                        val dx = event.rawX - initialX
                        val dy = event.rawY - initialY
                        val nx = ((abs(dx) * MAX_FINGER_DISTANCE) / MAX_WIGGLE_THRESHOLD).coerceAtMost(1f)
                        val ny = ((abs(dy) * MAX_FINGER_DISTANCE) / MAX_WIGGLE_THRESHOLD).coerceAtMost(1f)
                        morphLayout.morphHost.translationX = easeOutDecay(nx) * MAX_WIGGLE_THRESHOLD * sign(dx)
                        morphLayout.morphHost.translationY = easeOutDecay(ny) * MAX_WIGGLE_THRESHOLD * sign(dy)
                        val scale = 1f - easeOutDecay(max(nx, ny)) * 0.08f
                        morphLayout.morphHost.scaleX = scale
                        morphLayout.morphHost.scaleY = scale
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        translationXAnimation = startSpring(morphLayout.morphHost, SpringAnimation.TRANSLATION_X, 0f, morphLayout.morphHost.translationX)
                        translationYAnimation = startSpring(morphLayout.morphHost, SpringAnimation.TRANSLATION_Y, 0f, morphLayout.morphHost.translationY)
                        scaleXAnimation = startSpring(morphLayout.morphHost, SpringAnimation.SCALE_X, 1f, morphLayout.morphHost.scaleX)
                        scaleYAnimation = startSpring(morphLayout.morphHost, SpringAnimation.SCALE_Y, 1f, morphLayout.morphHost.scaleY)
                        isInitialTouch = true
                    }
                }
                false
            }
        }

        // 4. Compute final popup rect
        val marginPx = (MARGIN * density).toInt()
        val sysBars = ViewCompat.getRootWindowInsets(container)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
        val statusBarH = sysBars?.top ?: 0
        val navBarH = sysBars?.bottom ?: 0

        linearLayout.measure(
                View.MeasureSpec.makeMeasureSpec(container.width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.UNSPECIFIED)

        val pad2 = container.resources.getDimensionPixelSize(R.dimen.padding_10) * 2
        val finalW = (linearLayout.measuredWidth + pad2).coerceAtMost(container.width - marginPx * 2)
        val finalH = (linearLayout.measuredHeight + pad2).coerceAtMost((container.height * 2f / 3f).toInt())

        val finalLeft = (anchorRect.centerX() - finalW / 2)
            .coerceIn(marginPx, container.width - finalW - marginPx)
        val finalTop = (anchorRect.centerY() - finalH / 2)
            .coerceIn(marginPx + statusBarH, container.height - finalH - marginPx - navBarH)
        finalRect.set(finalLeft, finalTop, finalLeft + finalW, finalTop + finalH)

        // 5. Build MorphLayout — set contentAlpha/contentScale BEFORE addView
        //    so the addView override stamps them onto the scroll view child.
        morphLayout = MorphLayout(container.context).apply {
            outlineAmbientShadowColor = accentColor
            outlineSpotShadowColor = accentColor
            contentAlpha = 0f       // child starts invisible
            contentScale = 0.92f    // child starts slightly shrunk
            anchorBitmapAlpha = 0f       // anchor bitmap starts opaque
            anchorBitmap = anchorBmp
            scrimAlpha = 0f
            elevation = 0f
            // Wire dismiss for taps outside the card
            onOutsideTouchListener = { dismiss() }
            // addView AFTER setting contentAlpha/contentScale
            addView(popupScrollView, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT))
        }

        morphLayout.layoutParams = CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                CoordinatorLayout.LayoutParams.MATCH_PARENT).apply {
            behavior = OverScrollBehavior(container.context, null)
        }

        // 6. Add overlay and immediately set morph state to anchor rect so
        //    the first system layout pass positions the card correctly.
        container.addView(morphLayout)
        morphLayout.setMorphState(anchorRect, cornerRadius, anchorColor)
        anchorView.visibility = View.INVISIBLE
        morphLayout.visibility = View.INVISIBLE  // hidden until first animator frame

        // 7. Open animation
        morphAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = OPEN_DURATION
            interpolator = OPEN_INTERPOLATOR

            addUpdateListener { anim ->
                val p = anim.animatedValue as Float

                // Make visible on first frame — morph state already set to anchor rect
                if (morphLayout.visibility != View.VISIBLE) {
                    morphLayout.visibility = View.VISIBLE
                }

                // Rect + color lerp
                morphLayout.setMorphState(
                        lerp(anchorRect.left, finalRect.left, p),
                        lerp(anchorRect.top, finalRect.top, p),
                        lerp(anchorRect.right, finalRect.right, p),
                        lerp(anchorRect.bottom, finalRect.bottom, p),
                        cornerRadius,
                        ARG_EVALUATOR.evaluate(p, anchorColor, finalColor) as Int)

                // Z elevation arc: 0 → peak (40%) → final (100%)
                morphLayout.elevation = if (p < 0.4f) lerp(0f, peakZPx, p / 0.4f)
                else lerp(peakZPx, finalZPx, (p - 0.4f) / 0.6f)

                // Scrim fades in over first 70%
                morphLayout.scrimAlpha = (p / 0.7f).coerceIn(0f, 1f) * MAX_SCRIM

                // Anchor bitmap fades out over first 60%
                morphLayout.anchorBitmapAlpha = (p / 0.6f).coerceIn(0f, 1f)

                // Content reveals starting at 20% so rect is visibly growing first
                val cp = ((p - 0.20f) / 0.80f).coerceIn(0f, 1f)
                morphLayout.contentAlpha = cp
                morphLayout.contentScale = lerp(0.92f, 1.0f, cp)
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    morphLayout.elevation = finalZPx
                    morphLayout.contentAlpha = 1f
                    morphLayout.contentScale = 1f
                    morphLayout.anchorBitmapAlpha = 1f
                    morphLayout.scrimAlpha = MAX_SCRIM
                    onPopupCreated(popupScrollView, linearLayout)
                    setupBackPressListener()
                }
            })
            start()
        }
    }

    // ── Dismiss ──────────────────────────────────────────────────────────────────
    fun dismiss() {
        if (isDismissing) return
        isDismissing = true
        morphAnimator?.cancel()
        backCallback?.remove()
        backCallback = null

        val anchorColor = resolveAnchorColor()
        val finalColor = ThemeManager.theme.viewGroupTheme.backgroundColor
        val cornerRadius = AppearancePreferences.getCornerRadius()
        val density = container.resources.displayMetrics.density
        val finalZPx = FINAL_Z_DP * density

        container.getGlobalVisibleRect(containerRect)
        anchorView.getGlobalVisibleRect(anchorRect)
        anchorRect.offset(-containerRect.left, -containerRect.top)

        morphAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = CLOSE_DURATION
            interpolator = CLOSE_INTERPOLATOR

            addUpdateListener { anim ->
                val p = anim.animatedValue as Float

                morphLayout.setMorphState(
                        lerp(finalRect.left, anchorRect.left, p),
                        lerp(finalRect.top, anchorRect.top, p),
                        lerp(finalRect.right, anchorRect.right, p),
                        lerp(finalRect.bottom, anchorRect.bottom, p),
                        cornerRadius,
                        ARG_EVALUATOR.evaluate(p, finalColor, anchorColor) as Int)

                morphLayout.elevation = lerp(finalZPx, 0f, p)

                // Content collapses quickly ahead of the rect
                val cp = (1f - p * 1.5f).coerceIn(0f, 1f)
                morphLayout.contentAlpha = cp
                morphLayout.contentScale = lerp(1.0f, 0.92f, p)

                // Anchor bitmap fades back in
                morphLayout.anchorBitmapAlpha = (1f - p).coerceIn(0f, 1f)

                morphLayout.scrimAlpha = (1f - p).coerceIn(0f, 1f) * MAX_SCRIM
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = cleanup()
                override fun onAnimationCancel(animation: Animator) = cleanup()
            })
            start()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────
    private fun resolveAnchorColor(): Int = try {
        when (val bg = anchorView.background) {
            is ColorDrawable -> bg.color
            is MaterialShapeDrawable -> bg.fillColor?.defaultColor
                ?: ThemeManager.theme.viewGroupTheme.backgroundColor
            else -> ThemeManager.theme.viewGroupTheme.backgroundColor
        }
    } catch (_: Exception) {
        ThemeManager.theme.viewGroupTheme.backgroundColor
    }

    private fun cleanup() {
        anchorView.visibility = View.VISIBLE
        morphLayout.clearAnchorBitmap()
        if (morphLayout.parent != null) container.removeView(morphLayout)
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

    private fun startSpring(view: View, property: FloatPropertyCompat<View>,
                            final: Float, start: Float): SpringAnimation =
        SpringAnimation(view, property).apply {
            spring = SpringForce(final).apply {
                stiffness = SpringForce.STIFFNESS_VERY_LOW
                dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            }
            setStartValue(start)
            start()
        }

    private fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).toInt()
    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun easeOutDecay(n: Float): Float = 1f - (1f - n).pow(5)

    open fun onPopupCreated(scrollView: NestedScrollView, contentLayout: LinearLayout) {}
}