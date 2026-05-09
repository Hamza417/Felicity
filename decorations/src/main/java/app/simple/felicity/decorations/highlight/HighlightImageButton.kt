package app.simple.felicity.decorations.highlight

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatImageButton
import app.simple.felicity.decoration.R
import app.simple.felicity.decorations.highlight.HighlightImageButton.Companion.MODE_BOTH
import app.simple.felicity.decorations.highlight.HighlightImageButton.Companion.MODE_FLAT
import app.simple.felicity.decorations.highlight.HighlightImageButton.Companion.MODE_OUTLINE
import app.simple.felicity.manager.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.manager.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.shared.utils.UnitUtils.dpToPx
import app.simple.felicity.shared.utils.ViewUtils.triggerHover
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.models.Theme

/**
 * A pill-shaped image button that uses the same visual language as [HighlightTextView] —
 * a rounded background with a [app.simple.felicity.decorations.ripple.FelicityRippleDrawable]
 * ripple effect on tap.
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

    companion object {
        const val MODE_FLAT = HighlightViewDelegate.MODE_FLAT
        const val MODE_OUTLINE = HighlightViewDelegate.MODE_OUTLINE
        const val MODE_BOTH = HighlightViewDelegate.MODE_BOTH
        private const val DEFAULT_STROKE_DP = 0.5f
    }

    private val delegate = HighlightViewDelegate(strokeWidthPx = dpToPx(DEFAULT_STROKE_DP))

    init {
        if (!isInEditMode) {
            if (attrs != null) {
                val a = context.obtainStyledAttributes(attrs, R.styleable.HighlightImageButton)
                try {
                    delegate.highlightMode = a.getInt(R.styleable.HighlightImageButton_highlightMode, MODE_FLAT)
                    delegate.strokeWidthPx = a.getDimension(R.styleable.HighlightImageButton_highlightStrokeWidth, delegate.strokeWidthPx)
                    if (a.hasValue(R.styleable.HighlightImageButton_highlightCustomColor)) {
                        delegate.customColor = a.getColor(R.styleable.HighlightImageButton_highlightCustomColor, Color.TRANSPARENT)
                        delegate.useCustomColor = true
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
        if (!delegate.useCustomColor) {
            applyPillBackground()
            applyRippleForeground()
        }
        applyIconTint()
    }

    override fun onAccentChanged(accent: Accent) {
        if (!delegate.useCustomColor) {
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
        } else if (!delegate.useCustomColor && key == AppearancePreferences.ACCENT_COLOR) {
            applyPillBackground()
            applyRippleForeground()
        }
    }

    /**
     * Builds and sets the pill-shaped background. The look depends on the current highlight mode —
     * think of it like choosing between a filled button, a ghost button, or one with both a
     * border and a fill.
     */
    private fun applyPillBackground() {
        setBackground(delegate.buildBackground(delegate.getCornerRadius()))
    }

    /**
     * Slaps a ripple drawable on top as the foreground so taps get that satisfying
     * ink-splash ripple centered on the button.
     */
    private fun applyRippleForeground() {
        foreground = delegate.buildRipple(delegate.getCornerRadius())
    }

    /**
     * Tints the icon to the theme's regular icon color so it always looks right,
     * no matter which dark or light theme the user prefers.
     */
    private fun applyIconTint() {
        imageTintList = ColorStateList.valueOf(
                ThemeManager.theme.iconTheme.regularIconColor)
    }

    /**
     * Switches the button to a different highlight style on the fly. Handy if you need to
     * toggle it programmatically — like switching a button from ghost to filled when selected.
     *
     * @param mode One of [MODE_FLAT], [MODE_OUTLINE], or [MODE_BOTH].
     */
    fun setHighlightMode(mode: Int) {
        delegate.highlightMode = mode
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
        delegate.applyCustomColor(color)
        applyPillBackground()
        applyRippleForeground()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val animDuration = resources.getInteger(R.integer.animation_duration).toLong()
        when (event.action) {
            MotionEvent.ACTION_DOWN -> HighlightViewDelegate.animateTouchDown(this, animDuration)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> HighlightViewDelegate.animateTouchUp(this, animDuration)
        }
        return super.onTouchEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        triggerHover(event)
        return super.onGenericMotionEvent(event)
    }
}

