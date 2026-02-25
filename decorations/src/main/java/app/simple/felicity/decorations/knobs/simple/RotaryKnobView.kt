package app.simple.felicity.decorations.knobs.simple

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import app.simple.felicity.decoration.R
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
    private var knobRotation = 0f          // current visual rotation of the knob, clamped to START..END
    private var rotationAnimator: android.animation.ValueAnimator? = null
    private val debounceHandler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null
    private var pendingVolume = 0f
    var value = 130
    private var haptic = false
    private var listener: RotaryKnobListener? = null

    // ── Paints ────────────────────────────────────────────────────────────────

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // ── Customisation ─────────────────────────────────────────────────────────

    /** Fallback arc/tick color when not using [SimpleRotaryKnobDrawable]. */
    @ColorInt
    var arcColor: Int = SimpleRotaryKnobDrawable.DEFAULT_IDLE_COLOR
        set(value) { field = value; invalidate() }

    /**
     * Fraction of the total available radius occupied by the knob circle.
     * Everything else (gap + arc + gap + ticks) lives in the remaining fraction.
     * Default 0.72 — knob takes 72 %, outer ring takes 28 %.
     */
    var knobRadiusFraction: Float = 0.72f
        set(value) { field = value.coerceIn(0.1f, 0.95f); recalcGeometry() }

    /** Gap between knob outer edge and near edge of arc, as fraction of available radius. */
    var arcGapFraction: Float = 0.04f
        set(value) { field = value; recalcGeometry() }

    /** Arc stroke width as fraction of available radius. */
    var arcStrokeWidthFraction: Float = 0.02f
        set(value) { field = value; recalcGeometry() }

    /** Gap between far edge of arc and near end of tick, as fraction of available radius. */
    var tickGapFraction: Float = 0.05f
        set(value) { field = value; recalcGeometry() }

    /** Tick length as fraction of available radius. */
    var tickLengthFraction: Float = 0.08f
        set(value) { field = value; recalcGeometry() }

    /** Tick stroke width as fraction of available radius. */
    var tickStrokeWidthFraction: Float = 0.02f
        set(value) { field = value; invalidate() }

    // ── Computed geometry (recalculated in onSizeChanged) ─────────────────────

    private var cx = 0f
    private var cy = 0f
    private var knobRadiusPx = 0f
    private var arcCentreRadiusPx = 0f
    private var arcStrokeWidthPx = 0f
    private var tickStartRadiusPx = 0f
    private var tickEndRadiusPx = 0f
    private var tickStrokeWidthPx = 0f
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

        // When the drawable's color animates, redraw this view so arc/ticks stay in sync
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
    }

    // ── Size ──────────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalcGeometry()
    }

    /**
     * Recomputes all pixel geometry from the current view size and fraction properties.
     * Layout outward from centre:  [knob circle] [arcGap] [arc] [tickGap] [tick]
     * Everything fits within availableRadius = min(w, h) / 2.
     */
    private fun recalcGeometry() {
        if (width == 0 || height == 0) return

        cx = width / 2f
        cy = height / 2f
        val availableRadius = min(width, height) / 2f

        knobRadiusPx        = availableRadius * knobRadiusFraction
        arcStrokeWidthPx    = availableRadius * arcStrokeWidthFraction
        arcCentreRadiusPx   = knobRadiusPx + availableRadius * arcGapFraction + arcStrokeWidthPx / 2f
        tickStrokeWidthPx   = availableRadius * tickStrokeWidthFraction
        tickStartRadiusPx   = arcCentreRadiusPx + arcStrokeWidthPx / 2f + availableRadius * tickGapFraction
        tickEndRadiusPx     = tickStartRadiusPx + availableRadius * tickLengthFraction

        arcOval.set(
                cx - arcCentreRadiusPx, cy - arcCentreRadiusPx,
                cx + arcCentreRadiusPx, cy + arcCentreRadiusPx
        )

        // Update the drawable bounds so it knows its drawing area
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

        // ── Static arc (never rotates) ────────────────────────────────────────
        arcPaint.strokeWidth = arcStrokeWidthPx
        arcPaint.color = color
        canvas.drawArc(arcOval, ARC_START_ANGLE, ARC_SWEEP, false, arcPaint)

        // ── Static tick marks at min and max positions ────────────────────────
        tickPaint.strokeWidth = tickStrokeWidthPx
        tickPaint.color = color
        drawTick(canvas, ARC_START_ANGLE)
        drawTick(canvas, ARC_START_ANGLE + ARC_SWEEP)

        // ── Knob (rotated around its centre) ──────────────────────────────────
        canvas.save()
        canvas.rotate(knobRotation, cx, cy)
        knobDrawable.draw(canvas)
        canvas.restore()
    }

    private fun currentArcColor(): Int =
            (knobDrawable as? SimpleRotaryKnobDrawable)?.currentStateColor ?: arcColor

    private fun drawTick(canvas: Canvas, angleDeg: Float) {
        val rad = Math.toRadians(angleDeg.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        canvas.drawLine(
                cx + cosA * tickStartRadiusPx, cy + sinA * tickStartRadiusPx,
                cx + cosA * tickEndRadiusPx,   cy + sinA * tickEndRadiusPx,
                tickPaint
        )
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                rotationAnimator?.cancel()
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
                listener?.onRotate(angleToValue(knobRotation))
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
        val target = valueToAngle(volume)
        if (animate) {
            pendingVolume = volume
            debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
            debounceRunnable = Runnable { animateRotation(valueToAngle(pendingVolume)) }
            debounceHandler.postDelayed(debounceRunnable!!, 100)
        } else {
            knobRotation = target
            invalidate()
        }
    }

    private fun animateRotation(toAngle: Float) {
        rotationAnimator?.cancel()
        val start = knobRotation
        rotationAnimator = android.animation.ValueAnimator.ofFloat(start, toAngle).apply {
            duration = 300
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener {
                knobRotation = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun setListener(rotaryKnobListener: RotaryKnobListener) {
        this.listener = rotaryKnobListener
    }

    /** Replace the knob drawable. Must be a [RotaryKnobDrawable] subclass. */
    fun setKnobDrawable(drawable: RotaryKnobDrawable) {
        (knobDrawable as? SimpleRotaryKnobDrawable)?.onColorChanged = null
        knobDrawable = drawable
        (knobDrawable as? SimpleRotaryKnobDrawable)?.onColorChanged = { invalidate() }
        recalcGeometry()   // re-sets bounds and redraws
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
        private const val END   =  150f

        // Android canvas: 0° = 3 o'clock, CW+.
        // Knob min (−150° from 12 o'clock) → −90° − 150° = −240° ≡ 120° in canvas coords.
        // Sweep CW 300° reaches max (+150° from 12 o'clock, ≡ 60° in canvas coords).
        private const val ARC_START_ANGLE = 120f
        private const val ARC_SWEEP       = 300f
    }
}
