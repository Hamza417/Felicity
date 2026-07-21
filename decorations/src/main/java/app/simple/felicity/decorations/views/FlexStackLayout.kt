package app.simple.felicity.decorations.views

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.core.content.withStyledAttributes
import androidx.core.view.isGone

/**
 * A custom [ViewGroup] that seamlessly toggles between two layout modes:
 * 1. Overlapping (FrameLayout behavior): Children are stacked on top of each other and centered vertically.
 * 2. Stacking (LinearLayout behavior): Children are arranged vertically sequentially, supporting [FlexLayoutParams.weight].
 *
 * It fully respects child margins and standard Android attributes in both modes.
 */
class FlexStackLayout @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    /**
     * Toggles the layout configuration.
     * - `true`: Children overlap and center vertically.
     * - `false`: Children stack vertically.
     *
     * Modifying this value will automatically trigger a layout recalculation.
     */
    var isOverlapping: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (isOverlapping) {
            measureFrameLayout(widthMeasureSpec, heightMeasureSpec)
        } else {
            measureLinearLayout(widthMeasureSpec, heightMeasureSpec)
        }
    }

    /**
     * Measures children for the overlapping (FrameLayout) mode.
     * The container's size is determined by the widest and tallest children,
     * factoring in their respective margins and the container's padding.
     */
    private fun measureFrameLayout(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var totalHeight = 0
        var maxWidth = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.isGone) continue

            val lp = child.layoutParams as FlexLayoutParams

            // measureChildWithMargins automatically subtracts parent padding & child margins
            // from the available spec, ensuring match_parent works perfectly.
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)

            maxWidth = maxOf(maxWidth, child.measuredWidth + lp.leftMargin + lp.rightMargin)
            totalHeight = maxOf(totalHeight, child.measuredHeight + lp.topMargin + lp.bottomMargin)
        }

        val finalWidth = resolveSize(maxWidth + paddingLeft + paddingRight, widthMeasureSpec)
        val finalHeight = resolveSize(totalHeight + paddingTop + paddingBottom, heightMeasureSpec)
        setMeasuredDimension(finalWidth, finalHeight)
    }

    /**
     * Measures children for the vertically stacked (LinearLayout) mode using a two-pass system.
     *
     * Pass 1: Measures all children with a fixed size or `wrap_content`, and tallies total required height.
     * Pass 2: Distributes any remaining vertical space to children that have a layout weight > 0.
     */
    private fun measureLinearLayout(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var totalHeight = paddingTop + paddingBottom
        var maxWidth = 0
        var totalWeight = 0f

        // Pass 1: Measure non-weighted children
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.isGone) continue

            val lp = child.layoutParams as FlexLayoutParams
            if (lp.weight > 0f) {
                totalWeight += lp.weight
            } else {
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, totalHeight)

                totalHeight += child.measuredHeight + lp.topMargin + lp.bottomMargin
                maxWidth = maxOf(maxWidth, child.measuredWidth + lp.leftMargin + lp.rightMargin)
            }
        }

        // Pass 2: Distribute remaining space to weighted children
        if (totalWeight > 0f) {
            val heightSize = MeasureSpec.getSize(heightMeasureSpec)
            val remainingHeight = maxOf(0, heightSize - totalHeight)

            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child.isGone) continue

                val lp = child.layoutParams as FlexLayoutParams
                if (lp.weight > 0f) {
                    val weightHeightSpace = ((lp.weight / totalWeight) * remainingHeight).toInt()

                    // Subtract top and bottom margins so the view doesn't bleed out of its weighted boundary
                    val adjustedChildHeight = maxOf(0, weightHeightSpace - lp.topMargin - lp.bottomMargin)

                    val childHeightSpec = MeasureSpec.makeMeasureSpec(adjustedChildHeight, MeasureSpec.EXACTLY)
                    val childWidthSpec = getChildMeasureSpec(
                            widthMeasureSpec,
                            paddingLeft + paddingRight + lp.leftMargin + lp.rightMargin,
                            lp.width
                    )

                    child.measure(childWidthSpec, childHeightSpec)

                    totalHeight += child.measuredHeight + lp.topMargin + lp.bottomMargin
                    maxWidth = maxOf(maxWidth, child.measuredWidth + lp.leftMargin + lp.rightMargin)
                }
            }
        }

        val finalWidth = resolveSize(maxWidth + paddingLeft + paddingRight, widthMeasureSpec)
        val finalHeight = resolveSize(totalHeight, heightMeasureSpec)
        setMeasuredDimension(finalWidth, finalHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        var currentY = paddingTop
        val availableHeight = (bottom - top) - paddingTop - paddingBottom

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.isGone) continue

            val lp = child.layoutParams as FlexLayoutParams
            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            // X coordinate always respects parent padding + child's left margin
            val childLeft = paddingLeft + lp.leftMargin

            if (isOverlapping) {
                // FrameLayout Center Vertical Mode: Factor in margins before calculating true center
                val spaceForChild = availableHeight - lp.topMargin - lp.bottomMargin
                val verticalOffset = maxOf(0, (spaceForChild - childHeight) / 2)

                val childTop = paddingTop + lp.topMargin + verticalOffset
                child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight)
            } else {
                // LinearLayout Vertical Mode: Stack sequentially, keeping track of cumulative Y position
                val childTop = currentY + lp.topMargin
                child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight)
                currentY = childTop + childHeight + lp.bottomMargin
            }
        }
    }

    // --- Hardened LayoutParams Overrides ---
    // These overrides are strictly required to ensure margins and weights are not
    // stripped away when views are added dynamically or inflated from XML.

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        return FlexLayoutParams(context, attrs)
    }

    override fun generateDefaultLayoutParams(): LayoutParams {
        return FlexLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    override fun generateLayoutParams(p: LayoutParams?): LayoutParams {
        return when (p) {
            is FlexLayoutParams -> FlexLayoutParams(p)
            is MarginLayoutParams -> FlexLayoutParams(p)
            null -> generateDefaultLayoutParams()
            else -> FlexLayoutParams(p)
        }
    }

    override fun checkLayoutParams(p: LayoutParams?): Boolean {
        return p is FlexLayoutParams
    }

    /**
     * Custom [MarginLayoutParams] that extracts standard Android `layout_weight` attributes,
     * allowing children to dynamically scale in the stacked layout mode.
     */
    class FlexLayoutParams : MarginLayoutParams {

        /**
         * Defines how much of the remaining empty space this view should consume
         * in the vertical stacking mode. Evaluated identically to standard LinearLayout.
         */
        var weight: Float = 0f

        constructor(c: Context, attrs: AttributeSet?) : super(c, attrs) {
            if (attrs != null) {
                c.withStyledAttributes(attrs, intArrayOf(android.R.attr.layout_weight)) {
                    weight = getFloat(0, 0f)
                }
            }
        }

        constructor(width: Int, height: Int) : super(width, height)

        // Critical fallback constructor: If missing, adding views programmatically drops all margins
        constructor(source: MarginLayoutParams) : super(source)

        constructor(source: LayoutParams) : super(source)

        constructor(source: FlexLayoutParams) : super(source) {
            this.weight = source.weight
        }
    }
}