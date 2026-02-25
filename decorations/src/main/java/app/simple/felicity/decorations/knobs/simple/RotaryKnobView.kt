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
    private var labelYPx = 0f          // baseline Y for the label
    private val arcOval = RectF()


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

        // Label: centred between the two ticks, just below the knob bottom.
        // The midpoint of the arc gap at the bottom (270° in canvas = 6 o'clock) is
        // at cy + arcCentreRadiusPx. We place text a little further down.
        val labelTextSizePx = availableRadius * labelTextSizeFraction
        labelPaint.textSize = labelTextSizePx
        // Position baseline so the text sits in the gap between the bottom of the knob
        // and the bottom of the view, roughly centred in that space.
        val bottomOfKnob = cy + knobRadiusPx
        val bottomOfView = height.toFloat()
        labelYPx = bottomOfKnob + (bottomOfView - bottomOfKnob) / 2f + labelTextSizePx / 2f

        val r = knobRadiusPx.toInt()
        knobDrawable.setBounds(
                (cx - knobRadiusPx).toInt(), (cy - knobRadiusPx).toInt(),
                (cx - knobRadiusPx).toInt() + r * 2, (cy - knobRadiusPx).toInt() + r * 2
        )

        invalidate()
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val color = currentArcColor()

        // Arc
        arcPaint.strokeWidth = arcStrokeWidthPx
        arcPaint.color = color
        canvas.drawArc(arcOval, ARC_START_ANGLE, ARC_SWEEP, false, arcPaint)

        // Ticks
        tickPaint.strokeWidth = tickStrokeWidthPx
        tickPaint.color = color
        drawTick(canvas, ARC_START_ANGLE)
        drawTick(canvas, ARC_START_ANGLE + ARC_SWEEP)

        // Label between ticks
        if (labelText.isNotEmpty()) {
            labelPaint.color = labelColor
            canvas.drawText(labelText, cx, labelYPx, labelPaint)
        }

        // Knob — rotated around center; clamp visually so it never draws past the stops
        canvas.withRotation(knobRotation.coerceIn(START, END), cx, cy) {
            knobDrawable.draw(this)
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
    }
}
