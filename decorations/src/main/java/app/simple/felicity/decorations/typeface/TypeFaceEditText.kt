package app.simple.felicity.decorations.typeface

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.Color
import android.os.Build
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatEditText
import app.simple.felicity.core.utils.ColorUtils
import app.simple.felicity.core.utils.ColorUtils.animateColorChange
import app.simple.felicity.decoration.R
import app.simple.felicity.decorations.utils.TextViewUtils.setDrawableTint
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.shared.utils.ConditionUtils.invert
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.utils.TypeFace
import top.defaults.drawabletoolbox.DrawableBuilder
import java.lang.reflect.Field

open class TypeFaceEditText : AppCompatEditText, ThemeChangedListener {

    private var typedArray: TypedArray
    private var colorMode: Int = 1
    private var valueAnimator: ValueAnimator? = null

    constructor(context: Context) : super(context) {
        typedArray = context.theme.obtainStyledAttributes(null, R.styleable.TypeFaceTextView, 0, 0)
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {
        typedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.TypeFaceTextView, 0, 0)
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
            context!!,
            attrs,
            defStyleAttr
    ) {
        typedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.TypeFaceTextView, 0, 0)
        init()
    }

    private fun init() {
        if (isInEditMode.invert()) {
            typeface = TypeFace.getTypeFace(
                    AppearancePreferences.getAppFont(),
                    typedArray.getInt(R.styleable.TypeFaceTextView_appFontStyle, -1),
                    context
            )
            colorMode = typedArray.getInt(R.styleable.TypeFaceTextView_textColorStyle, 1)
            setHighlightColor()
            setTextColor(colorMode, false)
            setHintTextColor(app.simple.felicity.theme.managers.ThemeManager.theme.textViewTheme.tertiaryTextColor)
            setDrawableTint(app.simple.felicity.theme.managers.ThemeManager.theme.iconTheme.secondaryIconColor)
            setCursorDrawable()
            typedArray.recycle()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        app.simple.felicity.theme.managers.ThemeManager.addListener(this)
    }

    override fun onDetachedFromWindow() {
        valueAnimator?.cancel()
        super.onDetachedFromWindow()
        hideInput()
        app.simple.felicity.theme.managers.ThemeManager.removeListener(this)
    }

    override fun onThemeChanged(theme: app.simple.felicity.theme.themes.Theme, animate: Boolean) {
        setTextColor(colorMode, animate)
        setHighlightColor()
    }

    private fun setTextColor(mode: Int, animate: Boolean) {
        if (animate) {
            when (mode) {
                0 -> this.animateColorChange(app.simple.felicity.theme.managers.ThemeManager.theme.textViewTheme.headerTextColor)
                1 -> this.animateColorChange(app.simple.felicity.theme.managers.ThemeManager.theme.textViewTheme.primaryTextColor)
                2 -> this.animateColorChange(app.simple.felicity.theme.managers.ThemeManager.theme.textViewTheme.secondaryTextColor)
                3 -> this.animateColorChange(app.simple.felicity.theme.managers.ThemeManager.theme.textViewTheme.tertiaryTextColor)
                4 -> this.animateColorChange(app.simple.felicity.theme.managers.ThemeManager.theme.textViewTheme.quaternaryTextColor)
                5 -> this.animateColorChange(AppearancePreferences.getAccentColor())
            }
        } else {
            when (mode) {
                0 -> setTextColor(app.simple.felicity.theme.managers.ThemeManager.theme.textViewTheme.headerTextColor)
                1 -> setTextColor(app.simple.felicity.theme.managers.ThemeManager.theme.textViewTheme.primaryTextColor)
                2 -> setTextColor(app.simple.felicity.theme.managers.ThemeManager.theme.textViewTheme.secondaryTextColor)
                3 -> setTextColor(app.simple.felicity.theme.managers.ThemeManager.theme.textViewTheme.tertiaryTextColor)
                4 -> setTextColor(app.simple.felicity.theme.managers.ThemeManager.theme.textViewTheme.quaternaryTextColor)
                5 -> setTextColor(AppearancePreferences.getAccentColor())
            }
        }
    }

    open fun setBackground(animate: Boolean, @ColorInt color: Int) {
        if (animate) {
            valueAnimator = animateBackgroundColor(color)
        } else {
            backgroundTintList = ColorStateList.valueOf(color)
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun setCursorDrawable() {
        val drawable = DrawableBuilder()
            .rectangle()
            .width(resources.getDimensionPixelOffset(R.dimen.cursor_width))
            .ripple(false)
            .strokeWidth(0)
            .solidColor(AppearancePreferences.getAccentColor())
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            textCursorDrawable = drawable
        } else {
            try {
                // https://github.com/android/platform_frameworks_base/blob/kitkat-release/core/java/android/widget/TextView.java#L562-564
                val f: Field = TextView::class.java.getDeclaredField("mCursorDrawableRes")
                f.isAccessible = true
                f.set(this, drawable)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun setHighlightColor() {
        highlightColor = if (app.simple.felicity.theme.managers.ThemeUtils.isNightMode(resources)) {
            ColorUtils.lightenColor(Color.DKGRAY, 0.2F)
        } else {
            ColorUtils.lightenColor(Color.GRAY)
        }
    }

    open fun animateBackgroundColor(endColor: Int): ValueAnimator? {
        val valueAnimator = ValueAnimator.ofArgb(backgroundTintList!!.defaultColor, endColor)
        valueAnimator.duration = 500L
        valueAnimator.interpolator = DecelerateInterpolator()
        valueAnimator.addUpdateListener { animation: ValueAnimator ->
            backgroundTintList = ColorStateList.valueOf(animation.animatedValue as Int)
        }
        valueAnimator.start()
        return valueAnimator
    }

    open fun showInput() {
        requestFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    open fun hideInput() {
        clearFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(windowToken, InputMethodManager.RESULT_UNCHANGED_SHOWN)
    }

    @Suppress("unused")
    open fun toggleInput() {
        when (visibility) {
            VISIBLE -> {
                showInput()
            }

            INVISIBLE, GONE -> {
                hideInput()
            }
        }
    }
}
