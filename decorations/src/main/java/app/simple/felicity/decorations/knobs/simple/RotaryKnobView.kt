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
import app.simple.felicity.decorations.typeface.TypeFace
import app.simple.felicity.theme.managers.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@SuppressLint("ClickableViewAccessibility")
class RotaryKnobView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── Knob drawable ─────────────────────────────────────────────────────────

    private var knobDrawable: RotaryKnobDrawable = SimpleRotaryKnobDrawable()

    // ── Touch / rotation state ────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private var vibration: Vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private var lastMoveAngle = 0f
    private var knobRotation = 0f
    private var rotationAnimator: ValueAnimator? = null
    private var firstPositionSet = true   // true until setKnobPosition is called at least once
    private val debounceHandler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null
    private var pendingVolume = 0f
    var value = 130
    private var haptic = false
    private var listener: RotaryKnobListener? = null

    // ── Label state ───────────────────────────────────────────────────────────

    /** Current label string, produced by [RotaryKnobListener.onLabel]. */
    private var labelText: String = ""

    // ── Paints ────────────────────────────────────────────────────────────────

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val divisionIdlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val divisionAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        if (isInEditMode.not()) {
            typeface = TypeFace.getMediumTypeFace(context)
            color = ThemeManager.theme.textViewTheme.primaryTextColor
        }
    }

    // ── Customisation ─────────────────────────────────────────────────────────

    @ColorInt
    var arcColor: Int = SimpleRotaryKnobDrawable.DEFAULT_IDLE_COLOR
        set(value) {
            field = value; invalidate()
        }

    /** Fraction of available radius the knob circle occupies. Default 0.80. */
    var knobRadiusFraction: Float = 0.80f
        set(value) {
            field = value.coerceIn(0.1f, 0.95f); recalcGeometry()
        }

    /** Gap: knob outer edge → arc near edge, fraction of available radius. */
    var arcGapFraction: Float = 0.06f
        set(value) {
            field = value; recalcGeometry()
        }

    /** Arc stroke width, fraction of available radius. */
    var arcStrokeWidthFraction: Float = 0.01f
        set(value) {
            field = value; recalcGeometry()
        }

    /** Gap: arc far edge → tick near end, fraction of available radius. */
    var tickGapFraction: Float = 0.06f
        set(value) {
            field = value; recalcGeometry()
        }

    /** Tick length, fraction of available radius. */
    var tickLengthFraction: Float = 0.03f
        set(value) {
            field = value; recalcGeometry()
        }

    /** Tick stroke width, fraction of available radius. */
    var tickStrokeWidthFraction: Float = 0.01f
        set(value) {
            field = value; invalidate()
        }

    // ── Division lines ────────────────────────────────────────────────────────

    /** Number of division lines distributed evenly across the full arc. Default 20. */
    var divisionCount: Int = 200
        set(value) {
            field = value.coerceAtLeast(2); resetDivisions(); recalcGeometry()
        }

    /** Full (progressed) length of each division line, fraction of available radius. */
    var divisionProgressLengthFraction: Float = 0.07f
        set(value) {
            field = value; recalcGeometry()
        }

    /** Length of idle (not-yet-progressed) division lines, fraction of available radius. */
    var divisionIdleLengthFraction: Float = 0.03f
        set(value) {
            field = value; recalcGeometry()
        }

    /** Stroke width of division lines, fraction of available radius. */
    var divisionStrokeWidthFraction: Float = 0.008f
        set(value) {
            field = value; invalidate()
        }

    /** Color of the accent (progressed) division lines. */
    @ColorInt
    var divisionAccentColor: Int = SimpleRotaryKnobDrawable.DEFAULT_ACCENT_COLOR
        set(value) {
            field = value; invalidate()
        }

    /** Color of the idle (not-yet-progressed) division lines. */
    @ColorInt
    var divisionIdleColor: Int = SimpleRotaryKnobDrawable.DEFAULT_IDLE_COLOR
        set(value) {
            field = value; invalidate()
        }

    /** Label text size, fraction of available radius. Default 0.14. */
    var labelTextSizeFraction: Float = 0.10f
        set(value) {
            field = value; recalcGeometry()
        }

    /** Label color. Defaults to the same muted color as the arc. */
    @ColorInt
    var labelColor: Int = SimpleRotaryKnobDrawable.DEFAULT_IDLE_COLOR
        set(value) {
            field = value; invalidate()
        }

    /** Optional typeface for the label. */
    var labelTypeface: Typeface?
        get() = labelPaint.typeface
        set(value) {
            labelPaint.typeface = value ?: Typeface.DEFAULT; invalidate()
        }

    /** Duration in ms for the rotation animation. Default 400. */
    var animationDuration: Long = 400L

    /** Tension for the overshoot interpolator used on the first value set. Default 2.0. */
    var overshootTension: Float = 2.0f

    /** Deceleration factor for the decelerate interpolator used on subsequent sets. Default 1.5. */
    var decelerateFactor: Float = 1.5f

    // ── Computed geometry ─────────────────────────────────────────────────────

    private var cx = 0f
    private var cy = 0f
    private var knobRadiusPx = 0f
    private var arcCentreRadiusPx = 0f
    private var arcStrokeWidthPx = 0f
    private var tickStartRadiusPx = 0f
    private var tickEndRadiusPx = 0f
    private var tickStrokeWidthPx = 0f
    private var labelYPx = 0f
    private val arcOval = RectF()

    // Division lines geometry
    private var divStrokeWidthPx = 0f
    private var divProgressLengthPx = 0f   // full length when progressed
    private var divIdleLengthPx = 0f       // shorter length when idle
    private var divInnerRadiusPx = 0f      // inner end of the line (on the arc)

    /**
     * Per-line scale factors: 0f = idle (draws at idle length), 1f = fully progressed.
     * Values between 0 and 1 animate the line growing/shrinking.
     */
    private var divisionScales = FloatArray(divisionCount)

    /** Canvas-space angle for each division line. Pre-computed in recalcGeometry. */
    private var divisionAngles = FloatArray(divisionCount)

    /** The knob rotation on the previous draw frame — used to detect direction. */
    private var prevKnobRotation = 0f


    // ── Init ──────────────────────────────────────────────────────────────────

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
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        (knobDrawable as? SimpleRotaryKnobDrawable)?.onColorChanged = { invalidate() }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        (knobDrawable as? SimpleRotaryKnobDrawable)?.onColorChanged = null
        rotationAnimator?.cancel()
    }

    // ── Size ──────────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalcGeometry()
    }

    private fun recalcGeometry() {
        if (width == 0 || height == 0) return

        cx = width / 2f
        cy = height / 2f
        val availableRadius = min(width, height) / 2f

        knobRadiusPx = availableRadius * knobRadiusFraction
        arcStrokeWidthPx = availableRadius * arcStrokeWidthFraction
        arcCentreRadiusPx = knobRadiusPx + availableRadius * arcGapFraction + arcStrokeWidthPx / 2f
        tickStrokeWidthPx = availableRadius * tickStrokeWidthFraction
        tickStartRadiusPx = arcCentreRadiusPx + arcStrokeWidthPx / 2f + availableRadius * tickGapFraction
        tickEndRadiusPx = tickStartRadiusPx + availableRadius * tickLengthFraction

        arcOval.set(
                cx - arcCentreRadiusPx, cy - arcCentreRadiusPx,
                cx + arcCentreRadiusPx, cy + arcCentreRadiusPx
        )

        // Division lines sit on the arc centre-line radius
        divStrokeWidthPx = availableRadius * divisionStrokeWidthFraction
        divProgressLengthPx = availableRadius * divisionProgressLengthFraction
        divIdleLengthPx = availableRadius * divisionIdleLengthFraction
        // Lines are centred on arcCentreRadiusPx, inner end = centre - halfMaxLength
        divInnerRadiusPx = arcCentreRadiusPx - divProgressLengthPx / 2f

        // Pre-compute the canvas angle for every division line.
        // Evenly distributed across ARC_SWEEP, endpoints included (index 0 = min, N-1 = max).
        val n = divisionCount
        for (i in 0 until n) {
            val fraction = if (n > 1) i.toFloat() / (n - 1).toFloat() else 0f
            divisionAngles[i] = ARC_START_ANGLE + fraction * ARC_SWEEP
        }

        val labelTextSizePx = availableRadius * labelTextSizeFraction
        labelPaint.textSize = labelTextSizePx
        val bottomOfKnob = cy + knobRadiusPx
        labelYPx = bottomOfKnob + (height.toFloat() - bottomOfKnob) / 2f + labelTextSizePx / 2f

        val r = knobRadiusPx.toInt()
        knobDrawable.setBounds(
                (cx - knobRadiusPx).toInt(), (cy - knobRadiusPx).toInt(),
                (cx - knobRadiusPx).toInt() + r * 2, (cy - knobRadiusPx).toInt() + r * 2
        )

        invalidate()
    }

    /** Resets all per-line scale factors to 0 (used when divisionCount changes). */
    private fun resetDivisions() {
        divisionScales = FloatArray(divisionCount) { 0f }
        divisionAngles = FloatArray(divisionCount) { 0f }
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val color = currentArcColor()

        // ── Update division scales ────────────────────────────────────────────
        // Convert the knob rotation (-150..150) to a canvas angle (120..420).
        // Each line has a canvas angle; if that angle ≤ current knob canvas angle
        // it is "progressed". We drive scale toward 1 or 0 at DIVISION_LERP_SPEED.
        val knobCanvasAngle = ARC_START_ANGLE + ((knobRotation - START) / (END - START)) * ARC_SWEEP
        val lerp = DIVISION_LERP_SPEED
        for (i in divisionScales.indices) {
            val target = if (divisionAngles[i] <= knobCanvasAngle) 1f else 0f
            divisionScales[i] += (target - divisionScales[i]) * lerp
        }
        prevKnobRotation = knobRotation

        // ── Arc (static, never changes color) ────────────────────────────────
        arcPaint.strokeWidth = arcStrokeWidthPx
        arcPaint.color = color
        canvas.drawArc(arcOval, ARC_START_ANGLE, ARC_SWEEP, false, arcPaint)

        // ── Division lines ────────────────────────────────────────────────────
        val divStroke = divStrokeWidthPx
        for (i in divisionScales.indices) {
            val scale = divisionScales[i]
            val angleDeg = divisionAngles[i]
            val rad = Math.toRadians(angleDeg.toDouble())
            val cosA = cos(rad).toFloat()
            val sinA = sin(rad).toFloat()

            if (scale > 0.001f) {
                // Progressed (or animating in): accent color, grows from idle length → full length
                val len = divIdleLengthPx + (divProgressLengthPx - divIdleLengthPx) * scale
                val innerR = arcCentreRadiusPx - len / 2f
                val outerR = arcCentreRadiusPx + len / 2f
                divisionAccentPaint.strokeWidth = divStroke
                divisionAccentPaint.color = divisionAccentColor
                canvas.drawLine(
                        cx + cosA * innerR, cy + sinA * innerR,
                        cx + cosA * outerR, cy + sinA * outerR,
                        divisionAccentPaint
                )
            } else {
                // Idle: muted color, fixed short length
                val innerR = arcCentreRadiusPx - divIdleLengthPx / 2f
                val outerR = arcCentreRadiusPx + divIdleLengthPx / 2f
                divisionIdlePaint.strokeWidth = divStroke
                divisionIdlePaint.color = divisionIdleColor
                canvas.drawLine(
                        cx + cosA * innerR, cy + sinA * innerR,
                        cx + cosA * outerR, cy + sinA * outerR,
                        divisionIdlePaint
                )
            }
        }

        // ── Ticks ─────────────────────────────────────────────────────────────
        tickPaint.strokeWidth = tickStrokeWidthPx
        tickPaint.color = color
        drawTick(canvas, ARC_START_ANGLE)
        drawTick(canvas, ARC_START_ANGLE + ARC_SWEEP)

        // ── Label between ticks ───────────────────────────────────────────────
        if (labelText.isNotEmpty()) {
            labelPaint.color = labelColor
            canvas.drawText(labelText, cx, labelYPx, labelPaint)
        }

        // ── Knob — rotated around centre ──────────────────────────────────────
        canvas.withRotation(knobRotation.coerceIn(START, END), cx, cy) {
            knobDrawable.draw(this)
        }

        // Keep redrawing while any division line is still animating
        if (divisionScales.any { it > 0.001f && it < 0.999f }) {
            invalidate()
        }
    }

    private fun currentArcColor(): Int =
        (knobDrawable as? SimpleRotaryKnobDrawable)?.currentStateColor ?: arcColor

    private fun drawTick(canvas: Canvas, angleDeg: Float) {
        val rad = Math.toRadians(angleDeg.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        canvas.drawLine(
                cx + cosA * tickStartRadiusPx, cy + sinA * tickStartRadiusPx,
                cx + cosA * tickEndRadiusPx, cy + sinA * tickEndRadiusPx,
                tickPaint
        )
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                rotationAnimator?.cancel()
                rotationAnimator = null
                knobDrawable.onPressedStateChanged(true, 300)
                lastMoveAngle = calculateAngle(event.x, event.y)
                feedback()
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
                return true
            }
        }
        return false
    }

    private fun calculateAngle(x: Float, y: Float): Float {
        val px = (x / width.toFloat()) - 0.5
        val py = (1.0 - y / height.toFloat()) - 0.5
        var angle = (-Math.toDegrees(atan2(py, px))).toFloat() + 90f
        if (angle > 180f) angle -= 360f
        return angle
    }

    // ── Public API ────────────────────────────────────────────────────────────

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
        val interpolator = if (firstPositionSet) {
            OvershootInterpolator(overshootTension)
        } else {
            DecelerateInterpolator(decelerateFactor)
        }
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

    fun setListener(rotaryKnobListener: RotaryKnobListener) {
        this.listener = rotaryKnobListener
        // Seed the initial label
        labelText = rotaryKnobListener.onLabel(angleToValue(knobRotation))
        invalidate()
    }

    fun setKnobDrawable(drawable: RotaryKnobDrawable) {
        (knobDrawable as? SimpleRotaryKnobDrawable)?.onColorChanged = null
        knobDrawable = drawable
        (knobDrawable as? SimpleRotaryKnobDrawable)?.onColorChanged = { invalidate() }
        recalcGeometry()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun angleToValue(angle: Float): Float =
        ((angle - START) / (END - START) * 100f).coerceIn(0f, 100f)

    private fun valueToAngle(volume: Float): Float =
        START + (volume / 100f) * (END - START)

    private fun feedback() {
        CoroutineScope(Dispatchers.Default).launch {
            @Suppress("DEPRECATION")
            if (haptic) vibration.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        private const val START = -150f
        private const val END = 150f

        // Android canvas: 0° = 3 o'clock, CW+.
        // Knob min (−150° from 12 o'clock) = −240° ≡ 120° canvas.
        // Sweep CW 300° reaches max (+150° from 12 o'clock).
        private const val ARC_START_ANGLE = 120f
        private const val ARC_SWEEP = 300f

        /**
         * Per-frame lerp speed for division line scale animation (0..1).
         * Higher = faster animation. At 60 fps, 0.25 gives a ~4-frame transition.
         */
        private const val DIVISION_LERP_SPEED = 0.25f
    }
}
