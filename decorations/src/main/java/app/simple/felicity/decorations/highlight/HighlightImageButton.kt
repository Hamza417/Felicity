package app.simple.felicity.decorations.highlight

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatImageButton
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import app.simple.felicity.decoration.R
import app.simple.felicity.decorations.highlight.HighlightImageButton.Companion.MODE_BOTH
import app.simple.felicity.decorations.highlight.HighlightImageButton.Companion.MODE_FLAT
import app.simple.felicity.decorations.highlight.HighlightImageButton.Companion.MODE_OUTLINE
import app.simple.felicity.decorations.ripple.FelicityRippleDrawable
import app.simple.felicity.manager.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.manager.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.preferences.AccessibilityPreferences
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.shared.utils.ViewUtils.triggerHover
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.models.Theme
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

/**
 * A pill-shaped image button that uses the same visual language as [HighlightTextView] —
 * a rounded background with a [FelicityRippleDrawable] ripple effect on tap.
 *
 * Three highlight modes are available via the {@code highlightMode} XML attribute:
 *   flat    – filled pill using the theme highlight color (default).
 *   outline – stroke border only, no fill.
 *   both    – filled pill plus an accent-colored stroke.
 *
 * The optional {@code highlightCustomColor} attribute pins the fill and stroke to a fixed
 * color that never changes with the theme. Omit it to let the active theme drive the colors.
 *
 * The icon tint is always pulled from the theme's regular icon color so the button blends
 * in naturally no matter which theme or accent the user has chosen.
 *
 * @author Hamza417
 */
class HighlightImageButton @JvmOverloads constructor(
        context: android.content.Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : AppCompatImageButton(context, attrs, defStyleAttr),
    ThemeChangedListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    /** Fill + stroke use the flat theme highlight color by default. */
    companion object {
        const val MODE_FLAT = 0
        const val MODE_OUTLINE = 1
        const val MODE_BOTH = 2

        /** Default stroke thickness in dp — thin enough to look refined but still visible. */
        private const val DEFAULT_STROKE_DP = 0.5f
    }

    private var highlightMode = MODE_FLAT
    private var useCustomColor = false

    @ColorInt
    private var customColor = Color.TRANSPARENT
    private var strokeWidth = dpToPx(DEFAULT_STROKE_DP)

    init {
        if (!isInEditMode) {
            if (attrs != null) {
                val a = context.obtainStyledAttributes(attrs, R.styleable.HighlightImageButton)
                try {
                    highlightMode = a.getInt(R.styleable.HighlightImageButton_highlightMode, MODE_FLAT)
                    strokeWidth = a.getDimension(R.styleable.HighlightImageButton_highlightStrokeWidth, strokeWidth)
                    if (a.hasValue(R.styleable.HighlightImageButton_highlightCustomColor)) {
                        customColor = a.getColor(R.styleable.HighlightImageButton_highlightCustomColor, Color.TRANSPARENT)
                        useCustomColor = true
                    }
                } finally {
                    a.recycle()
                }
            }

            applyPillBackground()
            applyRippleForeground()
            applyIconTint()

            // Remove the default image button border/background shadow so only our
            // pill background shows through.
            setBackgroundDrawable(background)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            registerSharedPreferenceChangeListener()
            ThemeManager.addListener(this)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        unregisterSharedPreferenceChangeListener()
        ThemeManager.removeListener(this)
        clearAnimation()
        scaleX = 1f
        scaleY = 1f
        alpha = 1f
    }

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        if (!useCustomColor) {
            applyPillBackground()
            applyRippleForeground()
        }
        applyIconTint()
    }

    override fun onAccentChanged(accent: Accent) {
        if (!useCustomColor) {
            applyPillBackground()
            applyRippleForeground()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        // When the user changes the global corner radius we rebuild the background so the pill
        // rounds out smoothly — gotta keep things consistent across the whole app.
        if (key == AppearancePreferences.APP_CORNER_RADIUS) {
            applyPillBackground()
            applyRippleForeground()
        } else if (!useCustomColor && key == AppearancePreferences.ACCENT_COLOR) {
            applyPillBackground()
            applyRippleForeground()
        }
    }

    /**
     * Builds and sets the pill-shaped [MaterialShapeDrawable] background. The look depends
     * on [highlightMode] — think of it like choosing between a filled button, a ghost button,
     * or one with a border AND a fill (the fancy option).
     */
    private fun applyPillBackground() {
        val cornerRadius = AppearancePreferences.getCornerRadius()
        val background = MaterialShapeDrawable(
                ShapeAppearanceModel().toBuilder()
                    .setAllCorners(CornerFamily.ROUNDED, cornerRadius)
                    .build())

        val fillColor = resolveFillColor()
        val strokeColor = resolveStrokeColor()

        when (highlightMode) {
            MODE_OUTLINE -> {
                background.fillColor = ColorStateList.valueOf(Color.TRANSPARENT)
                background.setStroke(strokeWidth, strokeColor)
            }
            MODE_BOTH -> {
                background.fillColor = ColorStateList.valueOf(fillColor)
                background.setStroke(strokeWidth, strokeColor)
            }
            else -> {
                // MODE_FLAT is the boring-in-a-good-way default: just a clean filled pill.
                background.fillColor = ColorStateList.valueOf(fillColor)
            }
        }

        setBackground(background)
    }

    /**
     * Slaps a [FelicityRippleDrawable] on top as the foreground so taps get that
     * satisfying ink-splash ripple centered on the button.
     */
    private fun applyRippleForeground() {
        val rippleColor = if (useCustomColor) customColor
        else ThemeManager.accent.primaryAccentColor

        val ripple = FelicityRippleDrawable(rippleColor)
        ripple.setCornerRadius(AppearancePreferences.getCornerRadius())

        val startColor = if (highlightMode == MODE_OUTLINE) Color.TRANSPARENT
        else resolveFillColor()
        ripple.setStartColor(startColor)

        foreground = ripple
    }

    /**
     * Tints the icon to the theme's regular icon color so it always looks right,
     * no matter which dark or light theme the user prefers.
     */
    private fun applyIconTint() {
        imageTintList = ColorStateList.valueOf(
                ThemeManager.theme.iconTheme.regularIconColor)
    }

    /** Returns the fill color — either the custom pinned color or the theme highlight. */
    @ColorInt
    private fun resolveFillColor(): Int {
        return if (useCustomColor) customColor
        else ThemeManager.theme.viewGroupTheme.highlightColor
    }

    /** Returns the stroke color — either the custom pinned color or the active accent. */
    @ColorInt
    private fun resolveStrokeColor(): Int {
        return if (useCustomColor) customColor
        else ThemeManager.accent.primaryAccentColor
    }

    /**
     * Switches the button to a different highlight style on the fly. Handy if you need to
     * toggle it programmatically — like switching a button from ghost to filled when selected.
     *
     * @param mode One of [MODE_FLAT], [MODE_OUTLINE], or [MODE_BOTH].
     */
    fun setHighlightMode(mode: Int) {
        highlightMode = mode
        applyPillBackground()
        applyRippleForeground()
    }

    /**
     * Pins the fill and stroke to [color], ignoring the active theme and accent entirely.
     * Pass [Color.TRANSPARENT] to go back to theme-driven colors.
     *
     * @param color The ARGB color to pin to, or [Color.TRANSPARENT] to unpin.
     */
    fun setCustomHighlightColor(@ColorInt color: Int) {
        if (color == Color.TRANSPARENT) {
            useCustomColor = false
            customColor = Color.TRANSPARENT
        } else {
            useCustomColor = true
            customColor = color
        }
        applyPillBackground()
        applyRippleForeground()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Scale down slightly on press so the button has that satisfying "click" feel.
        // Only runs when the accessibility highlight mode is turned on.
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (AccessibilityPreferences.isHighlightMode() && isClickable) {
                    animate()
                        .scaleX(0.9f)
                        .scaleY(0.9f)
                        .alpha(0.7f)
                        .setInterpolator(LinearOutSlowInInterpolator())
                        .setDuration(resources.getInteger(R.integer.animation_duration).toLong())
                        .start()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (AccessibilityPreferences.isHighlightMode() && isClickable) {
                    animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setStartDelay(50)
                        .setInterpolator(LinearOutSlowInInterpolator())
                        .setDuration(resources.getInteger(R.integer.animation_duration).toLong())
                        .start()
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        triggerHover(event)
        return super.onGenericMotionEvent(event)
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
    }
}

