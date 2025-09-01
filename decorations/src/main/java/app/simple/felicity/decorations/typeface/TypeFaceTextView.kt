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
import app.simple.felicity.core.utils.ColorUtils.animateColorChange
import app.simple.felicity.core.utils.ColorUtils.animateDrawableColorChange
import app.simple.felicity.core.utils.ViewUtils
import app.simple.felicity.core.utils.ViewUtils.fadeInAnimation
import app.simple.felicity.core.utils.ViewUtils.fadeOutAnimation
import app.simple.felicity.core.utils.ViewUtils.slideInAnimation
import app.simple.felicity.core.utils.ViewUtils.slideOutAnimation
import app.simple.felicity.decoration.R
import app.simple.felicity.manager.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.manager.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.preferences.BehaviourPreferences
import app.simple.felicity.shared.utils.ConditionUtils.invert
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent

@Suppress("unused")
open class TypeFaceTextView : AppCompatTextView, ThemeChangedListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private val typedArray: TypedArray
    private var colorMode: Int = PRIMARY
    private var drawableTintMode = DRAWABLE_REGULAR
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
        colorMode = typedArray.getInt(R.styleable.TypeFaceTextView_textColorStyle, PRIMARY)
        drawableTintMode = typedArray.getInt(R.styleable.TypeFaceTextView_drawableTintStyle, DRAWABLE_REGULAR)
        isDrawableHidden = typedArray.getBoolean(R.styleable.TypeFaceTextView_isDrawableHidden, true)
        hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            breakStrategy = LineBreaker.BREAK_STRATEGY_SIMPLE
        }

        setTextColor(false)
        setDrawableTint(false)

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

    override fun onThemeChanged(theme: app.simple.felicity.theme.themes.Theme, animate: Boolean) {
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
                HEADER -> this.animateColorChange(ThemeManager.theme.textViewTheme.headerTextColor)
                PRIMARY -> this.animateColorChange(ThemeManager.theme.textViewTheme.primaryTextColor)
                SECONDARY -> this.animateColorChange(ThemeManager.theme.textViewTheme.secondaryTextColor)
                TERTIARY -> this.animateColorChange(ThemeManager.theme.textViewTheme.tertiaryTextColor)
                QUATERNARY -> this.animateColorChange(ThemeManager.theme.textViewTheme.quaternaryTextColor)
                ACCENT -> this.animateColorChange(ThemeManager.accent.primaryAccentColor)
                WHITE -> this.animateColorChange(Color.WHITE)
            }
        } else {
            when (colorMode) {
                HEADER -> setTextColor(ThemeManager.theme.textViewTheme.headerTextColor)
                PRIMARY -> setTextColor(ThemeManager.theme.textViewTheme.primaryTextColor)
                SECONDARY -> setTextColor(ThemeManager.theme.textViewTheme.secondaryTextColor)
                TERTIARY -> setTextColor(ThemeManager.theme.textViewTheme.tertiaryTextColor)
                QUATERNARY -> setTextColor(ThemeManager.theme.textViewTheme.quaternaryTextColor)
                ACCENT -> setTextColor(ThemeManager.accent.primaryAccentColor)
                WHITE -> setTextColor(ColorStateList.valueOf(Color.WHITE))
            }
        }
    }

    private fun setDrawableTint(animate: Boolean) {
        if (animate) {
            when (drawableTintMode) {
                DRAWABLE_ACCENT -> animateDrawableColorChange(lastDrawableColor, ThemeManager.accent.primaryAccentColor)
                DRAWABLE_REGULAR -> animateDrawableColorChange(lastDrawableColor, ThemeManager.theme.iconTheme.regularIconColor)
                DRAWABLE_SECONDARY -> animateDrawableColorChange(lastDrawableColor, ThemeManager.theme.iconTheme.secondaryIconColor)
                DRAWABLE_WARNING -> animateDrawableColorChange(lastDrawableColor, Color.RED)
            }
        } else {
            when (drawableTintMode) {
                DRAWABLE_ACCENT -> TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(ThemeManager.accent.primaryAccentColor))
                DRAWABLE_REGULAR -> TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(ThemeManager.theme.iconTheme.regularIconColor))
                DRAWABLE_SECONDARY -> TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(ThemeManager.theme.iconTheme.secondaryIconColor))
                DRAWABLE_WARNING -> TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(Color.RED))
            }
        }

        setLastDrawableColor()
    }

    private fun setLastDrawableColor() {
        lastDrawableColor = when (drawableTintMode) {
            DRAWABLE_ACCENT -> ThemeManager.accent.primaryAccentColor
            DRAWABLE_REGULAR -> ThemeManager.theme.iconTheme.regularIconColor
            DRAWABLE_SECONDARY -> ThemeManager.theme.iconTheme.secondaryIconColor
            DRAWABLE_WARNING -> Color.RED
            else -> Color.GRAY
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

    fun setTextWithSlideAnimation(text: String, duration: Long = 250, direction: Int = ViewUtils.LEFT, delay: Long = 0L, completion: (() -> Unit)? = null) {
        slideOutAnimation(duration, delay / 2L, direction) {
            this.text = text
            slideInAnimation(duration, delay / 2L, direction) {
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

    fun setDrawableTineMode(drawableTintMode: Int) {
        this.drawableTintMode = drawableTintMode
        setDrawableTint(animate = false)
    }

    fun setTypeFaceStyle(fontStyle: Int) {
        this.fontStyle = fontStyle
        typeface = TypeFace.getTypeFace(AppearancePreferences.getAppFont(), fontStyle, context)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            AppearancePreferences.ACCENT_COLOR -> {
                setTextColor(animate = true)
                setDrawableTint(animate = true)
            }
            AppearancePreferences.APP_FONT -> {
                typeface = TypeFace.getTypeFace(AppearancePreferences.getAppFont(), fontStyle, context)
                invalidate()
            }
        }
    }

    override fun onAccentChanged(accent: Accent) {
        super.onAccentChanged(accent)
        setTextColor(animate = true)
        setDrawableTint(animate = true)
    }

    companion object {
        const val EXTRALIGHT = -1
        const val LIGHT = 0
        const val REGULAR = 1
        const val MEDIUM = 2
        const val BOLD = 3
        const val BLACK = 4

        const val HEADER = 0
        const val PRIMARY = 1
        const val SECONDARY = 2
        const val TERTIARY = 3
        const val QUATERNARY = 4
        const val ACCENT = 5
        const val WHITE = 6

        const val DRAWABLE_ACCENT = 0
        const val DRAWABLE_REGULAR = 1
        const val DRAWABLE_SECONDARY = 2
        const val DRAWABLE_WARNING = 3
    }
}
