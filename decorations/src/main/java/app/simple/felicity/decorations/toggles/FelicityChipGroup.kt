@file:Suppress("PrivatePropertyName")

package app.simple.felicity.decorations.toggles

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatTextView
import app.simple.felicity.decorations.ripple.FelicityRippleDrawable
import app.simple.felicity.decorations.toggles.FelicityChipGroup.Companion.SELECTION_MULTI
import app.simple.felicity.decorations.toggles.FelicityChipGroup.Companion.SELECTION_SINGLE
import app.simple.felicity.decorations.typeface.TypeFace
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.themes.Theme
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

/**
 * A horizontally scrollable chip group that renders a list of [ChipButton] objects as
 * individual pill-shaped text chips. Each chip animates smoothly between a highlighted
 * (selected) and an unhighlighted (unselected) visual state using the active theme's
 * accent and highlight colors, following the same visual language as HighlightTextView.
 *
 * Two selection modes are supported, toggled via [selectionMode]:
 * - [SELECTION_SINGLE]: only one chip may be active at a time (default).
 * - [SELECTION_MULTI]: any number of chips may be active simultaneously.
 *
 * Chips are supplied programmatically via [setChips]. Per-chip selection callbacks are
 * provided through each [ChipButton]'s [ChipButton.onSelectionChanged] field, while a
 * group-level callback is available via [setOnSelectionChangedListener].
 *
 * Colors are sourced entirely from [ThemeManager]: the accent color fills an active chip,
 * and the theme highlight color fills an inactive chip background.
 *
 * @author Hamza417
 */
class FelicityChipGroup @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
) : HorizontalScrollView(context, attrs, defStyleAttr), ThemeChangedListener {

    companion object {
        /** Only one chip can be selected at a time. This is the default selection mode. */
        const val SELECTION_SINGLE = 0

        /** Multiple chips can be selected simultaneously. */
        const val SELECTION_MULTI = 1
    }

    /**
     * The selection mode governing this chip group. Switching to [SELECTION_SINGLE] after
     * chips are set retains only the lowest-index chip that was selected and deselects all others.
     */
    var selectionMode: Int = SELECTION_SINGLE
        set(value) {
            field = value
            if (value == SELECTION_SINGLE) {
                val first = selectedIndices.minOrNull() ?: return
                selectedIndices.retainAll(setOf(first))
                refreshAllChipStates(animate = false)
            }
        }

    /** Gap in pixels between adjacent chip views. */
    var chipSpacing: Float = dp(8f)
        set(value) {
            field = value
            updateChipMargins()
        }

    /**
     * Whether to save and restore the selected chip indices across configuration changes. If false,
     * the chip group will always start with no chips selected after a configuration change. Default is
     * false.
     */
    var shouldRestoreStates = false

    @ColorInt
    private var accentColor: Int = if (isInEditMode) {
        0xFF6200EE.toInt()
    } else {
        ThemeManager.accent.primaryAccentColor
    }

    @ColorInt
    private var idleColor: Int = if (isInEditMode) {
        Color.LTGRAY
    } else {
        ThemeManager.theme.viewGroupTheme.highlightColor
    }

    @ColorInt
    private var primaryTextColor: Int = if (isInEditMode) {
        0xFF212121.toInt()
    } else {
        ThemeManager.theme.textViewTheme.primaryTextColor
    }

    /** Text color rendered on top of the accent-filled selected chip. */
    @ColorInt
    private var selectedContentColor: Int = Color.WHITE

    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }

    private val selectedIndices = mutableSetOf<Int>()
    private val chipViews = mutableListOf<ChipItemView>()
    private var chips: List<ChipButton> = emptyList()
    private var onSelectionChangedListener: ((Set<ChipButton>) -> Unit)? = null

    init {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
        addView(container)
        if (!isInEditMode) {
            ThemeManager.addListener(this)
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Replaces the current list of chips and resets all selection state. Any previously
     * selected chips are deselected without firing callbacks.
     *
     * @param chips The ordered list of [ChipButton] items to display.
     */
    fun setChips(chips: List<ChipButton>) {
        this.chips = chips
        selectedIndices.clear()
        rebuildChips()
    }

    /**
     * Registers a callback that is invoked whenever the selection state of any chip changes.
     *
     * @param listener A lambda that receives the full set of currently selected [ChipButton]
     *                 objects after each change, allowing callers to identify chips by their
     *                 [ChipButton.tag] or [ChipButton.textResId] rather than a fragile positional
     *                 index.
     */
    fun setOnSelectionChangedListener(listener: (Set<ChipButton>) -> Unit) {
        onSelectionChangedListener = listener
    }

    /**
     * Programmatically selects the chip at [index].
     *
     * @param index          Zero-based index of the chip to select.
     * @param animate        Whether to animate the color transition.
     * @param notifyListener Whether to fire per-chip and group-level callbacks.
     */
    fun setSelectedIndex(index: Int, animate: Boolean = true, notifyListener: Boolean = false) {
        if (index < 0 || index >= chips.size) return
        when (selectionMode) {
            SELECTION_SINGLE -> {
                val previous = selectedIndices.firstOrNull()
                if (previous == index) return
                selectedIndices.clear()
                selectedIndices.add(index)
                previous?.let { chipViews.getOrNull(it)?.updateSelectionState(false, animate) }
                chipViews.getOrNull(index)?.updateSelectionState(true, animate)
                if (notifyListener) {
                    previous?.let { chips.getOrNull(it)?.onSelectionChanged?.invoke(false) }
                    chips.getOrNull(index)?.onSelectionChanged?.invoke(true)
                    onSelectionChangedListener?.invoke(getSelectedChips())
                }
            }

            SELECTION_MULTI -> {
                selectedIndices.add(index)
                chipViews.getOrNull(index)?.updateSelectionState(true, animate)
                if (notifyListener) {
                    chips.getOrNull(index)?.onSelectionChanged?.invoke(true)
                    onSelectionChangedListener?.invoke(getSelectedChips())
                }
            }
        }
    }

    /**
     * Returns a copy of the set of currently selected chip indices.
     */
    fun getSelectedIndices(): Set<Int> = selectedIndices.toSet()

    /**
     * Returns the set of currently selected [ChipButton] objects.
     */
    fun getSelectedChips(): Set<ChipButton> = selectedIndices.mapNotNull { chips.getOrNull(it) }.toSet()

    /**
     * Returns the selected chip index in single-selection mode, or -1 if no chip is selected.
     */
    fun getSelectedIndex(): Int = selectedIndices.firstOrNull() ?: -1

    /**
     * Programmatically selects the chip whose [ChipButton.tag] equals [tag].
     * If no chip matches, the call is a no-op.
     *
     * @param tag            The tag value to look up.
     * @param animate        Whether to animate the color transition.
     * @param notifyListener Whether to fire per-chip and group-level callbacks.
     */
    fun setSelectedByTag(tag: Any?, animate: Boolean = false, notifyListener: Boolean = false) {
        val index = chips.indexOfFirst { it.tag == tag }
        if (index >= 0) setSelectedIndex(index, animate, notifyListener)
    }

    // -------------------------------------------------------------------------
    // Internal chip management
    // -------------------------------------------------------------------------

    private fun rebuildChips() {
        container.removeAllViews()
        chipViews.clear()
        val cornerRadius = if (isInEditMode) {
            24f
        } else {
            AppearancePreferences.getCornerRadius().coerceIn(4f, 80f)
        }
        isHorizontalFadingEdgeEnabled = chips.size > 3
        setFadingEdgeLength(dp(24f).toInt())
        clipToPadding = false
        clipChildren = false
        chips.forEachIndexed { index, chip ->
            val chipView = ChipItemView(context, index, chip, cornerRadius)
            val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            if (index > 0) lp.marginStart = chipSpacing.toInt()
            chipView.layoutParams = lp
            container.addView(chipView)
            chipViews.add(chipView)
        }
    }

    private fun refreshAllChipStates(animate: Boolean) {
        chipViews.forEachIndexed { idx, view ->
            view.updateSelectionState(idx in selectedIndices, animate)
        }
    }

    private fun updateChipMargins() {
        chipViews.forEachIndexed { index, view ->
            val lp = view.layoutParams as? LinearLayout.LayoutParams ?: return@forEachIndexed
            lp.marginStart = if (index > 0) chipSpacing.toInt() else 0
            view.requestLayout()
        }
    }

    private fun propagateThemeColors() {
        chipViews.forEach {
            it.onThemeColorsUpdated(accentColor, idleColor, primaryTextColor, selectedContentColor)
        }
    }

    // -------------------------------------------------------------------------
    // ThemeChangedListener
    // -------------------------------------------------------------------------

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        idleColor = theme.viewGroupTheme.highlightColor
        primaryTextColor = theme.textViewTheme.primaryTextColor
        propagateThemeColors()
    }

    override fun onAccentChanged(accent: Accent) {
        accentColor = accent.primaryAccentColor
        propagateThemeColors()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (!isInEditMode) ThemeManager.removeListener(this)
    }

    // -------------------------------------------------------------------------
    // State save / restore
    // -------------------------------------------------------------------------

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        return SavedState(superState).also {
            it.selectedIndices = selectedIndices.toIntArray()
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            if (shouldRestoreStates) {
                selectedIndices.clear()
                selectedIndices.addAll(state.selectedIndices.toList())
                post { refreshAllChipStates(animate = false) }
            }
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    // -------------------------------------------------------------------------
    // Inner chip item view
    // -------------------------------------------------------------------------

    /**
     * A single pill-shaped text chip rendered inside [FelicityChipGroup]. It owns a
     * [MaterialShapeDrawable] background whose fill color transitions smoothly between the
     * accent color (selected) and the theme highlight color (unselected). The text color
     * transitions in parallel between [Color.WHITE] (selected) and the primary text color
     * (unselected).
     */
    private inner class ChipItemView(
            context: Context,
            private val index: Int,
            private val chip: ChipButton,
            private val cornerRadius: Float,
    ) : AppCompatTextView(context) {

        private val backgroundShape = MaterialShapeDrawable(
                ShapeAppearanceModel()
                    .toBuilder()
                    .setAllCorners(CornerFamily.ROUNDED, cornerRadius)
                    .build()
        )

        private var isChipSelected: Boolean = false
        private var colorAnimator: ValueAnimator? = null

        /** Current background fill color, kept in sync with [backgroundShape]. */
        private var currentFillColor: Int = idleColor

        /** Current text color, kept in sync with [setTextColor]. */
        private var currentTextColor: Int = primaryTextColor

        init {
            val hPad = dp(12f).toInt()
            val vPad = dp(6f).toInt()
            setPadding(hPad, vPad, hPad, vPad)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setText(chip.textResId)
            if (!isInEditMode) {
                typeface = TypeFace.getBoldTypeFace(context)
            }
            isClickable = true
            isFocusable = true
            backgroundShape.fillColor = ColorStateList.valueOf(idleColor)
            background = backgroundShape
            setTextColor(primaryTextColor)
            applyRippleForeground()
            setOnClickListener { handleClick() }
        }

        private fun handleClick() {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            when (selectionMode) {
                SELECTION_SINGLE -> {
                    if (isChipSelected) return
                    val previous = selectedIndices.firstOrNull()
                    selectedIndices.clear()
                    selectedIndices.add(index)
                    previous?.let {
                        chipViews.getOrNull(it)?.updateSelectionState(false, animate = true)
                        chips.getOrNull(it)?.onSelectionChanged?.invoke(false)
                    }
                    updateSelectionState(true, animate = true)
                    chip.onSelectionChanged?.invoke(true)
                    onSelectionChangedListener?.invoke(getSelectedChips())
                }

                SELECTION_MULTI -> {
                    val newState = !isChipSelected
                    if (newState) selectedIndices.add(index) else selectedIndices.remove(index)
                    updateSelectionState(newState, animate = true)
                    chip.onSelectionChanged?.invoke(newState)
                    onSelectionChangedListener?.invoke(getSelectedChips())
                }
            }
        }

        /**
         * Updates the chip's visual selection state, optionally animating the color transition.
         *
         * @param selected Whether this chip should appear selected.
         * @param animate  Whether to animate the fill and text color change.
         */
        fun updateSelectionState(selected: Boolean, animate: Boolean) {
            isChipSelected = selected
            val targetFill = if (selected) accentColor else this@FelicityChipGroup.idleColor
            val targetText = if (selected) selectedContentColor else primaryTextColor
            if (animate) {
                animateToColors(currentFillColor, targetFill, currentTextColor, targetText)
            } else {
                colorAnimator?.cancel()
                currentFillColor = targetFill
                currentTextColor = targetText
                backgroundShape.fillColor = ColorStateList.valueOf(targetFill)
                setTextColor(targetText)
            }
        }

        /**
         * Called by the parent group when theme or accent colors change. Immediately snaps
         * to the correct colors for the current selection state without animation and
         * refreshes the ripple foreground.
         *
         * @param accent    The updated accent color.
         * @param highlight The updated theme highlight color.
         * @param textColor The updated primary text color.
         * @param onAccent  The updated color for text rendered on the accent fill.
         */
        fun onThemeColorsUpdated(
                accent: Int,
                highlight: Int,
                textColor: Int,
                onAccent: Int,
        ) {
            colorAnimator?.cancel()
            currentFillColor = if (isChipSelected) accent else highlight
            currentTextColor = if (isChipSelected) onAccent else textColor
            backgroundShape.fillColor = ColorStateList.valueOf(currentFillColor)
            setTextColor(currentTextColor)
            applyRippleForeground()
        }

        private fun animateToColors(
                fromFill: Int,
                toFill: Int,
                fromText: Int,
                toText: Int,
        ) {
            colorAnimator?.cancel()
            colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 220L
                addUpdateListener { anim ->
                    val t = anim.animatedValue as Float
                    currentFillColor = blendColors(fromFill, toFill, t)
                    currentTextColor = blendColors(fromText, toText, t)
                    backgroundShape.fillColor = ColorStateList.valueOf(currentFillColor)
                    setTextColor(currentTextColor)
                }
                start()
            }
        }

        private fun applyRippleForeground() {
            val ripple = FelicityRippleDrawable(accentColor)
            ripple.setCornerRadius(cornerRadius)
            ripple.setStartColor(idleColor)
            foreground = ripple
        }

        /**
         * Blends two ARGB colors by linear interpolation at factor [t] (0 = [a], 1 = [b]).
         */
        @ColorInt
        private fun blendColors(@ColorInt a: Int, @ColorInt b: Int, t: Float): Int {
            val r = (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt()
            val g = (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt()
            val bl = (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
            return Color.rgb(r, g, bl)
        }

        private fun dp(v: Float): Float = v * resources.displayMetrics.density
    }

    // -------------------------------------------------------------------------
    // Saved state
    // -------------------------------------------------------------------------

    private class SavedState : BaseSavedState {
        var selectedIndices: IntArray = intArrayOf()

        constructor(superState: Parcelable?) : super(superState)

        private constructor(parcel: Parcel) : super(parcel) {
            selectedIndices = parcel.createIntArray() ?: intArrayOf()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeIntArray(selectedIndices)
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(source: Parcel): SavedState = SavedState(source)
            override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
        }
    }

    /**
     * Represents a single chip item within a [FelicityChipGroup].
     *
     * @param textResId          String resource ID for the chip label.
     * @param tag                Optional opaque value attached to this chip. Use it to associate
     *                           a domain object (e.g., an enum constant) with the chip so that
     *                           [setOnSelectionChangedListener] callbacks can identify which
     *                           chip is selected without relying on positional indices.
     * @param onSelectionChanged Optional callback invoked with the new boolean selection state
     *                           whenever this chip is selected or deselected by user interaction
     *                           or a programmatic call with notifyListener set to true.
     */
    data class ChipButton(
            @StringRes val textResId: Int,
            val tag: Any? = null,
            val onSelectionChanged: ((Boolean) -> Unit)? = null,
    )
}



