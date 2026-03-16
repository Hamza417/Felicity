@file:Suppress("PrivatePropertyName")

package app.simple.felicity.decorations.toggles

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorInt
import androidx.appcompat.content.res.AppCompatResources
import app.simple.felicity.decorations.typeface.TypeFace
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.themes.Theme
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A custom segmented button group that renders a list of [Button] objects as a
 * horizontally-divided rectangle. The selected segment is highlighted by a sliding indicator
 * that animates with a subtle squiggly motion when the selection changes.
 *
 * Buttons are supplied programmatically via [setButtons], and selection callbacks are
 * received via [setOnButtonSelectedListener]. Only single-selection mode is supported.
 *
 * Colors are sourced from [ThemeManager]: the accent color drives the highlight indicator,
 * divider/outline colors come from ViewGroupTheme, and text/icon colors come from
 * TextViewTheme and IconTheme respectively.
 *
 * @author Hamza417
 */
class FelicityButtonGroup @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr), ThemeChangedListener {

    // -------------------------------------------------------------------------
    // Configuration variables
    // -------------------------------------------------------------------------

    /**
     * Margin in pixels between the outer border and the highlight indicator.
     * Increase for a more inset appearance.
     */
    var highlightMargin: Float = dp(3f)
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Corner radius in pixels for the outer container border. Automatically
     * derived from [AppearancePreferences] during initialization.
     */
    var containerCornerRadius: Float = dp(8f)
        set(value) {
            field = value
            clipPath.reset()
            clipPath.addRoundRect(outerRect, field, field, Path.Direction.CW)
            invalidate()
        }

    /** Default width per button cell when the view is measured as WRAP_CONTENT. */
    var defaultCellWidth: Float = dp(48f)

    /** Height of the button group when measured as WRAP_CONTENT. */
    var defaultButtonHeight: Float = dp(40f)

    /**
     * Size in pixels at which icons are rendered (both width and height).
     */
    var iconSize: Float = dp(18f)
        set(value) {
            field = dp(value)
            invalidate()
        }

    // -------------------------------------------------------------------------
    // Colors
    // -------------------------------------------------------------------------

    @ColorInt
    private var accentColor: Int = if (isInEditMode) 0xFF6200EE.toInt()
    else ThemeManager.accent.primaryAccentColor

    @ColorInt
    private var outlineColor: Int = if (isInEditMode) 0xFF9E9E9E.toInt()
    else ThemeManager.theme.viewGroupTheme.dividerColor

    @ColorInt
    private var primaryTextColor: Int = if (isInEditMode) 0xFF212121.toInt()
    else ThemeManager.theme.textViewTheme.primaryTextColor

    @ColorInt
    private var iconColor: Int = if (isInEditMode) 0xFF212121.toInt()
    else ThemeManager.theme.iconTheme?.secondaryIconColor
        ?: ThemeManager.theme.textViewTheme.primaryTextColor

    /** Color applied to icons and text that sit on top of the active highlight. */
    @ColorInt
    private var selectedContentColor: Int = Color.WHITE

    // -------------------------------------------------------------------------
    // Paints
    // -------------------------------------------------------------------------

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(12f)
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private var buttons: List<Button> = emptyList()
    private var drawables: MutableList<Drawable?> = mutableListOf()
    private var selectedIndex: Int = 0

    /** Current animated left boundary of the highlight rectangle. */
    private var animHighlightLeft: Float = 0f

    /** Current animated right boundary of the highlight rectangle. */
    private var animHighlightRight: Float = 0f

    /**
     * Squiggle animation intensity, ranging 0 (no deformation) to 1 (max deformation).
     * This is driven by [sin](PI * animationFraction) so it peaks at the midpoint of
     * each slide and is zero at the start and end.
     */
    private var squiggleFraction: Float = 0.5f

    private var slideAnimator: ValueAnimator? = null
    private var onButtonSelectedListener: ((Int) -> Unit)? = null

    // Reusable geometry objects
    private val highlightPath = Path()
    private val clipPath = Path()
    private val outerRect = RectF()

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    init {
        if (!isInEditMode) {
            val prefRadius = AppearancePreferences.getCornerRadius()
            containerCornerRadius = dp((prefRadius / 4f).coerceIn(4f, 14f))
            textPaint.typeface = TypeFace.getMediumTypeFace(context)
        }
        applyThemeColors()
        isClickable = true
        isFocusable = true
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Replaces the current list of button items and re-renders the group.
     */
    fun setButtons(buttons: List<Button>) {
        this.buttons = buttons
        loadDrawables()
        selectedIndex = 0
        requestLayout()
        post {
            invalidate()
        }
    }

    /**
     * Registers a callback invoked whenever the user selects a different button.
     *
     * @param listener A lambda that receives the zero-based index of the newly selected button.
     */
    fun setOnButtonSelectedListener(listener: (Int) -> Unit) {
        onButtonSelectedListener = listener
    }

    /**
     * Programmatically selects the button at [index].
     *
     * @param index Zero-based index of the button to select.
     * @param animate Whether to animate the highlight sliding to the new position.
     * @param notifyListener Whether to invoke the registered [onButtonSelectedListener].
     */
    fun setSelectedIndex(index: Int, animate: Boolean = true, notifyListener: Boolean = true) {
        if (index < 0 || index >= buttons.size) {
            throw IndexOutOfBoundsException(
                    "Index $index is out of bounds for button count ${buttons.size}" +
                            ", maybe buttons were not set?")
        }

        val oldIndex = selectedIndex
        selectedIndex = index
        if (animate) {
            animateHighlightTo(index)
        } else {
            snapHighlightToIndex(index)
            invalidate()
        }
        if (notifyListener && index != oldIndex) {
            onButtonSelectedListener?.invoke(index)
        }
    }

    /** Returns the zero-based index of the currently selected button. */
    fun getSelectedIndex(): Int = selectedIndex

    // -------------------------------------------------------------------------
    // Measurement
    // -------------------------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val count = buttons.size.coerceAtLeast(1)
        val desiredW = (defaultCellWidth * count).toInt() + paddingLeft + paddingRight
        val desiredH = defaultButtonHeight.toInt() + paddingTop + paddingBottom
        setMeasuredDimension(
                resolveSize(desiredW, widthMeasureSpec),
                resolveSize(desiredH, heightMeasureSpec),
        )
    }

    // -------------------------------------------------------------------------
    // Geometry on size change
    // -------------------------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val halfStroke = outlinePaint.strokeWidth / 2f
        outerRect.set(
                paddingLeft + halfStroke,
                paddingTop + halfStroke,
                w - paddingRight - halfStroke,
                h - paddingBottom - halfStroke,
        )
        clipPath.reset()
        clipPath.addRoundRect(outerRect, containerCornerRadius, containerCornerRadius, Path.Direction.CW)
        snapHighlightToIndex(selectedIndex)
    }

    // -------------------------------------------------------------------------
    // Drawing
    // -------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (buttons.isEmpty() || outerRect.isEmpty) return

        val count = buttons.size
        val cellWidth = outerRect.width() / count

        // Clip all interior drawing to the rounded outer rectangle.
        canvas.save()
        canvas.clipPath(clipPath)

        // 1. Draw the sliding highlight path.
        buildHighlightPath(
                animHighlightLeft,
                outerRect.top + highlightMargin,
                animHighlightRight,
                outerRect.bottom - highlightMargin,
                squiggleFraction,
        )
        canvas.drawPath(highlightPath, highlightPaint)

        // 2. Draw dividers between cells.
        dividerPaint.color = outlineColor
        for (i in 1 until count) {
            val x = outerRect.left + i * cellWidth
            canvas.drawLine(x, outerRect.top, x, outerRect.bottom, dividerPaint)
        }

        // 3. Draw icons and/or text for each button cell.
        for (i in 0 until count) {
            val cellLeft = outerRect.left + i * cellWidth
            val cellRight = cellLeft + cellWidth
            val cx = (cellLeft + cellRight) / 2f
            val cy = (outerRect.top + outerRect.bottom) / 2f
            val contentColor = contentColorForCell(i)

            val drawable = drawables.getOrNull(i)
            val item = buttons.getOrNull(i) ?: continue

            when {
                drawable != null && item.textResId != null ->
                    drawIconAndText(canvas, drawable, item.textResId, cx, cy, contentColor)
                drawable != null ->
                    drawIcon(canvas, drawable, cx, cy, contentColor)
                item.textResId != null ->
                    drawText(canvas, item.textResId, cx, cy, contentColor)
            }
        }

        canvas.restore()

        // 4. Draw the outer container border on top of everything.
        outlinePaint.color = outlineColor
        canvas.drawRoundRect(outerRect, containerCornerRadius, containerCornerRadius, outlinePaint)
    }

    // -------------------------------------------------------------------------
    // Content color blending
    // -------------------------------------------------------------------------

    /**
     * Returns the content (icon/text) color for a cell at [cellIndex], blended between
     * [iconColor] and [selectedContentColor] based on how much of the highlight
     * currently overlaps that cell. This produces a smooth color transition during
     * the slide animation.
     */
    private fun contentColorForCell(cellIndex: Int): Int {
        if (buttons.isEmpty()) return iconColor
        val count = buttons.size
        val cellWidth = outerRect.width() / count
        val cellLeft = outerRect.left + cellIndex * cellWidth
        val cellRight = cellLeft + cellWidth

        val overlapLeft = max(animHighlightLeft, cellLeft)
        val overlapRight = min(animHighlightRight, cellRight)
        val overlap = (max(0f, overlapRight - overlapLeft) / (cellRight - cellLeft)).coerceIn(0f, 1f)

        return blendColors(iconColor, selectedContentColor, overlap)
    }

    // -------------------------------------------------------------------------
    // Drawing helpers
    // -------------------------------------------------------------------------

    private fun drawIcon(
            canvas: Canvas,
            drawable: Drawable,
            cx: Float,
            cy: Float,
            tint: Int,
    ) {
        val half = iconSize / 2f
        drawable.setBounds(
                (cx - half).toInt(),
                (cy - half).toInt(),
                (cx + half).toInt(),
                (cy + half).toInt(),
        )
        drawable.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
        drawable.draw(canvas)
    }

    private fun drawText(
            canvas: Canvas,
            textResId: Int,
            cx: Float,
            cy: Float,
            color: Int,
    ) {
        val text = context.getString(textResId)
        textPaint.color = color
        val metrics = textPaint.fontMetrics
        val textY = cy - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, cx, textY, textPaint)
    }

    private fun drawIconAndText(
            canvas: Canvas,
            drawable: Drawable,
            textResId: Int,
            cx: Float,
            cy: Float,
            tint: Int,
    ) {
        val text = context.getString(textResId)
        val textWidth = textPaint.measureText(text)
        val spacing = dp(4f)
        val totalWidth = iconSize + spacing + textWidth
        val iconCx = cx - totalWidth / 2f + iconSize / 2f
        val textCx = cx - totalWidth / 2f + iconSize + spacing + textWidth / 2f
        drawIcon(canvas, drawable, iconCx, cy, tint)
        drawText(canvas, textResId, textCx, cy, tint)
    }

    // -------------------------------------------------------------------------
    // Touch handling
    // -------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || buttons.isEmpty()) return false
        if (event.action == MotionEvent.ACTION_UP) {
            val count = buttons.size
            val cellWidth = outerRect.width() / count
            val touchedIndex = ((event.x - outerRect.left) / cellWidth)
                .toInt()
                .coerceIn(0, count - 1)
            if (touchedIndex != selectedIndex) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                setSelectedIndex(touchedIndex, animate = true, notifyListener = true)
            }
        }
        return true
    }

    // -------------------------------------------------------------------------
    // Highlight animation
    // -------------------------------------------------------------------------

    private fun animateHighlightTo(index: Int) {
        val fromLeft = animHighlightLeft
        val fromRight = animHighlightRight
        val count = buttons.size
        if (count == 0 || outerRect.isEmpty) return

        val cellWidth = outerRect.width() / count
        val toLeft = outerRect.left + index * cellWidth + highlightMargin
        val toRight = outerRect.left + (index + 1) * cellWidth - highlightMargin

        slideAnimator?.cancel()
        slideAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380L
            interpolator = DecelerateInterpolator(3F)
            addUpdateListener { anim ->
                val raw = anim.animatedFraction           // linear 0..1
                val t = anim.animatedValue as Float       // interpolated 0..1+
                animHighlightLeft = lerp(fromLeft, toLeft, t)
                animHighlightRight = lerp(fromRight, toRight, t)
                // Squiggle peaks at the midpoint of the animation and fades to zero.
                squiggleFraction = sin(PI.toFloat() * raw).coerceIn(0f, 1f)
                invalidate()
            }
        }
        slideAnimator!!.start()
    }

    /** Instantly positions the highlight rectangle over the cell at [index] with no animation. */
    private fun snapHighlightToIndex(index: Int) {
        if (buttons.isEmpty() || outerRect.isEmpty) return
        val count = buttons.size
        val cellWidth = outerRect.width() / count
        animHighlightLeft = outerRect.left + index * cellWidth + highlightMargin
        animHighlightRight = outerRect.left + (index + 1) * cellWidth - highlightMargin
        squiggleFraction = 0f
    }

    // -------------------------------------------------------------------------
    // Squiggly highlight path
    // -------------------------------------------------------------------------

    /**
     * Builds the highlight shape into [highlightPath]. When [squiggle] is 0 a standard
     * rounded rectangle is used. When [squiggle] > 0 the left and right vertical edges
     * are deformed by a small sinusoidal wave, giving the squiggly slide effect.
     *
     * The wave amplitude is [squiggle] × 2 dp, so the deformation is very faint.
     */
    private fun buildHighlightPath(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            squiggle: Float,
    ) {
        highlightPath.reset()
        val r = (containerCornerRadius - highlightMargin).coerceAtLeast(dp(2f))
        val amp = dp(2f) * squiggle

        // Fast path: use a plain rounded rect when there is no squiggle.
        if (amp < 0.1f) {
            highlightPath.addRoundRect(RectF(left, top, right, bottom), r, r, Path.Direction.CW)
            return
        }

        val innerH = bottom - top - 2f * r
        val steps = 20
        val freq = 2f * PI.toFloat()

        // Top edge (left corner end → right corner start)
        highlightPath.moveTo(left + r, top)
        highlightPath.lineTo(right - r, top)

        // Top-right arc (sweeps from 270° to 360°)
        highlightPath.arcTo(RectF(right - 2f * r, top, right, top + 2f * r), -90f, 90f)

        // Right edge going downward with sinusoidal deformation
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val y = top + r + t * innerH
            val x = right + amp * sin(freq * t)
            highlightPath.lineTo(x, y)
        }

        // Bottom-right arc (sweeps from 0° to 90°)
        highlightPath.arcTo(RectF(right - 2f * r, bottom - 2f * r, right, bottom), 0f, 90f)

        // Bottom edge (right corner end → left corner start)
        highlightPath.lineTo(left + r, bottom)

        // Bottom-left arc (sweeps from 90° to 180°)
        highlightPath.arcTo(RectF(left, bottom - 2f * r, left + 2f * r, bottom), 90f, 90f)

        // Left edge going upward with sinusoidal deformation (opposite phase for elastic look)
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val y = bottom - r - t * innerH
            val x = left - amp * sin(freq * t)
            highlightPath.lineTo(x, y)
        }

        // Top-left arc (sweeps from 180° to 270°)
        highlightPath.arcTo(RectF(left, top, left + 2f * r, top + 2f * r), 180f, 90f)
        highlightPath.close()
    }

    // -------------------------------------------------------------------------
    // Theme integration
    // -------------------------------------------------------------------------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            ThemeManager.addListener(this)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ThemeManager.removeListener(this)
        slideAnimator?.cancel()
    }

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        outlineColor = theme.viewGroupTheme.dividerColor
        primaryTextColor = theme.textViewTheme.primaryTextColor
        iconColor = theme.iconTheme?.regularIconColor ?: theme.textViewTheme.primaryTextColor
        applyThemeColors()
        invalidate()
    }

    override fun onAccentChanged(accent: Accent) {
        accentColor = accent.primaryAccentColor
        applyThemeColors()
        invalidate()
    }

    /** Pushes the current color fields into the relevant [Paint] objects. */
    private fun applyThemeColors() {
        highlightPaint.color = accentColor
        outlinePaint.color = outlineColor
        dividerPaint.color = outlineColor
        textPaint.color = primaryTextColor
    }

    // -------------------------------------------------------------------------
    // Drawable loading
    // -------------------------------------------------------------------------

    private fun loadDrawables() {
        drawables = buttons.map { item ->
            item.iconResId?.let { resId ->
                AppCompatResources.getDrawable(context, resId)?.mutate()
            }
        }.toMutableList()
    }

    // -------------------------------------------------------------------------
    // State save / restore
    // -------------------------------------------------------------------------

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        return SavedState(superState).also { it.selectedIndex = this.selectedIndex }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            selectedIndex = state.selectedIndex
            post { snapHighlightToIndex(selectedIndex); invalidate() }
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    private class SavedState : BaseSavedState {
        var selectedIndex: Int = 0

        constructor(superState: Parcelable?) : super(superState)

        private constructor(parcel: Parcel) : super(parcel) {
            selectedIndex = parcel.readInt()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(selectedIndex)
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(source: Parcel): SavedState = SavedState(source)
            override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
        }
    }

    // -------------------------------------------------------------------------
    // Utility functions
    // -------------------------------------------------------------------------

    /** Converts density-independent pixels to physical pixels. */
    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    /** Converts scale-independent pixels to physical pixels. */
    private fun sp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /**
     * Blends two ARGB [Color] values by linear interpolation at factor [t] (0 = [a], 1 = [b]).
     */
    @ColorInt
    private fun blendColors(@ColorInt a: Int, @ColorInt b: Int, t: Float): Int {
        val r = lerp(Color.red(a).toFloat(), Color.red(b).toFloat(), t).toInt()
        val g = lerp(Color.green(a).toFloat(), Color.green(b).toFloat(), t).toInt()
        val bl = lerp(Color.blue(a).toFloat(), Color.blue(b).toFloat(), t).toInt()
        return Color.rgb(r, g, bl)
    }

    companion object {
        data class Button(
                val textResId: Int? = null,
                val iconResId: Int? = null,
        )
    }
}