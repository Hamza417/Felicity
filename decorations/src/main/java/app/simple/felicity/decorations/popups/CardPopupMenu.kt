package app.simple.felicity.decorations.popups

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.setPadding
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import app.simple.felicity.decoration.R
import app.simple.felicity.decorations.behaviors.OverScrollBehavior
import app.simple.felicity.decorations.corners.DynamicCornersNestedScrollView
import app.simple.felicity.decorations.ripple.RippleUtils
import app.simple.felicity.decorations.typeface.TypeFaceTextView
import app.simple.felicity.decorations.typeface.TypefaceStyle
import app.simple.felicity.decorations.utils.TextViewUtils.setEndDrawable
import app.simple.felicity.theme.managers.ThemeManager
import com.google.android.material.card.MaterialCardView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

/**
 * A context menu popup that opens exactly at the given touch coordinates,
 * and simultaneously draws an animated stroke around the anchor MaterialCardView.
 */
class CardPopupMenu(
        private val container: ViewGroup,
        private val anchorView: MaterialCardView,
        private val touchX: Float,
        private val touchY: Float,
        private val menuItems: List<PopupMenuItem>,
        private val onMenuItemClick: (itemResId: Int) -> Unit,
        private val onDismiss: (() -> Unit)? = null
) {
    private lateinit var scrimView: View
    private lateinit var popupContainer: DynamicCornersNestedScrollView

    private var originalStrokeColor: Int? = null
    private var originalStrokeWidth: Int? = null

    private var backCallback: OnBackPressedCallback? = null
    private var isDismissing = false

    private var scaleXAnimation: SpringAnimation? = null
    private var scaleYAnimation: SpringAnimation? = null
    private var translationXAnimation: SpringAnimation? = null
    private var translationYAnimation: SpringAnimation? = null

    companion object {
        private const val DURATION = 300L
        private const val SCRIM_COLOR = "#66000000"
        private const val CONTENT_SCALE_START = 0.9f
        private const val CONTENT_ALPHA_START = 0f

        private const val MARGIN = 16 // in dp
        private const val MAX_WIGGLE_THRESHOLD = 72F
        private const val MAX_FINGER_DISTANCE = 0.05f

        private val EMPHASIZED_INTERPOLATOR = PathInterpolator(0.2f, 0f, 0f, 1f)
        private val DECELERATE_CUBIC = PathInterpolator(0.0f, 0.0f, 0.2f, 1f)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        // Transparent scrim
        scrimView = View(container.context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    container.height
            )
            isClickable = true
            setOnClickListener { dismiss() }
        }
        container.addView(scrimView)

        originalStrokeColor = anchorView.strokeColor
        originalStrokeWidth = anchorView.strokeWidth

        anchorView.strokeWidth = (1f * container.resources.displayMetrics.density).toInt()

        val accentColor = ThemeManager.accent.primaryAccentColor

        ValueAnimator.ofArgb(Color.TRANSPARENT, accentColor).apply {
            duration = DURATION
            interpolator = DECELERATE_CUBIC
            addUpdateListener {
                anchorView.strokeColor = it.animatedValue as Int
            }
            start()
        }

        var initialX = 0F
        var initialY = 0F
        var isInitialTouch = true

        val popupScrollView = DynamicCornersNestedScrollView(container.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isFillViewport = false
            elevation = 16f * resources.displayMetrics.density
            clipChildren = true
            clipToPadding = false
            clipToOutline = true
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

        val linearLayout = LinearLayout(container.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
        }

        val density = container.resources.displayMetrics.density
        val hasAnyIcon = menuItems.any { it.icon != 0 }

        menuItems.forEach { item ->
            val hPad = (8 * density).toInt()
            val vPad = (12 * density).toInt()

            val itemLayout = LinearLayout(container.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
                minimumWidth = (172 * density).toInt()
                setPadding(hPad, vPad, hPad * 2, vPad)
                isClickable = true
                isFocusable = true
                RippleUtils.setForegroundDrawable(this)
                setOnClickListener {
                    // Start dismissing *before* invoking the click callback
                    // so that any fragment navigation (like goBack()) isn't
                    // intercepted by this popup's back callback.
                    dismiss()
                    
                    when (item.title) {
                        is Int -> onMenuItemClick(item.title)
                        is String -> onMenuItemClick(menuItems.indexOf(item))
                        else -> throw IllegalArgumentException("Unsupported title type: ${item.title::class.java}")
                    }
                }
            }

            if (hasAnyIcon) {
                val iconSizePx = (16 * density * 1.3F).toInt()
                val iconView = AppCompatImageView(container.context).apply {
                    layoutParams = LinearLayout.LayoutParams(iconSizePx, iconSizePx).apply {
                        marginEnd = (16 * density).toInt()
                    }
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    if (item.icon != 0) {
                        setImageDrawable(ContextCompat.getDrawable(context, item.icon))
                        imageTintList = ColorStateList.valueOf(ThemeManager.accent.primaryAccentColor)
                    } else {
                        visibility = View.INVISIBLE
                    }
                }
                itemLayout.addView(iconView)
            }

            val textColumn = LinearLayout(container.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val titleView = TypeFaceTextView(container.context).apply {
                setTypeFaceStyle(TypefaceStyle.BOLD.style)
                setTextColor(ThemeManager.theme.textViewTheme.primaryTextColor)
                text = when (item.title) {
                    is Int -> context.getString(item.title)
                    is String -> item.title
                    else -> throw IllegalArgumentException("Unsupported title type: ${item.title::class.java}")
                }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setDrawableTineMode(TypeFaceTextView.DRAWABLE_ACCENT)
                compoundDrawablePadding = (5 * density).toInt()
                if (item.isExperimental) {
                    setEndDrawable(R.drawable.ic_beta_12dp)
                }
            }
            textColumn.addView(titleView)

            if (!item.summary.isNullOrBlank()) {
                val summaryView = TypeFaceTextView(container.context).apply {
                    setTypeFaceStyle(TypefaceStyle.REGULAR.style)
                    setTextColor(ThemeManager.theme.textViewTheme.tertiaryTextColor)
                    text = item.summary
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    maxWidth = (200 * density).toInt()
                }
                textColumn.addView(summaryView)
            }

            itemLayout.addView(textColumn)
            linearLayout.addView(itemLayout)
        }

        popupScrollView.addView(linearLayout)

        val marginPx = (MARGIN * density).toInt()
        val windowInsets = ViewCompat.getRootWindowInsets(container)
        val systemBarsInsets = windowInsets?.getInsets(WindowInsetsCompat.Type.systemBars())
        val statusBarHeight = systemBarsInsets?.top ?: 0
        val navigationBarHeight = systemBarsInsets?.bottom ?: 0

        popupScrollView.measure(
                View.MeasureSpec.makeMeasureSpec(container.width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.UNSPECIFIED
        )

        val popupWidth = popupScrollView.measuredWidth
        val popupHeight = popupScrollView.measuredHeight
        val maxHeight = (container.height * 2f / 3f).toInt()
        val finalHeight = if (popupHeight > maxHeight) maxHeight else popupHeight

        val containerLocation = IntArray(2)
        container.getLocationInWindow(containerLocation)

        // Compensate for status bar height
        val relativeTouchX = touchX - containerLocation[0]
        val relativeTouchY = touchY - containerLocation[1] - statusBarHeight

        var leftMargin = relativeTouchX - (popupWidth / 2f)
        var topMargin = relativeTouchY

        leftMargin = max(marginPx.toFloat(), min(leftMargin, (container.width - popupWidth - marginPx).toFloat()))

        val topMarginMin = marginPx + statusBarHeight
        val bottomMarginMin = marginPx + navigationBarHeight
        topMargin = max(topMarginMin.toFloat(), min(topMargin, (container.height - finalHeight - bottomMarginMin).toFloat()))

        val params = CoordinatorLayout.LayoutParams(
                popupWidth,
                finalHeight
        ).apply {
            this.leftMargin = leftMargin.toInt()
            this.topMargin = topMargin.toInt()
            behavior = OverScrollBehavior(container.context, null)
        }

        popupScrollView.layoutParams = params
        popupContainer = popupScrollView
        container.addView(popupContainer)

        // Setup initial state for show animation
        popupContainer.pivotX = (relativeTouchX - leftMargin)
        popupContainer.pivotY = 0f
        popupContainer.alpha = CONTENT_ALPHA_START
        popupContainer.scaleX = CONTENT_SCALE_START
        popupContainer.scaleY = CONTENT_SCALE_START

        val scrimAnimator = ValueAnimator.ofArgb(Color.TRANSPARENT, SCRIM_COLOR.toColorInt()).apply {
            duration = DURATION
            interpolator = DECELERATE_CUBIC
            addUpdateListener { scrimView.setBackgroundColor(it.animatedValue as Int) }
        }

        val contentAlphaAnimator = ObjectAnimator.ofFloat(popupContainer, View.ALPHA, CONTENT_ALPHA_START, 1f).apply {
            duration = DURATION
            interpolator = EMPHASIZED_INTERPOLATOR
        }

        val contentScaleXAnimator = ObjectAnimator.ofFloat(popupContainer, View.SCALE_X, CONTENT_SCALE_START, 1f).apply {
            duration = DURATION
            interpolator = EMPHASIZED_INTERPOLATOR
        }

        val contentScaleYAnimator = ObjectAnimator.ofFloat(popupContainer, View.SCALE_Y, CONTENT_SCALE_START, 1f).apply {
            duration = DURATION
            interpolator = EMPHASIZED_INTERPOLATOR
        }

        AnimatorSet().apply {
            playTogether(scrimAnimator, contentAlphaAnimator, contentScaleXAnimator, contentScaleYAnimator)
            start()
        }

        setupBackPressListener()
    }

    private fun dismiss() {
        if (isDismissing) return
        isDismissing = true
        backCallback?.remove()
        backCallback = null

        val scrimAnimator = ValueAnimator.ofArgb(SCRIM_COLOR.toColorInt(), Color.TRANSPARENT).apply {
            duration = DURATION
            interpolator = DECELERATE_CUBIC
            addUpdateListener { scrimView.setBackgroundColor(it.animatedValue as Int) }
        }

        val endColor = originalStrokeColor ?: Color.TRANSPARENT
        val strokeAnimator = ValueAnimator.ofArgb(anchorView.strokeColor, endColor).apply {
            duration = DURATION
            interpolator = DECELERATE_CUBIC
            addUpdateListener {
                anchorView.strokeColor = it.animatedValue as Int
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    originalStrokeWidth?.let { width ->
                        anchorView.strokeWidth = width
                    }
                }
            })
        }

        val contentAlphaAnimator = ObjectAnimator.ofFloat(popupContainer, View.ALPHA, 1f, 0f).apply {
            duration = DURATION
            interpolator = EMPHASIZED_INTERPOLATOR
        }

        val contentScaleXAnimator = ObjectAnimator.ofFloat(popupContainer, View.SCALE_X, 1f, CONTENT_SCALE_START).apply {
            duration = DURATION
            interpolator = EMPHASIZED_INTERPOLATOR
        }

        val contentScaleYAnimator = ObjectAnimator.ofFloat(popupContainer, View.SCALE_Y, 1f, CONTENT_SCALE_START).apply {
            duration = DURATION
            interpolator = EMPHASIZED_INTERPOLATOR
        }

        AnimatorSet().apply {
            playTogether(scrimAnimator, strokeAnimator, contentAlphaAnimator, contentScaleXAnimator, contentScaleYAnimator)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    container.removeView(scrimView)
                    container.removeView(popupContainer)
                    onDismiss?.invoke()
                }
            })
            start()
        }
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

    private fun easeOutDecay(normalized: Float): Float {
        return 1f - (1f - normalized).pow(5)
    }
}