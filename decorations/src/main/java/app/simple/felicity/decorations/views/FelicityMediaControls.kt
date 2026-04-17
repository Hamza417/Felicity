package app.simple.felicity.decorations.views

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import app.simple.felicity.decoration.R
import app.simple.felicity.decorations.ripple.FelicityRippleDrawable
import app.simple.felicity.decorations.views.FelicityMediaControls.Companion.GAP_DP
import app.simple.felicity.decorations.views.FelicityMediaControls.Companion.SEEK_INTERVAL_MS
import app.simple.felicity.manager.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.manager.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.models.Theme

/**
 * A single fully-custom [View] that draws five media control buttons directly on
 * the canvas — no child views, no ViewGroups, no layout overhead.
 *
 * The five slots from left to right are:
 *   [skip previous] [rewind] [play / pause] [forward] [skip next]
 *
 * Relative sizes (play = 1.0 baseline):
 *   - Play          → 1.00×
 *   - Skip prev/next → 0.70×
 *   - Rewind/forward → 0.60×
 *
 * When music starts playing the play pill grows to 1.15× while the other four
 * shrink proportionally, creating a satisfying bubble-like push effect.
 * The play/pause icon is crossfaded (alpha) between the two drawables.
 *
 * The outer rewind/forward pair can be hidden at runtime via [showSeekButtons].
 *
 * Click and seek callbacks must be wired by the caller via [setMediaControlListener].
 *
 * @author Hamza417
 */
class FelicityMediaControls @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), ThemeChangedListener, Drawable.Callback,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private var mediaControlListener: MediaControlListener? = null

    /**
     * When false, the two outer rewind/forward seek buttons are hidden and only
     * the three core buttons (previous, play, next) are drawn.
     */
    var showSeekButtons: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
        }

    // Button slot indices — keep these stable so array lookups are simple
    companion object {
        const val BTN_REWIND = 0
        const val BTN_PREVIOUS = 1
        const val BTN_PLAY = 2
        const val BTN_NEXT = 3
        const val BTN_FORWARD = 4
        const val BTN_COUNT = 5

        // Relative size of each button slot (play = 1.0)
        const val PLAY_RATIO = 1.00f
        const val PREV_NEXT_RATIO = 0.70f
        const val FWD_REW_RATIO = 0.60f

        // Minimum gap between adjacent pills in dp
        const val GAP_DP = 3f

        // How often the seek step fires while the button is held down (in milliseconds)
        const val SEEK_INTERVAL_MS = 500L

        interface MediaControlListener {
            /** Called when the user taps the skip-previous button. */
            fun onPreviousClick()

            /** Called when the user taps the skip-next button. */
            fun onNextClick()

            /** Called when the user taps the play/pause button. */
            fun onPlayClick()

            /**
             * Called once per seek step — both on a quick tap and repeatedly while the
             * forward button is held. The caller should seek the player forward by its
             * preferred step amount each time this fires.
             */
            fun onForwardStep()

            /**
             * Called once per seek step — both on a quick tap and repeatedly while the
             * rewind button is held. The caller should seek the player backward by its
             * preferred step amount each time this fires.
             */
            fun onRewindStep()
        }
    }

    // Playback state
    private var isPlaying = false

    // Per-group animated scale values — animated smoothly on play/pause transitions
    private var playScale = 1f
    private var prevNextScale = 1f
    private var fwdRewScale = 1f

    // Play/pause crossfade: play icon is visible when paused, pause icon when playing
    private var playIconAlpha = 255   // 255 = fully visible
    private var pauseIconAlpha = 0

    // Unscaled center positions and half-sizes for each button slot
    private val btnCx = FloatArray(BTN_COUNT)
    private val btnCy = FloatArray(BTN_COUNT)
    private val btnHalf = FloatArray(BTN_COUNT)   // half the side length (unscaled)
    private var gapPx = 0f

    // Drawing
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val drawRect = RectF()

    // Icon drawables (theme-tinted, mutable copies so setAlpha/setTint do not affect other users)
    private lateinit var icPlay: Drawable
    private lateinit var icPause: Drawable
    private lateinit var icPrevious: Drawable
    private lateinit var icNext: Drawable
    private lateinit var icRewind: Drawable
    private lateinit var icForward: Drawable

    // One ripple drawable per button — driven via Drawable.Callback to trigger invalidate()
    private val ripples = arrayOfNulls<FelicityRippleDrawable>(BTN_COUNT)

    // Touch tracking
    private val handler = Handler(Looper.getMainLooper())
    private var pressedButton = -1
    private var isPulsing = false
    private var holdRunnable: Runnable? = null

    // Cached theme values
    private var highlightColor = 0
    private var iconColor = 0
    private var accentColor = 0

    // Animators
    private var scaleAnim: ValueAnimator? = null
    private var fadeAnim: ValueAnimator? = null

    init {
        isClickable = true
        isFocusable = true
        if (!isInEditMode) {
            pullTheme()
            loadIcons()
            buildRipples()
        }
    }

    fun setMediaControlListener(listener: MediaControlListener) {
        mediaControlListener = listener
    }

    // ── Theme helpers ──────────────────────────────────────────────────────────

    /** Reads the current theme and accent colors into local fields. */
    private fun pullTheme() {
        highlightColor = ThemeManager.theme.viewGroupTheme.highlightColor
        iconColor = ThemeManager.theme.iconTheme.regularIconColor
        accentColor = ThemeManager.accent.primaryAccentColor
    }

    /** Loads (or reloads) all six icon drawables and applies the current icon tint. */
    private fun loadIcons() {
        fun load(res: Int) = ContextCompat.getDrawable(context, res)!!.mutate()
        icPlay = load(R.drawable.ic_play)
        icPause = load(R.drawable.ic_pause)
        icPrevious = load(R.drawable.ic_skip_previous)
        icNext = load(R.drawable.ic_skip_next)
        icRewind = load(R.drawable.ic_fast_rewind)
        icForward = load(R.drawable.ic_fast_forward)
        tintIcons()
    }

    private fun tintIcons() {
        for (d in listOf(icPlay, icPause, icPrevious, icNext, icRewind, icForward)) {
            d.setTint(iconColor)
        }
    }

    /**
     * Creates one [FelicityRippleDrawable] per button and registers this view as
     * the callback so that ripple animation frames trigger [invalidate].
     *
     * A very large corner radius is used so the ripple is always clipped to a
     * circle (the pills are square, so radius ≥ half-side = circle).
     */
    private fun buildRipples() {
        for (i in 0 until BTN_COUNT) {
            ripples[i] = FelicityRippleDrawable(accentColor).apply {
                setCornerRadius(10_000f)   // cap forces circular clip regardless of pill size
                setStartColor(highlightColor)
                callback = this@FelicityMediaControls
            }
        }
    }

    private fun refreshRippleColors() {
        for (r in ripples) {
            r?.setRippleColor(accentColor)
            r?.setStartColor(highlightColor)
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Natural height = play button diameter, which equals one unit of baseSize
        val desired = resources.getDimensionPixelSize(R.dimen.play_button_size) +
                paddingTop + paddingBottom
        val h = resolveSize(desired, heightMeasureSpec)
        val w = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(suggested5ButtonWidth())
        setMeasuredDimension(w, h)
    }

    /** Fallback minimum width when the parent gives no width constraint. */
    private fun suggested5ButtonWidth(): Int {
        val ref = resources.getDimensionPixelSize(R.dimen.play_button_size)
        return (ref * (PLAY_RATIO + 2 * PREV_NEXT_RATIO + 2 * FWD_REW_RATIO) + 4 * GAP_DP *
                resources.displayMetrics.density).toInt()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeLayout(w, h)
    }

    /**
     * Computes the center and unscaled half-size for each button slot so that all
     * buttons together fill the available width with [GAP_DP] of breathing room between
     * each pair, and the tallest button (play) fills the available height exactly.
     */
    private fun computeLayout(w: Int, h: Int) {
        gapPx = GAP_DP * resources.displayMetrics.density
        val contentW = (w - paddingLeft - paddingRight).toFloat()
        val contentH = (h - paddingTop - paddingBottom).toFloat()
        val midY = paddingTop + contentH / 2f

        if (showSeekButtons) {
            // 5 slots — total weight = 0.6 + 0.7 + 1.0 + 0.7 + 0.6 = 3.6
            val totalWeight = FWD_REW_RATIO + PREV_NEXT_RATIO + PLAY_RATIO + PREV_NEXT_RATIO + FWD_REW_RATIO
            val totalGaps = 4 * gapPx
            // Base size so that all 5 pills fit within available width at their ratios
            val base = minOf(contentH, (contentW - totalGaps) / totalWeight)

            btnHalf[BTN_REWIND] = base * FWD_REW_RATIO / 2f
            btnHalf[BTN_PREVIOUS] = base * PREV_NEXT_RATIO / 2f
            btnHalf[BTN_PLAY] = base * PLAY_RATIO / 2f
            btnHalf[BTN_NEXT] = base * PREV_NEXT_RATIO / 2f
            btnHalf[BTN_FORWARD] = base * FWD_REW_RATIO / 2f

            val totalW = btnHalf.sum() * 2f + totalGaps
            var x = paddingLeft + (contentW - totalW) / 2f

            for (btn in 0 until BTN_COUNT) {
                btnCx[btn] = x + btnHalf[btn]
                btnCy[btn] = midY
                x += btnHalf[btn] * 2f + gapPx
            }
        } else {
            // 3 slots — total weight = 0.7 + 1.0 + 0.7 = 2.4
            val totalWeight = PREV_NEXT_RATIO + PLAY_RATIO + PREV_NEXT_RATIO
            val totalGaps = 2 * gapPx
            val base = minOf(contentH, (contentW - totalGaps) / totalWeight)

            btnHalf[BTN_PREVIOUS] = base * PREV_NEXT_RATIO / 2f
            btnHalf[BTN_PLAY] = base * PLAY_RATIO / 2f
            btnHalf[BTN_NEXT] = base * PREV_NEXT_RATIO / 2f
            btnHalf[BTN_REWIND] = 0f
            btnHalf[BTN_FORWARD] = 0f

            val totalW = (btnHalf[BTN_PREVIOUS] + btnHalf[BTN_PLAY] + btnHalf[BTN_NEXT]) * 2f + totalGaps
            var x = paddingLeft + (contentW - totalW) / 2f

            for (btn in intArrayOf(BTN_PREVIOUS, BTN_PLAY, BTN_NEXT)) {
                btnCx[btn] = x + btnHalf[btn]
                btnCy[btn] = midY
                x += btnHalf[btn] * 2f + gapPx
            }
        }
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val slots = if (showSeekButtons) intArrayOf(0, 1, 2, 3, 4) else intArrayOf(1, 2, 3)
        for (btn in slots) {
            drawSlot(canvas, btn)
        }
    }

    /**
     * Draws one button slot: background pill → ripple overlay → icon (or crossfaded
     * play/pause icons for the play slot).
     */
    private fun drawSlot(canvas: Canvas, btn: Int) {
        val scale = currentScale(btn)
        val hs = btnHalf[btn] * scale   // scaled half-size
        val cx = btnCx[btn]
        val cy = btnCy[btn]

        // The pill is a square with corner radius = half-side, making it a circle
        drawRect.set(cx - hs, cy - hs, cx + hs, cy + hs)

        // Background highlight
        bgPaint.color = highlightColor
        canvas.drawRoundRect(drawRect, hs, hs, bgPaint)

        // Ripple — set bounds each frame since the pill size changes with scale
        val ripple = ripples[btn]
        if (ripple != null) {
            ripple.setBounds(drawRect.left.toInt(), drawRect.top.toInt(),
                             drawRect.right.toInt(), drawRect.bottom.toInt())
            ripple.draw(canvas)
        }

        // Icon — leave 22% padding on each side so it breathes inside the pill
        val margin = hs * 0.22f
        val il = (cx - hs + margin).toInt()
        val it = (cy - hs + margin).toInt()
        val ir = (cx + hs - margin).toInt()
        val ib = (cy + hs - margin).toInt()

        if (btn == BTN_PLAY) {
            // Crossfade: play icon fades out, pause icon fades in (and vice versa)
            icPlay.setBounds(il, it, ir, ib)
            icPlay.alpha = playIconAlpha
            icPlay.draw(canvas)

            icPause.setBounds(il, it, ir, ib)
            icPause.alpha = pauseIconAlpha
            icPause.draw(canvas)
        } else {
            val icon = when (btn) {
                BTN_PREVIOUS -> icPrevious
                BTN_NEXT -> icNext
                BTN_REWIND -> icRewind
                BTN_FORWARD -> icForward
                else -> return
            }
            icon.alpha = 255
            icon.setBounds(il, it, ir, ib)
            icon.draw(canvas)
        }
    }

    /** Returns the live animated scale for [btn]. */
    private fun currentScale(btn: Int) = when (btn) {
        BTN_PLAY -> playScale
        BTN_PREVIOUS, BTN_NEXT -> prevNextScale
        BTN_REWIND, BTN_FORWARD -> fwdRewScale
        else -> 1f
    }

    // ── Playback state ────────────────────────────────────────────────────────

    /**
     * Updates the play/pause state. When [animate] is true, the icon crossfade and
     * scale bubble animations run simultaneously so the transition feels snappy.
     *
     * When paused → playing: play button grows to 1.15×, others shrink.
     * When playing → paused: everything returns to 1×.
     */
    fun setPlaying(playing: Boolean, animate: Boolean = true) {
        if (isPlaying == playing && animate) return
        isPlaying = playing

        // Where each group needs to end up
        val toPlayScale = if (playing) 1.15f else 1f
        val toPrevNextScale = if (playing) 0.90f else 1f
        val toFwdRewScale = if (playing) 0.85f else 1f
        val toPlayIcon = if (playing) 0 else 255
        val toPauseIcon = if (playing) 255 else 0

        if (!animate) {
            playScale = toPlayScale
            prevNextScale = toPrevNextScale
            fwdRewScale = toFwdRewScale
            playIconAlpha = toPlayIcon
            pauseIconAlpha = toPauseIcon
            invalidate()
            return
        }

        // Capture start values so the animator interpolates from wherever we are now
        val fromPlay = playScale
        val fromPrevNext = prevNextScale
        val fromFwdRew = fwdRewScale
        val fromPlayIcon = playIconAlpha
        val fromPauseIcon = pauseIconAlpha

        scaleAnim?.cancel()
        scaleAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280L
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener {
                val t = it.animatedFraction
                playScale = fromPlay + (toPlayScale - fromPlay) * t
                prevNextScale = fromPrevNext + (toPrevNextScale - fromPrevNext) * t
                fwdRewScale = fromFwdRew + (toFwdRewScale - fromFwdRew) * t
                invalidate()
            }
            start()
        }

        fadeAnim?.cancel()
        fadeAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200L
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener {
                val t = it.animatedFraction
                playIconAlpha = (fromPlayIcon + (toPlayIcon - fromPlayIcon) * t).toInt()
                pauseIconAlpha = (fromPauseIcon + (toPauseIcon - fromPauseIcon) * t).toInt()
                invalidate()
            }
            start()
        }
    }

    // ── Touch handling ────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val btn = hitTest(event.x, event.y)
                if (btn < 0) return false
                pressedButton = btn
                isPulsing = false

                // Arm the ripple
                val hs = btnHalf[btn] * currentScale(btn)
                val r = ripples[btn] ?: return true
                r.setBounds(
                        (btnCx[btn] - hs).toInt(), (btnCy[btn] - hs).toInt(),
                        (btnCx[btn] + hs).toInt(), (btnCy[btn] + hs).toInt()
                )
                r.setHotspot(event.x, event.y)
                r.state = intArrayOf(android.R.attr.state_pressed)

                // Queue the hold-pulse for seek buttons
                if (btn == BTN_FORWARD || btn == BTN_REWIND) {
                    scheduleHoldPulse(btn)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val btn = pressedButton
                if (btn >= 0) {
                    ripples[btn]?.state = intArrayOf()
                }
                cancelHoldPulse()

                // Fire click only if this was a clean tap (no pulsing, no drag)
                if (btn >= 0 && !isPulsing) {
                    when (btn) {
                        BTN_PREVIOUS -> mediaControlListener?.onPreviousClick()
                        BTN_NEXT -> mediaControlListener?.onNextClick()
                        BTN_PLAY -> mediaControlListener?.onPlayClick()
                        BTN_FORWARD -> mediaControlListener?.onForwardStep()
                        BTN_REWIND -> mediaControlListener?.onRewindStep()
                    }
                }
                isPulsing = false
                pressedButton = -1
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // If the finger dragged off the originally pressed button, abort the press
                if (pressedButton >= 0 && hitTest(event.x, event.y) != pressedButton) {
                    ripples[pressedButton]?.state = intArrayOf()
                    cancelHoldPulse()
                    isPulsing = true
                    pressedButton = -1
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (pressedButton >= 0) {
                    ripples[pressedButton]?.state = intArrayOf()
                    cancelHoldPulse()
                }
                isPulsing = false
                pressedButton = -1
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Returns which button slot [x], [y] falls inside, or -1 if none.
     * The hit area scales with the button's current animated size.
     */
    private fun hitTest(x: Float, y: Float): Int {
        val slots = if (showSeekButtons) intArrayOf(0, 1, 2, 3, 4) else intArrayOf(1, 2, 3)
        for (btn in slots) {
            val hs = btnHalf[btn] * currentScale(btn)
            if (x in (btnCx[btn] - hs)..(btnCx[btn] + hs) &&
                    y in (btnCy[btn] - hs)..(btnCy[btn] + hs)) {
                return btn
            }
        }
        return -1
    }

    /**
     * Starts a delayed pulse runnable so the seek button fires repeatedly while held.
     * The first pulse fires after the standard long-press timeout; subsequent ones fire
     * every [SEEK_INTERVAL_MS].
     */
    private fun scheduleHoldPulse(btn: Int) {
        holdRunnable = object : Runnable {
            override fun run() {
                isPulsing = true
                if (btn == BTN_FORWARD) {
                    mediaControlListener?.onForwardStep()
                } else {
                    mediaControlListener?.onRewindStep()
                }
                handler.postDelayed(this, SEEK_INTERVAL_MS)
            }
        }
        handler.postDelayed(holdRunnable!!, ViewConfiguration.getLongPressTimeout().toLong())
    }

    private fun cancelHoldPulse() {
        holdRunnable?.let { handler.removeCallbacks(it) }
        holdRunnable = null
    }

    // ── Drawable.Callback — lets ripple animations trigger redraws ─────────

    override fun invalidateDrawable(drawable: Drawable) {
        invalidate()
    }

    override fun scheduleDrawable(drawable: Drawable, what: Runnable, `when`: Long) {
        handler.postAtTime(what, `when`)
    }

    override fun unscheduleDrawable(drawable: Drawable, what: Runnable) {
        handler.removeCallbacks(what)
    }

    // ── Theme change listeners ─────────────────────────────────────────────

    private fun applyThemeChanges() {
        pullTheme()
        tintIcons()
        refreshRippleColors()
        invalidate()
    }

    override fun onThemeChanged(theme: Theme, animate: Boolean) = applyThemeChanges()
    override fun onAccentChanged(accent: Accent) = applyThemeChanges()

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == AppearancePreferences.ACCENT_COLOR || key == AppearancePreferences.APP_CORNER_RADIUS) {
            applyThemeChanges()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            ThemeManager.addListener(this)
            registerSharedPreferenceChangeListener()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (!isInEditMode) {
            ThemeManager.removeListener(this)
            unregisterSharedPreferenceChangeListener()
        }
        cancelHoldPulse()
        scaleAnim?.cancel()
        fadeAnim?.cancel()
    }
}