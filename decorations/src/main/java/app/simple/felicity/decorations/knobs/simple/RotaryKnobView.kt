package app.simple.felicity.decorations.knobs.simple

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import app.simple.felicity.decoration.R
import app.simple.felicity.shared.helpers.ImageHelper.toBitmapDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2

// TODO - fix over scrolling
@SuppressLint("ClickableViewAccessibility")
class RotaryKnobView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : RelativeLayout(context, attrs, defStyleAttr) {

    private var vibration: Vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    private var lastMoveAngle = 0F
    private var lastDialAngle = 0F
    private var startAngle = 0F
    private var lastAngle = 0F

    private var knobImageView: ImageView
    var knobDrawable: TransitionDrawable
    private var springAnimation: SpringAnimation? = null
    private val debounceHandler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null

    private var pendingVolume: Float = 0f
    var value = 130
    private var haptic = false
    private var listener: RotaryKnobListener? = null

    init {

        val view = LayoutInflater.from(context).inflate(R.layout.knob, this, true)
        knobImageView = view.findViewById(R.id.knobImageView)

        context.theme.obtainStyledAttributes(attrs, R.styleable.RotaryKnobView, 0, 0).apply {
            try {
                value = getInt(R.styleable.RotaryKnobView_initialValue, 50)

                knobDrawable = TransitionDrawable(
                        arrayOf<Drawable>(
                                R.drawable.knob_normal.toBitmapDrawable(context, resources.getDimensionPixelSize(R.dimen.volume_knob_dimension)),
                                R.drawable.knob_pressed.toBitmapDrawable(context, resources.getDimensionPixelSize(R.dimen.volume_knob_dimension))
                        )
                )

                knobDrawable.isCrossFadeEnabled = true

                knobImageView.setImageDrawable(knobDrawable)
            } finally {
                recycle()
            }
        }

        this.setOnTouchListener(MyOnTouchListener())
    }

    /**
     * We're only interested in e2 - the coordinates of the end movement.
     * We calculate the polar angle (Theta) from these coordinates and use these to animate the
     * knob movement and calculate the value
     */
    private inner class MyOnTouchListener : OnTouchListener {
        override fun onTouch(v: View?, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    knobDrawable.startTransition(500)
                    lastMoveAngle = calculateAngle(event.x, event.y)
                    lastDialAngle = knobImageView.rotation
                    startAngle = lastMoveAngle
                    feedback()
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    requestDisallowInterceptTouchEvent(true)

                    val currentAngle = calculateAngle(event.x, event.y)
                    var angleDelta = currentAngle - lastMoveAngle

                    // Handle wrap-around (e.g., from 179 to -179 degrees)
                    if (angleDelta > 180) angleDelta -= 360
                    if (angleDelta < -180) angleDelta += 360

                    var newAngle = knobImageView.rotation + angleDelta
                    newAngle = newAngle.coerceIn(START, END)

                    knobImageView.rotation = newAngle
                    lastAngle = newAngle
                    lastMoveAngle = currentAngle

                    listener?.onRotate(angleToValue(newAngle))
                    listener?.onIncrement(abs(angleDelta))
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    knobDrawable.reverseTransition(300)
                    return true
                }
            }

            return true
        }
    }

    /**
     * Calculate the angle from x,y coordinates of the touch event
     * The 0,0 coordinates in android are the top left corner of the view.
     * Dividing x and y by height and width we normalize them to the range of 0 - 1 values:
     * (0,0) top left, (1,1) bottom right.
     * While x's direction is correct - going up from left to right, y's isn't - it's
     * lowest value is at the top. W
     * So we reverse it by subtracting y from 1.
     * Now x is going from 0 (most left) to 1 (most right),
     * and Y is going from 0 (most downwards) to 1 (most upwards).
     * We now need to bring 0,0 to the middle - so subtract 0.5 from both x and y.
     * now 0,0 is in the middle, 0, 0.5 is at 12 o'clock and 0.5, 0 is at 3 o'clock.
     * Now that we have the coordinates in proper cartesian coordinate system - and we can calculate
     * "theta" - the angle between the x axis and the point by calculating atan2(y,x).
     * However, theta is the angle between the x axis and the point, and it rises as we turn
     * counter-clockwise. In addition, atan2 returns (in radians) angles in the range of -180
     * through 180 degrees (-PI through PI). And we want 0 to be at 12 o'clock.
     * So we reverse the direction of the angle by prefixing it with a minus sign,
     * and add 90 to move the "zero degrees" point north (taking care to handling the range between
     * 180 and 270 degrees, bringing them to their proper values of -180 .. -90 by adding 360 to the
     * value.
     *
     * @param x - x coordinate of the touch event
     * @param y - y coordinate of the touch event
     * @return
     */
    private fun calculateAngle(x: Float, y: Float): Float {
        val px = (x / width.toFloat()) - 0.5
        val py = (1 - y / height.toFloat()) - 0.5
        var angle = -(Math.toDegrees(atan2(py, px)))
            .toFloat() + 90
        if (angle > 180) angle -= 360
        return angle
    }

    fun setKnobPosition(volume: Float, animate: Boolean = true) {
        if (animate) {
            pendingVolume = volume
            debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
            debounceRunnable = Runnable {
                animateRotation(valueToAngle(pendingVolume))
            }
            debounceHandler.postDelayed(debounceRunnable!!, 100) // 100ms debounce
        } else {
            knobImageView.rotation = valueToAngle(volume)
        }
    }

    private fun animateRotation(toAngle: Float) {
        springAnimation?.cancel()
        springAnimation = SpringAnimation(knobImageView, SpringAnimation.ROTATION, toAngle).apply {
            spring.stiffness = SpringForce.STIFFNESS_LOW
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            start()
        }
    }

    fun setListener(rotaryKnobListener: RotaryKnobListener) {
        this.listener = rotaryKnobListener
    }

    private fun angleToValue(angle: Float): Float {
        val normalized = (angle - START) / (END - START)
        return (normalized * 100).coerceIn(0F, 100F)
    }

    private fun valueToAngle(volume: Float): Float {
        return START + (volume / 100f) * (END - START)
    }

    private fun feedback() {
        CoroutineScope(Dispatchers.Default).launch {
            if (haptic) {
                vibration.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    companion object {
        private const val TAG = "RotaryKnobView"
        private const val START = -150F
        private const val END = 150F
    }
}
