package app.simple.felicity.extensions.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withClip
import app.simple.felicity.R
import app.simple.felicity.decorations.ripple.DynamicRippleConstraintLayout
import app.simple.felicity.engine.managers.MediaPlaybackManager
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.repository.listeners.MediaStateListener
import app.simple.felicity.repository.managers.SelectionManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.theme.managers.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * A [DynamicRippleConstraintLayout] that automatically registers itself with
 * [MediaPlaybackManager] to reflect the currently playing song state. Instead of
 * highlighting the entire row, it draws a small play icon at the right edge so you
 * always know which song is on stage without the whole row screaming for attention.
 *
 * When a song is also sitting in the [SelectionManager] basket, a check icon appears
 * right next to the play icon — two little buddies hanging out on the right side.
 * If the song is only selected (not playing), just the check icon shows up.
 *
 * When [enableDragHandle] is true, a drag-grip icon is drawn at the very far right,
 * and the play / check icons shift one slot to the left to make room. This removes
 * the need for a dedicated ImageButton in the layout, which is one less view per row.
 *
 * The background behind the icons fades in from the left using a cubic ease-in
 * bezier gradient, so it looks smooth and intentional rather than a hard line.
 *
 * Call [setAudioID] once per bind cycle to associate a song ID with this view.
 * All subsequent updates are handled internally without any adapter callbacks.
 *
 * @author Hamza417
 */
class MediaAwareRippleConstraintLayout @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null
) : DynamicRippleConstraintLayout(context, attrs), MediaStateListener {

    private var audioID: Long = -1L

    /** True when this song is the one currently on stage (i.e., playing right now). */
    private var isPlaying: Boolean = false

    /** True when this song is currently sitting in the selection basket. */
    private var isInSelection: Boolean = false

    /**
     * When true, a drag-grip icon is drawn at the far-right edge and the play / check
     * icons shift left to make room. The layout should reserve an equivalent margin so
     * text views don't slide under the handle area.
     */
    var enableDragHandle: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            // Force a geometry rebuild on the next draw pass.
            cachedShaderW = -1f
            updateRightPadding()
            invalidate()
        }

    /** Coroutine scope that lives exactly as long as this view is attached to a window. */
    private var selectionScope: CoroutineScope? = null

    /**
     * The little play icon that shows up when this row is the currently playing song.
     * It's like a tiny spotlight — subtle but clear.
     */
    private val playDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_play)

    /** The checkmark that appears when this song is selected — "yep, this one's picked!" */
    private val checkDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_check)

    /**
     * The drag-grip icon drawn at the far-right slot when [enableDragHandle] is true.
     * Using the same drawable that the old ImageButton used, so it looks identical.
     */
    private val dragHandleDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_drag_indicator)

    /** Paint used to draw the gradient background strip behind the icons. */
    private val selectionBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /**
     * Reusable clip path for the gradient strip — rebuilt whenever the view size or state
     * changes so we never allocate inside onDraw. The right corners are rounded to match
     * the app's global corner radius, giving the strip that polished, belongs-here feel.
     */
    private val selectionClipPath = Path()

    /** Reusable RectF so we don't allocate a new one in every layout pass. */
    private val selectionRect = RectF()

    /**
     * Cached values that let us detect when the geometry needs rebuilding.
     * We check these in [onDraw] and only redo the math when something actually changed.
     */
    private var cachedShaderW = -1f
    private var cachedShaderH = -1f
    private var cachedShaderColor = 0
    private var cachedIsPlaying = false
    private var cachedIsInSelection = false
    private var cachedDragHandle = false

    /**
     * The last icon count we applied as right padding. Tracked so we only call
     * [setPadding] when the count actually changes — layout passes are expensive.
     */
    private var appliedIconCount = -1

    init {
        // ViewGroups skip onDraw by default. We need it to draw the gradient strip
        // behind the child views, so we have to opt in explicitly.
        setWillNotDraw(false)
    }

    /**
     * Binds the given [audioID] to this view. The initial playing and selection states are
     * applied instantly (no animation) so a recycled view always reflects the correct state
     * right away — no awkward delay where a row thinks it's still the old song.
     *
     * @param audioID the ID of the audio item this view represents.
     */
    fun setAudioID(audioID: Long) {
        if (audioID == -1L) return
        this.audioID = audioID
        isPlaying = audioID == MediaPlaybackManager.getCurrentSongId()
        isInSelection = SelectionManager.selectedAudios.value.any { it.id == audioID }
        // Reset so a recycled view never skips the padding update due to a stale cache.
        appliedIconCount = -1
        updateRightPadding()
        invalidate()
    }

    /**
     * Returns true if the given x coordinate falls inside the drag handle's touch region.
     * The drag handle always occupies the rightmost slot (one row-height wide), so the
     * adapter can call this to decide whether a finger-down event should start a drag.
     *
     * Only meaningful when [enableDragHandle] is true — always returns false otherwise.
     */
    fun isDragHandleRegion(x: Float): Boolean {
        if (!enableDragHandle) return false
        return x >= width - height
    }

    /**
     * Called by [MediaPlaybackManager] on the main thread whenever the playing song changes.
     * Instead of lighting up the whole row, we just flip the play-icon flag and ask
     * the view to redraw — subtle and non-intrusive.
     *
     * @param audio the newly playing [Audio], or null if playback stopped.
     */
    override fun onAudioChange(audio: Audio?) {
        val shouldBePlaying = audio?.id == audioID
        if (isPlaying == shouldBePlaying) return
        isPlaying = shouldBePlaying
        updateRightPadding()
        invalidate()
    }

    /**
     * Rebuilds the cached gradient shader and clip path whenever the view size, accent
     * color, or icon visibility changes. Calling this from [onSizeChanged] keeps [onDraw]
     * completely allocation-free — no new objects are created per frame.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildSelectionGeometry(w.toFloat(), h.toFloat(), isPlaying, isInSelection)
        updateRightPadding()
    }

    /**
     * Called after every layout pass, including the very first one where the view gets
     * real dimensions for the first time. This is the safety net for the case where
     * [setAudioID] was called before layout (height was 0 then, so padding was deferred).
     */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateRightPadding()
    }

    /**
     * Calculates how many icons are currently visible and sets the view's right padding
     * to exactly that width so the ConstraintLayout children never slide under the drawn
     * icons. We skip the call if nothing has changed to avoid unnecessary layout passes.
     */
    private fun updateRightPadding() {
        if (height == 0) return
        val h = height.toFloat()
        val iconSize = (h * 0.45f).toInt()
        val iconPadding = (h * 0.10f).toInt()
        val iconCount = (if (enableDragHandle) 1 else 0) +
                (if (isPlaying) 1 else 0) +
                (if (isInSelection) 1 else 0)
        if (iconCount == appliedIconCount) return
        appliedIconCount = iconCount
        val rightPad = iconCount * (iconSize + iconPadding)
        setPadding(paddingLeft, paddingTop, rightPad, paddingBottom)

        // WARN: do not call invalidate here
    }

    /**
     * Computes the gradient shader and the rounded clip path for the icon strip.
     * The gradient follows a cubic ease-in Bézier curve — it starts nearly invisible
     * and ramps up quickly near the right edge, which feels much more natural than
     * a boring straight linear fade.
     *
     * The strip width adapts to how many icons need to be shown: the drag handle always
     * gets one slot on the far right when enabled, and the play / check icons each get a slot on top of that.
     */
    private fun rebuildSelectionGeometry(w: Float, h: Float, playing: Boolean, inSelection: Boolean) {
        if (w <= 0f || h <= 0f) return

        // Use the secondary accent color — it tends to be softer and reads better in both
        // light and dark themes, unlike the primary accent which can be too harsh.
        val accentColor = ThemeManager.accent.secondaryAccentColor
        val maxAlpha = 60

        // Count how many icon slots the strip needs to cover.
        // The drag handle is always one extra slot on the far right when enabled.
        var slotCount = if (playing && inSelection) 2f else if (playing || inSelection) 1f else 0f
        if (enableDragHandle) slotCount += 1f

        // Even if no play/check icon is visible, we still draw the drag handle strip.
        if (slotCount == 0f) {
            cachedShaderW = w
            cachedShaderH = h
            cachedShaderColor = accentColor
            cachedIsPlaying = playing
            cachedIsInSelection = inSelection
            cachedDragHandle = enableDragHandle
            return
        }

        val indicatorW = h * slotCount

        // Build a cubic ease-in curve using multiple color stops so the fade-in
        // looks smooth and intentional rather than a blunt hard-edge transition.
        // f(t) = t^3 maps directly to alpha, giving a slow start that accelerates.
        val positions = floatArrayOf(0f, 0.15f, 0.30f, 0.45f, 0.65f, 0.80f, 1f)
        val colors = positions.map { t ->
            val alpha = (maxAlpha * t * t * t).toInt().coerceIn(0, maxAlpha)
            ColorUtils.setAlphaComponent(accentColor, alpha)
        }.toIntArray()

        selectionBgPaint.shader = LinearGradient(
                w - indicatorW, 0f,
                w, 0f,
                colors,
                positions,
                Shader.TileMode.CLAMP
        )

        // Rounded clip path — right corners follow the app's global radius so the strip
        // looks like it belongs there instead of being a blunt rectangle.
        val cornerR = AppearancePreferences.getCornerRadius()
        selectionRect.set(w - indicatorW, 0f, w, h)
        selectionClipPath.rewind()
        selectionClipPath.addRoundRect(selectionRect, cornerR, cornerR, Path.Direction.CW)

        cachedShaderW = w
        cachedShaderH = h
        cachedShaderColor = accentColor
        cachedIsPlaying = playing
        cachedIsInSelection = inSelection
        cachedDragHandle = enableDragHandle
    }

    /**
     * Draws only the gradient background strip behind all child views. The icons
     * themselves are drawn in [dispatchDraw] so they always appear on top of children.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val shouldDraw = isPlaying || isInSelection || enableDragHandle
        if (!shouldDraw) return

        val w = width.toFloat()
        val h = height.toFloat()

        // Rebuild geometry if anything that affects the strip has changed.
        if (cachedShaderW != w || cachedShaderH != h ||
                cachedShaderColor != ThemeManager.accent.secondaryAccentColor ||
                cachedIsPlaying != isPlaying || cachedIsInSelection != isInSelection ||
                cachedDragHandle != enableDragHandle) {
            rebuildSelectionGeometry(w, h, isPlaying, isInSelection)
        }

        // Only draw the strip rectangle if there's something inside it.
        val hasStrip = isPlaying || isInSelection || enableDragHandle
        if (hasStrip && !selectionRect.isEmpty) {
            canvas.withClip(selectionClipPath) {
                drawRect(selectionRect, selectionBgPaint)
            }
        }
    }

    /**
     * Draws the play, check, and drag-handle icons on top of all child views.
     * By hooking into [dispatchDraw] (which runs after children are painted), the icons
     * are never hidden behind album art, text, or any other child.
     *
     * Icon layout from left to right inside the strip (all anchored from the right edge):
     * - When drag handle is enabled: [play?] [check?] [drag handle]
     * - When drag handle is disabled: [play?] [check?]
     */
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Nothing to draw if no icons are active and the drag handle is off.
        if (!isPlaying && !isInSelection && !enableDragHandle) return

        val iconSize = (h * 0.45f).toInt()
        val iconPadding = (h * 0.10f).toInt()
        val iconTop = ((h - iconSize) / 2f).toInt()
        val accentColor = ThemeManager.accent.secondaryAccentColor

        // Icons are packed tightly — each one steps left by exactly (iconSize + iconPadding)
        // from the previous, just like the original single-pass drawing did.
        // Slot 0 = rightmost (drag handle when enabled), slot 1 = next left, and so on.
        var nextSlot = 0

        // A small helper so every icon lands at the right x using the same formula.
        fun iconLeft(slot: Int) = (w - (slot + 1) * (iconSize + iconPadding)).toInt()

        if (enableDragHandle) {
            val left = iconLeft(nextSlot++)
            dragHandleDrawable?.let {
                it.setTint(accentColor)
                it.setBounds(left, iconTop, left + iconSize, iconTop + iconSize)
                it.alpha = 200
                it.draw(canvas)
            }
        }

        if (isInSelection) {
            val left = iconLeft(nextSlot++)
            checkDrawable?.let {
                it.setTint(accentColor)
                it.setBounds(left, iconTop, left + iconSize, iconTop + iconSize)
                it.alpha = 200
                it.draw(canvas)
            }
        }

        if (isPlaying) {
            val left = iconLeft(nextSlot)
            playDrawable?.let {
                it.setTint(accentColor)
                it.setBounds(left, iconTop, left + iconSize, iconTop + iconSize)
                it.alpha = 200
                it.draw(canvas)
            }
        }

        updateRightPadding()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        MediaPlaybackManager.registerListener(this)

        // Start watching the selection basket. Any time songs enter or leave,
        // we check if this particular row needs to update its indicator.
        selectionScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        selectionScope?.launch {
            SelectionManager.selectedAudios.collect { selected ->
                val nowSelected = selected.any { it.id == audioID }
                if (nowSelected != isInSelection) {
                    isInSelection = nowSelected
                    updateRightPadding()
                    invalidate()
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        MediaPlaybackManager.unregisterListener(this)
        selectionScope?.cancel()
        selectionScope = null
    }

    companion object {
        private const val TAG = "MediaAwareRippleConstraintLayout"
    }
}