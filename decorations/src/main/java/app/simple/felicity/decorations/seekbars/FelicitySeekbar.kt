package app.simple.felicity.decorations.seekbars

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorInt
import androidx.core.graphics.withScale
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import app.simple.felicity.decoration.R
import app.simple.felicity.decorations.typeface.TypeFace
import app.simple.felicity.manager.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.manager.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.themes.Theme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FelicitySeekbar @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), SharedPreferences.OnSharedPreferenceChangeListener, ThemeChangedListener {

    interface OnSeekChangeListener {
        fun onProgressChanged(seekbar: FelicitySeekbar, progress: Float, fromUser: Boolean)
        fun onStartTrackingTouch(seekbar: FelicitySeekbar) {}
        fun onStopTrackingTouch(seekbar: FelicitySeekbar) {}
    }

    fun interface SideLabelProvider {
        /**
         * Called whenever the seekbar needs to render the side labels.
         * Return a [Pair] where [Pair.first] is the left label and [Pair.second] is the right label.
         * Return null strings to hide a specific label.
         */
        fun getLabels(progress: Float, min: Float, max: Float): Pair<String?, String?>
    }

    /** Provides the formatted string for the left-side label. */
    fun interface LeftLabelProvider {
        fun getLabel(progress: Float, min: Float, max: Float): String?
    }

    /** Provides the formatted string for the right-side label. */
    fun interface RightLabelProvider {
        fun getLabel(progress: Float, min: Float, max: Float): String?
    }

    private var listener: OnSeekChangeListener? = null

    // Range: [minProgress..maxProgress]
    private var minProgress = 0f
    private var maxProgress = 100f
    private var progressInternal = 0f // current float progress for animation, in absolute units within [min..max]
    private var defaultProgress = 0f // value to reset to on double tap
    private var hasDefaultSet = false // only reset if explicitly configured
    private var defaultIndicatorEnabled = true // can be disabled explicitly

    @ColorInt
    private var trackColor: Int = if (isInEditMode) {
        Color.LTGRAY
    } else {
        ThemeManager.theme.viewGroupTheme.highlightColor
    }

    @ColorInt
    private var progressColor: Int = if (isInEditMode) {
        Color.BLUE
    } else {
        ThemeManager.accent.primaryAccentColor
    }

    @ColorInt
    private var thumbRingColor: Int = if (isInEditMode) {
        Color.WHITE
    } else {
        ThemeManager.theme.viewGroupTheme.backgroundColor
    }

    @ColorInt
    private var thumbInnerColor: Int = if (isInEditMode) {
        Color.TRANSPARENT
    } else {
        thumbRingColor
    }

    private var trackHeightPx: Float
    private var thumbRadiusPx: Float
    private var thumbRingWidthPx: Float

    // New: horizontal width of pill-shaped thumb (full width, not half). Default set after radius init.
    private var thumbWidthPx: Float

    private var smudgeEnabled = true
    private var smudgeRadius = 10f
    private var smudgeColor = progressColor
    private var smudgeOffsetY = 0f
    private var thumbShadowRadius = 0f
    private var thumbShadowColor = progressColor

    // Optional overrides for corner radii (rx=ry) of thumb fill only (ring follows thumb)
    private var thumbCornerRadiusPxOverride: Float? = null

    // Thumb shape selection: PILL or OVAL (circle/ellipse)
    enum class ThumbShape { PILL, OVAL, CIRCLE }

    private var thumbShape: ThumbShape = ThumbShape.OVAL

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val thumbRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val thumbInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val smudgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val thumbShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // Press ring paint (MD2-style halo around thumb on press)
    private val thumbPressRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    // New: default indicator paint (drawn above progress)
    private val defaultIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val trackRect = RectF()
    private val smudgeRect = RectF()
    private val progressRect = RectF()

    // Reuse rect for default indicator to avoid allocations during draw
    private val defaultIndicatorRect = RectF()

    // Reusable temp rects for thumb drawing to avoid allocations
    private val thumbOuterRect = RectF()
    private val thumbStrokeRect = RectF()
    private val thumbInnerRect = RectF()

    // Extra rect for press ring
    private val thumbPressRingRect = RectF()

    // ----- Side label support -----
    private var leftLabelEnabled = false
    private var rightLabelEnabled = false
    private var leftLabelProvider: LeftLabelProvider? = null
    private var rightLabelProvider: RightLabelProvider? = null

    @ColorInt
    private var labelTextColor: Int = if (isInEditMode) Color.WHITE
    else ThemeManager.theme.textViewTheme.secondaryTextColor

    private var labelTextSize: Float  // set in init

    /**
     * Extra gap in px added on top of the mandatory minimum clearance.
     * Minimum clearance = thumb half-width + press-ring outset (computed at draw time).
     * This value is customisable via [setLabelGap] or the XML attr felicityLabelGap.
     */
    private var labelGapPx: Float = 0f // set in init; 0 means "just the mandatory clearance"

    // Per-side animated widths (text width only — gap is added on top when computing reserve)
    private var leftLabelAnimatedWidth = 0f
    private var rightLabelAnimatedWidth = 0f

    // Per-side animated alpha
    private var leftLabelAlpha = 0f
    private var rightLabelAlpha = 0f

    // Shared scale — both labels scale together during tracking
    private var labelScale = 1f

    // Optional solid background pill drawn behind each label
    private var labelBackgroundEnabled = false

    @ColorInt
    private var labelBackgroundColor: Int = if (isInEditMode) Color.DKGRAY
    else ThemeManager.theme.viewGroupTheme.highlightColor

    private var labelBackgroundCornerRadius = 0f
    private var labelBackgroundPaddingH = 0f
    private var labelBackgroundPaddingV = 0f

    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelBgRect = RectF()

    private var leftLabelWidthAnimator: ValueAnimator? = null
    private var rightLabelWidthAnimator: ValueAnimator? = null
    private var leftLabelAlphaAnimator: ValueAnimator? = null
    private var rightLabelAlphaAnimator: ValueAnimator? = null
    private var labelScaleAnimator: ValueAnimator? = null

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
    }

    // Cached label strings — refreshed when progress or providers change, never inside onDraw
    private var cachedLeftLabel: String? = null
    private var cachedRightLabel: String? = null

    // setMaxWithReset sequencing flag
    private var pendingMaxAfterReset: Float? = null
    private var pendingProgressAfterReset: Float? = null
    private var isResetAnimationInFlight = false

    // Default indicator configuration
    @ColorInt
    private var defaultIndicatorColor: Int = if (isInEditMode) {
        Color.WHITE
    } else {
        ThemeManager.accent.secondaryAccentColor
    }
    private var defaultIndicatorWidthPx: Float

    private var isDragging = false
    private var thumbScale = 1f
    private var thumbScaleAnimator: ValueAnimator? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var downOnThumb = false

    // Press ring animation
    private var pressRingProgress = 0f // 0..1
    private var pressRingAnimator: ValueAnimator? = null
    private var pressRingOutsetPx: Float
    private var pressRingStrokePx: Float

    @ColorInt
    private var pressRingColor: Int

    // Helper: total outward outset for press ring including half the stroke
    private fun pressRingTotalOutset(): Float = pressRingOutsetPx + (pressRingStrokePx / 2f)

    // Gesture detection for double-tap to reset
    private var consumedDoubleTap = false
    private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (hasDefaultSet) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                resetToDefault(animate = true)
                consumedDoubleTap = true
                startPressRing(false)
                return true
            }
            return false
        }
    }
    private val gestureDetector = GestureDetector(context, gestureListener).apply {
        setOnDoubleTapListener(gestureListener)
    }

    // Spring animation support
    private val progressProperty = object : FloatPropertyCompat<FelicitySeekbar>("felicityProgress") {
        override fun getValue(view: FelicitySeekbar): Float = view.progressInternal
        override fun setValue(view: FelicitySeekbar, value: Float) {
            view.progressInternal = value.coerceIn(minProgress, maxProgress)
            invalidate()
            // During animation, treat as programmatic (fromUser = false)
            listener?.onProgressChanged(this@FelicitySeekbar, getProgress(), false)
        }
    }

    private var animateFromUser = false

    private val springAnimation = SpringAnimation(this, progressProperty).apply {
        spring = SpringForce().apply {
            stiffness = SpringForce.STIFFNESS_VERY_LOW
            dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
        }
        addEndListener { _, _, _, _ ->
            listener?.onProgressChanged(this@FelicitySeekbar, getProgress(), animateFromUser)
            // restore base spring if it was altered for fast snap
            spring?.stiffness = baseStiffness
            spring?.dampingRatio = baseDamping
        }
    }

    private val baseStiffness = SpringForce.STIFFNESS_LOW
    private val baseDamping = SpringForce.DAMPING_RATIO_NO_BOUNCY

    private val fastStiffness = SpringForce.STIFFNESS_HIGH
    private val fastDamping = SpringForce.DAMPING_RATIO_NO_BOUNCY

    // Animator to drive progress changes smoothly (replaces spring for progress)
    private var progressAnimator: ValueAnimator? = null
    private var progressAnimFromUser: Boolean = false

    init {
        val d = resources.displayMetrics.density
        trackHeightPx = 4f * d
        thumbRadiusPx = 12f * d
        thumbRingWidthPx = 4f * d
        // Default pill width: 3x radius (i.e., 1.5x diameter)
        thumbWidthPx = thumbRadiusPx * 3f
        // MD2 press ring defaults
        pressRingOutsetPx = 6f * d
        pressRingStrokePx = 2f * d
        pressRingColor = progressColor
        // Default indicator stroke width
        defaultIndicatorWidthPx = 2f * d
        // Label defaults
        labelTextSize = 12f * d
        labelGapPx = 8f * d          // extra gap on top of the mandatory thumb+ring clearance
        labelBackgroundCornerRadius = 6f * d
        labelBackgroundPaddingH = 6f * d
        labelBackgroundPaddingV = 3f * d

        context.theme.obtainStyledAttributes(attrs, R.styleable.FelicitySeekbar, defStyleAttr, 0).apply {
            try {
                if (hasValue(R.styleable.FelicitySeekbar_felicityMin)) {
                    setMinInternal(getFloat(R.styleable.FelicitySeekbar_felicityMin, minProgress))
                }
                if (hasValue(R.styleable.FelicitySeekbar_felicityMax)) {
                    setMaxInternal(getFloat(R.styleable.FelicitySeekbar_felicityMax, maxProgress))
                }
                if (hasValue(R.styleable.FelicitySeekbar_felicityProgress)) {
                    val tv = peekValue(R.styleable.FelicitySeekbar_felicityProgress)
                    progressInternal = when (tv.type) {
                        TypedValue.TYPE_FLOAT -> getFloat(R.styleable.FelicitySeekbar_felicityProgress, minProgress)
                        else -> getFloat(R.styleable.FelicitySeekbar_felicityProgress, minProgress)
                    }
                }
                progressInternal = progressInternal.coerceIn(minProgress, maxProgress)

                trackColor = getColor(R.styleable.FelicitySeekbar_felicityTrackColor, trackColor)
                progressColor = getColor(R.styleable.FelicitySeekbar_felicityProgressColor, progressColor)
                thumbRingColor = getColor(R.styleable.FelicitySeekbar_felicityThumbRingColor, thumbRingColor)
                thumbInnerColor = getColor(R.styleable.FelicitySeekbar_felicityThumbInnerColor, thumbInnerColor)
                trackHeightPx = getDimension(R.styleable.FelicitySeekbar_felicityTrackHeight, trackHeightPx)
                thumbRadiusPx = getDimension(R.styleable.FelicitySeekbar_felicityThumbRadius, thumbRadiusPx)
                thumbWidthPx = getDimension(R.styleable.FelicitySeekbar_felicityThumbWidth, thumbWidthPx)
                thumbRingWidthPx = getDimension(R.styleable.FelicitySeekbar_felicityThumbRingWidth, thumbRingWidthPx)
                smudgeEnabled = getBoolean(R.styleable.FelicitySeekbar_felicitySmudgeEnabled, smudgeEnabled)
                smudgeRadius = getDimension(R.styleable.FelicitySeekbar_felicitySmudgeRadius, 2f * d)
                smudgeColor = getColor(R.styleable.FelicitySeekbar_felicitySmudgeColor, smudgeColor)
                smudgeOffsetY = getDimension(R.styleable.FelicitySeekbar_felicitySmudgeOffsetY, 0f)
                thumbShadowRadius = getDimension(R.styleable.FelicitySeekbar_felicityThumbShadowRadius, 6f * d)
                thumbShadowColor = getColor(R.styleable.FelicitySeekbar_felicityThumbShadowColor, thumbShadowColor)
                defaultIndicatorColor = getColor(R.styleable.FelicitySeekbar_felicityDefaultIndicatorColor, thumbRingColor)
                // New: read thumb shape enum (0=pill, 1=oval, 2=circle)
                if (hasValue(R.styleable.FelicitySeekbar_felicityThumbShape)) {
                    thumbShape = when (getInt(R.styleable.FelicitySeekbar_felicityThumbShape, 0)) {
                        1 -> ThumbShape.OVAL
                        2 -> ThumbShape.CIRCLE
                        else -> ThumbShape.PILL
                    }
                }
                // Label attrs
                val labelsEnabledXml = getBoolean(R.styleable.FelicitySeekbar_felicityLabelsEnabled, false)
                labelTextSize = getDimension(R.styleable.FelicitySeekbar_felicityLabelTextSize, labelTextSize)
                labelTextColor = getColor(R.styleable.FelicitySeekbar_felicityLabelTextColor, labelTextColor)
                labelGapPx = getDimension(R.styleable.FelicitySeekbar_felicityLabelGap, labelGapPx)
                labelBackgroundEnabled = getBoolean(R.styleable.FelicitySeekbar_felicityLabelBackgroundEnabled, labelBackgroundEnabled)
                labelBackgroundColor = getColor(R.styleable.FelicitySeekbar_felicityLabelBackgroundColor, labelBackgroundColor)
                labelBackgroundCornerRadius = getDimension(R.styleable.FelicitySeekbar_felicityLabelBackgroundCornerRadius, labelBackgroundCornerRadius)
                if (labelsEnabledXml) {
                    leftLabelEnabled = true
                    rightLabelEnabled = true
                }
            } finally {
                recycle()
            }
        }

        // TODO - We are overriding thumb style, might need to change later otherwise thumbShape won't work
        applyThumbPreferences()

        // If width is smaller than diameter, coerce to diameter to avoid inverted corners
        thumbWidthPx = max(thumbWidthPx, thumbRadiusPx * 2f)

        applyPaintColors()
        setupSmudgeAndShadow()
        applyThemeProps()
        setupLabelPaint()

        // Labels enabled via XML: mark alpha as fully visible; width is resolved in onLayout
        // once we have a measured size and a provider attached.
        // (Width animators are started from setLeftLabelProvider / setRightLabelProvider)
        if (leftLabelEnabled) leftLabelAlpha = 1f
        if (rightLabelEnabled) rightLabelAlpha = 1f

        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(android.R.string.untitled)
        // Ensure no outline-based clipping occurs (elevation/outline providers)
        clipToOutline = false
        clipBounds = null
        outlineProvider = null
    }

    private fun rangeSpan(): Float = (maxProgress - minProgress)

    private fun valueToFraction(value: Float): Float {
        val span = rangeSpan()
        return if (span <= 0f) 0f else (value - minProgress) / span
    }

    private fun fractionToValue(fraction: Float): Float {
        val span = rangeSpan()
        return if (span <= 0f) minProgress else minProgress + (fraction * span)
    }

    private fun applyPaintColors() {
        trackPaint.color = trackColor
        progressPaint.color = progressColor
        thumbRingPaint.color = thumbRingColor
        thumbRingPaint.strokeWidth = thumbRingWidthPx
        thumbInnerPaint.color = thumbInnerColor
        // Press ring picks accent color with dynamic alpha
        thumbPressRingPaint.color = progressColor
        thumbPressRingPaint.strokeWidth = pressRingStrokePx
        // Default indicator paint
        defaultIndicatorPaint.color = defaultIndicatorColor
    }

    private fun applyThemeProps() {
        if (isInEditMode.not()) {
            progressColor = ThemeManager.accent.primaryAccentColor
            trackColor = ThemeManager.theme.viewGroupTheme.highlightColor
            thumbRingColor = Color.WHITE
            thumbInnerColor = progressColor
            smudgeColor = progressColor
            thumbShadowColor = progressColor
            pressRingColor = progressColor
            defaultIndicatorColor = ThemeManager.accent.secondaryAccentColor
            labelTextColor = ThemeManager.theme.textViewTheme.secondaryTextColor
            labelBackgroundColor = ThemeManager.theme.viewGroupTheme.highlightColor
            setThumbCornerRadius(AppearancePreferences.getCornerRadius())
            applyPaintColors()
            setupSmudgeAndShadow()
            setupLabelPaint()
        }
    }

    private fun setupLabelPaint() {
        labelPaint.textSize = labelTextSize
        labelPaint.color = labelTextColor
        labelBgPaint.color = labelBackgroundColor
        if (isInEditMode.not()) {
            TypeFace.getMediumTypeFace(context)?.let { labelPaint.typeface = it }
        }
    }

    private fun setupSmudgeAndShadow() {
        if (smudgeEnabled || thumbShadowRadius > 0f) {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }
        if (smudgeEnabled) {
            smudgePaint.color = smudgeColor
            smudgePaint.maskFilter = BlurMaskFilter(smudgeRadius, BlurMaskFilter.Blur.NORMAL)
        } else {
            smudgePaint.maskFilter = null
        }
        if (thumbShadowRadius > 0f) {
            thumbShadowPaint.setShadowLayer(thumbShadowRadius, 0f, 0f, thumbShadowColor)
            thumbShadowPaint.color = Color.TRANSPARENT
        } else {
            thumbShadowPaint.clearShadowLayer()
        }
    }

    fun setOnSeekChangeListener(listener: OnSeekChangeListener?) {
        this.listener = listener
    }

    /**
     * Animates the thumb back to [minProgress] first, then applies the new max and any
     * [setProgress] call that arrived while the animation was running.
     *
     * This prevents the thumb from jumping when:
     *  - The old max and new max differ significantly while the thumb is not at the start.
     *  - The caller does `setMaxWithReset(newMax)` + `setProgress(someValue)` in sequence
     *    (the progress call is deferred until after the max is applied).
     */
    fun setMaxWithReset(max: Float, progress: Float? = null) {
        // Store what we eventually want to apply
        pendingMaxAfterReset = max
        if (progress != null) pendingProgressAfterReset = progress

        // If already at min (or range is trivially small), apply immediately without animation
        val epsilon = (rangeSpan() * 0.001f).coerceAtLeast(0.0001f)
        if (progressInternal <= minProgress + epsilon && !isResetAnimationInFlight) {
            applyPendingMaxAndProgress()
            return
        }

        // If a reset is already running, just update the pending values and let it finish
        if (isResetAnimationInFlight) return

        // Cancel any ongoing progress animation — we're taking full control
        if (springAnimation.isRunning) springAnimation.cancel()
        progressAnimator?.cancel()

        isResetAnimationInFlight = true
        val start = progressInternal
        progressAnimFromUser = false

        progressAnimator = ValueAnimator.ofFloat(start, minProgress).apply {
            duration = 500L
            interpolator = DecelerateInterpolator(1.5F)
            addUpdateListener { anim ->
                // Only write if we're still the authoritative animation (not canceled by another call)
                if (isResetAnimationInFlight) {
                    progressInternal = (anim.animatedValue as Float).coerceIn(minProgress, maxProgress)
                    invalidate()
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (isResetAnimationInFlight) {
                        isResetAnimationInFlight = false
                        applyPendingMaxAndProgress()
                    }
                }

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    // onAnimationEnd is called after onAnimationCancel by ValueAnimator,
                    // but isResetAnimationInFlight will already be false if we canceled externally.
                    // Reset the flag so a new call can proceed.
                    isResetAnimationInFlight = false
                }
            })
            start()
        }
    }

    private fun applyPendingMaxAndProgress() {
        val newMax = pendingMaxAfterReset ?: return
        val newProgress = pendingProgressAfterReset
        pendingMaxAfterReset = null
        pendingProgressAfterReset = null

        // Apply new max first — progress is at minProgress so no jump possible
        setMax(newMax)

        // Now animate (or snap) to the desired progress within the new range
        if (newProgress != null) {
            setProgress(newProgress.coerceIn(minProgress, maxProgress), fromUser = false, animate = true)
        } else {
            // Ensure progress is still valid within the new range
            progressInternal = progressInternal.coerceIn(minProgress, maxProgress)
            invalidate()
            listener?.onProgressChanged(this, getProgress(), false)
        }
    }

    fun setMax(max: Float) {
        setMaxInternal(max)
        if (progressInternal > maxProgress) {
            progressInternal = maxProgress
            invalidate()
        }
        if (defaultProgress > maxProgress) {
            defaultProgress = maxProgress
        }
    }

    private fun setMaxInternal(max: Float) {
        maxProgress = max
        if (maxProgress < minProgress) {
            // Keep range valid: shift min down to max
            minProgress = maxProgress
        }
    }

    fun getMax(): Float = maxProgress

    fun setMin(min: Float) {
        setMinInternal(min)
        if (progressInternal < minProgress) {
            progressInternal = minProgress
            invalidate()
        }
        if (defaultProgress < minProgress) {
            defaultProgress = minProgress
        }
    }

    private fun setMinInternal(min: Float) {
        minProgress = min
        if (maxProgress < minProgress) {
            // Keep range valid: raise max up to min
            maxProgress = minProgress
        }
    }

    fun getMin(): Float = minProgress

    fun setRange(min: Float, max: Float) {
        // Preserve intent even if min>max: collapse to single point at the midpoint after ordering
        if (min <= max) {
            minProgress = min
            maxProgress = max
        } else {
            minProgress = max
            maxProgress = min
        }
        progressInternal = progressInternal.coerceIn(minProgress, maxProgress)
        defaultProgress = defaultProgress.coerceIn(minProgress, maxProgress)
        invalidate()
    }

    fun setProgress(progress: Float, fromUser: Boolean = false, animate: Boolean = false) {
        // If a reset animation is in flight, queue this progress to be applied after max is set.
        if (isResetAnimationInFlight) {
            pendingProgressAfterReset = progress
            return
        }

        val target = progress.coerceIn(minProgress, maxProgress)
        if (!animate) {
            if (springAnimation.isRunning) springAnimation.cancel()
            progressAnimator?.cancel()
            if (progressInternal == target) return
            progressInternal = target
            refreshCachedLabels()
            invalidate()
            listener?.onProgressChanged(this, getProgress(), fromUser)
        } else {
            animateFromUser = fromUser
            if (springAnimation.isRunning) springAnimation.cancel()
            progressAnimator?.cancel()
            progressAnimFromUser = fromUser
            val start = progressInternal
            if (start == target) return
            progressAnimator = ValueAnimator.ofFloat(start, target).apply {
                duration = if (fromUser) 420L else 460L
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    progressInternal = (anim.animatedValue as Float).coerceIn(minProgress, maxProgress)
                    refreshCachedLabels()
                    invalidate()
                    listener?.onProgressChanged(this@FelicitySeekbar, getProgress(), progressAnimFromUser)
                }
                start()
            }
        }
    }

    fun getProgress(): Float = progressInternal

    fun setDefaultProgress(value: Float) {
        if (value < 0f) {
            // Negative value: disable the default indicator entirely
            hasDefaultSet = false
            defaultIndicatorEnabled = false
            invalidate()
            return
        }
        hasDefaultSet = true
        defaultProgress = value.coerceIn(minProgress, maxProgress)
        invalidate()
    }

    /**
     * Explicitly enable or disable drawing of the default indicator line.
     * When disabled, the indicator will not be drawn regardless of [setDefaultProgress].
     */
    fun setDefaultIndicatorEnabled(enabled: Boolean) {
        defaultIndicatorEnabled = enabled
        invalidate()
    }

    fun isDefaultIndicatorEnabled(): Boolean = defaultIndicatorEnabled

    fun resetToDefault(animate: Boolean = true) {
        setProgress(defaultProgress, fromUser = true, animate = animate)
    }

    // -------- Side label API --------

    /** Show or hide the left label with an animation. */
    fun setLeftLabelEnabled(enabled: Boolean) {
        if (leftLabelEnabled == enabled) return
        leftLabelEnabled = enabled
        animateSideLabel(left = true, show = enabled)
    }

    /** Show or hide the right label with an animation. */
    fun setRightLabelEnabled(enabled: Boolean) {
        if (rightLabelEnabled == enabled) return
        rightLabelEnabled = enabled
        animateSideLabel(left = false, show = enabled)
    }

    /** Convenience — enable/disable both sides at once. */
    fun setLabelsEnabled(enabled: Boolean) {
        setLeftLabelEnabled(enabled)
        setRightLabelEnabled(enabled)
    }

    fun isLeftLabelEnabled(): Boolean = leftLabelEnabled
    fun isRightLabelEnabled(): Boolean = rightLabelEnabled

    /**
     * Set the provider for the left-side label.
     * Automatically enables the left label if it isn't already.
     * Pass null to clear the provider and hide the left label.
     */
    fun setLeftLabelProvider(provider: LeftLabelProvider?) {
        leftLabelProvider = provider
        if (provider != null) {
            cachedLeftLabel = provider.getLabel(progressInternal, minProgress, maxProgress)
            if (!leftLabelEnabled) {
                leftLabelEnabled = true
                animateSideLabel(left = true, show = true)
            } else {
                // Already enabled — re-measure and resize if the text width changed
                remeasureSideLabel(left = true)
            }
        } else {
            cachedLeftLabel = null
            leftLabelEnabled = false
            animateSideLabel(left = true, show = false)
        }
        invalidate()
    }

    /**
     * Set the provider for the right-side label.
     * Automatically enables the right label if it isn't already.
     * Pass null to clear the provider and hide the right label.
     */
    fun setRightLabelProvider(provider: RightLabelProvider?) {
        rightLabelProvider = provider
        if (provider != null) {
            cachedRightLabel = provider.getLabel(progressInternal, minProgress, maxProgress)
            if (!rightLabelEnabled) {
                rightLabelEnabled = true
                animateSideLabel(left = false, show = true)
            } else {
                remeasureSideLabel(left = false)
            }
        } else {
            cachedRightLabel = null
            rightLabelEnabled = false
            animateSideLabel(left = false, show = false)
        }
        invalidate()
    }

    /** Refresh cached label strings from providers (call when progress changes). */
    private fun refreshCachedLabels() {
        cachedLeftLabel = leftLabelProvider?.getLabel(progressInternal, minProgress, maxProgress)
        cachedRightLabel = rightLabelProvider?.getLabel(progressInternal, minProgress, maxProgress)
    }

    private fun measureTextWidth(text: String?): Float =
        if (text.isNullOrEmpty()) 0f else labelPaint.measureText(text)

    private fun animateSideLabel(left: Boolean, show: Boolean) {
        val currentText = if (left) cachedLeftLabel else cachedRightLabel
        val targetWidth = if (show) measureTextWidth(currentText).coerceAtLeast(1f) else 0f
        val currentWidth = if (left) leftLabelAnimatedWidth else rightLabelAnimatedWidth
        val currentAlpha = if (left) leftLabelAlpha else rightLabelAlpha
        val targetAlpha = if (show) 1f else 0f

        // Width animator
        val widthAnim = ValueAnimator.ofFloat(currentWidth, targetWidth).apply {
            duration = 300L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                if (left) leftLabelAnimatedWidth = v else rightLabelAnimatedWidth = v
                requestLayout()
                invalidate()
            }
        }

        // Alpha animator
        val alphaAnim = ValueAnimator.ofFloat(currentAlpha, targetAlpha).apply {
            duration = 250L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                if (left) leftLabelAlpha = v else rightLabelAlpha = v
                invalidate()
            }
        }

        if (left) {
            leftLabelWidthAnimator?.cancel()
            leftLabelAlphaAnimator?.cancel()
            leftLabelWidthAnimator = widthAnim
            leftLabelAlphaAnimator = alphaAnim
        } else {
            rightLabelWidthAnimator?.cancel()
            rightLabelAlphaAnimator?.cancel()
            rightLabelWidthAnimator = widthAnim
            rightLabelAlphaAnimator = alphaAnim
        }

        widthAnim.start()
        alphaAnim.start()
    }

    /** Re-animate to the new measured width if text content changed while already visible. */
    private fun remeasureSideLabel(left: Boolean) {
        val text = if (left) cachedLeftLabel else cachedRightLabel
        val newWidth = measureTextWidth(text).coerceAtLeast(1f)
        val currentWidth = if (left) leftLabelAnimatedWidth else rightLabelAnimatedWidth
        if (newWidth == currentWidth) return

        val widthAnim = ValueAnimator.ofFloat(currentWidth, newWidth).apply {
            duration = 200L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                if (left) leftLabelAnimatedWidth = v else rightLabelAnimatedWidth = v
                requestLayout()
                invalidate()
            }
        }

        if (left) {
            leftLabelWidthAnimator?.cancel()
            leftLabelWidthAnimator = widthAnim
        } else {
            rightLabelWidthAnimator?.cancel()
            rightLabelWidthAnimator = widthAnim
        }
        widthAnim.start()
    }

    /**
     * Show or hide the solid background pill drawn behind each label.
     * The pill uses [labelBackgroundColor] which defaults to the theme highlight colour.
     */
    fun setLabelBackgroundEnabled(enabled: Boolean) {
        if (labelBackgroundEnabled == enabled) return
        labelBackgroundEnabled = enabled
        invalidate()
    }

    fun isLabelBackgroundEnabled(): Boolean = labelBackgroundEnabled

    /** Override the label background fill colour. */
    fun setLabelBackgroundColor(@ColorInt color: Int) {
        labelBackgroundColor = color
        labelBgPaint.color = color
        invalidate()
    }

    /** Override the label background corner radius in pixels. */
    fun setLabelBackgroundCornerRadius(radiusPx: Float) {
        labelBackgroundCornerRadius = radiusPx.coerceAtLeast(0f)
        invalidate()
    }

    /**
     * Set the extra gap in pixels added between the label text's right edge and the thumb's
     * left edge (at position 0). The mandatory minimum is always enforced:
     *   minimum = thumb half-width + press-ring outset
     * This value is an *extra* cushion on top of that minimum.
     * Default is 8dp.
     */
    fun setLabelGap(gapPx: Float) {
        labelGapPx = gapPx.coerceAtLeast(0f)
        requestLayout()
        invalidate()
    }

    fun getLabelGap(): Float = labelGapPx

    private fun animateLabelScale(focused: Boolean) {
        val target = if (focused) 1.12f else 1f
        if (labelScale == target) return
        labelScaleAnimator?.cancel()
        labelScaleAnimator = ValueAnimator.ofFloat(labelScale, target).apply {
            duration = if (focused) 200L else 280L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                labelScale = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val baseHeight = max(trackHeightPx, thumbRadiusPx * 2f) + (pressRingTotalOutset() * 2f)
        val verticalBlur = max(thumbShadowRadius, if (smudgeEnabled) (smudgeRadius + abs(smudgeOffsetY)) else 0f)
        val desiredHeight = (paddingTop + paddingBottom + baseHeight + verticalBlur * 2f).toInt()
        // Reserve = text width + mandatory clearance (thumb half + ring + extra gap)
        val leftReserve = if (leftLabelAnimatedWidth > 0f) totalLabelReserve(leftLabelAnimatedWidth).toInt() else 0
        val rightReserve = if (rightLabelAnimatedWidth > 0f) totalLabelReserve(rightLabelAnimatedWidth).toInt() else 0
        val resolvedWidth = resolveSize(suggestedMinimumWidth + paddingLeft + paddingRight + leftReserve + rightReserve, widthMeasureSpec)
        val resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(resolvedWidth, resolvedHeight)
    }

    private fun horizontalOutset(): Float = max(max(thumbShadowRadius, if (smudgeEnabled) smudgeRadius else 0f), pressRingTotalOutset())

    /**
     * The minimum gap between label text and the nearest edge of the thumb when the
     * thumb is at position 0 (left label) or max (right label).
     *
     * = thumb half-width (baseSafeInset) + press-ring outset + [labelGapPx]
     *
     * This is computed fresh each time so it automatically adapts when thumb size or
     * press-ring size changes at runtime.
     */
    private fun mandatoryLabelClearance(): Float {
        val baseSafeInset = when (thumbShape) {
            ThumbShape.CIRCLE -> thumbRadiusPx
            else -> thumbWidthPx / 2f
        }
        return baseSafeInset + pressRingTotalOutset() + labelGapPx
    }

    /**
     * Total horizontal space to reserve for one label on its side.
     * = text width + mandatory clearance (thumb half + ring + extra gap)
     */
    private fun totalLabelReserve(textWidth: Float): Float = textWidth + mandatoryLabelClearance()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val hOut = horizontalOutset()
        val baseSafeInset = when (thumbShape) {
            ThumbShape.CIRCLE -> thumbRadiusPx
            else -> thumbWidthPx / 2f
        }

        // Total space reserved per side = text width + mandatory clearance
        // The track is then inset by baseSafeInset on top of that (standard thumb-fit inset).
        // Geometry:
        //   left edge of view
        //   + paddingLeft + hOut                           ← view safe area
        //   + leftLabelAnimatedWidth                       ← text zone
        //   + mandatoryLabelClearance()                    ← gap from text to thumb centre
        //   = left (track start / thumb centre at pos 0)
        //
        // Note: mandatoryLabelClearance = baseSafeInset + pressRing + labelGapPx
        // so "left" already includes baseSafeInset — we must NOT add it again.
        val leftTotalReserve = if (leftLabelAnimatedWidth > 0f) totalLabelReserve(leftLabelAnimatedWidth) else 0f
        val rightTotalReserve = if (rightLabelAnimatedWidth > 0f) totalLabelReserve(rightLabelAnimatedWidth) else 0f

        // Track bounds: baseSafeInset is already inside mandatoryLabelClearance when labels
        // are present; for the no-label case we still need it to keep thumb inside bounds.
        val left = if (leftTotalReserve > 0f) {
            paddingLeft.toFloat() + hOut + leftTotalReserve
        } else {
            paddingLeft.toFloat() + hOut + baseSafeInset
        }
        val right = if (rightTotalReserve > 0f) {
            (width - paddingRight).toFloat() - hOut - rightTotalReserve
        } else {
            (width - paddingRight).toFloat() - hOut - baseSafeInset
        }

        if (right <= left) return
        val centerY = height / 2f + if (smudgeEnabled) smudgeOffsetY else 0f
        val trackRadius = trackHeightPx / 2f
        trackRect.set(left, centerY - trackRadius, right, centerY + trackRadius)

        val clampedFraction = valueToFraction(progressInternal).coerceIn(0f, 1f)
        val progressRight = left + (right - left) * clampedFraction

        if (smudgeEnabled && progressRight > left) {
            smudgeRect.set(left, trackRect.top, progressRight, trackRect.bottom)
            canvas.drawRoundRect(smudgeRect, trackRadius, trackRadius, smudgePaint)
        }
        canvas.drawRoundRect(trackRect, trackRadius, trackRadius, trackPaint)
        if (progressRight > left) {
            progressRect.set(left, trackRect.top, progressRight, trackRect.bottom)
            canvas.drawRoundRect(progressRect, trackRadius, trackRadius, progressPaint)
        }

        // Default indicator
        val indicatorEpsilon = (rangeSpan() * 0.001f).coerceAtLeast(0.0001f)
        if (hasDefaultSet && defaultIndicatorEnabled && (defaultProgress - minProgress) > indicatorEpsilon) {
            val df = valueToFraction(defaultProgress).coerceIn(0f, 1f)
            val dx = left + (right - left) * df
            val halfW = defaultIndicatorWidthPx / 2f
            defaultIndicatorRect.set(dx - halfW, trackRect.top, dx + halfW, trackRect.bottom)
            defaultIndicatorPaint.color = defaultIndicatorColor
            canvas.drawRoundRect(defaultIndicatorRect, halfW, halfW, defaultIndicatorPaint)
        }

        // Thumb
        val cx = progressRight
        val cy = trackRect.centerY()
        val scaledR = thumbRadiusPx * thumbScale
        val scaledHalfW = (thumbWidthPx / 2f) * thumbScale

        thumbOuterRect.set(cx - scaledHalfW, cy - scaledR, cx + scaledHalfW, cy + scaledR)

        when (thumbShape) {
            ThumbShape.OVAL -> {
                if (thumbShadowRadius > 0f) canvas.drawOval(thumbOuterRect, thumbShadowPaint)
                if (thumbInnerColor != Color.TRANSPARENT) {
                    thumbInnerRect.set(thumbOuterRect)
                    thumbInnerRect.inset(thumbRingWidthPx / 2f, thumbRingWidthPx / 2f)
                    canvas.drawOval(thumbInnerRect, thumbInnerPaint)
                }
                thumbStrokeRect.set(thumbOuterRect)
                thumbStrokeRect.inset(thumbRingWidthPx / 2f, thumbRingWidthPx / 2f)
                canvas.drawOval(thumbStrokeRect, thumbRingPaint)
                if (pressRingProgress > 0f) {
                    val extra = pressRingOutsetPx * pressRingProgress
                    thumbPressRingRect.set(thumbOuterRect)
                    thumbPressRingRect.inset(-extra, -extra)
                    val alpha = (0.35f * pressRingProgress * 255).toInt().coerceIn(0, 255)
                    thumbPressRingPaint.color = (pressRingColor and 0x00FFFFFF) or (alpha shl 24)
                    thumbPressRingPaint.strokeWidth = pressRingStrokePx
                    canvas.drawOval(thumbPressRingRect, thumbPressRingPaint)
                }
            }
            ThumbShape.PILL -> {
                val baseThumbCornerR = 100f
                if (thumbShadowRadius > 0f) canvas.drawRoundRect(thumbOuterRect, baseThumbCornerR, baseThumbCornerR, thumbShadowPaint)
                if (thumbInnerColor != Color.TRANSPARENT) {
                    thumbInnerRect.set(thumbOuterRect)
                    val inset = thumbRingWidthPx / 2f
                    thumbInnerRect.inset(inset, inset)
                    canvas.drawRoundRect(thumbInnerRect, max(0f, baseThumbCornerR - inset), max(0f, baseThumbCornerR - inset), thumbInnerPaint)
                }
                thumbStrokeRect.set(thumbOuterRect)
                val strokeInset = thumbRingWidthPx / 2f
                thumbStrokeRect.inset(strokeInset, strokeInset)
                val ringR = max(0f, baseThumbCornerR - strokeInset)
                canvas.drawRoundRect(thumbStrokeRect, ringR, ringR, thumbRingPaint)
                if (pressRingProgress > 0f) {
                    val extra = pressRingOutsetPx * pressRingProgress
                    thumbPressRingRect.set(thumbOuterRect)
                    thumbPressRingRect.inset(-extra, -extra)
                    val pressRingCornerR = baseThumbCornerR + extra
                    val alpha = (0.35f * pressRingProgress * 255).toInt().coerceIn(0, 255)
                    thumbPressRingPaint.color = (pressRingColor and 0x00FFFFFF) or (alpha shl 24)
                    thumbPressRingPaint.strokeWidth = pressRingStrokePx
                    canvas.drawRoundRect(thumbPressRingRect, pressRingCornerR, pressRingCornerR, thumbPressRingPaint)
                }
            }
            ThumbShape.CIRCLE -> {
                if (thumbShadowRadius > 0f) canvas.drawCircle(cx, cy, scaledR, thumbShadowPaint)
                if (thumbInnerColor != Color.TRANSPARENT) {
                    canvas.drawCircle(cx, cy, max(0f, scaledR - thumbRingWidthPx / 2f), thumbInnerPaint)
                }
                canvas.drawCircle(cx, cy, max(0f, scaledR - thumbRingWidthPx / 2f), thumbRingPaint)
                if (pressRingProgress > 0f) {
                    val extra = pressRingOutsetPx * pressRingProgress
                    val alpha = (0.35f * pressRingProgress * 255).toInt().coerceIn(0, 255)
                    thumbPressRingPaint.color = (pressRingColor and 0x00FFFFFF) or (alpha shl 24)
                    thumbPressRingPaint.strokeWidth = pressRingStrokePx
                    canvas.drawCircle(cx, cy, scaledR + extra, thumbPressRingPaint)
                }
            }
        }

        // ---- Draw side labels ----
        // Label text zone: [paddingLeft + hOut .. paddingLeft + hOut + labelAnimatedWidth]
        // Label centre X = paddingLeft + hOut + labelAnimatedWidth / 2
        // Thumb left edge at pos 0 = left (= paddingLeft + hOut + totalLabelReserve)
        // Gap between text right edge and thumb left edge = mandatoryLabelClearance() = baseSafeInset + ring + labelGapPx
        val textY = centerY - ((labelPaint.ascent() + labelPaint.descent()) / 2f)
        val fontHalfHeight = (labelPaint.descent() - labelPaint.ascent()) / 2f

        if (leftLabelAlpha > 0f && leftLabelAnimatedWidth > 0f) {
            val text = cachedLeftLabel
            if (!text.isNullOrEmpty()) {
                val alpha = (leftLabelAlpha * 255f).toInt().coerceIn(0, 255)
                val leftLabelCx = paddingLeft.toFloat() + hOut + leftLabelAnimatedWidth / 2f
                canvas.withScale(labelScale, labelScale, leftLabelCx, centerY) {
                    if (labelBackgroundEnabled) {
                        val textHalfW = labelPaint.measureText(text) / 2f
                        labelBgPaint.color = (labelBackgroundColor and 0x00FFFFFF) or (alpha shl 24)
                        labelBgRect.set(
                                leftLabelCx - textHalfW - labelBackgroundPaddingH,
                                centerY - fontHalfHeight - labelBackgroundPaddingV,
                                leftLabelCx + textHalfW + labelBackgroundPaddingH,
                                centerY + fontHalfHeight + labelBackgroundPaddingV
                        )
                        drawRoundRect(labelBgRect, labelBackgroundCornerRadius, labelBackgroundCornerRadius, labelBgPaint)
                    }
                    labelPaint.alpha = alpha
                    drawText(text, leftLabelCx, textY, labelPaint)
                }
            }
        }

        if (rightLabelAlpha > 0f && rightLabelAnimatedWidth > 0f) {
            val text = cachedRightLabel
            if (!text.isNullOrEmpty()) {
                val alpha = (rightLabelAlpha * 255f).toInt().coerceIn(0, 255)
                val rightLabelCx = (width - paddingRight).toFloat() - hOut - rightLabelAnimatedWidth / 2f
                canvas.withScale(labelScale, labelScale, rightLabelCx, centerY) {
                    if (labelBackgroundEnabled) {
                        val textHalfW = labelPaint.measureText(text) / 2f
                        labelBgPaint.color = (labelBackgroundColor and 0x00FFFFFF) or (alpha shl 24)
                        labelBgRect.set(
                                rightLabelCx - textHalfW - labelBackgroundPaddingH,
                                centerY - fontHalfHeight - labelBackgroundPaddingV,
                                rightLabelCx + textHalfW + labelBackgroundPaddingH,
                                centerY + fontHalfHeight + labelBackgroundPaddingV
                        )
                        drawRoundRect(labelBgRect, labelBackgroundCornerRadius, labelBackgroundCornerRadius, labelBgPaint)
                    }
                    labelPaint.alpha = alpha
                    drawText(text, rightLabelCx, textY, labelPaint)
                }
            }
        }
    }

    @Suppress("UnnecessaryVariable")
    private fun isPointOnThumb(x: Float, y: Float): Boolean {
        val hOut = horizontalOutset()
        val baseSafeInset = when (thumbShape) {
            ThumbShape.CIRCLE -> thumbRadiusPx
            else -> thumbWidthPx / 2f
        }
        val left = if (leftLabelAnimatedWidth > 0f) {
            paddingLeft.toFloat() + hOut + totalLabelReserve(leftLabelAnimatedWidth)
        } else {
            paddingLeft.toFloat() + hOut + baseSafeInset
        }
        val right = if (rightLabelAnimatedWidth > 0f) {
            (width - paddingRight).toFloat() - hOut - totalLabelReserve(rightLabelAnimatedWidth)
        } else {
            (width - paddingRight).toFloat() - hOut - baseSafeInset
        }
        if (right <= left) return false
        val progressX = left + (right - left) * valueToFraction(progressInternal).coerceIn(0f, 1f)
        val cy = height / 2f + if (smudgeEnabled) smudgeOffsetY else 0f
        val dx = x - progressX
        val dy = y - cy
        val halfH = thumbRadiusPx * thumbScale
        val halfW = (thumbWidthPx / 2f) * thumbScale

        return when (thumbShape) {
            ThumbShape.OVAL -> {
                val a = halfW
                val b = halfH
                if (a <= 0f || b <= 0f) false else (dx * dx) / (a * a) + (dy * dy) / (b * b) <= 1f
            }
            ThumbShape.PILL -> {
                val cornerR = min(thumbCornerRadiusPxOverride ?: halfH, min(halfH, halfW))
                val bodyHalfW = max(0f, halfW - cornerR)
                if (abs(dx) <= bodyHalfW && abs(dy) <= halfH) true else {
                    val dlx = dx - (-bodyHalfW)
                    val drx = dx - bodyHalfW
                    (dlx * dlx + dy * dy <= cornerR * cornerR) || (drx * drx + dy * dy <= cornerR * cornerR)
                }
            }
            ThumbShape.CIRCLE -> {
                dx * dx + dy * dy <= halfH * halfH
            }
        }
    }

    private fun startPressRing(show: Boolean) {
        val target = if (show) 1f else 0f
        if (pressRingProgress == target) return
        pressRingAnimator?.cancel()
        pressRingAnimator = ValueAnimator.ofFloat(pressRingProgress, target).apply {
            duration = if (show) 160 else 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                pressRingProgress = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // First pass events to gesture detector to catch double-tap
        consumedDoubleTap = false
        gestureDetector.onTouchEvent(event)
        if (consumedDoubleTap) {
            // Avoid triggering drag/tap behaviors for this gesture
            isDragging = false
            thumbScaleAnimator?.cancel()
            thumbScale = 1f
            startPressRing(false)
            invalidate()
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isEnabled) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                isDragging = true
                downX = event.x
                downY = event.y
                downOnThumb = isPointOnThumb(downX, downY)
                if (downOnThumb) {
                    startPressRing(true)
                } else {
                    // fast animate to tap position
                    val hOut = horizontalOutset()
                    val baseSafeInset = when (thumbShape) {
                        ThumbShape.CIRCLE -> thumbRadiusPx
                        else -> thumbWidthPx / 2f
                    }
                    val tapLeft = if (leftLabelAnimatedWidth > 0f) {
                        paddingLeft.toFloat() + hOut + totalLabelReserve(leftLabelAnimatedWidth)
                    } else {
                        paddingLeft.toFloat() + hOut + baseSafeInset
                    }
                    val tapRight = if (rightLabelAnimatedWidth > 0f) {
                        (width - paddingRight).toFloat() - hOut - totalLabelReserve(rightLabelAnimatedWidth)
                    } else {
                        (width - paddingRight).toFloat() - hOut - baseSafeInset
                    }
                    val clamped = min(max(event.x, tapLeft), tapRight)
                    val fraction = if (tapRight > tapLeft) (clamped - tapLeft) / (tapRight - tapLeft) else 0f
                    val newProgress = fractionToValue(fraction).coerceIn(minProgress, maxProgress)
                    setProgress(newProgress, fromUser = true, animate = true)
                }
                listener?.onStartTrackingTouch(this)
                if (leftLabelEnabled || rightLabelEnabled) animateLabelScale(true)
                performClick()
                return true
            }
            MotionEvent.ACTION_MOVE -> if (isDragging) {
                updateFromTouch(event.x, true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    listener?.onStopTrackingTouch(this)
                    if (leftLabelEnabled || rightLabelEnabled) animateLabelScale(false)
                }
                if (downOnThumb) startPressRing(false)
                downOnThumb = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateFromTouch(x: Float, fromUser: Boolean) {
        val hOut = horizontalOutset()
        val baseSafeInset = when (thumbShape) {
            ThumbShape.CIRCLE -> thumbRadiusPx
            else -> thumbWidthPx / 2f
        }
        val left = if (leftLabelAnimatedWidth > 0f) {
            paddingLeft.toFloat() + hOut + totalLabelReserve(leftLabelAnimatedWidth)
        } else {
            paddingLeft.toFloat() + hOut + baseSafeInset
        }
        val right = if (rightLabelAnimatedWidth > 0f) {
            (width - paddingRight).toFloat() - hOut - totalLabelReserve(rightLabelAnimatedWidth)
        } else {
            (width - paddingRight).toFloat() - hOut - baseSafeInset
        }
        if (right <= left) return
        val clamped = min(max(x, left), right)
        val fraction = (clamped - left) / (right - left)
        val newProgress = fractionToValue(fraction).coerceIn(minProgress, maxProgress)
        setProgress(newProgress, fromUser, animate = false)
    }

    // Thumb shape public API
    fun setThumbShape(shape: ThumbShape) {
        if (thumbShape != shape) {
            thumbShape = shape
            invalidate()
        }
    }

    fun getThumbShape(): ThumbShape = thumbShape

    // configure default indicator color
    fun setDefaultIndicatorColor(@ColorInt color: Int) {
        defaultIndicatorColor = color
        defaultIndicatorPaint.color = color
        invalidate()
    }

    // Public API for pill corner radius override used by theme prefs
    fun setThumbCornerRadius(radius: Float) {
        thumbCornerRadiusPxOverride = max(0f, radius)
        invalidate()
    }

    private fun applyThumbPreferences() {
        if (isInEditMode.not()) {
            val shape = AppearancePreferences.getSeekbarThumbStyle()
            when (shape) {
                AppearancePreferences.SEEKBAR_THUMB_PILL -> setThumbShape(ThumbShape.PILL)
                AppearancePreferences.SEEKBAR_THUMB_CIRCLE -> setThumbShape(ThumbShape.CIRCLE)
                else -> setThumbShape(ThumbShape.OVAL)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isInEditMode.not()) {
            registerSharedPreferenceChangeListener()
            ThemeManager.addListener(this)
        }
    }

    override fun onSharedPreferenceChanged(p0: SharedPreferences?, p1: String?) {
        when (p1) {
            AppearancePreferences.APP_CORNER_RADIUS -> {
                setThumbCornerRadius(AppearancePreferences.getCornerRadius())
            }
            AppearancePreferences.SEEKBAR_THUMB_STYLE -> {
                applyThumbPreferences()
            }
            AppearancePreferences.APP_FONT -> {
                setupLabelPaint()
                invalidate()
            }
        }
    }

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        super.onThemeChanged(theme, animate)
        applyThemeProps()
    }

    override fun onAccentChanged(accent: Accent) {
        super.onAccentChanged(accent)
        applyThemeProps()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        unregisterSharedPreferenceChangeListener()
        isResetAnimationInFlight = false
        pendingMaxAfterReset = null
        pendingProgressAfterReset = null
        progressAnimator?.cancel()
        pressRingAnimator?.cancel()
        leftLabelWidthAnimator?.cancel()
        rightLabelWidthAnimator?.cancel()
        leftLabelAlphaAnimator?.cancel()
        rightLabelAlphaAnimator?.cancel()
        labelScaleAnimator?.cancel()
        if (springAnimation.isRunning) springAnimation.cancel()
        ThemeManager.removeListener(this)
    }
}