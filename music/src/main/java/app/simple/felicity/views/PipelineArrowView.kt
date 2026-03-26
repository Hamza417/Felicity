package app.simple.felicity.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.themes.Theme

/**
 * A lightweight custom [View] that renders a single continuous downward-pointing pipeline
 * arrow — a vertical center line terminating in a filled chevron arrowhead at the bottom.
 *
 * The arrow fills the full height of the view so it can be placed in the left column of a
 * horizontal [android.widget.LinearLayout] alongside multi-section content; the view
 * naturally stretches to match its sibling's measured height.
 *
 * Colors are derived from [ThemeManager.accent] and updated automatically whenever the
 * app theme or accent palette changes, so no manual color management is required by callers.
 *
 * Typical usage in XML:
 * ```xml
 * <app.simple.felicity.views.PipelineArrowView
 *     android:layout_width="20dp"
 *     android:layout_height="match_parent" />
 * ```
 *
 * @author Hamza417
 */
class PipelineArrowView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), ThemeChangedListener {

    private val density = resources.displayMetrics.density

    private val lineWidthPx = 2f * density
    private val arrowheadHeightPx = 10f * density
    private val arrowheadHalfWidthPx = 5f * density

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = lineWidthPx
        strokeCap = Paint.Cap.ROUND
    }

    private val arrowheadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val arrowheadPath = Path()

    private var accentColor: Int = 0
        set(value) {
            field = value
            linePaint.color = value
            arrowheadPaint.color = value
            invalidate()
        }

    init {
        if (!isInEditMode) {
            accentColor = ThemeManager.accent.primaryAccentColor
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            ThemeManager.addListener(this)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ThemeManager.removeListener(this)
    }

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        accentColor = ThemeManager.accent.primaryAccentColor
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val lineEnd = height - arrowheadHeightPx

        canvas.drawLine(cx, 0f, cx, lineEnd, linePaint)

        arrowheadPath.reset()
        arrowheadPath.moveTo(cx, height.toFloat())
        arrowheadPath.lineTo(cx - arrowheadHalfWidthPx, lineEnd)
        arrowheadPath.lineTo(cx + arrowheadHalfWidthPx, lineEnd)
        arrowheadPath.close()
        canvas.drawPath(arrowheadPath, arrowheadPaint)
    }
}

