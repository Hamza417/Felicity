package app.simple.felicity.decorations.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import app.simple.felicity.decoration.R
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.themes.Theme

/**
 * A lightweight custom [android.view.View] that renders a downward-pointing pipeline arrow with optional
 * start and end icon decorators drawn at the top and bottom of the view respectively.
 *
 * When [startIcon] is set it is drawn centered at the top; the connecting line begins
 * below it after a small gap. When [endIcon] is set it is drawn centered at the bottom;
 * the arrowhead tip lands just above it so the chevron visually "points into" the icon.
 * If either property is `null` the corresponding end behaves as the default
 * (line starts at the top edge / arrowhead sits at the bottom edge).
 *
 * The arrowhead uses [android.graphics.CornerPathEffect] so all three corners of the chevron are soft
 * and rounded rather than sharp geometric points. The line itself retains [android.graphics.Paint.Cap.ROUND]
 * ends for visual consistency.
 *
 * Both icons are automatically tinted with [app.simple.felicity.theme.managers.ThemeManager.accent] and re-tinted whenever
 * the theme or accent palette changes, so no external color management is required.
 *
 * XML usage example:
 * ```xml
 * <app.simple.felicity.views.PipelineArrowView
 *     android:layout_width="20dp"
 *     android:layout_height="match_parent"
 *     app:startIcon="@drawable/ic_audio_file_16dp"
 *     app:endIcon="@drawable/ic_memory" />
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

    private val lineStrokeWidthPx = 2f * density
    private val arrowheadHeightPx = 10f * density
    private val arrowheadHalfWidthPx = 6f * density
    private val iconSpacingPx = 4f * density
    private val iconMarginPx = 2f * density
    private val cornerRadiusPx = 4f * density

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = lineStrokeWidthPx
        strokeCap = Paint.Cap.ROUND
    }

    private val arrowheadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        pathEffect = CornerPathEffect(cornerRadiusPx)
    }

    private val arrowheadPath = Path()

    private var accentColor: Int = 0
        set(value) {
            field = value
            linePaint.color = value
            arrowheadPaint.color = value
            startDrawable?.setTint(value)
            endDrawable?.setTint(value)
            invalidate()
        }

    /**
     * Optional icon drawn centered at the top (start) of the arrow.
     * Set via [setStartIcon] or the `app:startIcon` XML attribute.
     */
    private var startDrawable: Drawable? = null

    /**
     * Optional icon drawn centered at the bottom (end) of the arrow.
     * Set via [setEndIcon] or the `app:endIcon` XML attribute.
     */
    private var endDrawable: Drawable? = null

    init {
        if (!isInEditMode) {
            accentColor = ThemeManager.accent.primaryAccentColor
        }

        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.PipelineArrowView, 0, 0)
        try {
            if (a.hasValue(R.styleable.PipelineArrowView_startIcon)) {
                setStartIcon(a.getResourceId(R.styleable.PipelineArrowView_startIcon, 0))
            }
            if (a.hasValue(R.styleable.PipelineArrowView_endIcon)) {
                setEndIcon(a.getResourceId(R.styleable.PipelineArrowView_endIcon, 0))
            }
        } finally {
            a.recycle()
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

    /**
     * Sets the icon drawn at the top (start) of the arrow from a drawable resource ID.
     * Pass `0` to clear any existing start icon.
     *
     * @param resId Drawable resource ID, or `0` to clear.
     */
    fun setStartIcon(@DrawableRes resId: Int) {
        startDrawable = if (resId != 0) {
            ContextCompat.getDrawable(context, resId)?.also { it.setTint(accentColor) }
        } else {
            null
        }
        invalidate()
    }

    /**
     * Sets the icon drawn at the top (start) of the arrow directly.
     *
     * @param drawable The [Drawable] to display, or `null` to clear.
     */
    fun setStartIcon(drawable: Drawable?) {
        startDrawable = drawable?.also { it.setTint(accentColor) }
        invalidate()
    }

    /**
     * Sets the icon drawn at the bottom (end) of the arrow from a drawable resource ID.
     * Pass `0` to clear any existing end icon.
     *
     * @param resId Drawable resource ID, or `0` to clear.
     */
    fun setEndIcon(@DrawableRes resId: Int) {
        endDrawable = if (resId != 0) {
            ContextCompat.getDrawable(context, resId)?.also { it.setTint(accentColor) }
        } else {
            null
        }
        invalidate()
    }

    /**
     * Sets the icon drawn at the bottom (end) of the arrow directly.
     *
     * @param drawable The [Drawable] to display, or `null` to clear.
     */
    fun setEndIcon(drawable: Drawable?) {
        endDrawable = drawable?.also { it.setTint(accentColor) }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val iconSize = (width - iconMarginPx * 2).coerceAtLeast(0f)
        val iconLeft = iconMarginPx.toInt()
        val iconRight = (iconMarginPx + iconSize).toInt()

        // Line starts below startIcon (if present), otherwise at the top edge
        val lineTop = if (startDrawable != null) iconSize + iconSpacingPx else 0f

        // Arrowhead tip sits above endIcon (if present), otherwise at the bottom edge
        val arrowTip = if (endDrawable != null) {
            height - iconSize - iconSpacingPx
        } else {
            height.toFloat()
        }
        val arrowBase = arrowTip - arrowheadHeightPx

        // Start icon — drawn at the very top, centered horizontally
        startDrawable?.let { d ->
            d.setBounds(iconLeft, 0, iconRight, iconSize.toInt())
            d.draw(canvas)
        }

        // Connecting vertical line
        if (lineTop < arrowBase) {
            canvas.drawLine(cx, lineTop, cx, arrowBase, linePaint)
        }

        // Arrowhead chevron — CornerPathEffect rounds all three corners
        arrowheadPath.reset()
        arrowheadPath.moveTo(cx, arrowTip)
        arrowheadPath.lineTo(cx - arrowheadHalfWidthPx, arrowBase)
        arrowheadPath.lineTo(cx + arrowheadHalfWidthPx, arrowBase)
        arrowheadPath.close()
        canvas.drawPath(arrowheadPath, arrowheadPaint)

        // End icon — drawn at the very bottom, centered horizontally
        endDrawable?.let { d ->
            val top = (height - iconSize).toInt()
            d.setBounds(iconLeft, top, iconRight, height)
            d.draw(canvas)
        }
    }
}