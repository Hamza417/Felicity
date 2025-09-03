package app.simple.felicity.decorations.fastscroll

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.decorations.typeface.TypeFace
import app.simple.felicity.theme.managers.ThemeManager
import java.lang.ref.WeakReference
import kotlin.math.ceil

/**
 * Windows-like jump navigation overlay:
 *  - Hidden by default; call show()/hide().
 *  - Displays provided positions in a grid (label + background).
 *  - Clicking a position invokes listener; caller handles RecyclerView jump manually.
 *  - Only responsibility with RecyclerView: overlay addition via attachTo().
 */
class SectionedFastScroller @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface VisibilityListener {
        fun onShowStart() {}
        fun onShowEnd() {}
        fun onHideStart() {}
        fun onHideEnd() {}
    }

    data class Position(val label: String, val index: Int)

    private var recyclerRef: WeakReference<RecyclerView>? = null

    private var positions: List<Position> = emptyList()
    private var positionRects: MutableList<RectF> = mutableListOf()

    // Grid configuration
    private var columns = 5 // current active columns (set dynamically)
    private var portraitColumns = 5
    private var landscapeColumns = 10 // landscape wider grid (8-12 range); adjustable via setter
    private var rows = 0
    private val cellSpacing = dp(28f) // larger spacing
    private val bigMargin = dp(72f) // huge margin on all sides
    private val cellPaddingV = dp(8f)

    // Visual styles
    private val overlayColor = ThemeManager.theme.viewGroupTheme.backgroundColor // opaque background
    private val textColor = ThemeManager.theme.textViewTheme.primaryTextColor
    private val pressedTextColor = ThemeManager.accent.primaryAccentColor

    // Removed button backgrounds – flat text only

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = sp(22f) // larger text
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.05f
        typeface = TypeFace.getBoldTypeFace(context)
    }

    // Animation config
    private var animationDurationShow = 300L
    private var animationDurationHide = 260L
    private val inScaleStart = 1.52f
    private val outScaleEnd = 1.52f

    private var fadeScaleAnimator: ValueAnimator? = null

    private var isShowing = false
    private var isLaidOutOnce = false

    private var pressedIndex = -1
    private var onPositionSelected: ((Position) -> Unit)? = null

    private var lastOrientation = -1

    private var visibilityListener: VisibilityListener? = null

    fun setVisibilityListener(listener: VisibilityListener?) {
        visibilityListener = listener
    }

    init {
        alpha = 0f
        scaleX = 0.92f
        scaleY = 0.92f
        visibility = GONE
        isClickable = true
        isFocusable = true
        setBackgroundColor(overlayColor) // opaque backdrop
        setWillNotDraw(false)
    }

    /** Attach overlay to RecyclerView's parent so it can draw above list. */
    fun attachTo(recyclerView: RecyclerView) {
        recyclerRef = WeakReference(recyclerView)
        val parent = recyclerView.parent
        if (parent is ViewGroup && parent.indexOfChild(this) == -1) {
            parent.addView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    fun setPositions(list: List<Position>) {
        positions = list
        updateColumnsForOrientation()
        rows = if (positions.isEmpty()) 0 else ceil(positions.size / columns.toFloat()).toInt()
        computeRects(width, height)
        invalidate()
    }

    fun setOnPositionSelectedListener(listener: (Position) -> Unit) {
        onPositionSelected = listener
    }

    fun show(animated: Boolean = true) {
        if (isShowing) return
        visibilityListener?.onShowStart()
        isShowing = true
        visibility = VISIBLE
        // Prep starting state for in animation
        if (animated) {
            alpha = 0f
            scaleX = inScaleStart
            scaleY = inScaleStart
        } else {
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
        }
        animateIn(animated)
    }

    fun hide(animated: Boolean = true) {
        if (!isShowing && fadeScaleAnimator == null) return // Already hidden and no running anim

        visibilityListener?.onHideStart()
        animateOut(animated)

        // Defer flipping isShowing until the animation actually ends so content can animate out.
        // If not animated, animateOut will set isShowing=false immediately.
    }

    fun toggle() = if (isShowing) hide() else show()

    private fun animateIn(animated: Boolean) {
        fadeScaleAnimator?.cancel()
        if (!animated) {
            alpha = 1f; scaleX = 1f; scaleY = 1f
            visibilityListener?.onShowEnd()
            return
        }
        var wasCanceled = false
        fadeScaleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animationDurationShow
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                val f = va.animatedFraction
                alpha = f
                val s = inScaleStart + (1f - inScaleStart) * f // shrink to 1f
                scaleX = s; scaleY = s
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    wasCanceled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    fadeScaleAnimator = null
                    if (!wasCanceled && isShowing) {
                        visibilityListener?.onShowEnd()
                    }
                }
            })
            start()
        }
    }

    private fun animateOut(animated: Boolean) {
        fadeScaleAnimator?.cancel()
        if (!animated) {
            // Immediately hide and reset state
            isShowing = false
            visibilityListener?.onHideEnd()
            return
        }
        // Ensure starting baseline (might be mid-animation)
        if (scaleX != 1f && scaleX < 1f) {
            scaleX = 1f; scaleY = 1f
        }
        val startAlpha = alpha
        val startScale = scaleX
        fadeScaleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animationDurationHide
            interpolator = AccelerateInterpolator()
            addUpdateListener { va ->
                val f = va.animatedFraction
                alpha = startAlpha * (1f - f)
                val s = startScale + (outScaleEnd - startScale) * f // grow outward
                scaleX = s; scaleY = s
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var wasCanceled = false
                override fun onAnimationCancel(animation: Animator) {
                    wasCanceled = true
                }
                override fun onAnimationEnd(animation: Animator) {
                    if (!wasCanceled) {
                        // Now mark hidden and reset visibility/state
                        isShowing = false
                        visibility = GONE
                        alpha = 0f
                        scaleX = 1f
                        scaleY = 1f
                        visibilityListener?.onHideEnd()
                    }

                    fadeScaleAnimator = null
                }
            })
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val orientation = resources.configuration.orientation
        if (orientation != lastOrientation) {
            lastOrientation = orientation
            updateColumnsForOrientation()
            rows = if (positions.isEmpty()) 0 else ceil(positions.size / columns.toFloat()).toInt()
        }
        isLaidOutOnce = true
        computeRects(w, h)
    }

    private fun computeRects(w: Int, h: Int) {
        positionRects.clear()
        if (!isLaidOutOnce || positions.isEmpty() || w == 0 || h == 0) return

        val usableWidth = (w - bigMargin * 2).coerceAtLeast(dp(120f))
        val totalSpacingX = cellSpacing * (columns - 1)
        val cellWidth = ((usableWidth - totalSpacingX) / columns.toFloat()).coerceAtLeast(dp(48f))
        val cellHeight = (textPaint.textSize + cellPaddingV * 2)

        val totalGridHeight = rows * cellHeight + (rows - 1) * cellSpacing
        val startY = (h - totalGridHeight) / 2f

        for (i in positions.indices) {
            val row = i / columns
            val col = i % columns

            // Determine how many items this row actually has
            val isLastRow = row == rows - 1
            val remainder = positions.size % columns
            val itemsInRow = if (isLastRow && remainder != 0) remainder else columns

            // Width for this specific row
            val rowSpacing = cellSpacing * (itemsInRow - 1)
            val rowWidth = itemsInRow * cellWidth + rowSpacing

            // Center this row horizontally inside full width (w)
            val rowStartX = (w - rowWidth) / 2f
            val top = startY + row * (cellHeight + cellSpacing)

            // Column index inside this (possibly partial) row
            val colInThisRow = if (itemsInRow == columns) col else i - row * columns
            val left = rowStartX + colInThisRow * (cellWidth + cellSpacing)

            positionRects.add(RectF(left, top, left + cellWidth, top + cellHeight))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (positions.isEmpty() || !isShowing) return

        val fm = textPaint.fontMetrics
        val textCenterOffset = (fm.bottom + fm.top) / 2f
        for (i in positionRects.indices) {
            val rect = positionRects[i]
            val pos = positions[i]
            val cx = rect.centerX()
            val cy = rect.centerY() - textCenterOffset
            if (i == pressedIndex) {
                textPaint.color = pressedTextColor
            } else {
                textPaint.color = textColor
            }
            // Flat letters only
            canvas.drawText(pos.label, cx, cy, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isShowing) return false
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedIndex = hitTest(x, y)
                if (pressedIndex != -1) {
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val newIndex = hitTest(x, y)
                if (newIndex != pressedIndex) {
                    pressedIndex = newIndex
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val selected = if (event.actionMasked == MotionEvent.ACTION_UP) pressedIndex else -1
                val idx = pressedIndex
                pressedIndex = -1
                invalidate()
                if (selected != -1 && idx in positions.indices) {
                    performClick()
                    onPositionSelected?.invoke(positions[idx])
                }
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    // Optionally auto-hide after selection
                    hide(animated = true)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun hitTest(x: Float, y: Float): Int {
        for (i in positionRects.indices) {
            if (positionRects[i].contains(x, y)) return i
        }
        return -1
    }

    override fun onDetachedFromWindow() {
        recyclerRef = null
        fadeScaleAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float {
        val metrics: DisplayMetrics = resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, metrics)
    }

    private fun sp(value: Float): Float {
        val metrics: DisplayMetrics = resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, metrics)
    }

    fun setColumnCounts(portrait: Int, landscape: Int) {
        portraitColumns = portrait.coerceAtLeast(1)
        landscapeColumns = landscape.coerceAtLeast(1)
        updateColumnsForOrientation()
        rows = if (positions.isEmpty()) 0 else ceil(positions.size / columns.toFloat()).toInt()
        computeRects(width, height)
        invalidate()
    }

    private fun updateColumnsForOrientation() {
        val orientation = resources.configuration.orientation
        columns = if (orientation == Configuration.ORIENTATION_LANDSCAPE) landscapeColumns else portraitColumns
    }

    companion object {
        fun attach(recyclerView: RecyclerView): SectionedFastScroller {
            val ctx = recyclerView.context
            val nav = SectionedFastScroller(ctx)
            nav.attachTo(recyclerView)
            return nav
        }
    }
}