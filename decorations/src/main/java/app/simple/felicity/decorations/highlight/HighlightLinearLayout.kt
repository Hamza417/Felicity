package app.simple.felicity.decorations.highlight

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import app.simple.felicity.decoration.R
import app.simple.felicity.manager.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.manager.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.shared.utils.UnitUtils.dpToPx
import app.simple.felicity.shared.utils.ViewUtils.triggerHover
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.models.Theme

class HighlightLinearLayout @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr),
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
                val a = context.obtainStyledAttributes(attrs, R.styleable.HighlightLinearLayout)
                try {
                    delegate.highlightMode = a.getInt(R.styleable.HighlightLinearLayout_highlightMode, MODE_FLAT)
                    delegate.strokeWidthPx = a.getDimension(R.styleable.HighlightLinearLayout_highlightStrokeWidth, delegate.strokeWidthPx)
                    if (a.hasValue(R.styleable.HighlightLinearLayout_highlightCustomColor)) {
                        delegate.customColor = a.getColor(R.styleable.HighlightLinearLayout_highlightCustomColor, Color.TRANSPARENT)
                        delegate.useCustomColor = true
                    }
                } finally {
                    a.recycle()
                }
            }

            applyPillBackground()
            applyRippleForeground()

            // Remove the default background shadow so only our pill background shows through.
            setBackgroundDrawable(background)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            registerSharedPreferenceChangeListener()
            ThemeManager.addListener(this)

            if (!delegate.useCustomColor) {
                applyPillBackground()
                applyRippleForeground()
            }
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
    }

    override fun onAccentChanged(accent: Accent) {
        if (!delegate.useCustomColor) {
            applyPillBackground()
            applyRippleForeground()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == AppearancePreferences.APP_CORNER_RADIUS) {
            applyPillBackground()
            applyRippleForeground()
        } else if (!delegate.useCustomColor && key == AppearancePreferences.ACCENT_COLOR) {
            applyPillBackground()
            applyRippleForeground()
        }
    }

    private fun applyPillBackground() {
        setBackground(delegate.buildBackground(delegate.getCornerRadius()))
    }

    private fun applyRippleForeground() {
        foreground = delegate.buildRipple(delegate.getCornerRadius())
    }

    fun setHighlightMode(mode: Int) {
        delegate.highlightMode = mode
        applyPillBackground()
        applyRippleForeground()
    }

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
