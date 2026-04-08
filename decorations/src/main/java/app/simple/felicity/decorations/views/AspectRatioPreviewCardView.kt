package app.simple.felicity.decorations.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import app.simple.felicity.decorations.corners.DynamicCornerMaterialCardView

/**
 * A specialized card view used exclusively for UI-selection preview pages.
 *
 * Hardcodes a portrait 9:21 aspect ratio (width : height) by overriding [onMeasure],
 * eliminating all runtime display-metrics calculations. The card fits itself inside
 * whatever space is available from the parent: if filling the width would exceed the
 * available height, it constrains to the height instead.
 *
 * All touch events are intercepted and consumed at this view level so that the
 * preview fragment hosted inside cannot receive any user interaction while still
 * allowing ViewPager2 to process horizontal swipe gestures through its own
 * touch-intercept mechanism.
 *
 * @author Hamza417
 */
class AspectRatioPreviewCardView : DynamicCornerMaterialCardView {

    constructor(context: Context, attrs: AttributeSet?) :
            super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec)

        /**
         * Calculate dimensions for a fixed portrait 9:21 ratio.
         * If the height required for full width exceeds available height,
         * constrain by height and derive width from it instead.
         */
        val heightForFullWidth = (parentWidth * 21f / 9f).toInt()

        val finalWidth: Int
        val finalHeight: Int

        if (heightForFullWidth <= parentHeight) {
            finalWidth = parentWidth
            finalHeight = heightForFullWidth
        } else {
            finalHeight = parentHeight
            finalWidth = (parentHeight * 9f / 21f).toInt()
        }

        super.onMeasure(
                MeasureSpec.makeMeasureSpec(finalWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(finalHeight, MeasureSpec.EXACTLY)
        )
    }

    /**
     * Intercepts all touch events before they reach the hosted child fragment,
     * keeping the preview fully non-interactive.
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    /**
     * Consumes every touch event so nothing leaks to underlying views behind the card.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        performClick()
        return true
    }

    /**
     * Required companion to [onTouchEvent] for accessibility compliance.
     * No-op because this view intentionally blocks all interaction.
     */
    override fun performClick(): Boolean = super.performClick()
}

