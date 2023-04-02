package app.simple.felicity.decorations.checkbox

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import app.simple.felicity.R
import app.simple.felicity.decorations.switchview.SwitchCallbacks
import app.simple.felicity.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.themes.Theme
import app.simple.felicity.utils.ColorUtils.animateColorChange
import app.simple.felicity.utils.ConditionUtils.invert
import app.simple.felicity.utils.ViewUtils

@SuppressLint("ClickableViewAccessibility")
class InureCheckBox @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : CheckBoxFrameLayout(context, attrs, defStyleAttr), ThemeChangedListener {

    private var thumb: ImageView
    private var switchCallbacks: SwitchCallbacks? = null

    private val tension = 3.5F

    private var isChecked: Boolean = false

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.checkbox, this, true)

        thumb = view.findViewById(R.id.switch_thumb)

        clipChildren = false
        clipToPadding = false
        clipToOutline = false

        if (isInEditMode.invert()) {
            ViewUtils.addShadow(this)
        }

        setOnClickListener {
            isChecked = if (isChecked) {
                animateUnchecked()
                switchCallbacks?.onCheckedChanged(false)
                false
            } else {
                animateChecked()
                switchCallbacks?.onCheckedChanged(true)
                true
            }
        }

        unchecked()
        requestLayout()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                thumb.animate()
                    .scaleY(1.5F)
                    .scaleX(1.5F)
                    .setInterpolator(DecelerateInterpolator(1.5F))
                    .setDuration(500L)
                    .start()
            }
            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_UP,
            -> {
                thumb.animate()
                    .scaleY(1.0F)
                    .scaleX(1.0F)
                    .setInterpolator(DecelerateInterpolator(1.5F))
                    .setDuration(500L)
                    .start()
            }
        }

        return super.onTouchEvent(event)
    }

    fun setChecked(boolean: Boolean) {
        isChecked = if (boolean) {
            animateChecked()
            boolean
        } else {
            animateUnchecked()
            boolean
        }
    }

    fun isChecked(): Boolean {
        return isChecked
    }

    fun setCheckedWithoutAnimations(boolean: Boolean) {
        isChecked = if (boolean) {
            checked()
            true
        } else {
            unchecked()
            false
        }
    }

    private fun animateUnchecked() {
        thumb.animate()
            .scaleX(0F)
            .scaleY(0F)
            .alpha(0F)
            .setInterpolator(DecelerateInterpolator())
            .setDuration(500L)
            .start()

        animateColorChange(ThemeManager.theme.switchTheme.switchOffColor)
        animateElevation(0F)
    }

    private fun animateChecked() {
        thumb.animate()
            .scaleX(1F)
            .scaleY(1F)
            .alpha(1F)
            .setInterpolator(OvershootInterpolator(tension))
            .setDuration(500L)
            .start()

        animateColorChange(ThemeManager.accent.primaryAccentColor)
        animateElevation(25F)
    }

    private fun unchecked() {
        thumb.scaleX = 0F
        thumb.scaleY = 0F
        this.backgroundTintList = ColorStateList.valueOf(ThemeManager.theme.switchTheme.switchOffColor)
        elevation = 0F
        invalidate()
    }

    private fun checked() {
        thumb.scaleX = 1F
        thumb.scaleY = 1F
        this.backgroundTintList = ColorStateList.valueOf(ThemeManager.accent.primaryAccentColor)
        elevation = 25F
        invalidate()
    }

    private fun animateElevation(elevation: Float) {
        val valueAnimator = ValueAnimator.ofFloat(this.elevation, elevation)
        valueAnimator.duration = 500L
        valueAnimator.interpolator = LinearOutSlowInInterpolator()
        valueAnimator.addUpdateListener {
            this.elevation = it.animatedValue as Float
        }
        valueAnimator.start()
    }

    fun setOnCheckedChangeListener(switchCallbacks: SwitchCallbacks) {
        this.switchCallbacks = switchCallbacks
    }

    override fun onViewRemoved(child: View?) {
        super.onViewRemoved(child)
        thumb.clearAnimation()
    }

    /**
     * Inverts the switch's checked status. If the switch is checked then
     * it will be unchecked and vice-versa
     */
    fun toggle() {
        isChecked = if (isChecked) {
            animateUnchecked()
            switchCallbacks?.onCheckedChanged(false)
            false
        } else {
            animateChecked()
            switchCallbacks?.onCheckedChanged(true)
            true
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isInEditMode.invert()) {
            ThemeManager.addListener(this)
        }
    }

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        if (!isChecked) {
            if (animate) {
                animateUnchecked()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ThemeManager.removeListener(this)
    }
}