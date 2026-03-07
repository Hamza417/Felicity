package app.simple.felicity.decorations.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import kotlin.math.min
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

    private val anchorRect = Rect()
    private val finalRect = Rect()
    private val containerRect = Rect()

    companion object {
        private const val OPEN_DURATION = 520L
        private const val CLOSE_DURATION = 480L
        private const val MARGIN = 16 // dp

        private const val MAX_WIGGLE_THRESHOLD = 72F
        private const val MAX_FINGER_DISTANCE = 0.05f

        // Z-depth: lifts from 0 → PEAK during open, settles to FINAL at rest
        private const val PEAK_Z_DP = 28f
        private const val FINAL_Z_DP = 14f

        // Single smooth decelerate for everything — feels natural, no jank
        private val OPEN_INTERPOLATOR = DecelerateInterpolator(2.5f)
        private val CLOSE_INTERPOLATOR = DecelerateInterpolator(1.8f)

        private val ARG_EVALUATOR = ArgbEvaluator()
    }

    // ─── Anchor snapshot ────────────────────────────────────────────────────────

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

    // ─── Build & show ────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        var initialX = 0F
        var initialY = 0F
        var isInitialTouch = true

        // 1. Capture anchor geometry and snapshot BEFORE hiding it
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

        // 2. Build menu items
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
                val textSizePx = textSize * 1.3f
                val iconResId = menuIcons?.getOrNull(menuItems.indexOf(resId)) ?: 0
                val drawable = if (iconResId != 0) {
                    ContextCompat.getDrawable(context, iconResId)?.apply {
                        setBounds(0, 0, textSizePx.toInt(), textSizePx.toInt())
                    }
                } else null
                setCompoundDrawables(drawable, null, null, null)
                setDrawableTineMode(TypeFaceTextView.DRAWABLE_ACCENT)
            }
            linearLayout.addView(tv)
        }

        // 3. Scroll view — no background, morphHost clips it
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
                            translationXAnimation?.cancel(); translationYAnimation?.cancel()
                            scaleXAnimation?.cancel(); scaleYAnimation?.cancel()
                        }
                        val dx = event.rawX - initialX
                        val dy = event.rawY - initialY
                        val dampX = dx * MAX_FINGER_DISTANCE
                        val dampY = dy * MAX_FINGER_DISTANCE
                        val nx = (abs(dampX) / MAX_WIGGLE_THRESHOLD).coerceAtMost(1f)
                        val ny = (abs(dampY) / MAX_WIGGLE_THRESHOLD).coerceAtMost(1f)
                        val easedX = easeOutDecay(nx) * MAX_WIGGLE_THRESHOLD * sign(dampX)
                        val easedY = easeOutDecay(ny) * MAX_WIGGLE_THRESHOLD * sign(dampY)
                        morphLayout.morphHost.translationX = easedX
                        morphLayout.morphHost.translationY = easedY
                        morphLayout.shadowHost.translationX = easedX
                        morphLayout.shadowHost.translationY = easedY
                        val intensity = max(nx, ny)
                        val scale = 1f - easeOutDecay(intensity) * 0.08f
                        morphLayout.morphHost.scaleX = scale
                        morphLayout.morphHost.scaleY = scale
                        morphLayout.shadowHost.scaleX = scale
                        morphLayout.shadowHost.scaleY = scale
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        translationXAnimation = startSpring(morphLayout.morphHost, SpringAnimation.TRANSLATION_X, 0f, morphLayout.morphHost.translationX)
                        translationYAnimation = startSpring(morphLayout.morphHost, SpringAnimation.TRANSLATION_Y, 0f, morphLayout.morphHost.translationY)
                        scaleXAnimation = startSpring(morphLayout.morphHost, SpringAnimation.SCALE_X, 1f, morphLayout.morphHost.scaleX)
                        scaleYAnimation = startSpring(morphLayout.morphHost, SpringAnimation.SCALE_Y, 1f, morphLayout.morphHost.scaleY)
                        startSpring(morphLayout.shadowHost, SpringAnimation.TRANSLATION_X, 0f, morphLayout.shadowHost.translationX)
                        startSpring(morphLayout.shadowHost, SpringAnimation.TRANSLATION_Y, 0f, morphLayout.shadowHost.translationY)
                        startSpring(morphLayout.shadowHost, SpringAnimation.SCALE_X, 1f, morphLayout.shadowHost.scaleX)
                        startSpring(morphLayout.shadowHost, SpringAnimation.SCALE_Y, 1f, morphLayout.shadowHost.scaleY)
                        isInitialTouch = true
                    }
                }
                false
            }
        }

        // 4. Compute final popup rect
        val marginPx = (MARGIN * density).toInt()
        val insets = ViewCompat.getRootWindowInsets(container)
        val sysBars = insets?.getInsets(WindowInsetsCompat.Type.systemBars())
        val statusBarH = sysBars?.top ?: 0
        val navBarH = sysBars?.bottom ?: 0

        linearLayout.measure(
                View.MeasureSpec.makeMeasureSpec(container.width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.UNSPECIFIED)

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

        // 5. Build MorphLayout
        morphLayout = MorphLayout(container.context).apply {
            outlineAmbientShadowColor = accentColor
            outlineSpotShadowColor = accentColor
            // Content starts slightly scaled-down (never zero — zero = invisible + broken layout)
            // and fully transparent. Anchor bitmap starts fully opaque (alpha=0 means show bitmap).
            contentAlpha = 0f
            contentScale = 0.92f
            anchorBitmapAlpha = 0f
            anchorBitmap = anchorBmp
            elevation = 0f
            addView(popupScrollView, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT))
        }

        morphLayout.layoutParams = CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                CoordinatorLayout.LayoutParams.MATCH_PARENT).apply {
            behavior = OverScrollBehavior(container.context, null)
        }

        // 6. Scrim
        scrimView = View(container.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            setOnClickListener { dismiss() }
        }

        container.addView(scrimView)
        container.addView(morphLayout)
        anchorView.visibility = View.INVISIBLE
        morphLayout.visibility = View.INVISIBLE

        // 7. Animate — single continuous pass, all driven by one interpolated value p ∈ [0,1]
        //
        //   Rect + corner + color : p 0→1  (full duration, smoothly decelerating)
        //   Z elevation           : 0→peakZ→finalZ arc
        //   Anchor bitmap         : fades out over first 60% of animation
        //   Content alpha         : fades in over full duration
        //   Content scale         : 0.92→1.0 over full duration
        //   Scrim                 : fades in over first 70%

        morphAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = OPEN_DURATION
            interpolator = OPEN_INTERPOLATOR

            addUpdateListener { anim ->
                val p = anim.animatedValue as Float

                // First frame: position the card at the anchor, then reveal
                if (morphLayout.visibility != View.VISIBLE) {
                    morphLayout.setMorphState(anchorRect, cornerRadius, anchorColor)
                    morphLayout.visibility = View.VISIBLE
                }

                // ── Rect morph (full duration) ────────────────────────────────
                val l = lerp(anchorRect.left, finalRect.left, p)
                val t = lerp(anchorRect.top, finalRect.top, p)
                val r = lerp(anchorRect.right, finalRect.right, p)
                val b = lerp(anchorRect.bottom, finalRect.bottom, p)
                val color = ARG_EVALUATOR.evaluate(p, anchorColor, finalColor) as Int
                morphLayout.setMorphState(l, t, r, b, cornerRadius, color)

                // ── Z elevation arc ───────────────────────────────────────────
                // Rises quickly to peak then gently settles — like lifting off a table
                val zArc = if (p < 0.4f) lerp(0f, peakZPx, p / 0.4f)
                else lerp(peakZPx, finalZPx, (p - 0.4f) / 0.6f)
                morphLayout.elevation = zArc

                // ── Anchor bitmap cross-fade out (first 60%) ──────────────────
                morphLayout.anchorBitmapAlpha = (p / 0.6f).coerceIn(0f, 1f)

                // ── Content reveal (full duration, delayed start at 20%) ──────
                val contentP = ((p - 0.20f) / 0.80f).coerceIn(0f, 1f)
                morphLayout.contentAlpha = contentP
                morphLayout.contentScale = lerp(0.92f, 1.0f, contentP)

                // ── Scrim ─────────────────────────────────────────────────────
                scrimView.alpha = (p / 0.7f).coerceIn(0f, 1f) * 0.55f
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    morphLayout.elevation = finalZPx
                    morphLayout.contentAlpha = 1f
                    morphLayout.contentScale = 1f
                    morphLayout.anchorBitmapAlpha = 1f
                    scrimView.alpha = 0.55f
                    onPopupCreated(popupScrollView, linearLayout)
                    setupBackPressListener()
                }
            })

            start()
        }
    }

    // ─── Dismiss ─────────────────────────────────────────────────────────────────

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

                // Rect collapses back to anchor
                val l = lerp(finalRect.left, anchorRect.left, p)
                val t = lerp(finalRect.top, anchorRect.top, p)
                val r = lerp(finalRect.right, anchorRect.right, p)
                val b = lerp(finalRect.bottom, anchorRect.bottom, p)
                val color = ARG_EVALUATOR.evaluate(p, finalColor, anchorColor) as Int
                morphLayout.setMorphState(l, t, r, b, cornerRadius, color)

                // Z drops back to surface as card lands
                morphLayout.elevation = lerp(finalZPx, 0f, p)

                // Content fades + collapses quickly
                val contentP = (1f - p * 1.5f).coerceIn(0f, 1f)
                morphLayout.contentAlpha = contentP
                morphLayout.contentScale = lerp(1.0f, 0.92f, p)

                // Anchor bitmap fades back in over last 50%
                morphLayout.anchorBitmapAlpha = (1f - p).coerceIn(0f, 1f)

                scrimView.alpha = (1f - p).coerceIn(0f, 1f) * 0.55f
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = cleanup()
                override fun onAnimationCancel(animation: Animator) = cleanup()
            })

            start()
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private fun resolveAnchorColor(): Int {
        return try {
            when (val bg = anchorView.background) {
                is ColorDrawable -> bg.color
                is MaterialShapeDrawable -> bg.fillColor?.defaultColor
                        ?: ThemeManager.theme.viewGroupTheme.backgroundColor
                else -> ThemeManager.theme.viewGroupTheme.backgroundColor
            }
        } catch (_: Exception) {
            ThemeManager.theme.viewGroupTheme.backgroundColor
        }
    }

    private fun cleanup() {
        anchorView.visibility = View.VISIBLE
        morphLayout.clearAnchorBitmap()
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

    fun easeOutDecay(normalized: Float): Float = 1f - (1f - normalized).pow(5)

    open fun onPopupCreated(scrollView: NestedScrollView, contentLayout: LinearLayout) {}
}