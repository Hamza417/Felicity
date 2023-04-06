package app.simple.felicity.decorations.typeface

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.text.LineBreaker
import android.os.Build
import android.text.Layout
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import app.simple.felicity.R
import app.simple.felicity.interfaces.ThemeChangedListener
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.preferences.BehaviourPreferences
import app.simple.felicity.preferences.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.preferences.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.themes.Theme
import app.simple.felicity.utils.ColorUtils.animateColorChange
import app.simple.felicity.utils.ColorUtils.animateDrawableColorChange
import app.simple.felicity.utils.ConditionUtils.invert
import app.simple.felicity.utils.TypeFace
import app.simple.felicity.utils.ViewUtils.fadeInAnimation
import app.simple.felicity.utils.ViewUtils.fadeOutAnimation
import app.simple.felicity.utils.ViewUtils.slideInAnimation
import app.simple.felicity.utils.ViewUtils.slideOutAnimation

@Suppress("unused")
open class TypeFaceTextView : AppCompatTextView, ThemeChangedListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private val typedArray: TypedArray
    private var colorMode: Int = 1
    private var drawableTintMode = 2
    private var isDrawableHidden = true
    private var lastDrawableColor = Color.GRAY

    var fontStyle = MEDIUM
        set(value) {
            field = value
            typeface = TypeFace.getTypeFace(AppearancePreferences.getAppFont(), field, context)
        }

    constructor(context: Context) : super(context) {
        typedArray = context.theme.obtainStyledAttributes(null, R.styleable.TypeFaceTextView, 0, 0)
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        typedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.TypeFaceTextView, 0, 0)
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        typedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.TypeFaceTextView, defStyleAttr, 0)
        init()
    }

    private fun init() {
        if (isInEditMode) return
        typeface = TypeFace.getTypeFace(AppearancePreferences.getAppFont(), typedArray.getInt(R.styleable.TypeFaceTextView_appFontStyle, BOLD), context)
        colorMode = typedArray.getInt(R.styleable.TypeFaceTextView_textColorStyle, 1)
        drawableTintMode = typedArray.getInt(R.styleable.TypeFaceTextView_drawableTintStyle, 1)
        isDrawableHidden = typedArray.getBoolean(R.styleable.TypeFaceTextView_isDrawableHidden, true)

        hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            breakStrategy = LineBreaker.BREAK_STRATEGY_SIMPLE
        }

        setTextColor(false)

        //        if (DevelopmentPreferences.get(DevelopmentPreferences.preferencesIndicator) && isDrawableHidden) {
        //            setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        //        } else {
        //            setDrawableTint(false)
        //        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (isSingleLine) {
                if (BehaviourPreferences.isMarqueeOn()) {
                    isSelected = true
                } else {
                    isSingleLine = false
                    ellipsize = null
                }
            }
        } else {
            if (lineCount <= 1) {
                if (BehaviourPreferences.isMarqueeOn()) {
                    isSelected = true
                } else {
                    isSingleLine = false
                    ellipsize = null
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isInEditMode.invert()) {
            registerSharedPreferenceChangeListener()
        }
        ThemeManager.addListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        unregisterSharedPreferenceChangeListener()
        ThemeManager.removeListener(this)
    }

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        setTextColor(animate = animate)
        setDrawableTint(animate = animate)
    }

    override fun setCompoundDrawablesWithIntrinsicBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setCompoundDrawablesWithIntrinsicBounds(left, top, right, bottom)
        setDrawableTint(false)
    }

    private fun setTextColor(animate: Boolean) {
        if (animate) {
            when (colorMode) {
                0 -> this.animateColorChange(ThemeManager.theme.textViewTheme.headerTextColor)
                1 -> this.animateColorChange(ThemeManager.theme.textViewTheme.primaryTextColor)
                2 -> this.animateColorChange(ThemeManager.theme.textViewTheme.secondaryTextColor)
                3 -> this.animateColorChange(ThemeManager.theme.textViewTheme.tertiaryTextColor)
                4 -> this.animateColorChange(ThemeManager.theme.textViewTheme.quaternaryTextColor)
                5 -> this.animateColorChange(ThemeManager.accent.primaryAccentColor)
                6 -> setTextColor(ColorStateList.valueOf(Color.WHITE))
            }
        } else {
            when (colorMode) {
                0 -> setTextColor(ThemeManager.theme.textViewTheme.headerTextColor)
                1 -> setTextColor(ThemeManager.theme.textViewTheme.primaryTextColor)
                2 -> setTextColor(ThemeManager.theme.textViewTheme.secondaryTextColor)
                3 -> setTextColor(ThemeManager.theme.textViewTheme.tertiaryTextColor)
                4 -> setTextColor(ThemeManager.theme.textViewTheme.quaternaryTextColor)
                5 -> setTextColor(ThemeManager.accent.primaryAccentColor)
                6 -> setTextColor(ColorStateList.valueOf(Color.WHITE))
            }
        }
    }

    private fun setDrawableTint(animate: Boolean) {
        if (animate) {
            when (drawableTintMode) {
                0, 3 -> animateDrawableColorChange(lastDrawableColor, AppearancePreferences.getAccentColor())
                1 -> animateDrawableColorChange(lastDrawableColor, ThemeManager.theme.iconTheme.regularIconColor)
                2 -> animateDrawableColorChange(lastDrawableColor, ThemeManager.theme.iconTheme.secondaryIconColor)
            }
        } else {
            when (drawableTintMode) {
                0, 3 -> TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(AppearancePreferences.getAccentColor()))
                1 -> TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(ThemeManager.theme.iconTheme.regularIconColor))
                2 -> TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(ThemeManager.theme.iconTheme.secondaryIconColor))
            }
        }

        setLastDrawableColor()
    }

    private fun setLastDrawableColor() {
        lastDrawableColor = when (drawableTintMode) {
            0 -> ThemeManager.accent.primaryAccentColor
            1 -> ThemeManager.theme.iconTheme.regularIconColor
            2 -> ThemeManager.theme.iconTheme.secondaryIconColor
            else -> ThemeManager.theme.iconTheme.secondaryIconColor
        }
    }

    fun setTextWithAnimation(text: String, duration: Long = 250, completion: (() -> Unit)? = null) {
        fadeOutAnimation(duration) {
            this.text = text
            fadeInAnimation(duration) {
                completion?.let {
                    it()
                }
            }
        }
    }

    fun setTextWithSlideAnimation(text: String, duration: Long = 250, delay: Long = 0L, completion: (() -> Unit)? = null) {
        slideOutAnimation(duration, delay / 2L) {
            this.text = text
            slideInAnimation(duration, delay / 2L) {
                completion?.let {
                    it()
                }
            }
        }
    }

    fun setTextWithAnimation(resId: Int, duration: Long = 250, completion: (() -> Unit)? = null) {
        fadeOutAnimation(duration) {
            this.text = context.getString(resId)
            fadeInAnimation(duration) {
                completion?.let {
                    it()
                }
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            AppearancePreferences.accentColor -> {
                setTextColor(animate = true)
                setDrawableTint(animate = true)
            }
        }
    }

    companion object {
        const val LIGHT = 0
        const val REGULAR = 1
        const val MEDIUM = 2
        const val BOLD = 3
    }
}