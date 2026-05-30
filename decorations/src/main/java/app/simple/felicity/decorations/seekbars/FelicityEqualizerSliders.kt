package app.simple.felicity.decorations.seekbars

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.VibrationEffect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import app.simple.felicity.decorations.drawables.ThumbPillDrawable
import app.simple.felicity.decorations.knobs.SimpleBaseKnobDrawable
import app.simple.felicity.decorations.seekbars.FelicityEqualizerSliders.Companion.NO_ACTIVE_BAND
import app.simple.felicity.decorations.seekbars.FelicityEqualizerSliders.Companion.PEQ_DELETE_BTN_RADIUS_DP
import app.simple.felicity.decorations.seekbars.FelicityEqualizerSliders.Companion.PEQ_KNOB_DIAMETER_DP
import app.simple.felicity.decorations.seekbars.FelicityEqualizerSliders.Companion.PREAMP_BAND_INDEX
import app.simple.felicity.decorations.typeface.TypeFace
import app.simple.felicity.decorations.utils.VibrateUtils.vibrateEffect
import app.simple.felicity.manager.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.manager.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.models.Theme
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import app.simple.felicity.decoration.R as DecoR

/**
 * A 10-band graphic equalizer slider view plus a dedicated pre-amplifier slider,
 * spanning 31 Hz to 16 kHz with an overall gain stage.
 *
 * The preamp column is rendered at the far left with a tinted background highlight
 * and a vertical separator that visually distinguishes it from the 10 EQ-band columns.
 * Each band (including the preamp) is rendered as a vertical slider with a fader-style
 * pill thumb featuring three horizontal grip lines. A smooth Catmull-Rom spline
 * (EQ bands only) connects the 10 band-gain thumbs to represent the frequency curve.
 *
 * The text-gap calculation is restructured so [textGapPx] exclusively controls the gap
 * between the track and the label row without accumulating extra space at the bottom of
 * the view when the value is changed.
 *
 * @author Hamza417
 */
class FelicityEqualizerSliders @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), ThemeChangedListener, SharedPreferences.OnSharedPreferenceChangeListener {

    // -------------------------------------------------------------------------
    // Public interface
    // -------------------------------------------------------------------------

    /**
     * Callback fired whenever a band's gain changes due to user interaction.
     */
    fun interface OnBandChangedListener {
        /**
         * @param bandIndex [PREAMP_BAND_INDEX] for the preamp, 0-9 for EQ bands
         * @param gain      current gain in dB, range [MIN_DB .. MAX_DB]
         * @param fromUser  true when the change originated from a touch event
         */
        fun onBandChanged(bandIndex: Int, gain: Float, fromUser: Boolean)
    }

    /**
     * Callback fired in real-time whenever any parametric EQ band changes — whether
     * the user is dragging the gain slider, turning the Q knob, or turning the
     * frequency knob. The full band list is provided so the listener can push a
     * complete snapshot to the DSP without having to query the view again.
     *
     * @param bands A snapshot of all PEQ bands in their updated state.
     */
    fun interface OnPeqBandChangedListener {
        fun onPeqBandsChanged(bands: List<PeqBand>)
    }

    private var bandChangedListener: OnBandChangedListener? = null
    private var peqBandChangedListener: OnPeqBandChangedListener? = null

    fun setOnBandChangedListener(listener: OnBandChangedListener?) {
        bandChangedListener = listener
    }

    fun setOnPeqBandChangedListener(listener: OnPeqBandChangedListener?) {
        peqBandChangedListener = listener
    }

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    companion object {
        /** Number of EQ frequency bands (31 Hz → 16 kHz). */
        private const val BAND_COUNT = 10

        /** Total visual columns: 1 preamp + 10 EQ bands. */
        private const val TOTAL_COLUMNS = BAND_COUNT + 1

        const val MIN_DB = -15f
        const val MAX_DB = 15f
        private const val DEFAULT_DB = 0f

        /**
         * Band index used in [OnBandChangedListener] callbacks to identify the
         * pre-amplifier slider. The 10 EQ bands use indices 0–9.
         */
        const val PREAMP_BAND_INDEX = -1

        /** Sentinel value for [activeBandIndex] meaning no band is currently touched. */
        private const val NO_ACTIVE_BAND = Int.MIN_VALUE

        /** Human-readable frequency labels for each EQ band. */
        val FREQUENCY_LABELS = arrayOf(
                "31 Hz", "62 Hz", "125 Hz", "250 Hz", "500 Hz",
                "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz"
        )

        private const val OVERSCROLL_DECAY_FACTOR = 400f
        private const val MAX_OVERSCROLL_DP = 128f
        private const val TAG = "FelicityEqualizerSliders"

        /** The total angular sweep of a knob in degrees (from min to max). */
        private const val KNOB_SWEEP_DEG = 270f

        /** Where the knob arc begins, in canvas-angle degrees (clockwise from 3 o'clock). */
        private const val KNOB_ARC_START_DEG = 135f

        /**
         * Knob body diameter in dp. Section width is derived from this so the layout
         * always wraps its contents rather than relying on a hard-coded section size.
         */
        private const val PEQ_KNOB_DIAMETER_DP = 96f

        /** Horizontal inset on both sides of each section, in dp. */
        private const val PEQ_SECTION_H_PADDING_DP = 24f

        /** Radius of the delete button circle drawn at the top-right corner of each section. */
        private const val PEQ_DELETE_BTN_RADIUS_DP = 10f

        /**
         * Gap in dp between the outer edge of a knob's arc and the nearest label (pill above
         * and value text below). Increase this to prevent text from touching the arc.
         */
        private const val PEQ_KNOB_LABEL_GAP_DP = 12f

        /** Stroke width of the arc track and progress arc around each knob, in dp. */
        private const val PEQ_ARC_STROKE_DP = 1.5f

        /**
         * Horizontal gap in dp between the gain slider's right edge and the left edge of
         * the knob arc. Changing this moves the knob column closer to or farther from the slider.
         */
        private const val PEQ_SLIDER_KNOB_GAP_DP = 16f

        /** Minimum Q value exposed by the frequency knob. */
        const val PEQ_Q_MIN = 0.1f

        /** Maximum Q value exposed by the frequency knob. */
        const val PEQ_Q_MAX = 12f

        /** Minimum frequency in Hz for the freq knob (log scale). */
        const val PEQ_FREQ_MIN_HZ = 20f

        /** Maximum frequency in Hz for the freq knob (log scale). */
        const val PEQ_FREQ_MAX_HZ = 20000f

        /**
         * Holds the gain, Q, and center frequency for a single parametric EQ band.
         * All values are in their real units — normalization only happens during drawing.
         */
        data class PeqBand(
                var gain: Float = 0f,
                var q: Float = 1.0f,
                var frequencyHz: Float = 1000f
        )

        /** The available display modes for this equalizer widget. */
        enum class Mode { GRAPHIC, PARAMETRIC }
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** Raw gain values for EQ bands 0–9 in dB. */
    private val gains = FloatArray(BAND_COUNT) { DEFAULT_DB }

    /** Animated display values for EQ bands 0–9 (driven by ValueAnimator). */
    private val displayGains = FloatArray(BAND_COUNT) { DEFAULT_DB }
    private val gainAnimators = arrayOfNulls<ValueAnimator>(BAND_COUNT)

    /** Raw gain for the preamp in dB. */
    private var preampGain: Float = DEFAULT_DB

    /** Animated display value for the preamp. */
    private var preampDisplayGain: Float = DEFAULT_DB
    private var preampGainAnimator: ValueAnimator? = null

    // -------------------------------------------------------------------------
    // Scroll state
    // -------------------------------------------------------------------------

    private var scrollOffset = 0f
    private var maxScroll = 0f
    private var centeredMode = false
    private var centeringOffset = 0f

    // -------------------------------------------------------------------------
    // Mode and PEQ state
    // -------------------------------------------------------------------------

    /**
     * Switches between the 10-band graphic EQ and the parametric EQ.
     * Changing this triggers a full layout recalculation and redraw.
     */
    var mode: Mode = Mode.GRAPHIC
        set(value) {
            field = value
            if (width > 0 && height > 0) recalculateLayout(width, height)
            invalidate()
        }

    /** The list of parametric bands the user has added. Starts with two default bands. */
    private val peqBands = mutableListOf(
            PeqBand(gain = 0f, q = 1f, frequencyHz = 200f),
            PeqBand(gain = 0f, q = 1f, frequencyHz = 2000f)
    )

    /**
     * One [SimpleBaseKnobDrawable] per PEQ band for the Q knob.
     * These stay in sync with [peqBands] — same index, same size.
     */
    private val peqQKnobDrawables = mutableListOf<SimpleBaseKnobDrawable>()

    /**
     * One [SimpleBaseKnobDrawable] per PEQ band for the frequency knob.
     * Kept parallel to [peqQKnobDrawables].
     */
    private val peqFreqKnobDrawables = mutableListOf<SimpleBaseKnobDrawable>()

    /**
     * Animated press scale for each PEQ band's gain slider. When the user presses the
     * slider this grows above 1f so the thumb expands and the halo ring appears — the
     * same visual feedback used in graphic EQ mode.
     */
    private val peqGainPressScales = mutableListOf<Float>().apply { repeat(2) { add(1f) } }

    /** Per-band animators that drive [peqGainPressScales]. */
    private val peqGainPressAnimators = mutableListOf<ValueAnimator?>().apply { repeat(2) { add(null) } }

    /**
     * True once the view is attached to a window so new drawables created while the
     * view is live can immediately register with the theme system.
     */
    private var peqDrawablesAttached = false

    init {
        // Pre-create drawables for the two default bands so they're ready before
        // the view is laid out for the first time.
        repeat(peqBands.size) {
            peqQKnobDrawables.add(makePeqKnobDrawable())
            peqFreqKnobDrawables.add(makePeqKnobDrawable())
        }
        // The gain press lists were seeded with 2 entries above; if the initial band count
        // ever changes those pre-fills will be reset here to stay consistent.
        while (peqGainPressScales.size < peqBands.size) peqGainPressScales.add(1f)
        while (peqGainPressAnimators.size < peqBands.size) peqGainPressAnimators.add(null)
    }

    /**
     * Creates a [SimpleBaseKnobDrawable], wires its [android.graphics.drawable.Drawable.Callback]
     * to this view so [invalidateSelf] calls trigger a redraw, and returns it ready to use.
     */
    private fun makePeqKnobDrawable(): SimpleBaseKnobDrawable =
        SimpleBaseKnobDrawable().also { it.callback = this }

    /** Which element in a PEQ section is currently being dragged. */
    private enum class PeqTarget { NONE, GAIN_SLIDER, Q_KNOB, FREQ_KNOB, ADD_BUTTON, DELETE_BUTTON }

    private var peqTouchTarget = PeqTarget.NONE
    private var peqTouchBandIndex = -1

    /**
     * The finger angle captured on the previous MOVE event, used to compute incremental
     * rotation deltas so the knob always follows the finger direction and never jumps
     * when the gesture crosses the dead zone at the bottom of the arc.
     */
    private var peqKnobPrevAngle = 0f

    /**
     * Running normalized value (0..1) for the knob that is currently being dragged.
     * Updated incrementally each frame so small movements accumulate smoothly.
     */
    private var peqKnobCurrentNorm = 0f

    /** The band value (gain/q/freq normalized) when a knob drag started. */
    private var peqKnobValueAtDown = 0f

    /** The gain value of the touched PEQ band at the time the drag started. */
    private var peqGainAtDown = 0f

    /** Pre-computed section width in pixels for PEQ mode. */
    private var peqSectionWidthPx = 0f

    /** Knob body radius in pixels (half of [PEQ_KNOB_DIAMETER_DP]). */
    private var peqKnobRadiusPx = 0f

    /** Outer visual radius of the knob including the surrounding arc track. */
    private var peqKnobArcRadiusPx = 0f

    /** X position of the "Add Band" button center in content space (not view space). */
    private var peqAddButtonContentX = 0f

    // -------------------------------------------------------------------------
    // Overscroll spring / fling
    // -------------------------------------------------------------------------

    private val maxOverscrollPx get() = MAX_OVERSCROLL_DP * resources.displayMetrics.density

    private val scrollOffsetProperty = object : FloatPropertyCompat<FelicityEqualizerSliders>("scrollOffset") {
        override fun getValue(view: FelicityEqualizerSliders): Float = view.scrollOffset
        override fun setValue(view: FelicityEqualizerSliders, value: Float) {
            view.scrollOffset = value.coerceIn(-maxOverscrollPx, maxScroll + maxOverscrollPx)
            view.invalidate()
        }
    }

    private val scrollSpring = SpringAnimation(this, scrollOffsetProperty).apply {
        spring = SpringForce().apply {
            stiffness = SpringForce.STIFFNESS_LOW
            dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
        }
    }

    private val scrollFling = FlingAnimation(this, scrollOffsetProperty).apply {
        friction = 1.1f
        addEndListener { _, _, _, _ -> snapScrollToBounds() }
    }

    private var velocityTracker: VelocityTracker? = null

    // -------------------------------------------------------------------------
    // Touch state
    // -------------------------------------------------------------------------

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var activeBandIndex = NO_ACTIVE_BAND
    private var isScrollGesture = false
    private var isBandGesture = false
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var scrollOffsetAtDown = 0f
    private var thumbYAtDown = 0f

    // -------------------------------------------------------------------------
    // Press-scale animations per band (EQ bands 0–9 + preamp)
    // -------------------------------------------------------------------------

    private val pressScales = FloatArray(BAND_COUNT) { 1f }
    private val pressScaleAnimators = arrayOfNulls<ValueAnimator>(BAND_COUNT)
    private var preampPressScale: Float = 1f
    private var preampPressScaleAnimator: ValueAnimator? = null

    // -------------------------------------------------------------------------
    // Layout geometry (recomputed in onSizeChanged)
    // -------------------------------------------------------------------------

    private var columnWidth = 0f
    private var trackTop = 0f
    private var trackBottom = 0f
    private var trackLength = 0f

    /**
     * Top of the label row, computed in [recalculateLayout] as a fixed position
     * relative to the view bottom. [textGapPx] controls the gap between [trackBottom]
     * and this point without affecting the bottom margin.
     */
    private var textRegionTop = 0f
    private var contentWidth = 0f

    // -------------------------------------------------------------------------
    // Dimension constants
    // -------------------------------------------------------------------------

    private val d = resources.displayMetrics.density

    var bandSpacingDp: Float = 50f
        set(value) {
            field = value.coerceAtLeast(36f)
            if (width > 0 && height > 0) {
                recalculateLayout(width, height)
                invalidate()
            }
        }

    private val thumbHalfWidthPx = 12f * d
    private val thumbHalfHeightPx = 24f * d
    private val trackStrokePx = 5f * d
    private val bezierStrokePx = 2.5f * d
    private val thumbRingStrokePx = 3f * d
    private val gripLineStrokePx = 1.5f * d
    private val gripLineHalfLengthFraction = 0.42f
    private val gripLineSpacingFraction = 0.22f
    private val sliderVerticalPaddingPx = thumbHalfHeightPx + 4f * d
    private val pressRingOutsetPx = 5f * d

    /**
     * Gap between the bottom of the track (plus its thumb area) and the top of the
     * label row. Affects only this visual gap; it does not accumulate extra space at
     * the bottom of the view.
     */
    private val textGapPx = 10f * d

    // -------------------------------------------------------------------------
    // Shadow / glow state
    // -------------------------------------------------------------------------

    private var shadowEffectEnabled = false

    // -------------------------------------------------------------------------
    // Colors
    // -------------------------------------------------------------------------

    @ColorInt
    private var trackColor = Color.DKGRAY

    @ColorInt
    private var accentColor = Color.WHITE

    @ColorInt
    private var thumbRingColor = Color.WHITE

    @ColorInt
    private var thumbInnerColor = Color.WHITE

    @ColorInt
    private var primaryTextColor = Color.WHITE

    @ColorInt
    private var secondaryTextColor = Color.GRAY

    @ColorInt
    private var centerLineColor = Color.GRAY

    @ColorInt
    private var bezierColor = Color.WHITE

    // -------------------------------------------------------------------------
    // Paints
    // -------------------------------------------------------------------------

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val bezierPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    /**
     * Filled gradient paint used to draw the translucent fade below the bezier curve.
     * The [LinearGradient] shader is rebuilt dynamically in [drawBezierFill] each frame
     * because the gradient bounds change as the user drags the band thumbs.
     */
    private val bezierFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /** Unified pill-thumb drawable shared across all columns (including the preamp). */
    private val thumbPillDrawable = ThumbPillDrawable(ThumbPillDrawable.Orientation.VERTICAL).apply {
        ringStrokePx = thumbRingStrokePx
        gripLineStrokePx = this@FelicityEqualizerSliders.gripLineStrokePx
        gripLineHalfLengthFraction = this@FelicityEqualizerSliders.gripLineHalfLengthFraction
        gripLineSpacingFraction = this@FelicityEqualizerSliders.gripLineSpacingFraction
    }

    private val trackProgressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }

    private val pressRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val freqTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val valueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    /**
     * Semi-transparent fill drawn behind the preamp column to visually distinguish it
     * from the 10 EQ-band columns.
     */
    private val preampBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /**
     * Vertical separator line drawn between the preamp column and the first EQ-band column.
     */
    private val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }

    private val bezierPath = Path()
    private val bezierFillPath = Path()
    private val pressRingRect = RectF()

    /** Background arc track drawn behind each PEQ knob's progress arc. */
    private val knobArcTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    /** Filled arc showing how far a knob has been turned. */
    private val knobArcProgressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    /** Body fill of each PEQ knob circle. */
    private val knobBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /** Outer ring stroke on each PEQ knob. */
    private val knobRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    /** The small indicator dot inside each knob that shows its rotational position. */
    private val knobIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /** Label text drawn below each knob (e.g., "Q" or "FREQ"). */
    private val knobLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    /**
     * Tinted background drawn behind each PEQ section so the gain slider and its
     * two knobs read as a single grouped unit.
     */
    private val peqSectionBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /** "ADD BAND" button background. */
    private val addBandButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /** Text drawn inside the "ADD BAND" button. */
    private val addBandTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    /** Background pill drawn above each knob to show its type ("Q" or "FREQ"). */
    private val knobTypeLabelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /** Text inside the type label pill above each knob. */
    private val knobTypeLabelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    /** The × stroke drawn inside the delete button circle. */
    private val deleteBtnXPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    /**
     * The trash/delete icon loaded from ic_delete.xml, tinted red so it reads clearly
     * against the translucent button background. Loaded once and reused each frame.
     */
    private val deleteIconDrawable = if (!isInEditMode) {
        ContextCompat.getDrawable(context, DecoR.drawable.ic_delete)?.mutate()?.also {
            it.setTint(ThemeManager.theme.iconTheme.secondaryIconColor)
        }
    } else null

    private val knobArcRect = RectF()

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    init {
        isClickable = true
        isFocusable = true
        if (!isInEditMode) {
            applyThemeColors()
        }
        applyPaintColors()
        setupTextPaints()
        if (!isInEditMode) {
            updateShadowEffect()
        }
    }

    // -------------------------------------------------------------------------
    // Theme application
    // -------------------------------------------------------------------------

    private fun applyThemeColors() {
        accentColor = ThemeManager.accent.primaryAccentColor
        trackColor = ThemeManager.theme.viewGroupTheme.highlightColor
        thumbRingColor = Color.WHITE
        thumbInnerColor = accentColor
        primaryTextColor = ThemeManager.theme.textViewTheme.primaryTextColor
        secondaryTextColor = ThemeManager.theme.textViewTheme.secondaryTextColor
        centerLineColor = ThemeManager.theme.viewGroupTheme.dividerColor
        bezierColor = accentColor
    }

    private fun applyPaintColors() {
        trackPaint.color = trackColor
        trackPaint.strokeWidth = trackStrokePx

        bezierPaint.color = bezierColor
        bezierPaint.strokeWidth = bezierStrokePx


        thumbPillDrawable.fillColor = thumbInnerColor
        thumbPillDrawable.ringColor = thumbRingColor

        trackProgressPaint.color = accentColor
        trackProgressPaint.strokeWidth = trackStrokePx


        pressRingPaint.color = accentColor
        pressRingPaint.strokeWidth = 1.5f * d

        centerLinePaint.color = centerLineColor
        centerLinePaint.strokeWidth = 1f * d
        centerLinePaint.alpha = 80

        freqTextPaint.color = secondaryTextColor
        freqTextPaint.textSize = 9.5f * d

        valueTextPaint.color = primaryTextColor
        valueTextPaint.textSize = 10.5f * d

        // Preamp background: accent color with low alpha
        preampBackgroundPaint.color = accentColor
        preampBackgroundPaint.alpha = 18

        separatorPaint.color = centerLineColor
        separatorPaint.strokeWidth = 1f * d
        separatorPaint.alpha = 90

        // PEQ knob paints
        knobArcTrackPaint.color = trackColor
        knobArcTrackPaint.strokeWidth = 3f * d
        knobArcProgressPaint.color = accentColor
        knobArcProgressPaint.strokeWidth = 3f * d
        knobBodyPaint.color = trackColor
        knobRingPaint.color = centerLineColor
        knobRingPaint.strokeWidth = 1.5f * d
        knobIndicatorPaint.color = accentColor
        knobLabelPaint.color = secondaryTextColor
        knobLabelPaint.textSize = 8.5f * d
        peqSectionBgPaint.color = accentColor
        peqSectionBgPaint.alpha = 12
        addBandButtonPaint.color = accentColor
        addBandButtonPaint.alpha = 30
        addBandTextPaint.color = accentColor
        addBandTextPaint.textSize = 10f * d
        knobTypeLabelBgPaint.color = accentColor
        knobTypeLabelBgPaint.alpha = 35
        knobTypeLabelTextPaint.color = accentColor
        knobTypeLabelTextPaint.textSize = 8f * d
        deleteBtnXPaint.color = Color.argb(200, 25, 25, 25)
        deleteBtnXPaint.strokeWidth = 1.8f * d

        // Re-apply shadow layers so the glow color tracks accent/theme changes.
        applyShadowLayers()
    }

    private fun setupTextPaints() {
        if (!isInEditMode) {
            val tf = TypeFace.getBoldTypeFace(context)
            freqTextPaint.typeface = tf
            valueTextPaint.typeface = tf
            knobLabelPaint.typeface = tf
            addBandTextPaint.typeface = tf
            knobTypeLabelTextPaint.typeface = tf
        }
    }

    /**
     * Applies or removes GPU shadow layers on [bezierPaint] and [trackProgressPaint].
     *
     * [Paint.setShadowLayer] is composited on the GPU by the HWUI RenderThread pipeline
     * (API 28+) without requiring [LAYER_TYPE_SOFTWARE], so the view stays hardware
     * accelerated and renders at full frame rate. The shadow radius and color are
     * derived from the current accent color so the glow always matches the UI theme.
     *
     * Called from [updateShadowEffect] when the preference changes and from
     * [applyPaintColors] whenever the accent or theme colors are updated.
     */
    private fun applyShadowLayers() {
        if (shadowEffectEnabled) {
            val r = Color.red(bezierColor)
            val g = Color.green(bezierColor)
            val b = Color.blue(bezierColor)
            val glowColor = Color.argb(200, r, g, b)
            val progressGlowColor = Color.argb(160, r, g, b)

            bezierPaint.setShadowLayer(10f * d, 0f, 0f, glowColor)
            trackProgressPaint.setShadowLayer(8f * d, 0f, 0f, progressGlowColor)
        } else {
            bezierPaint.clearShadowLayer()
            trackProgressPaint.clearShadowLayer()
        }
    }

    /**
     * Reads the shadow-effect preference, updates the shadow layers on the relevant
     * paints, and ensures the view stays on the hardware-accelerated layer at all times.
     */
    private fun updateShadowEffect() {
        shadowEffectEnabled = AppearancePreferences.isShadowEffectOn()
        applyShadowLayers()
        setLayerType(LAYER_TYPE_HARDWARE, null)
        invalidate()
    }

    // -------------------------------------------------------------------------
    // Layout geometry
    // -------------------------------------------------------------------------

    /**
     * Tells the View that every PEQ knob drawable belongs to this view, so when a
     * drawable calls [android.graphics.drawable.Drawable.invalidateSelf] — for example
     * during a press-color animation — the invalidation actually reaches [onDraw].
     * Without this, the base [View.invalidateDrawable] silently discards the call.
     */
    override fun verifyDrawable(who: android.graphics.drawable.Drawable): Boolean {
        if (peqQKnobDrawables.any { it === who }) return true
        if (peqFreqKnobDrawables.any { it === who }) return true
        return super.verifyDrawable(who)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalculateLayout(w, h)
    }

    private fun recalculateLayout(w: Int, h: Int) {
        val availableWidth = w - paddingStart - paddingEnd
        columnWidth = bandSpacingDp * d
        contentWidth = columnWidth * TOTAL_COLUMNS

        // The label row sits at a fixed position anchored from the view bottom.
        // sliderVerticalPaddingPx below the track provides space for the thumb cap.
        // textGapPx exclusively controls the gap from trackBottom to textRegionTop.
        val twoLineTextHeight = freqTextPaint.fontSpacing + valueTextPaint.fontSpacing
        textRegionTop = (h - paddingBottom).toFloat() - twoLineTextHeight - sliderVerticalPaddingPx

        trackTop = paddingTop + sliderVerticalPaddingPx
        trackBottom = textRegionTop - textGapPx
        trackLength = trackBottom - trackTop

        // PEQ geometry: knob radius is fixed by the diameter constant; section width is
        // then derived so it just wraps its contents rather than using a hard-coded value.
        peqKnobRadiusPx = PEQ_KNOB_DIAMETER_DP / 2f * d
        peqKnobArcRadiusPx = peqKnobRadiusPx * 1.22f  // body radius + arc inset (22 %)
        val hPad = PEQ_SECTION_H_PADDING_DP * d
        val innerGap = PEQ_SLIDER_KNOB_GAP_DP * d
        // Section = left pad + slider zone + slider-knob gap + knob+arc zone + right pad
        peqSectionWidthPx = 2f * hPad + 2f * thumbHalfWidthPx + innerGap + 2f * peqKnobArcRadiusPx
        val arcStroke = PEQ_ARC_STROKE_DP * d
        knobArcTrackPaint.strokeWidth = arcStroke
        knobArcProgressPaint.strokeWidth = arcStroke
        knobRingPaint.strokeWidth = peqKnobRadiusPx * 0.07f

        // Content width for PEQ mode: preamp column + N sections + add-button column.
        val peqContentWidth = columnWidth + peqSectionWidthPx * peqBands.size + peqSectionWidthPx
        peqAddButtonContentX = columnWidth + peqSectionWidthPx * peqBands.size + peqSectionWidthPx * 0.5f

        val effectiveContentWidth = if (mode == Mode.PARAMETRIC) peqContentWidth else contentWidth
        centeredMode = effectiveContentWidth <= availableWidth
        centeringOffset = paddingStart.toFloat() + if (centeredMode) (availableWidth - effectiveContentWidth) / 2f else 0f
        maxScroll = if (centeredMode) 0f else (effectiveContentWidth - availableWidth).coerceAtLeast(0f)
        scrollOffset = scrollOffset.coerceIn(0f, maxScroll)
    }

    // -------------------------------------------------------------------------
    // Public gain API
    // -------------------------------------------------------------------------

    /**
     * Sets the gain for an EQ band.
     *
     * @param bandIndex 0-based EQ band index (0 = 31 Hz, 9 = 16 kHz).
     * @param gain      Gain in dB, clamped to the range -15..+15.
     * @param animate   Animate the thumb to the new position.
     * @param fromUser  True when the change was initiated by the user.
     */
    fun setBandGain(bandIndex: Int, gain: Float, animate: Boolean = false, fromUser: Boolean = false) {
        if (bandIndex !in 0 until BAND_COUNT) return
        val clamped = gain.coerceIn(MIN_DB, MAX_DB)
        gains[bandIndex] = clamped
        gainAnimators[bandIndex]?.cancel()
        if (animate) {
            val start = displayGains[bandIndex]
            gainAnimators[bandIndex] = ValueAnimator.ofFloat(start, clamped).apply {
                duration = 300L
                interpolator = DecelerateInterpolator()
                addUpdateListener { displayGains[bandIndex] = it.animatedValue as Float; invalidate() }
                start()
            }
        } else {
            displayGains[bandIndex] = clamped
            invalidate()
        }
        if (fromUser) bandChangedListener?.onBandChanged(bandIndex, clamped, true)
    }

    /** Returns the current gain for EQ [bandIndex] in dB. */
    fun getBandGain(bandIndex: Int): Float = if (bandIndex in 0 until BAND_COUNT) gains[bandIndex] else 0f

    /**
     * Sets the pre-amplifier gain.
     *
     * @param gain     Gain in dB, clamped to the range -15..+15.
     * @param animate  Animate the thumb to the new position.
     * @param fromUser True when the change was initiated by the user.
     */
    fun setPreampGain(gain: Float, animate: Boolean = false, fromUser: Boolean = false) {
        val clamped = gain.coerceIn(MIN_DB, MAX_DB)
        preampGain = clamped
        preampGainAnimator?.cancel()
        if (animate) {
            val start = preampDisplayGain
            preampGainAnimator = ValueAnimator.ofFloat(start, clamped).apply {
                duration = 300L
                interpolator = DecelerateInterpolator()
                addUpdateListener { preampDisplayGain = it.animatedValue as Float; invalidate() }
                start()
            }
        } else {
            preampDisplayGain = clamped
            invalidate()
        }
        if (fromUser) bandChangedListener?.onBandChanged(PREAMP_BAND_INDEX, clamped, true)
    }

    /** Returns the current preamp gain in dB. */
    fun getPreampGain(): Float = preampGain

    /** Resets all EQ bands and the preamp to 0 dB. */
    fun resetAllBands(animate: Boolean = true) {
        for (i in 0 until BAND_COUNT) setBandGain(i, DEFAULT_DB, animate)
        setPreampGain(DEFAULT_DB, animate)
    }

    /** Sets all EQ band gains from an array (does not affect the preamp). */
    fun setAllGains(allGains: FloatArray, animate: Boolean = false) {
        for (i in 0 until BAND_COUNT) {
            setBandGain(i, if (i < allGains.size) allGains[i] else DEFAULT_DB, animate)
        }
    }

    /** Returns a snapshot of all parametric bands in their current state. */
    fun getPeqBands(): List<PeqBand> = peqBands.toList()

    /** Replaces the PEQ band list with the given bands and refreshes the layout. */
    fun setPeqBands(bands: List<PeqBand>) {
        detachAllPeqDrawables()
        peqBands.clear()
        peqBands.addAll(bands)
        peqQKnobDrawables.clear()
        peqFreqKnobDrawables.clear()
        peqGainPressAnimators.forEach { it?.cancel() }
        peqGainPressScales.clear()
        peqGainPressAnimators.clear()
        repeat(peqBands.size) {
            peqQKnobDrawables.add(makePeqKnobDrawable().also { if (peqDrawablesAttached) it.onAttachedToKnobView() })
            peqFreqKnobDrawables.add(makePeqKnobDrawable().also { if (peqDrawablesAttached) it.onAttachedToKnobView() })
            peqGainPressScales.add(1f)
            peqGainPressAnimators.add(null)
        }
        if (width > 0 && height > 0) recalculateLayout(width, height)
        invalidate()
    }

    /** Appends a new PEQ band with default values and scrolls to show it. */
    fun addPeqBand(band: PeqBand = PeqBand()) {
        peqBands.add(band)
        val qD = makePeqKnobDrawable().also { if (peqDrawablesAttached) it.onAttachedToKnobView() }
        val fD = makePeqKnobDrawable().also { if (peqDrawablesAttached) it.onAttachedToKnobView() }
        peqQKnobDrawables.add(qD)
        peqFreqKnobDrawables.add(fD)
        peqGainPressScales.add(1f)
        peqGainPressAnimators.add(null)
        if (width > 0 && height > 0) recalculateLayout(width, height)
        invalidate()
    }

    private fun detachAllPeqDrawables() {
        if (peqDrawablesAttached) {
            peqQKnobDrawables.forEach { it.onDetachedFromKnobView() }
            peqFreqKnobDrawables.forEach { it.onDetachedFromKnobView() }
        }
    }

    // -------------------------------------------------------------------------
    // Geometry helpers
    // -------------------------------------------------------------------------

    private fun gainToThumbY(gain: Float): Float {
        val fraction = (gain - MIN_DB) / (MAX_DB - MIN_DB)
        return trackBottom - fraction * trackLength
    }

    private fun thumbYToGain(y: Float): Float {
        if (trackLength <= 0f) return DEFAULT_DB
        return (MIN_DB + (trackBottom - y) / trackLength * (MAX_DB - MIN_DB)).coerceIn(MIN_DB, MAX_DB)
    }

    /** Returns the view-space X center of the given internal column (0 = preamp, 1–10 = EQ). */
    private fun columnCenterX(columnIndex: Int): Float =
        centeringOffset + columnIndex * columnWidth + columnWidth / 2f - scrollOffset

    /** Returns the view-space X center of EQ [bandIndex] (0–9). */
    private fun bandCenterX(bandIndex: Int): Float = columnCenterX(bandIndex + 1)

    /** Returns the view-space X center of the preamp column. */
    private fun preampCenterX(): Float = columnCenterX(0)

    /**
     * Returns [PREAMP_BAND_INDEX] when [x] is over the preamp column, 0–9 for EQ bands,
     * or [NO_ACTIVE_BAND] when outside all columns.
     */
    private fun bandIndexAtX(x: Float): Int {
        val contentX = x - centeringOffset + scrollOffset
        if (contentX < 0f || contentX >= contentWidth) return NO_ACTIVE_BAND
        val col = (contentX / columnWidth).toInt().coerceIn(0, TOTAL_COLUMNS - 1)
        return if (col == 0) PREAMP_BAND_INDEX else col - 1
    }

    // -------------------------------------------------------------------------
    // Drawing
    // -------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (trackLength <= 0f) return

        when (mode) {
            Mode.GRAPHIC -> drawGraphicMode(canvas)
            Mode.PARAMETRIC -> drawParametricMode(canvas)
        }
    }

    private fun drawGraphicMode(canvas: Canvas) {
        val visibleLeft = -columnWidth
        val visibleRight = width.toFloat() + columnWidth

        drawPreampBackground(canvas)
        drawCenterLine(canvas)
        drawBezierFill(canvas)
        drawBezierCurve(canvas)
        drawPreampSeparator(canvas)
        drawTracksAndThumbs(canvas, visibleLeft, visibleRight)
        drawPreampTrackAndThumb(canvas)
        drawLabels(canvas, visibleLeft, visibleRight)
        drawPreampLabel(canvas)
    }

    /**
     * Draws the tinted highlight rectangle behind the preamp column so it reads as
     * a distinct region from the 10 EQ-band columns.
     */
    private fun drawPreampBackground(canvas: Canvas) {
        val left = centeringOffset - scrollOffset + 3f * d
        val right = left + columnWidth - 6f * d
        val top = trackTop - sliderVerticalPaddingPx * 0.5f
        val bottom = (height - paddingBottom).toFloat()
        canvas.drawRoundRect(left, top, right, bottom, 10f * d, 10f * d, preampBackgroundPaint)
    }

    /**
     * Draws a hairline vertical separator between the preamp column and the first EQ column
     * to reinforce the visual boundary.
     */
    private fun drawPreampSeparator(canvas: Canvas) {
        val x = centeringOffset + columnWidth - scrollOffset
        val top = trackTop - sliderVerticalPaddingPx * 0.5f
        val bottom = (height - paddingBottom).toFloat()
        canvas.drawLine(x, top, x, bottom, separatorPaint)
    }

    private fun drawCenterLine(canvas: Canvas) {
        val zeroY = gainToThumbY(0f)
        // Span the full content including the preamp column.
        val lineLeft = centeringOffset - scrollOffset
        val lineRight = lineLeft + contentWidth
        canvas.drawLine(lineLeft, zeroY, lineRight, zeroY, centerLinePaint)
    }

    /**
     * Draws the track, thumb, progress segment, and grip lines for the preamp column.
     */
    private fun drawPreampTrackAndThumb(canvas: Canvas) {
        val cx = preampCenterX()
        val thumbY = gainToThumbY(preampDisplayGain)
        val zeroY = gainToThumbY(0f)
        drawColumnTrackAndThumb(canvas, cx, thumbY, zeroY, preampPressScale)
    }

    private fun drawTracksAndThumbs(canvas: Canvas, visibleLeft: Float, visibleRight: Float) {
        val zeroY = gainToThumbY(0f)
        for (i in 0 until BAND_COUNT) {
            val cx = bandCenterX(i)
            if (cx < visibleLeft || cx > visibleRight) continue
            drawColumnTrackAndThumb(canvas, cx, gainToThumbY(displayGains[i]), zeroY, pressScales[i])
        }
    }

    /**
     * Draws the track line, progress segment (with optional glow), and pill thumb for a
     * single column at [cx] with the thumb positioned at [thumbY].
     *
     * @param cx        View-space X center of the column.
     * @param thumbY    View-space Y of the thumb center.
     * @param zeroY     View-space Y corresponding to 0 dB reference.
     * @param scale     Current press-scale for the thumb.
     */
    private fun drawColumnTrackAndThumb(canvas: Canvas, cx: Float, thumbY: Float, zeroY: Float, scale: Float) {
        canvas.drawLine(cx, trackTop, cx, trackBottom, trackPaint)

        val progressTop = minOf(zeroY, thumbY)
        val progressBottom = maxOf(zeroY, thumbY)
        if (progressBottom > progressTop) {
            canvas.drawLine(cx, progressTop, cx, progressBottom, trackProgressPaint)
        }

        val halfW = thumbHalfWidthPx * scale
        val halfH = thumbHalfHeightPx * scale

        if (scale > 1f) {
            val haloAlpha = ((scale - 1f) / 0.12f * 80f).toInt().coerceIn(0, 80)
            pressRingRect.set(
                    cx - halfW - pressRingOutsetPx,
                    thumbY - halfH - pressRingOutsetPx,
                    cx + halfW + pressRingOutsetPx,
                    thumbY + halfH + pressRingOutsetPx
            )
            pressRingPaint.alpha = haloAlpha
            canvas.drawRoundRect(
                    pressRingRect,
                    thumbHalfHeightPx + pressRingOutsetPx,
                    thumbHalfHeightPx + pressRingOutsetPx,
                    pressRingPaint
            )
        }

        thumbPillDrawable.setBoundsF(cx - halfW, thumbY - halfH, cx + halfW, thumbY + halfH)
        thumbPillDrawable.draw(canvas)
    }

    /**
     * Draws the Catmull-Rom spline connecting the 10 EQ-band thumbs (not the preamp).
     * The glow effect is applied via [Paint.setShadowLayer] directly on [bezierPaint],
     * keeping rendering on the hardware-accelerated layer at all times.
     */
    private fun drawBezierCurve(canvas: Canvas) {
        bezierPath.reset()
        val pts = Array(BAND_COUNT) { i -> Pair(bandCenterX(i), gainToThumbY(displayGains[i])) }

        bezierPath.moveTo(pts[0].first, pts[0].second)
        for (i in 0 until BAND_COUNT - 1) {
            val p0 = if (i > 0) pts[i - 1] else pts[i]
            val p1 = pts[i]
            val p2 = pts[i + 1]
            val p3 = if (i < BAND_COUNT - 2) pts[i + 2] else pts[i + 1]
            val cp1x = p1.first + (p2.first - p0.first) / 6f
            val cp1y = p1.second + (p2.second - p0.second) / 6f
            val cp2x = p2.first - (p3.first - p1.first) / 6f
            val cp2y = p2.second - (p3.second - p1.second) / 6f
            bezierPath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.first, p2.second)
        }

        canvas.drawPath(bezierPath, bezierPaint)
    }

    /**
     * Draws a translucent gradient fill below the Catmull-Rom spline to add visual depth.
     *
     * The filled path traces the same spline as [drawBezierCurve] and then closes
     * downward to [trackBottom]. A vertical [LinearGradient] fades from the accent
     * color (low opacity) at the topmost visible point of the curve to fully transparent
     * at [trackBottom], so the fill never reaches the label row at the bottom of the view.
     *
     * Must be called BEFORE [drawBezierCurve] in [onDraw] so the stroke renders on top.
     */
    private fun drawBezierFill(canvas: Canvas) {
        bezierFillPath.reset()

        val pts = Array(BAND_COUNT) { i -> Pair(bandCenterX(i), gainToThumbY(displayGains[i])) }

        // Trace the same Catmull-Rom spline as drawBezierCurve.
        bezierFillPath.moveTo(pts[0].first, pts[0].second)
        for (i in 0 until BAND_COUNT - 1) {
            val p0 = if (i > 0) pts[i - 1] else pts[i]
            val p1 = pts[i]
            val p2 = pts[i + 1]
            val p3 = if (i < BAND_COUNT - 2) pts[i + 2] else pts[i + 1]
            val cp1x = p1.first + (p2.first - p0.first) / 6f
            val cp1y = p1.second + (p2.second - p0.second) / 6f
            val cp2x = p2.first - (p3.first - p1.first) / 6f
            val cp2y = p2.second - (p3.second - p1.second) / 6f
            bezierFillPath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.first, p2.second)
        }

        // Close the path straight down to trackBottom, across, and back up to the start,
        // so the filled area sits entirely below the spline line.
        bezierFillPath.lineTo(pts.last().first, trackBottom)
        bezierFillPath.lineTo(pts.first().first, trackBottom)
        bezierFillPath.close()

        // Gradient runs from the topmost (smallest Y) visible curve point to trackBottom.
        // This makes the fill feel anchored to wherever the curve sits at any given moment.
        val topY = pts.minOf { it.second }
        val r = Color.red(bezierColor)
        val g = Color.green(bezierColor)
        val b = Color.blue(bezierColor)

        bezierFillPaint.shader = LinearGradient(
                0f, topY,
                0f, trackBottom,
                Color.argb(52, r, g, b),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
        )

        canvas.drawPath(bezierFillPath, bezierFillPaint)
    }

    /** Draws frequency and dB-value labels for the 10 EQ bands. */
    private fun drawLabels(canvas: Canvas, visibleLeft: Float, visibleRight: Float) {
        val freqY = textRegionTop + freqTextPaint.fontSpacing * 0.85f
        val valueY = freqY + freqTextPaint.fontSpacing
        for (i in 0 until BAND_COUNT) {
            val cx = bandCenterX(i)
            if (cx < visibleLeft || cx > visibleRight) continue
            canvas.drawText(FREQUENCY_LABELS[i], cx, freqY, freqTextPaint)
            val gain = displayGains[i]
            valueTextPaint.color = if (abs(gain) < 0.05f) secondaryTextColor else accentColor
            canvas.drawText(formatGain(gain), cx, valueY, valueTextPaint)
        }
    }

    /**
     * Draws the "PREAMP" label and current dB value below the preamp column.
     */
    private fun drawPreampLabel(canvas: Canvas) {
        val cx = preampCenterX()
        if (cx < -columnWidth || cx > width + columnWidth) return
        val freqY = textRegionTop + freqTextPaint.fontSpacing * 0.85f
        val valueY = freqY + freqTextPaint.fontSpacing

        // Draw "PREAMP" in the accent color to match the highlighted region.
        val savedFreqColor = freqTextPaint.color
        freqTextPaint.color = accentColor
        canvas.drawText("PREAMP", cx, freqY, freqTextPaint)
        freqTextPaint.color = savedFreqColor

        valueTextPaint.color = if (abs(preampDisplayGain) < 0.05f) secondaryTextColor else accentColor
        canvas.drawText(formatGain(preampDisplayGain), cx, valueY, valueTextPaint)
    }

    // -------------------------------------------------------------------------
    // Parametric EQ drawing
    // -------------------------------------------------------------------------

    /**
     * Draws the entire parametric EQ layout: the shared preamp slider on the left,
     * then one section per PEQ band, then an "Add Band" button at the end.
     */
    private fun drawParametricMode(canvas: Canvas) {
        val viewLeft = -peqSectionWidthPx
        val viewRight = width.toFloat() + peqSectionWidthPx

        // Preamp column is shared with graphic mode and stays on the left.
        drawPreampBackground(canvas)
        drawPreampSeparator(canvas)
        drawPreampTrackAndThumb(canvas)
        drawPreampLabel(canvas)

        // Draw each parametric band section.
        for (i in peqBands.indices) {
            val cx = peqBandSectionCenterX(i)
            if (cx < viewLeft || cx > viewRight) continue
            drawPeqSection(canvas, i, cx)
        }

        // "Add Band" button appears after the last section.
        val addX = centeringOffset + peqAddButtonContentX - scrollOffset
        if (addX in viewLeft..viewRight) {
            drawAddBandButton(canvas, addX)
        }
    }

    /**
     * Returns the horizontal center of a PEQ band section in view coordinates.
     * Sections start right after the preamp column.
     */
    private fun peqBandSectionCenterX(bandIndex: Int): Float =
        centeringOffset + columnWidth + bandIndex * peqSectionWidthPx + peqSectionWidthPx * 0.5f - scrollOffset

    /**
     * Draws one PEQ band section: a tinted background, a gain slider on the left side,
     * two stacked knobs (Q on top, frequency on bottom) just to its right, and a small
     * delete button in the top-right corner of the background.
     *
     * Section width is content-driven: left pad + slider + inner gap + knob+arc + right pad.
     */
    private fun drawPeqSection(canvas: Canvas, bandIndex: Int, cx: Float) {
        val band = peqBands[bandIndex]
        val hPad = PEQ_SECTION_H_PADDING_DP * d
        val innerGap = PEQ_SLIDER_KNOB_GAP_DP * d
        val arcR = peqKnobArcRadiusPx
        val halfSection = peqSectionWidthPx * 0.5f

        val bgLeft = cx - halfSection + 3f * d
        val bgRight = cx + halfSection - 3f * d
        val bgTop = trackTop - sliderVerticalPaddingPx * 0.5f
        val bgBottom = (height - paddingBottom).toFloat()
        canvas.drawRoundRect(bgLeft, bgTop, bgRight, bgBottom, 8f * d, 8f * d, peqSectionBgPaint)

        // Gain slider: left-inset by hPad, centered on the slider's own half-width.
        val sliderCx = bgLeft + 3f * d + hPad + thumbHalfWidthPx
        val zeroY = gainToThumbY(0f)
        val thumbY = gainToThumbY(band.gain.coerceIn(MIN_DB, MAX_DB))
        val gainPressScale = peqGainPressScales.getOrElse(bandIndex) { 1f }
        drawColumnTrackAndThumb(canvas, sliderCx, thumbY, zeroY, gainPressScale)

        val freqY = textRegionTop + freqTextPaint.fontSpacing * 0.85f
        val valueY = freqY + freqTextPaint.fontSpacing
        valueTextPaint.color = if (abs(band.gain) < 0.05f) secondaryTextColor else accentColor
        canvas.drawText(formatGain(band.gain), sliderCx, valueY, valueTextPaint)
        val savedFreqColor = freqTextPaint.color
        freqTextPaint.color = secondaryTextColor
        canvas.drawText("GAIN", sliderCx, freqY, freqTextPaint)
        freqTextPaint.color = savedFreqColor

        // Knob column: starts right after the slider's right edge + inner gap.
        val knobCx = sliderCx + thumbHalfWidthPx + innerGap + arcR

        // The label pill above the knob and the value label below it each take up
        // vertical space, so we compute the knob centers from the available track range
        // rather than using fixed fractions. This prevents the Q value label from
        // overlapping the FREQ pill label when the knobs are close together.
        val labelGap = PEQ_KNOB_LABEL_GAP_DP * d
        val pillH = knobTypeLabelTextPaint.fontSpacing * 0.9f + 4f * d
        // Vertical space occupied by one knob's visual chrome (pill above + arc + label below).
        val knobTotalHeight = pillH + labelGap + 2f * arcR + labelGap + knobLabelPaint.fontSpacing
        val trackMid = trackTop + trackLength * 0.5f
        val qKnobCy = trackMid - knobTotalHeight * 0.5f - arcR - labelGap - pillH * 0.5f + knobTotalHeight * 0.5f
        val freqKnobCy = qKnobCy + knobTotalHeight

        drawPeqKnob(canvas, knobCx, qKnobCy, qToNormalized(band.q), "Q", formatQLabel(band.q),
                    peqQKnobDrawables.getOrNull(bandIndex))
        drawPeqKnob(canvas, knobCx, freqKnobCy, freqToNormalized(band.frequencyHz), "FREQ", formatFreqLabel(band.frequencyHz),
                    peqFreqKnobDrawables.getOrNull(bandIndex))

        // Delete button: small circle in the top-right corner of the section background.
        val delR = PEQ_DELETE_BTN_RADIUS_DP * d
        val delCx = bgRight - delR - 2f * d
        val delCy = bgTop + delR + 2f * d
        drawDeleteButton(canvas, delCx, delCy, delR)
    }

    /**
     * Draws a single round knob at ([cx], [cy]) with a progress arc, a static type
     * label pill above the arc, and a dynamic value label below it.
     *
     * The [drawable] handles the knob body, ring, and animated indicator dot. It is
     * rotated on the canvas so the dot tracks the current position. When [drawable]
     * is null a simple manual fallback is used.
     *
     * @param normalizedValue  Current position of the knob in the range 0..1.
     * @param typeLabel        Short static name shown in the pill above (e.g., "Q" or "FREQ").
     * @param valueLabel       Current human-readable value shown below the arc.
     * @param drawable         Per-knob drawable that owns the press-state animation.
     */
    private fun drawPeqKnob(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            normalizedValue: Float,
            typeLabel: String,
            valueLabel: String,
            drawable: SimpleBaseKnobDrawable? = null
    ) {
        val r = peqKnobRadiusPx
        val arcR = peqKnobArcRadiusPx

        // Background arc track.
        knobArcRect.set(cx - arcR, cy - arcR, cx + arcR, cy + arcR)
        canvas.drawArc(knobArcRect, KNOB_ARC_START_DEG, KNOB_SWEEP_DEG, false, knobArcTrackPaint)

        // Filled progress arc sweeps from start to the current value.
        val sweepAngle = normalizedValue * KNOB_SWEEP_DEG
        if (sweepAngle > 0f) {
            canvas.drawArc(knobArcRect, KNOB_ARC_START_DEG, sweepAngle, false, knobArcProgressPaint)
        }

        // Draw the knob body using the SimpleBaseKnobDrawable so the press-state color
        // animation (idle → accent) is handled by the drawable itself. The drawable's
        // indicator dot always points straight up, so we rotate the canvas to the correct
        // position before drawing and then restore it.
        //
        // Rotation mapping: normalized=0.5 → 0° (12 o'clock), 0 → -135°, 1 → +135°.
        val rotationDeg = (normalizedValue - 0.5f) * KNOB_SWEEP_DEG
        if (drawable != null) {
            drawable.setBounds((cx - r).toInt(), (cy - r).toInt(), (cx + r).toInt(), (cy + r).toInt())
            canvas.save()
            canvas.rotate(rotationDeg, cx, cy)
            drawable.draw(canvas)
            canvas.restore()
        } else {
            // Fallback for the rare case the drawable list is out of sync.
            knobBodyPaint.color = trackColor
            canvas.drawCircle(cx, cy, r, knobBodyPaint)
            canvas.drawCircle(cx, cy, r, knobRingPaint)
            val indicatorAngleRad = Math.toRadians((KNOB_ARC_START_DEG + normalizedValue * KNOB_SWEEP_DEG).toDouble())
            knobIndicatorPaint.color = accentColor
            canvas.drawCircle(
                    cx + r * 0.6f * cos(indicatorAngleRad).toFloat(),
                    cy + r * 0.6f * sin(indicatorAngleRad).toFloat(),
                    r * 0.12f, knobIndicatorPaint
            )
        }

        // Type label pill above the arc — shows the knob's purpose ("Q" or "FREQ").
        val pillPad = 4f * d
        val pillH = knobTypeLabelTextPaint.fontSpacing * 0.9f + pillPad
        val pillW = knobTypeLabelTextPaint.measureText(typeLabel) + pillPad * 2.5f
        val labelGap = PEQ_KNOB_LABEL_GAP_DP * d
        val pillCy = cy - arcR - labelGap - pillH * 0.5f
        val pillLeft = cx - pillW * 0.5f
        val pillTop = pillCy - pillH * 0.5f
        canvas.drawRoundRect(pillLeft, pillTop, pillLeft + pillW, pillTop + pillH, pillH * 0.5f, pillH * 0.5f, knobTypeLabelBgPaint)
        canvas.drawText(typeLabel, cx, pillCy + knobTypeLabelTextPaint.fontSpacing * 0.35f, knobTypeLabelTextPaint)

        // Current value label below the arc.
        val labelY = cy + arcR + labelGap + knobLabelPaint.fontSpacing * 0.9f
        canvas.drawText(valueLabel, cx, labelY, knobLabelPaint)
    }

    /**
     * Draws the "ADD BAND" button as a rounded rectangle with a plus label,
     * centered at ([cx], mid-track).
     */
    private fun drawAddBandButton(canvas: Canvas, cx: Float) {
        val hPad = PEQ_SECTION_H_PADDING_DP * d
        val btnW = peqSectionWidthPx - hPad * 2f
        val btnH = addBandTextPaint.fontSpacing + 14f * d
        val btnCy = trackTop + trackLength * 0.5f
        val left = cx - btnW * 0.5f
        val top = btnCy - btnH * 0.5f
        canvas.drawRoundRect(left, top, left + btnW, top + btnH, 10f * d, 10f * d, addBandButtonPaint)
        addBandTextPaint.color = accentColor
        canvas.drawText("＋ ADD BAND", cx, btnCy + addBandTextPaint.fontSpacing * 0.36f, addBandTextPaint)
    }

    /**
     * Draws the delete button — a small translucent circle with the trash-bin icon — at ([cx], [cy]).
     * The [radius] matches [PEQ_DELETE_BTN_RADIUS_DP] converted to pixels.
     * The icon color is set when the drawable is initialized so it always reads well on
     * the semi-transparent red background circle.
     */
    private fun drawDeleteButton(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val icon = deleteIconDrawable
        if (icon != null) {
            val iconHalf = (radius * 0.72f).toInt()
            icon.setBounds((cx - iconHalf).toInt(), (cy - iconHalf).toInt(),
                           (cx + iconHalf).toInt(), (cy + iconHalf).toInt())
            icon.draw(canvas)
        } else {
            // Fallback: simple × in case the drawable couldn't be loaded.
            val arm = radius * 0.45f
            canvas.drawLine(cx - arm, cy - arm, cx + arm, cy + arm, deleteBtnXPaint)
            canvas.drawLine(cx + arm, cy - arm, cx - arm, cy + arm, deleteBtnXPaint)
        }
    }

    /** Returns a compact Q label string (e.g., "1.0" or "0.5"). */
    private fun formatQLabel(q: Float): String = "%.1f".format(q)

    /** Maps Q factor (0.1..10) to a normalized 0..1 value on a log scale. */
    private fun qToNormalized(q: Float): Float {
        val clamped = q.coerceIn(PEQ_Q_MIN, PEQ_Q_MAX)
        return (ln(clamped.toDouble()) - ln(PEQ_Q_MIN.toDouble())).toFloat() /
                (ln(PEQ_Q_MAX.toDouble()) - ln(PEQ_Q_MIN.toDouble())).toFloat()
    }

    /** Converts a normalized 0..1 value back to a Q factor on a log scale. */
    private fun normalizedToQ(norm: Float): Float {
        val logMin = ln(PEQ_Q_MIN.toDouble())
        val logMax = ln(PEQ_Q_MAX.toDouble())
        return exp(logMin + norm.coerceIn(0f, 1f) * (logMax - logMin)).toFloat()
    }

    /** Maps a frequency in Hz (20..20000) to a normalized 0..1 value on a log scale. */
    private fun freqToNormalized(hz: Float): Float {
        val clamped = hz.coerceIn(PEQ_FREQ_MIN_HZ, PEQ_FREQ_MAX_HZ)
        return (ln(clamped.toDouble()) - ln(PEQ_FREQ_MIN_HZ.toDouble())).toFloat() /
                (ln(PEQ_FREQ_MAX_HZ.toDouble()) - ln(PEQ_FREQ_MIN_HZ.toDouble())).toFloat()
    }

    /** Converts a normalized 0..1 value back to a frequency in Hz on a log scale. */
    private fun normalizedToFreq(norm: Float): Float {
        val logMin = ln(PEQ_FREQ_MIN_HZ.toDouble())
        val logMax = ln(PEQ_FREQ_MAX_HZ.toDouble())
        return exp(logMin + norm.coerceIn(0f, 1f) * (logMax - logMin)).toFloat()
    }

    /** Returns a short human-readable frequency string (e.g., "1.2k", "250"). */
    private fun formatFreqLabel(hz: Float): String = when {
        hz >= 1000f -> "${"%.1f".format(hz / 1000f)}k"
        else -> "${hz.toInt()}"
    }

    private fun formatGain(gain: Float): String = when {
        abs(gain) < 0.05f -> "0"
        gain > 0f -> "+${"%.1f".format(gain)}"
        else -> "%.1f".format(gain)
    }

    // -------------------------------------------------------------------------
    // Touch handling
    // -------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mode == Mode.PARAMETRIC) return handlePeqTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Only claim the event when the finger lands inside the slider content area.
                // In landscape mode (or any configuration where the content is narrower than
                // the view), touches in the horizontal blank/padding regions are passed back
                // to the parent so they don't block scroll containers or other gestures.
                if (!isTouchWithinContentBounds(event.x)) return false
                handleDown(event)
            }
            MotionEvent.ACTION_MOVE -> {
                handleMove(event)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handleUp(event)
            }
        }
        performClick()
        return true
    }

    /**
     * Returns true when [x] falls within the scrollable content area in view coordinates.
     *
     * The content spans from [centeringOffset] − [scrollOffset] (left edge of the first
     * column) to that value plus [contentWidth] (right edge of the last column). Touches
     * outside this range — e.g. in horizontal padding or centered-mode blank flanks —
     * should not be consumed by this view.
     */
    private fun isTouchWithinContentBounds(x: Float): Boolean {
        val contentLeft = centeringOffset - scrollOffset
        val contentRight = contentLeft + contentWidth
        return x >= contentLeft && x <= contentRight
    }

    override fun performClick(): Boolean {
        super.performClick(); return true
    }

    private fun handleDown(event: MotionEvent) {
        touchStartX = event.x
        touchStartY = event.y
        scrollOffsetAtDown = scrollOffset
        isScrollGesture = false
        isBandGesture = false

        if (scrollSpring.isRunning) scrollSpring.cancel()
        if (scrollFling.isRunning) scrollFling.cancel()

        velocityTracker?.recycle()
        velocityTracker = VelocityTracker.obtain()
        velocityTracker?.addMovement(event)

        val band = bandIndexAtX(event.x)
        if (band != NO_ACTIVE_BAND) {
            thumbYAtDown = gainToThumbY(
                    if (band == PREAMP_BAND_INDEX) preampDisplayGain else displayGains[band]
            )
            startBandPressAnimation(band, true)
            context.vibrateEffect(VibrationEffect.EFFECT_TICK, TAG)
        }
        activeBandIndex = band
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun handleMove(event: MotionEvent) {
        velocityTracker?.addMovement(event)
        val dx = event.x - touchStartX
        val dy = event.y - touchStartY

        if (!isScrollGesture && !isBandGesture) {
            val adx = abs(dx);
            val ady = abs(dy)
            if (adx > touchSlop || ady > touchSlop) {
                isBandGesture = activeBandIndex != NO_ACTIVE_BAND && ady > adx * 0.8f
                isScrollGesture = !isBandGesture && adx > ady * 0.8f
                if (isScrollGesture && activeBandIndex != NO_ACTIVE_BAND) {
                    startBandPressAnimation(activeBandIndex, false)
                }
            }
        }

        when {
            isBandGesture && activeBandIndex != NO_ACTIVE_BAND -> {
                if (isEnabled) {
                    val newGain = thumbYToGain(thumbYAtDown + (event.y - touchStartY)).coerceIn(MIN_DB, MAX_DB)
                    if (activeBandIndex == PREAMP_BAND_INDEX) {
                        val prev = preampGain
                        if (newGain != prev) {
                            if (prev.toInt() != newGain.toInt()) context.vibrateEffect(VibrationEffect.EFFECT_CLICK, TAG)
                            val hitLimit = (newGain == MIN_DB && prev > MIN_DB) || (newGain == MAX_DB && prev < MAX_DB)
                            if (hitLimit) context.vibrateEffect(VibrationEffect.EFFECT_HEAVY_CLICK, TAG)
                            preampGain = newGain
                            preampGainAnimator?.cancel()
                            preampDisplayGain = newGain
                            bandChangedListener?.onBandChanged(PREAMP_BAND_INDEX, newGain, true)
                            invalidate()
                        }
                    } else {
                        val band = activeBandIndex
                        val prev = gains[band]
                        if (newGain != prev) {
                            if (prev.toInt() != newGain.toInt()) context.vibrateEffect(VibrationEffect.EFFECT_CLICK, TAG)
                            val hitLimit = (newGain == MIN_DB && prev > MIN_DB) || (newGain == MAX_DB && prev < MAX_DB)
                            if (hitLimit) context.vibrateEffect(VibrationEffect.EFFECT_HEAVY_CLICK, TAG)
                            gains[band] = newGain
                            gainAnimators[band]?.cancel()
                            displayGains[band] = newGain
                            bandChangedListener?.onBandChanged(band, newGain, true)
                            invalidate()
                        }
                    }
                }
            }
            isScrollGesture -> {
                if (centeredMode) return
                scrollOffset = applyOverscrollResistance(scrollOffsetAtDown - dx)
                invalidate()
            }
        }
    }

    private fun handleUp(@Suppress("UNUSED_PARAMETER") event: MotionEvent) {
        if (activeBandIndex != NO_ACTIVE_BAND) startBandPressAnimation(activeBandIndex, false)

        if (isScrollGesture && !centeredMode) {
            velocityTracker?.computeCurrentVelocity(1000)
            val flingVelocity = -(velocityTracker?.xVelocity ?: 0f)
            if (abs(flingVelocity) > 50f) {
                if (scrollFling.isRunning) scrollFling.cancel()
                scrollFling.setMinValue(-maxOverscrollPx)
                scrollFling.setMaxValue(maxScroll + maxOverscrollPx)
                scrollFling.setStartVelocity(flingVelocity)
                scrollFling.setStartValue(scrollOffset)
                scrollFling.start()
            } else {
                snapScrollToBounds()
            }
        }

        velocityTracker?.recycle(); velocityTracker = null
        activeBandIndex = NO_ACTIVE_BAND
        isScrollGesture = false; isBandGesture = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    // -------------------------------------------------------------------------
    // Parametric EQ touch handling
    // -------------------------------------------------------------------------

    /**
     * Handles all touch events when in [Mode.PARAMETRIC]. Identifies whether the user
     * is touching a gain slider, one of the two knobs, or the "Add Band" button, and
     * routes the gesture accordingly.
     */
    private fun handlePeqTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isTouchWithinPeqContentBounds(event.x)) return false
                peqHandleDown(event)
            }
            MotionEvent.ACTION_MOVE -> peqHandleMove(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> peqHandleUp(event)
        }
        performClick()
        return true
    }

    /**
     * Returns true when [x] falls within the PEQ content area
     * (preamp column + all band sections + add button).
     */
    private fun isTouchWithinPeqContentBounds(x: Float): Boolean {
        val contentLeft = centeringOffset - scrollOffset
        val contentRight = contentLeft + columnWidth + peqSectionWidthPx * peqBands.size + peqSectionWidthPx
        return x >= contentLeft && x <= contentRight
    }

    private fun peqHandleDown(event: MotionEvent) {
        touchStartX = event.x
        touchStartY = event.y
        scrollOffsetAtDown = scrollOffset
        isScrollGesture = false
        isBandGesture = false
        peqTouchTarget = PeqTarget.NONE
        peqTouchBandIndex = -1
        activeBandIndex = NO_ACTIVE_BAND

        if (scrollSpring.isRunning) scrollSpring.cancel()
        if (scrollFling.isRunning) scrollFling.cancel()

        velocityTracker?.recycle()
        velocityTracker = VelocityTracker.obtain()
        velocityTracker?.addMovement(event)

        val contentX = event.x - centeringOffset + scrollOffset

        // Preamp slider column.
        if (contentX >= 0f && contentX < columnWidth) {
            activeBandIndex = PREAMP_BAND_INDEX
            thumbYAtDown = gainToThumbY(preampDisplayGain)
            startBandPressAnimation(PREAMP_BAND_INDEX, true)
            context.vibrateEffect(VibrationEffect.EFFECT_TICK, TAG)
            parent?.requestDisallowInterceptTouchEvent(true)
            return
        }

        // One of the PEQ band sections.
        val sectionIndex = ((contentX - columnWidth) / peqSectionWidthPx).toInt()
        if (sectionIndex in peqBands.indices) {
            val cx = peqBandSectionCenterX(sectionIndex)
            val halfSection = peqSectionWidthPx * 0.5f
            val hPad = PEQ_SECTION_H_PADDING_DP * d
            val innerGap = PEQ_SLIDER_KNOB_GAP_DP * d
            val arcR = peqKnobArcRadiusPx
            val bgLeft = cx - halfSection + 3f * d
            val bgRight = cx + halfSection - 3f * d
            val bgTop = trackTop - sliderVerticalPaddingPx * 0.5f
            val sliderCx = bgLeft + 3f * d + hPad + thumbHalfWidthPx
            val knobCx = sliderCx + thumbHalfWidthPx + innerGap + arcR
            val delR = PEQ_DELETE_BTN_RADIUS_DP * d
            val delCx = bgRight - delR - 2f * d
            val delCy = bgTop + delR + 2f * d

            // Mirror the knob center calculation from drawPeqSection so hit-test positions
            // always match what's actually drawn on screen.
            val labelGap = PEQ_KNOB_LABEL_GAP_DP * d
            val pillH = knobTypeLabelTextPaint.fontSpacing * 0.9f + 4f * d
            val knobTotalHeight = pillH + labelGap + 2f * arcR + labelGap + knobLabelPaint.fontSpacing
            val trackMid = trackTop + trackLength * 0.5f
            val qKnobCyActual = trackMid - knobTotalHeight * 0.5f - arcR - labelGap - pillH * 0.5f + knobTotalHeight * 0.5f
            val freqKnobCyActual = qKnobCyActual + knobTotalHeight

            when {
                dist(event.x, event.y, delCx, delCy) <= delR * 1.5f -> {
                    peqTouchTarget = PeqTarget.DELETE_BUTTON
                    peqTouchBandIndex = sectionIndex
                    context.vibrateEffect(VibrationEffect.EFFECT_TICK, TAG)
                }
                dist(event.x, event.y, knobCx, qKnobCyActual) <= arcR * 1.1f -> {
                    peqTouchTarget = PeqTarget.Q_KNOB
                    peqTouchBandIndex = sectionIndex
                    peqKnobPrevAngle = angleDeg(event.x - knobCx, event.y - qKnobCyActual)
                    peqKnobCurrentNorm = qToNormalized(peqBands[sectionIndex].q)
                    peqKnobValueAtDown = peqKnobCurrentNorm
                    peqQKnobDrawables.getOrNull(sectionIndex)?.onPressedStateChanged(true, 140)
                    context.vibrateEffect(VibrationEffect.EFFECT_TICK, TAG)
                }
                dist(event.x, event.y, knobCx, freqKnobCyActual) <= arcR * 1.1f -> {
                    peqTouchTarget = PeqTarget.FREQ_KNOB
                    peqTouchBandIndex = sectionIndex
                    peqKnobPrevAngle = angleDeg(event.x - knobCx, event.y - freqKnobCyActual)
                    peqKnobCurrentNorm = freqToNormalized(peqBands[sectionIndex].frequencyHz)
                    peqKnobValueAtDown = peqKnobCurrentNorm
                    peqFreqKnobDrawables.getOrNull(sectionIndex)?.onPressedStateChanged(true, 140)
                    context.vibrateEffect(VibrationEffect.EFFECT_TICK, TAG)
                }
                abs(event.x - sliderCx) <= thumbHalfWidthPx * 2.5f -> {
                    peqTouchTarget = PeqTarget.GAIN_SLIDER
                    peqTouchBandIndex = sectionIndex
                    peqGainAtDown = peqBands[sectionIndex].gain
                    thumbYAtDown = gainToThumbY(peqGainAtDown)
                    startPeqGainPressAnimation(sectionIndex, true)
                    context.vibrateEffect(VibrationEffect.EFFECT_TICK, TAG)
                }
            }
            parent?.requestDisallowInterceptTouchEvent(true)
            return
        }

        // "Add Band" button.
        val addBtnViewX = centeringOffset + peqAddButtonContentX - scrollOffset
        if (abs(event.x - addBtnViewX) <= peqSectionWidthPx * 0.4f &&
                abs(event.y - (trackTop + trackLength * 0.5f)) <= peqKnobRadiusPx * 1.5f) {
            peqTouchTarget = PeqTarget.ADD_BUTTON
        }

        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun peqHandleMove(event: MotionEvent) {
        velocityTracker?.addMovement(event)
        val dx = event.x - touchStartX
        val dy = event.y - touchStartY

        // Preamp slider drag.
        if (activeBandIndex == PREAMP_BAND_INDEX) {
            if (!isBandGesture && !isScrollGesture) {
                if (abs(dy) > touchSlop) isBandGesture = true
                else if (abs(dx) > touchSlop) isScrollGesture = true
            }
            if (isBandGesture) {
                val newGain = thumbYToGain(thumbYAtDown + dy).coerceIn(MIN_DB, MAX_DB)
                if (newGain != preampGain) {
                    if (preampGain.toInt() != newGain.toInt()) context.vibrateEffect(VibrationEffect.EFFECT_CLICK, TAG)
                    preampGain = newGain; preampDisplayGain = newGain
                    bandChangedListener?.onBandChanged(PREAMP_BAND_INDEX, newGain, true)
                    invalidate()
                }
            } else if (isScrollGesture && !centeredMode) {
                startBandPressAnimation(PREAMP_BAND_INDEX, false)
                scrollOffset = applyOverscrollResistance(scrollOffsetAtDown - dx)
                invalidate()
            }
            return
        }

        when (peqTouchTarget) {
            PeqTarget.GAIN_SLIDER -> {
                if (!isBandGesture && !isScrollGesture) {
                    if (abs(dy) > touchSlop) isBandGesture = true
                    else if (abs(dx) > touchSlop) {
                        isScrollGesture = true
                        // Finger moved sideways — cancel the thumb press state.
                        startPeqGainPressAnimation(peqTouchBandIndex, false)
                    }
                }
                if (isBandGesture && peqTouchBandIndex >= 0) {
                    val newGain = thumbYToGain(thumbYAtDown + dy).coerceIn(MIN_DB, MAX_DB)
                    val band = peqBands[peqTouchBandIndex]
                    if (newGain != band.gain) {
                        if (band.gain.toInt() != newGain.toInt()) context.vibrateEffect(VibrationEffect.EFFECT_CLICK, TAG)
                        band.gain = newGain
                        peqBandChangedListener?.onPeqBandsChanged(peqBands.toList())
                        invalidate()
                    }
                } else if (isScrollGesture && !centeredMode) {
                    scrollOffset = applyOverscrollResistance(scrollOffsetAtDown - dx)
                    invalidate()
                }
            }
            PeqTarget.Q_KNOB, PeqTarget.FREQ_KNOB -> {
                if (peqTouchBandIndex < 0) return
                // A knob rotation gesture is never converted to a horizontal scroll — the
                // user intentionally landed on the knob so we own all subsequent movement.
                if (isScrollGesture) return
                val cx = peqBandSectionCenterX(peqTouchBandIndex)
                val halfSection = peqSectionWidthPx * 0.5f
                val hPad = PEQ_SECTION_H_PADDING_DP * d
                val innerGap = PEQ_SLIDER_KNOB_GAP_DP * d
                val bgLeft = cx - halfSection + 3f * d
                val sliderCx = bgLeft + 3f * d + hPad + thumbHalfWidthPx
                val knobCx = sliderCx + thumbHalfWidthPx + innerGap + peqKnobArcRadiusPx

                // Mirror the knob center calculation from drawPeqSection.
                val labelGap = PEQ_KNOB_LABEL_GAP_DP * d
                val pillH = knobTypeLabelTextPaint.fontSpacing * 0.9f + 4f * d
                val knobTotalHeight = pillH + labelGap + 2f * peqKnobArcRadiusPx + labelGap + knobLabelPaint.fontSpacing
                val trackMid = trackTop + trackLength * 0.5f
                val qKnobCy = trackMid - knobTotalHeight * 0.5f - peqKnobArcRadiusPx - labelGap - pillH * 0.5f + knobTotalHeight * 0.5f
                val freqKnobCy = qKnobCy + knobTotalHeight
                val knobCy = if (peqTouchTarget == PeqTarget.Q_KNOB) qKnobCy else freqKnobCy

                // Compute the angle delta from the previous frame rather than from the
                // initial touch-down position. This means the knob always moves in the
                // direction the finger is rotating, and crossing the dead zone at the
                // bottom of the arc never causes a sudden value jump.
                val currentAngle = angleDeg(event.x - knobCx, event.y - knobCy)
                var delta = currentAngle - peqKnobPrevAngle
                // Wrap delta to the nearest direction so a slow pass through 0°/360° is smooth.
                if (delta > 180f) delta -= 360f
                if (delta < -180f) delta += 360f
                peqKnobPrevAngle = currentAngle

                peqKnobCurrentNorm = (peqKnobCurrentNorm + delta / KNOB_SWEEP_DEG).coerceIn(0f, 1f)
                val band = peqBands[peqTouchBandIndex]
                if (peqTouchTarget == PeqTarget.Q_KNOB) {
                    val newQ = normalizedToQ(peqKnobCurrentNorm)
                    if (abs(newQ - band.q) > 0.01f) {
                        band.q = newQ
                        peqBandChangedListener?.onPeqBandsChanged(peqBands.toList())
                        invalidate()
                    }
                } else {
                    val newFreq = normalizedToFreq(peqKnobCurrentNorm)
                    if (abs(newFreq - band.frequencyHz) > 0.5f) {
                        band.frequencyHz = newFreq
                        peqBandChangedListener?.onPeqBandsChanged(peqBands.toList())
                        invalidate()
                    }
                }
            }
            PeqTarget.NONE, PeqTarget.ADD_BUTTON, PeqTarget.DELETE_BUTTON -> {
                // Treat as a plain horizontal scroll when no specific element was hit.
                if (!isScrollGesture && abs(dx) > touchSlop) isScrollGesture = true
                if (isScrollGesture && !centeredMode) {
                    scrollOffset = applyOverscrollResistance(scrollOffsetAtDown - dx)
                    invalidate()
                }
            }
        }
    }

    private fun peqHandleUp(event: MotionEvent) {
        if (activeBandIndex == PREAMP_BAND_INDEX) startBandPressAnimation(PREAMP_BAND_INDEX, false)

        // Release any knob or gain-slider press animations before list mutation.
        if (peqTouchBandIndex >= 0) {
            when (peqTouchTarget) {
                PeqTarget.Q_KNOB -> peqQKnobDrawables.getOrNull(peqTouchBandIndex)?.onPressedStateChanged(false, 200)
                PeqTarget.FREQ_KNOB -> peqFreqKnobDrawables.getOrNull(peqTouchBandIndex)?.onPressedStateChanged(false, 200)
                PeqTarget.GAIN_SLIDER -> startPeqGainPressAnimation(peqTouchBandIndex, false)
                else -> {}
            }
        }

        if (peqTouchTarget == PeqTarget.ADD_BUTTON) {
            val dx = event.x - touchStartX
            val dy = event.y - touchStartY
            if (abs(dx) < touchSlop * 2 && abs(dy) < touchSlop * 2) {
                addPeqBand()
                context.vibrateEffect(VibrationEffect.EFFECT_TICK, TAG)
            }
        }

        if (peqTouchTarget == PeqTarget.DELETE_BUTTON && peqTouchBandIndex in peqBands.indices) {
            val dx = event.x - touchStartX
            val dy = event.y - touchStartY
            if (abs(dx) < touchSlop * 2 && abs(dy) < touchSlop * 2) {
                // Detach the drawables for this band before removing it from the list.
                if (peqDrawablesAttached) {
                    peqQKnobDrawables.getOrNull(peqTouchBandIndex)?.onDetachedFromKnobView()
                    peqFreqKnobDrawables.getOrNull(peqTouchBandIndex)?.onDetachedFromKnobView()
                }
                peqQKnobDrawables.removeAt(peqTouchBandIndex)
                peqFreqKnobDrawables.removeAt(peqTouchBandIndex)
                peqBands.removeAt(peqTouchBandIndex)
                peqGainPressAnimators.getOrNull(peqTouchBandIndex)?.cancel()
                if (peqTouchBandIndex < peqGainPressScales.size) peqGainPressScales.removeAt(peqTouchBandIndex)
                if (peqTouchBandIndex < peqGainPressAnimators.size) peqGainPressAnimators.removeAt(peqTouchBandIndex)
                if (width > 0 && height > 0) recalculateLayout(width, height)
                context.vibrateEffect(VibrationEffect.EFFECT_HEAVY_CLICK, TAG)
                invalidate()
            }
        }

        if (isScrollGesture && !centeredMode) {
            velocityTracker?.computeCurrentVelocity(1000)
            val flingVelocity = -(velocityTracker?.xVelocity ?: 0f)
            if (abs(flingVelocity) > 50f) {
                if (scrollFling.isRunning) scrollFling.cancel()
                scrollFling.setMinValue(-maxOverscrollPx)
                scrollFling.setMaxValue(maxScroll + maxOverscrollPx)
                scrollFling.setStartVelocity(flingVelocity)
                scrollFling.setStartValue(scrollOffset)
                scrollFling.start()
            } else {
                snapScrollToBounds()
            }
        }

        velocityTracker?.recycle(); velocityTracker = null
        activeBandIndex = NO_ACTIVE_BAND
        peqTouchTarget = PeqTarget.NONE
        peqTouchBandIndex = -1
        isScrollGesture = false; isBandGesture = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    /** Returns the angle in degrees (canvas convention: 0° = right, clockwise) for the vector (dx, dy). */
    private fun angleDeg(dx: Float, dy: Float): Float {
        val deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        return if (deg < 0f) deg + 360f else deg
    }

    /** Returns the Euclidean distance between two points. */
    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2;
        val dy = y1 - y2
        return kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    // -------------------------------------------------------------------------
    // Overscroll
    // -------------------------------------------------------------------------

    private fun applyOverscrollResistance(rawTarget: Float): Float = when {
        rawTarget < 0f -> {
            val r = -rawTarget
            -(maxOverscrollPx * (1f - exp(-r / OVERSCROLL_DECAY_FACTOR)))
        }
        rawTarget > maxScroll -> {
            val r = rawTarget - maxScroll
            maxScroll + maxOverscrollPx * (1f - exp(-r / OVERSCROLL_DECAY_FACTOR))
        }
        else -> rawTarget
    }

    private fun snapScrollToBounds() {
        val target = scrollOffset.coerceIn(0f, maxScroll)
        if (scrollOffset == target) return
        if (scrollSpring.isRunning) scrollSpring.cancel()
        scrollSpring.setStartValue(scrollOffset)
        scrollSpring.animateToFinalPosition(target)
    }

    // -------------------------------------------------------------------------
    // Press-scale animations
    // -------------------------------------------------------------------------

    /**
     * Dispatches a press animation to the correct per-band slot, handling both the
     * preamp and EQ bands via a single call site.
     *
     * @param bandIndex [PREAMP_BAND_INDEX] for the preamp, 0–9 for EQ bands.
     * @param pressed   True to animate to the pressed scale, false to release.
     */
    private fun startBandPressAnimation(bandIndex: Int, pressed: Boolean) {
        when {
            bandIndex == PREAMP_BAND_INDEX -> startPreampPressAnimation(pressed)
            bandIndex in 0 until BAND_COUNT -> startPressAnimation(bandIndex, pressed)
        }
    }

    private fun startPressAnimation(bandIndex: Int, pressed: Boolean) {
        val target = if (pressed) 1.10f else 1f
        pressScaleAnimators[bandIndex]?.cancel()
        val start = pressScales[bandIndex]
        pressScaleAnimators[bandIndex] = ValueAnimator.ofFloat(start, target).apply {
            duration = if (pressed) 140L else 200L
            interpolator = DecelerateInterpolator()
            addUpdateListener { pressScales[bandIndex] = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun startPreampPressAnimation(pressed: Boolean) {
        val target = if (pressed) 1.10f else 1f
        preampPressScaleAnimator?.cancel()
        val start = preampPressScale
        preampPressScaleAnimator = ValueAnimator.ofFloat(start, target).apply {
            duration = if (pressed) 140L else 200L
            interpolator = DecelerateInterpolator()
            addUpdateListener { preampPressScale = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    /**
     * Animates the press scale for a PEQ band's gain slider so the thumb expands and
     * shows the halo ring — the same visual response as the graphic EQ sliders.
     */
    private fun startPeqGainPressAnimation(bandIndex: Int, pressed: Boolean) {
        if (bandIndex !in peqGainPressScales.indices) return
        val target = if (pressed) 1.10f else 1f
        peqGainPressAnimators.getOrNull(bandIndex)?.cancel()
        val start = peqGainPressScales[bandIndex]
        val animator = ValueAnimator.ofFloat(start, target).apply {
            duration = if (pressed) 140L else 200L
            interpolator = DecelerateInterpolator()
            addUpdateListener { peqGainPressScales[bandIndex] = it.animatedValue as Float; invalidate() }
            start()
        }
        if (bandIndex < peqGainPressAnimators.size) {
            peqGainPressAnimators[bandIndex] = animator
        }
    }

    // -------------------------------------------------------------------------
    // ThemeChangedListener
    // -------------------------------------------------------------------------

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        super.onThemeChanged(theme, animate)
        applyThemeColors()
        applyPaintColors()
        updateShadowEffect()
    }

    override fun onAccentChanged(accent: Accent) {
        super.onAccentChanged(accent)
        accentColor = accent.primaryAccentColor
        thumbInnerColor = accentColor
        bezierColor = accentColor
        applyPaintColors()
        updateShadowEffect()
    }

    // -------------------------------------------------------------------------
    // SharedPreferences
    // -------------------------------------------------------------------------

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            AppearancePreferences.APP_FONT -> {
                setupTextPaints(); invalidate()
            }
            AppearancePreferences.SHADOW_EFFECT -> updateShadowEffect()
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            registerSharedPreferenceChangeListener()
            ThemeManager.addListener(this)
            peqQKnobDrawables.forEach { it.onAttachedToKnobView() }
            peqFreqKnobDrawables.forEach { it.onAttachedToKnobView() }
            peqDrawablesAttached = true
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (!isInEditMode) {
            unregisterSharedPreferenceChangeListener()
            ThemeManager.removeListener(this)
            detachAllPeqDrawables()
            peqDrawablesAttached = false
        }
        scrollSpring.cancel()
        scrollFling.cancel()
        velocityTracker?.recycle(); velocityTracker = null
        gainAnimators.forEach { it?.cancel() }
        pressScaleAnimators.forEach { it?.cancel() }
        peqGainPressAnimators.forEach { it?.cancel() }
        preampGainAnimator?.cancel()
        preampPressScaleAnimator?.cancel()
    }

    // -------------------------------------------------------------------------
    // Measurement
    // -------------------------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {

        val resolvedWidth = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val resolvedHeight = resolveSize(
                (paddingTop + paddingBottom + 240f * d).toInt(),
                heightMeasureSpec
        )
        setMeasuredDimension(resolvedWidth, resolvedHeight)
    }
}

