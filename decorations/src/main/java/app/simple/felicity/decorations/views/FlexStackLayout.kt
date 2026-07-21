package app.simple.felicity.decorations.views

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.core.view.isGone

class FlexStackLayout @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    // The boolean flag that toggles the behavior
    var isOverlapping: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                // Forces the layout to recalculate sizes and positions
                requestLayout()
            }
        }

    init {
        clipChildren = false
        clipToPadding = false
        clipToOutline = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var totalHeight = 0
        var maxWidth = 0

        // Measure all visible children
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.isGone) continue

            measureChild(child, widthMeasureSpec, heightMeasureSpec)

            maxWidth = maxOf(maxWidth, child.measuredWidth)

            if (isOverlapping) {
                // FrameLayout mode: container height is the tallest child
                totalHeight = maxOf(totalHeight, child.measuredHeight)
            } else {
                // LinearLayout mode: container height is the sum of all children
                totalHeight += child.measuredHeight
            }
        }

        // Apply any padding set on this ViewGroup
        maxWidth += paddingLeft + paddingRight
        totalHeight += paddingTop + paddingBottom

        setMeasuredDimension(
                resolveSize(maxWidth, widthMeasureSpec),
                resolveSize(totalHeight, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        var currentY = paddingTop
        val currentX = paddingLeft

        // Calculate the total height available inside the container (minus padding)
        val availableHeight = (bottom - top) - paddingTop - paddingBottom

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.isGone) continue

            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            if (isOverlapping) {
                // FrameLayout mode: Center vertically
                val verticalCenterOffset = paddingTop + (availableHeight - childHeight) / 2

                child.layout(
                        currentX,
                        verticalCenterOffset,
                        currentX + childWidth,
                        verticalCenterOffset + childHeight
                )
            } else {
                // LinearLayout mode: Stack children vertically
                child.layout(currentX, currentY, currentX + childWidth, currentY + childHeight)
                currentY += childHeight
            }
        }
    }
}