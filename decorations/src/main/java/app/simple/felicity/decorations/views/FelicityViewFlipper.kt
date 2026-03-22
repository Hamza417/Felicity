package app.simple.felicity.decorations.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs

/**
 * A lightweight horizontal [ViewGroup] that supports smooth swipe gestures to switch between
 * its child screens, similar to a single-page-visible ViewPager but without any adapter
 * overhead. Children are laid out side-by-side; the visible one is determined by
 * [displayedChild]. Swiping left advances to the next child while swiping right goes back
 * to the previous one.
 *
 * Vertical drag gestures are intentionally NOT consumed so that a parent view (e.g., a
 * swipe-down-to-close container) can still handle them.
 *
 * Use [setOnScreenChangedListener] to be notified whenever the displayed child changes so
 * that an external indicator (e.g., a button group) can stay in sync.
 *
 * @author Hamza417
 */
class FelicityViewFlipper @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    private var currentIndex = 0
    private var dragOffset = 0f
    private var isDragging = false
    private var isHorizontalDrag = false
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f

    private var onScreenChangedListener: ((index: Int) -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity

    private var velocityTracker: VelocityTracker? = null

    /**
     * Fraction of the page width that a drag must exceed in order to commit to a page
     * change when the finger lifts without a qualifying fling velocity.
     */
    private val snapThreshold = 0.35f

    /**
     * Duration in milliseconds for the settle animation after a drag or fling.
     */
    private val settleDurationMs = 280L

    private val decelerateInterpolator = DecelerateInterpolator(1.5f)

    /**
     * The index of the currently visible child view. Setting this programmatically
     * animates the transition to the target screen.
     */
    var displayedChild: Int
        get() = currentIndex
        set(value) {
            val target = value.coerceIn(0, (childCount - 1).coerceAtLeast(0))
            if (target != currentIndex) {
                commitPageChange(target)
            }
        }

    /**
     * Registers [listener] to be called whenever the displayed child changes, either by
     * user swipe or a programmatic [displayedChild] assignment.
     *
     * @param listener Lambda receiving the new child index.
     */
    fun setOnScreenChangedListener(listener: (index: Int) -> Unit) {
        onScreenChangedListener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)

        val childWidthSpec = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
        val childHeightSpec = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) {
            getChildAt(i).measure(childWidthSpec, childHeightSpec)
        }
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val pageWidth = r - l
        val pageHeight = b - t
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.layout(0, 0, pageWidth, pageHeight)
            child.translationX = (i - currentIndex) * pageWidth.toFloat() + dragOffset
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                lastX = ev.x
                isHorizontalDrag = false
                isDragging = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(ev)
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(ev)
                val dx = abs(ev.x - downX)
                val dy = abs(ev.y - downY)
                if (!isHorizontalDrag && dx > touchSlop) {
                    if (dx > dy) {
                        isHorizontalDrag = true
                        isDragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.recycle()
                velocityTracker = null
                isHorizontalDrag = false
                isDragging = false
            }
        }
        return false
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        velocityTracker?.addMovement(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                lastX = ev.x
                isHorizontalDrag = false
                isDragging = true
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain()
                }
                velocityTracker?.addMovement(ev)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - lastX
                val totalDx = abs(ev.x - downX)
                val totalDy = abs(ev.y - downY)

                if (!isHorizontalDrag) {
                    if (totalDx > touchSlop && totalDx > totalDy) {
                        isHorizontalDrag = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    } else if (totalDy > touchSlop) {
                        // Clearly vertical — let the parent handle it.
                        isDragging = false
                        return false
                    }
                }

                if (isHorizontalDrag) {
                    dragOffset += dx
                    applyTranslations()
                }
                lastX = ev.x
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isHorizontalDrag) {
                    velocityTracker?.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
                    val velX = velocityTracker?.xVelocity ?: 0f
                    velocityTracker?.recycle()
                    velocityTracker = null

                    val pageWidth = width.toFloat()
                    val isFling = abs(velX) >= minFlingVelocity
                    val nextIndex = when {
                        isFling && velX < 0 && currentIndex < childCount - 1 -> currentIndex + 1
                        isFling && velX > 0 && currentIndex > 0 -> currentIndex - 1
                        dragOffset < -(pageWidth * snapThreshold) && currentIndex < childCount - 1 -> currentIndex + 1
                        dragOffset > (pageWidth * snapThreshold) && currentIndex > 0 -> currentIndex - 1
                        else -> currentIndex
                    }

                    commitPageChange(nextIndex)
                } else {
                    performClick()
                }
                isDragging = false
                isHorizontalDrag = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    /**
     * Advances the flipper to the next child if one exists.
     */
    @Suppress("unused")
    fun showNext() {
        if (currentIndex < childCount - 1) {
            displayedChild = currentIndex + 1
        }
    }

    /**
     * Moves the flipper back to the previous child if one exists.
     */
    @Suppress("unused")
    fun showPrevious() {
        if (currentIndex > 0) {
            displayedChild = currentIndex - 1
        }
    }

    /**
     * Commits a page change to [targetIndex], animating all children from their current
     * (potentially mid-drag) [translationX] to their final resting positions.
     */
    private fun commitPageChange(targetIndex: Int) {
        val oldIndex = currentIndex
        currentIndex = targetIndex.coerceIn(0, (childCount - 1).coerceAtLeast(0))
        dragOffset = 0f
        animateToCurrentIndex()
        if (currentIndex != oldIndex) {
            onScreenChangedListener?.invoke(currentIndex)
        }
    }

    /**
     * Translates all children without animation to reflect [dragOffset] and [currentIndex].
     * Called continuously during a drag gesture.
     */
    private fun applyTranslations() {
        val pageWidth = width.toFloat()
        if (pageWidth == 0f) return
        for (i in 0 until childCount) {
            getChildAt(i).translationX = (i - currentIndex) * pageWidth + dragOffset
        }
    }

    /**
     * Animates all children to their final positions for [currentIndex] with a smooth
     * decelerate interpolation. Any in-progress animator on each child is replaced.
     */
    private fun animateToCurrentIndex() {
        val pageWidth = width.toFloat()
        if (pageWidth == 0f) {
            applyTranslations()
            return
        }
        for (i in 0 until childCount) {
            val targetX = (i - currentIndex) * pageWidth
            getChildAt(i).animate()
                .translationX(targetX)
                .setDuration(settleDurationMs)
                .setInterpolator(decelerateInterpolator)
                .start()
        }
    }
}

