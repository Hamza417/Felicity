package app.simple.felicity.decorations.knobs.simple

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.annotation.ColorInt
import androidx.core.graphics.withRotation
import app.simple.felicity.decoration.R
import app.simple.felicity.decorations.knobs.simple.RotaryKnobView.Companion.END
import app.simple.felicity.decorations.knobs.simple.RotaryKnobView.Companion.START
import app.simple.felicity.decorations.typeface.TypeFace
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.themes.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A fully custom rotary knob view drawn entirely on canvas — no XML layout or child views.
 *
 * Layout (outward from centre):
 *   [knob circle] → [gap] → [arc / division ring] → [gap] → [ticks]
 *
 * The arc ring is split into two visual regions:
 *  - **Progressed region** (min → current value): accent-coloured division lines only, no arc stroke.
 *  - **Remaining region** (current value → max): idle-coloured arc segments drawn in the gaps
 *    between division lines, no division lines.
 *
 * Division lines grow from 0 → full height as the knob sweeps over them (forward) and
 * shrink back when the knob retreats (backward), animated via a per-frame lerp.
 */
@SuppressLint("ClickableViewAccessibility")
class RotaryKnobView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), ThemeChangedListener {

    /** The drawable used to paint the rotating knob circle. Must be a [RotaryKnobDrawable]. */
    private var knobDrawable: RotaryKnobDrawable = SimpleRotaryKnobDrawable()

    @Suppress("DEPRECATION")
    private var vibration: Vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    /** Current angular position of the knob in degrees, clamped to [START]..[END]. */
    private var knobRotation = 0f

    private var lastMoveAngle = 0f
    private var rotationAnimator: ValueAnimator? = null

    /** True until the first animated [setKnobPosition] call — selects overshoot vs decelerate. */
    private var firstPositionSet = true

    private val debounceHandler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null
    private var pendingVolume = 0f
    var value = 130
    private var haptic = false
    private var listener: RotaryKnobListener? = null

    /** Current display string sourced from [RotaryKnobListener.onLabel]. */
    private var labelText: String = ""

    /** Per-line scale factors: 0.0 = fully idle, 1.0 = fully progressed. Written by animators, read in onDraw. */
    private var divisionScales = FloatArray(0)

    /** Pre-computed canvas-space angle (degrees) for each division line. Set in [recalcGeometry]. */
    private var divisionAngles = FloatArray(0)

    /**
     * Per-line [ValueAnimator] instances that drive [divisionScales].
     * Each animator runs independently so lines animate in a natural trailing-wave order
     * rather than all updating simultaneously on the same lerp tick.
     */
    private var divisionAnimators = arrayOfNulls<ValueAnimator>(0)

    /** Target scale for each line (0f or 1f). Tracked so redundant animator restarts are skipped. */
    private var divisionTargets = FloatArray(0)

    /** Paint for idle arc segments drawn between division lines in the remaining region. */
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    /** Paint for the min/max end-stop tick marks. */
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    /** Paint for idle (not-yet-progressed) division lines — drawn in remaining region if needed. */
    private val divisionIdlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    /** Paint for accent (progressed) division lines. */
    private val divisionAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    /** Paint for the value label drawn below the knob. */
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        if (!isInEditMode) {
            typeface = TypeFace.getMediumTypeFace(context)
            color = ThemeManager.theme.textViewTheme.primaryTextColor
        }
    }

    /**
     * Paint for the tiny text labels rendered just beyond each end-stop tick mark.
     * Text size is governed by [tickLabelTextSizeFraction].
     */
    private val tickLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        if (!isInEditMode) {
            typeface = TypeFace.getMediumTypeFace(context)
            color = ThemeManager.theme.textViewTheme.secondaryTextColor
        }
    }

    /** Fallback arc/tick color used when no [SimpleRotaryKnobDrawable] is attached. */
    @ColorInt
    var arcColor: Int = SimpleRotaryKnobDrawable.DEFAULT_IDLE_COLOR
        set(value) {
            field = value; invalidate()
        }

    /**
     * Fraction of the total available radius (= min(w,h)/2) occupied by the knob circle.
     * The remaining fraction is shared between gap, arc ring, gap, and ticks.
     * Clamped to 0.1..0.95.
     */
    var knobRadiusFraction: Float = 0.80f
        set(value) {
            field = value.coerceIn(0.1f, 0.95f); recalcGeometry()
        }

    /** Gap between the knob outer edge and the arc/division ring, as fraction of available radius. */
    var arcGapFraction: Float = 0.06f
        set(value) {
            field = value; recalcGeometry()
        }

    /**
     * Stroke width of the arc segments drawn in the remaining (idle) region,
     * as a fraction of available radius.
     */
    var arcStrokeWidthFraction: Float = 0.01f
        set(value) {
            field = value; recalcGeometry()
        }

    /** Gap between the far edge of the arc ring and the near end of the end-stop ticks. */
    var tickGapFraction: Float = 0.06f
        set(value) {
            field = value; recalcGeometry()
        }

    /** Length of the end-stop tick marks at min and max, as fraction of available radius. */
    var tickLengthFraction: Float = 0.03f
        set(value) {
            field = value; recalcGeometry()
        }

    /** Stroke width of the end-stop ticks, as fraction of available radius. */
    var tickStrokeWidthFraction: Float = 0.01f
        set(value) {
            field = value; invalidate()
        }

    /**
     * Number of division lines distributed evenly across the full 300° arc range.
     * Must be ≥ 2. Changing this resets all scale factors to 0.
     */
    var divisionCount: Int = 200
        set(value) {
            field = value.coerceAtLeast(2); resetDivisions(); recalcGeometry()
        }

    /**
     * Height of a fully-progressed division line, as fraction of available radius.
     * Lines grow from [divisionIdleLengthFraction] to this value.
     */
    var divisionProgressLengthFraction: Float = 0.07f
        set(value) {
            field = value; recalcGeometry()
        }

    /**
     * Height of a not-yet-progressed division line, as fraction of available radius.
     * In the new gutter design idle lines are not drawn — this is used only as the
     * animation start height when a line begins growing.
     */
    var divisionIdleLengthFraction: Float = 0.03f
        set(value) {
            field = value; recalcGeometry()
        }

    /** Stroke width of every division line, as fraction of available radius. */
    var divisionStrokeWidthFraction: Float = 0.008f
        set(value) {
            field = value; invalidate()
        }

    /** Color of accent (progressed) division lines. */
    @ColorInt
    var divisionAccentColor: Int = SimpleRotaryKnobDrawable.DEFAULT_ACCENT_COLOR
        set(value) {
            field = value; invalidate()
        }

    /** Color of idle (remaining-region) arc segments. */
    @ColorInt
    var divisionIdleColor: Int = SimpleRotaryKnobDrawable.DEFAULT_IDLE_COLOR
        set(value) {
            field = value; invalidate()
        }

    /** Label text size as fraction of available radius. */
    var labelTextSizeFraction: Float = 0.10f
        set(value) {
            field = value; recalcGeometry()
        }

    /** Color of the value label rendered between the end-stop ticks. */
    @ColorInt
    var labelColor: Int = SimpleRotaryKnobDrawable.DEFAULT_IDLE_COLOR
        set(value) {
            field = value; invalidate()
        }

    /**
     * Text drawn just beyond the **minimum** (start) end-stop tick.
     * Set to an empty string to hide the label (default).
     */
    var tickStartText: String = ""
        set(value) {
            field = value; invalidate()
        }

    /**
     * Text drawn just beyond the **maximum** (end) end-stop tick.
     * Set to an empty string to hide the label (default).
     */
    var tickEndText: String = ""
        set(value) {
            field = value; invalidate()
        }

    /**
     * Size of the tick label text as a fraction of the available radius.
     * Intentionally very small — 0.05 = 5 % — so it never crowds the arc ring.
     * Changing this triggers a geometry recalculation.
     */
    var tickLabelTextSizeFraction: Float = 0.05f
        set(value) {
            field = value; recalcGeometry()
        }

    /**
     * Color applied to both tick label texts.
     * Defaults to the theme secondary text color, updated automatically on theme changes.
     */
    @ColorInt
    var tickLabelColor: Int = SimpleRotaryKnobDrawable.DEFAULT_IDLE_COLOR
        set(value) {
            field = value; invalidate()
        }

    /** Typeface used for the value label. Defaults to the app medium typeface. */
    var labelTypeface: Typeface?
        get() = labelPaint.typeface
        set(value) {
            labelPaint.typeface = value ?: Typeface.DEFAULT; invalidate()
        }

    /** Duration of the programmatic rotation animation in milliseconds. */
    var animationDuration: Long = 400L

    /** Overshoot tension applied on the very first [setKnobPosition] call. */
    var overshootTension: Float = 2.0f

    /** Deceleration factor applied on all subsequent [setKnobPosition] calls. */
    var decelerateFactor: Float = 1.5f

    /**
     * Duration in milliseconds for each individual division line grow/shrink animation.
     * Shorter values produce a tighter trailing wave; longer values a more fluid sweep.
     * Default 120 ms gives a smooth trail at normal knob speeds.
     */
    var divisionLineDuration: Long = 160L

    private var cx = 0f
    private var cy = 0f
    private var knobRadiusPx = 0f
    private var arcCentreRadiusPx = 0f
    private var arcStrokeWidthPx = 0f
    private var tickStartRadiusPx = 0f
    private var tickEndRadiusPx = 0f
    private var tickLabelRadiusPx = 0f
    private var tickStrokeWidthPx = 0f
    private var labelYPx = 0f
    private var divStrokeWidthPx = 0f
    private var divProgressLengthPx = 0f
    private var divIdleLengthPx = 0f
    private val arcOval = RectF()

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.RotaryKnobView, 0, 0).apply {
            try {
                value = getInt(R.styleable.RotaryKnobView_initialValue, 50)
            } finally {
                recycle()
            }
        }
        (knobDrawable as? SimpleRotaryKnobDrawable)?.onColorChanged = { invalidate() }
        setOnTouchListener { _, event -> handleTouch(event) }

        if (isInEditMode.not()) {
            setThemeColors(ThemeManager.theme)
            setAccentColor(ThemeManager.accent)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        (knobDrawable as? SimpleRotaryKnobDrawable)?.onColorChanged = { invalidate() }
        if (isInEditMode.not()) {
            ThemeManager.addListener(this)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        (knobDrawable as? SimpleRotaryKnobDrawable)?.onColorChanged = null
        rotationAnimator?.cancel()
        divisionAnimators.forEach { it?.cancel() }
        if (isInEditMode.not()) {
            ThemeManager.removeListener(this)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalcGeometry()
    }

    override fun onAccentChanged(accent: Accent) {
        super.onAccentChanged(accent)
        setAccentColor(accent)
    }

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        super.onThemeChanged(theme, animate)
        setThemeColors(theme)
    }

    fun setAccentColor(accent: Accent) {
        if (knobDrawable is SimpleRotaryKnobDrawable) {
            val drawable = knobDrawable as SimpleRotaryKnobDrawable
            drawable.accentColor = accent.primaryAccentColor
        }
        divisionAccentColor = accent.primaryAccentColor
    }

    fun setThemeColors(theme: Theme) {
        labelPaint.color = theme.textViewTheme.primaryTextColor
        labelColor = theme.textViewTheme.primaryTextColor
        tickLabelColor = theme.textViewTheme.secondaryTextColor
        tickLabelPaint.color = theme.textViewTheme.secondaryTextColor
        if (knobDrawable is SimpleRotaryKnobDrawable) {
            val drawable = knobDrawable as SimpleRotaryKnobDrawable
            drawable.idleColor = theme.viewGroupTheme.highlightColor
        }
        divisionIdleColor = theme.viewGroupTheme.highlightColor
    }

    /**
     * Recomputes all pixel geometry from the current view size and fraction properties.
     *
     * Visual layout (outward from center):
     * ```
     * |← knobRadius →|← arcGap →|← arcStroke →|← tickGap →|← tick →|
     * ```
     * All values are derived from `availableRadius = min(width, height) / 2` so the
     * entire composition always fits within the view bounds.
     */
    private fun recalcGeometry() {
        if (width == 0 || height == 0) return
        cx = width / 2f
        cy = height / 2f
        val r = min(width, height) / 2f

        knobRadiusPx = r * knobRadiusFraction
        arcStrokeWidthPx = r * arcStrokeWidthFraction
        arcCentreRadiusPx = knobRadiusPx + r * arcGapFraction + arcStrokeWidthPx / 2f
        tickStrokeWidthPx = r * tickStrokeWidthFraction
        tickStartRadiusPx = arcCentreRadiusPx + arcStrokeWidthPx / 2f + r * tickGapFraction
        tickEndRadiusPx = tickStartRadiusPx + r * tickLengthFraction

        // Tick label -> tiny text placed just beyond the outer end of the tick mark.
        val tickLabelSizePx = r * tickLabelTextSizeFraction
        tickLabelPaint.textSize = tickLabelSizePx
        // Center the glyph one full text-size beyond the tick end so it clears the mark.
        tickLabelRadiusPx = tickEndRadiusPx + tickLabelSizePx

        divStrokeWidthPx = r * divisionStrokeWidthFraction
        divProgressLengthPx = r * divisionProgressLengthFraction
        divIdleLengthPx = r * divisionIdleLengthFraction

        arcOval.set(
                cx - arcCentreRadiusPx, cy - arcCentreRadiusPx,
                cx + arcCentreRadiusPx, cy + arcCentreRadiusPx
        )

        // Distribute division lines evenly across the full sweep, endpoints included.
        val n = divisionCount
        if (divisionAngles.size != n) {
            divisionAnimators.forEach { it?.cancel() }
            divisionAngles = FloatArray(n)
            divisionScales = FloatArray(n)
            divisionTargets = FloatArray(n)
            divisionAnimators = arrayOfNulls(n)
        }
        for (i in 0 until n) {
            val fraction = if (n > 1) i.toFloat() / (n - 1).toFloat() else 0f
            divisionAngles[i] = ARC_START_ANGLE + fraction * ARC_SWEEP
        }

        val labelSizePx = r * labelTextSizeFraction
        labelPaint.textSize = labelSizePx
        val knobBottom = cy + knobRadiusPx
        labelYPx = knobBottom + (height - knobBottom) / 2f + labelSizePx / 2f

        val kr = knobRadiusPx.toInt()
        knobDrawable.setBounds(
                (cx - knobRadiusPx).toInt(), (cy - knobRadiusPx).toInt(),
                (cx - knobRadiusPx).toInt() + kr * 2, (cy - knobRadiusPx).toInt() + kr * 2
        )
        invalidate()
    }

    /** Cancels all running per-line animators and resets scale/target/animator arrays to match [divisionCount]. */
    private fun resetDivisions() {
        divisionAnimators.forEach { it?.cancel() }
        divisionScales = FloatArray(divisionCount) { 0f }
        divisionAngles = FloatArray(divisionCount) { 0f }
        divisionTargets = FloatArray(divisionCount) { 0f }
        divisionAnimators = arrayOfNulls(divisionCount)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val idleColor = divisionIdleColor
        val accentColor = divisionAccentColor

        /**
         * Map knob rotation (START..END) to a canvas-space angle (ARC_START..ARC_START+ARC_SWEEP).
         * Division lines at or before this angle are considered progressed.
         */
        val knobCanvasAngle = ARC_START_ANGLE + ((knobRotation - START) / (END - START)) * ARC_SWEEP

        // Start/reverse per-line ValueAnimators for any line whose target has flipped.
        updateDivisionAnimators(knobCanvasAngle)

        /**
         * Draw the arc/division ring as two non-overlapping gutter regions:
         *
         *  - PROGRESSED: accent division lines only (scale > 0). No arc stroke here.
         *  - REMAINING:  idle arc segments in the angular gaps between idle lines. No lines here.
         *
         * Together the two regions tile the full 300° sweep without overlap.
         */
        arcPaint.color = idleColor
        arcPaint.strokeWidth = arcStrokeWidthPx
        divisionAccentPaint.color = accentColor
        divisionAccentPaint.strokeWidth = divStrokeWidthPx

        val n = divisionAngles.size
        for (i in 0 until n) {
            val lineAngle = divisionAngles[i]
            val scale = divisionScales[i]

            // Compute the angular half-width of this division line at current scale so the arc
            // segment starts cleanly after the line's edge (gutter effect).
            val lineLen = divIdleLengthPx + (divProgressLengthPx - divIdleLengthPx) * scale.coerceAtLeast(0.001f)
            val halfLineAngleDeg = Math.toDegrees((divStrokeWidthPx / 2f / arcCentreRadiusPx).toDouble()).toFloat()

            if (scale > 0.001f) {
                // PROGRESSED — draw the accent division line, no arc.
                val innerR = arcCentreRadiusPx - lineLen / 2f
                val outerR = arcCentreRadiusPx + lineLen / 2f
                val rad = Math.toRadians(lineAngle.toDouble())
                val cosA = cos(rad).toFloat()
                val sinA = sin(rad).toFloat()
                canvas.drawLine(
                        cx + cosA * innerR, cy + sinA * innerR,
                        cx + cosA * outerR, cy + sinA * outerR,
                        divisionAccentPaint
                )
            } else {
                // IDLE —> draw an arc segment in the gap leading up to this line position.
                // The segment runs from the midpoint after the previous line to just before this one.
                val prevMidAngle = if (i == 0) ARC_START_ANGLE
                else (divisionAngles[i - 1] + lineAngle) / 2f
                val segStart = prevMidAngle
                val segEnd = lineAngle - halfLineAngleDeg
                val sweep = segEnd - segStart
                if (sweep > 0f) {
                    canvas.drawArc(arcOval, segStart, sweep, false, arcPaint)
                }

                // Also draw the segment from this line to the midpoint toward the next line,
                // only if the next line is also idle, otherwise the next iteration handles it.
                val nextMidAngle = if (i == n - 1) ARC_START_ANGLE + ARC_SWEEP
                else (lineAngle + divisionAngles[i + 1]) / 2f
                val seg2Start = lineAngle + halfLineAngleDeg
                val seg2Sweep = nextMidAngle - seg2Start
                if (seg2Sweep > 0f && (i == n - 1 || divisionScales[i + 1] <= 0.001f)) {
                    canvas.drawArc(arcOval, seg2Start, seg2Sweep, false, arcPaint)
                }
            }
        }

        // End-stop tick marks at the min (ARC_START_ANGLE) and max (ARC_START_ANGLE + ARC_SWEEP).
        tickPaint.strokeWidth = tickStrokeWidthPx
        tickPaint.color = currentArcColor()
        drawTick(canvas, ARC_START_ANGLE, tickStartText)
        drawTick(canvas, ARC_START_ANGLE + ARC_SWEEP, tickEndText)

        // Value label centred below the knob, between the two end-stop ticks.
        if (labelText.isNotEmpty()) {
            labelPaint.color = labelColor
            canvas.drawText(labelText, cx, labelYPx, labelPaint)
        }

        // Knob circle — rotated around the view centre; visually clamped to START..END.
        canvas.withRotation(knobRotation.coerceIn(START, END), cx, cy) {
            knobDrawable.draw(this)
        }
    }

    /** Returns the color to use for static elements (ticks). Mirrors the knob drawable's animated state color. */
    private fun currentArcColor(): Int =
        (knobDrawable as? SimpleRotaryKnobDrawable)?.currentStateColor ?: arcColor

    /**
     * Compares each line's desired target against [knobCanvasAngle] and starts a new
     * [ValueAnimator] only when the target has actually changed (0 → 1 or 1 → 0).
     *
     * Because each animator starts from the line's *current mid-flight scale*, reversals
     * are seamless — no pop or jump when the knob direction changes. The
     * [android.view.animation.AccelerateDecelerateInterpolator] gives the smoothest
     * grow/shrink feel for a trailing-wave effect.
     */
    private fun updateDivisionAnimators(knobCanvasAngle: Float) {
        val interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        for (i in divisionAngles.indices) {
            val newTarget = if (divisionAngles[i] <= knobCanvasAngle) 1f else 0f
            if (newTarget == divisionTargets[i]) continue  // target unchanged — nothing to do

            divisionTargets[i] = newTarget
            val fromScale = divisionScales[i]  // pick up from wherever the scale currently sits
            divisionAnimators[i]?.cancel()
            divisionAnimators[i] = ValueAnimator.ofFloat(fromScale, newTarget).apply {
                duration = divisionLineDuration
                this.interpolator = interpolator
                addUpdateListener { anim ->
                    divisionScales[i] = anim.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    /**
     * Draws a single end-stop tick mark radiating outward from [tickStartRadiusPx] to
     * [tickEndRadiusPx] at the given canvas-space angle, then renders [label] just beyond
     * the tick end along the same radial direction when the string is non-empty.
     *
     * The label text is sized by [tickLabelTextSizeFraction] — intentionally tiny so it
     * never competes visually with the arc ring or value label.
     */
    private fun drawTick(canvas: Canvas, angleDeg: Float, label: String = "") {
        val rad = Math.toRadians(angleDeg.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()

        // Tick line
        canvas.drawLine(
                cx + cosA * tickStartRadiusPx, cy + sinA * tickStartRadiusPx,
                cx + cosA * tickEndRadiusPx, cy + sinA * tickEndRadiusPx,
                tickPaint
        )

        // Optional label centred just beyond the outer end of the tick, along the same radial axis.
        if (label.isNotEmpty()) {
            tickLabelPaint.color = tickLabelColor
            // Offset by half the text height to optically center the glyph on the radial direction.
            val textOffset = (tickLabelPaint.descent() - tickLabelPaint.ascent()) / 2f - tickLabelPaint.descent()
            canvas.drawText(
                    label,
                    cx + cosA * tickLabelRadiusPx,
                    cy + sinA * tickLabelRadiusPx + textOffset,
                    tickLabelPaint
            )
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                rotationAnimator?.cancel()
                rotationAnimator = null
                knobDrawable.onPressedStateChanged(true, 300)
                lastMoveAngle = calculateAngle(event.x, event.y)
                feedback()
                listener?.onUserInteractionStart()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val currentAngle = calculateAngle(event.x, event.y)
                var delta = currentAngle - lastMoveAngle
                if (delta > 180f) delta -= 360f
                if (delta < -180f) delta += 360f
                knobRotation = (knobRotation + delta).coerceIn(START, END)
                lastMoveAngle = currentAngle
                val v = angleToValue(knobRotation)
                labelText = listener?.onLabel(v) ?: ""
                listener?.onRotate(v)
                listener?.onIncrement(abs(delta))
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                knobDrawable.onPressedStateChanged(false, 300)
                listener?.onUserInteractionEnd()
                return true
            }
        }
        return false
    }

    /**
     * Converts a touch-event (x, y) to a knob angle in degrees, with 0° at 12 o'clock
     * and positive values clockwise. Returns a value in -180..180.
     */
    private fun calculateAngle(x: Float, y: Float): Float {
        val px = (x / width.toFloat()) - 0.5
        val py = (1.0 - y / height.toFloat()) - 0.5
        var angle = (-Math.toDegrees(atan2(py, px))).toFloat() + 90f
        if (angle > 180f) angle -= 360f
        return angle
    }

    /**
     * Moves the knob to the given [volume] (0..100).
     *
     * If [animate] is true the move is debounced and driven by a [ValueAnimator] using
     * [OvershootInterpolator] on the first call and [DecelerateInterpolator] thereafter.
     * If [animate] is false the knob snaps instantly.
     */
    fun setKnobPosition(volume: Float, animate: Boolean = true) {
        if (animate) {
            pendingVolume = volume
            debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
            debounceRunnable = Runnable { animateTo(valueToAngle(pendingVolume)) }
            debounceHandler.postDelayed(debounceRunnable!!, 100)
        } else {
            rotationAnimator?.cancel()
            knobRotation = valueToAngle(volume)
            labelText = listener?.onLabel(volume) ?: ""
            firstPositionSet = false
            invalidate()
        }
    }

    private fun animateTo(targetAngle: Float) {
        val clamped = targetAngle.coerceIn(START, END)
        rotationAnimator?.cancel()
        val interpolator = if (firstPositionSet) OvershootInterpolator(overshootTension)
        else DecelerateInterpolator(decelerateFactor)
        firstPositionSet = false
        val from = knobRotation
        rotationAnimator = ValueAnimator.ofFloat(from, clamped).apply {
            duration = animationDuration
            this.interpolator = interpolator
            addUpdateListener { anim ->
                knobRotation = anim.animatedValue as Float
                labelText = listener?.onLabel(angleToValue(knobRotation)) ?: ""
                invalidate()
            }
            start()
        }
    }

    /**
     * Attaches a [RotaryKnobListener] to receive rotation callbacks and supply the
     * display label string. Calling this also seeds the initial label text.
     */
    fun setListener(rotaryKnobListener: RotaryKnobListener) {
        listener = rotaryKnobListener
        labelText = rotaryKnobListener.onLabel(angleToValue(knobRotation))
        invalidate()
    }

    /**
     * Sets the tiny labels that appear just beyond the min and max end-stop tick marks.
     *
     * Both labels are rendered at [tickLabelTextSizeFraction] of the available radius —
     * typically very small (≈ 5 %) — so they sit neatly outside the arc ring without
     * cluttering the design.
     *
     * @param startText  Label for the minimum (start) tick. Pass an empty string to hide it.
     * @param endText    Label for the maximum (end) tick. Pass an empty string to hide it.
     */
    fun setTickTexts(startText: String, endText: String) {
        tickStartText = startText
        tickEndText = endText
        // invalidate() is triggered by each property setter individually.
    }

    /**
     * Replaces the knob visual with a custom [RotaryKnobDrawable] implementation.
     * The drawable's bounds are updated immediately.
     */
    fun setKnobDrawable(drawable: RotaryKnobDrawable) {
        (knobDrawable as? SimpleRotaryKnobDrawable)?.onColorChanged = null
        knobDrawable = drawable
        (knobDrawable as? SimpleRotaryKnobDrawable)?.onColorChanged = { invalidate() }
        recalcGeometry()
    }

    /** Maps a knob angle in [START]..[END] to a value in 0..100. */
    private fun angleToValue(angle: Float): Float =
        ((angle - START) / (END - START) * 100f).coerceIn(0f, 100f)

    /** Maps a value in 0..100 to a knob angle in [START]..[END]. */
    private fun valueToAngle(volume: Float): Float =
        START + (volume / 100f) * (END - START)

    private fun feedback() {
        CoroutineScope(Dispatchers.Default).launch {
            @Suppress("DEPRECATION")
            if (haptic) vibration.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    companion object {
        /** Knob minimum angle, degrees from 12 o'clock (clockwise positive). */
        private const val START = -150f

        /** Knob maximum angle, degrees from 12 o'clock. */
        private const val END = 150f

        /**
         * Android canvas angle for the start of the arc (= knob minimum position).
         * Canvas 0° = 3 o'clock, CW+. Knob −150° from 12 o'clock = −90° − 150° = −240° ≡ 120°.
         */
        private const val ARC_START_ANGLE = 120f

        /** Total angular sweep of the arc from min to max (END − START = 300°). */
        private const val ARC_SWEEP = 300f
    }
}
