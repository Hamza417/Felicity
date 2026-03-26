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
import android.text.TextPaint
import android.text.TextUtils
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
import kotlin.math.max
import kotlin.math.min

/**
 * A custom segmented button group that renders a list of [Button] objects as a
 * horizontally-divided rectangle. The selected segment is highlighted by a sliding indicator
 * that animates with a subtle squiggly motion when the selection changes.
 *
 * Each cell's width is derived from its content (text measurement or icon size) so that
 * labels always fit without clipping. When the view is given a fixed width (e.g. via
 * constraints), cells scale proportionally to fill it.
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

    /** Fallback cell width used only when no buttons are set and the view is WRAP_CONTENT. */
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
    else ThemeManager.theme.textViewTheme.primaryTextColor

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

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
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
     * Per-cell widths, derived from content measurements and scaled to fill the available
     * width. Recomputed whenever [onSizeChanged] fires or [setButtons] schedules a new frame.
     */
    private var cellWidths: FloatArray = FloatArray(0)

    /**
     * The left-edge X coordinate of each cell within the view.
     * Derived from [cellWidths] and [outerRect].
     */
    private var cellLefts: FloatArray = FloatArray(0)

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
            textPaint.typeface = TypeFace.getBoldTypeFace(context)
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
            /*
             * If the view's size did not change (e.g. it is constrained to an exact width),
             * onSizeChanged will not fire, so we force a geometry recalculation here.
             * We use the current selectedIndex value (which may have been updated by a
             * subsequent setSelectedIndex call) so the highlight snaps to the right cell.
             */
            recomputeCellGeometry()
            snapHighlightToIndex(selectedIndex)
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
        /*
         * Desired width is the sum of content-based natural cell widths so that the view
         * wraps its labels correctly in WRAP_CONTENT mode.
         * In EXACTLY / AT_MOST mode, resolveSize clamps to the spec, and onSizeChanged
         * will later recompute per-cell widths proportionally.
         */
        val desiredW = if (buttons.isEmpty()) {
            defaultCellWidth.toInt() + paddingLeft + paddingRight
        } else {
            computeNaturalCellWidths().sum().toInt() + paddingLeft + paddingRight
        }
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
        recomputeCellGeometry()
        snapHighlightToIndex(selectedIndex)
    }

    // -------------------------------------------------------------------------
    // Cell geometry helpers
    // -------------------------------------------------------------------------

    /**
     * Computes the natural (content-driven) minimum width of every cell.
     *
     * Natural width = measured content size + [MIN_CELL_H_PADDING] so that the label
     * always fits with a comfortable margin. These values are used both for [onMeasure]
     * (WRAP_CONTENT) and as weights for proportional distribution when the view has a
     * fixed width.
     */
    private fun computeNaturalCellWidths(): FloatArray {
        return FloatArray(buttons.size) { i ->
            val button = buttons[i]
            val drawable = drawables.getOrNull(i)
            when {
                drawable != null && button.textResId != null -> {
                    val text = context.getString(button.textResId)
                    iconSize + dp(4f) + textPaint.measureText(text) + MIN_CELL_H_PADDING
                }
                drawable != null -> iconSize + MIN_CELL_H_PADDING
                button.textResId != null -> {
                    val text = context.getString(button.textResId)
                    textPaint.measureText(text) + MIN_CELL_H_PADDING
                }
                else -> MIN_CELL_H_PADDING
            }
        }
    }

    /**
     * Distributes the available width ([outerRect]) across cells proportionally to their
     * natural content widths, then stores the results in [cellWidths] and [cellLefts].
     *
     * Proportional distribution preserves visual balance: a cell whose label is twice as
     * wide as another receives twice the space, rather than every cell receiving the same
     * fixed slice. If a cell somehow ends up smaller than its natural width (very small
     * view), the text will be ellipsized by the draw helpers.
     */
    private fun recomputeCellGeometry() {
        if (buttons.isEmpty() || outerRect.isEmpty) {
            cellWidths = FloatArray(0)
            cellLefts = FloatArray(0)
            return
        }
        val naturalWidths = computeNaturalCellWidths()
        val totalNatural = naturalWidths.sum()
        val available = outerRect.width()

        // Scale every natural width by the same factor so the cells fill the view exactly.
        val scale = if (totalNatural > 0f) available / totalNatural else 1f
        cellWidths = FloatArray(buttons.size) { i -> naturalWidths[i] * scale }

        cellLefts = FloatArray(buttons.size)
        var x = outerRect.left
        for (i in buttons.indices) {
            cellLefts[i] = x
            x += cellWidths[i]
        }
    }

    // -------------------------------------------------------------------------
    // Drawing
    // -------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (buttons.isEmpty() || outerRect.isEmpty || cellLefts.isEmpty()) return

        val count = buttons.size

        // Clip all interior drawing to the rounded outer rectangle.
        canvas.save()
        canvas.clipPath(clipPath)

        // 1. Draw the sliding highlight path.
        buildHighlightPath(
                animHighlightLeft,
                outerRect.top + highlightMargin,
                animHighlightRight,
                outerRect.bottom - highlightMargin,
        )
        canvas.drawPath(highlightPath, highlightPaint)

        // 2. Draw dividers at the left edge of every cell except the first.
        dividerPaint.color = outlineColor
        for (i in 1 until count) {
            val x = cellLefts[i]
            canvas.drawLine(x, outerRect.top, x, outerRect.bottom, dividerPaint)
        }

        // 3. Draw icons and/or text for each button cell.
        for (i in 0 until count) {
            val cellLeft = cellLefts[i]
            val cellRight = cellLeft + cellWidths[i]
            val cx = (cellLeft + cellRight) / 2f
            val cy = (outerRect.top + outerRect.bottom) / 2f
            val contentColor = contentColorForCell(i)
            val cellW = cellWidths[i]

            val drawable = drawables.getOrNull(i)
            val item = buttons.getOrNull(i) ?: continue

            when {
                drawable != null && item.textResId != null ->
                    drawIconAndText(canvas, drawable, item.textResId, cx, cy, contentColor, cellW)
                drawable != null ->
                    drawIcon(canvas, drawable, cx, cy, contentColor)
                item.textResId != null ->
                    drawText(canvas, item.textResId, cx, cy, contentColor, cellW)
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
        if (buttons.isEmpty() || cellLefts.isEmpty()) return iconColor
        val cellLeft = cellLefts[cellIndex]
        val cellRight = cellLeft + cellWidths[cellIndex]

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

    /**
     * Draws a text label centered at ([cx], [cy]), ellipsizing if it exceeds [cellWidth].
     */
    private fun drawText(
            canvas: Canvas,
            textResId: Int,
            cx: Float,
            cy: Float,
            color: Int,
            cellWidth: Float,
    ) {
        val text = context.getString(textResId)
        // Leave equal padding on both sides from the cell edge.
        val maxTextWidth = (cellWidth - dp(8f)).coerceAtLeast(0f)
        val displayText = TextUtils.ellipsize(text, textPaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
        textPaint.color = color
        val metrics = textPaint.fontMetrics
        val textY = cy - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(displayText, cx, textY, textPaint)
    }

    /**
     * Draws an icon and a text label side-by-side, centered within the cell.
     * The text is ellipsized if the combined content would exceed [cellWidth].
     */
    private fun drawIconAndText(
            canvas: Canvas,
            drawable: Drawable,
            textResId: Int,
            cx: Float,
            cy: Float,
            tint: Int,
            cellWidth: Float,
    ) {
        val spacing = dp(4f)
        // Space available for the text after the icon, spacing, and side padding.
        val availableForText = (cellWidth - iconSize - spacing - dp(8f)).coerceAtLeast(0f)
        val rawText = context.getString(textResId)
        val displayText = TextUtils.ellipsize(rawText, textPaint, availableForText, TextUtils.TruncateAt.END).toString()
        val textWidth = textPaint.measureText(displayText)

        val totalContent = iconSize + spacing + textWidth
        val iconCx = cx - totalContent / 2f + iconSize / 2f
        val textCx = cx + totalContent / 2f - textWidth / 2f

        drawIcon(canvas, drawable, iconCx, cy, tint)

        textPaint.color = tint
        val metrics = textPaint.fontMetrics
        val textY = cy - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(displayText, textCx, textY, textPaint)
    }

    // -------------------------------------------------------------------------
    // Touch handling
    // -------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || buttons.isEmpty() || cellLefts.isEmpty()) return false
        if (event.action == MotionEvent.ACTION_UP) {
            // Determine which cell the touch lands in by walking the left-edge array.
            val touchX = event.x
            var touchedIndex = buttons.size - 1
            for (i in buttons.indices) {
                if (touchX < cellLefts[i] + cellWidths[i]) {
                    touchedIndex = i
                    break
                }
            }
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
        if (buttons.isEmpty() || outerRect.isEmpty || cellLefts.isEmpty()) return
        val fromLeft = animHighlightLeft
        val fromRight = animHighlightRight

        val toLeft = cellLefts[index] + highlightMargin
        val toRight = cellLefts[index] + cellWidths[index] - highlightMargin

        slideAnimator?.cancel()
        slideAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380L
            interpolator = DecelerateInterpolator(3F)
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                animHighlightLeft = lerp(fromLeft, toLeft, t)
                animHighlightRight = lerp(fromRight, toRight, t)
                invalidate()
            }
        }
        slideAnimator!!.start()
    }

    /** Instantly positions the highlight rectangle over the cell at [index] with no animation. */
    private fun snapHighlightToIndex(index: Int) {
        if (buttons.isEmpty() || outerRect.isEmpty || cellLefts.isEmpty()) return
        animHighlightLeft = cellLefts[index] + highlightMargin
        animHighlightRight = cellLefts[index] + cellWidths[index] - highlightMargin
    }

    // -------------------------------------------------------------------------
    // Squiggly highlight path
    // -------------------------------------------------------------------------

    /**
     * Builds the highlight shape into [highlightPath] as a plain rounded rectangle.
     */
    private fun buildHighlightPath(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
    ) {
        highlightPath.reset()
        val r = (containerCornerRadius - highlightMargin).coerceAtLeast(dp(2f))
        highlightPath.addRoundRect(RectF(left, top, right, bottom), r, r, Path.Direction.CW)
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
        iconColor = theme.textViewTheme.primaryTextColor
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
        /** Total horizontal padding reserved per cell (split equally on left and right). */
        private const val MIN_CELL_H_PADDING_DP = 22f

        data class Button(
                val textResId: Int? = null,
                val iconResId: Int? = null,
        )
    }

    /** [MIN_CELL_H_PADDING_DP] converted to pixels via the current display density. */
    private val MIN_CELL_H_PADDING: Float get() = dp(MIN_CELL_H_PADDING_DP)
}