package app.simple.felicity.extensions.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withClip
import app.simple.felicity.R
import app.simple.felicity.engine.managers.MediaPlaybackManager
import app.simple.felicity.extensions.views.MediaAwareDelegate.Companion.knownRowHeights
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
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds all the media-aware drawing and state logic shared between
 * [MediaAwareRippleConstraintLayout] and [MediaAwareRippleLinearLayout].
 *
 * Both layouts own one instance of this and forward their lifecycle and draw
 * calls here, so any change to the indicator behavior only needs to be made once.
 *
 * In **list mode** a cubic-ease-in gradient strip grows from the right edge
 * to indicate that a song is playing or selected, with icons drawn on top.
 *
 * In **grid mode** no padding is added. Instead, when the song is active the
 * child views are faded out by drawing them into a translucent canvas layer,
 * and the icons are drawn on top at full opacity so they always pop.
 *
 * @param view the host [View] — used to read dimensions, apply padding, and invalidate.
 * @param context the context used to load drawable resources.
 *
 * @author Hamza417
 */
class MediaAwareDelegate(private val view: View, context: Context) : MediaStateListener {

    private var audioID: Long = -1L

    var isPlaying: Boolean = false
        private set

    var isInSelection: Boolean = false
        private set

    /**
     * When true, a drag-grip icon is drawn at the far-right edge in list mode and the
     * play / check icons shift one slot to the left to make room.
     */
    var enableDragHandle: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            cachedShaderW = -1f
            updateRightPadding()
            view.invalidate()
        }

    /**
     * When true, the host view behaves as a grid cell. No right padding is ever applied.
     * If the song is active, child views are rendered at reduced opacity and the play /
     * check icons float in the center at full opacity so they're always legible.
     */
    var enableGridMode: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            updateRightPadding()
            view.invalidate()
        }

    private var selectionScope: CoroutineScope? = null

    private val playDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_play)
    private val checkDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_check)
    private val dragHandleDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_drag_indicator)

    /** Paint for the cubic-ease-in gradient strip shown in list mode. */
    private val selectionBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val selectionClipPath = Path()
    private val selectionRect = RectF()

    private var cachedShaderW = -1f
    private var cachedShaderH = -1f
    private var cachedShaderColor = 0
    private var cachedIsPlaying = false
    private var cachedIsInSelection = false
    private var cachedDragHandle = false

    /** Whether [onMeasure] has ever produced a real measurement for this instance. */
    private var hasMeasuredOnce = false

    /**
     * Binds an audio ID to the host view. Playing and selection state are resolved
     * immediately so a recycled view never shows stale indicators.
     */
    fun setAudioID(audioID: Long) {
        if (audioID == -1L) return
        this.audioID = audioID
        isPlaying = audioID == MediaPlaybackManager.getCurrentSongId()
        isInSelection = SelectionManager.selectedAudios.value.any { it.id == audioID }
        updateRightPadding()
        view.invalidate()
    }

    /**
     * Returns true if the given x coordinate is over the drag handle region (rightmost
     * slot). Only meaningful when [enableDragHandle] is true.
     */
    fun isDragHandleRegion(x: Float): Boolean {
        if (!enableDragHandle) return false
        return x >= view.width - view.height
    }

    override fun onAudioChange(audio: Audio?) {
        val shouldBePlaying = audio?.id == audioID
        if (isPlaying == shouldBePlaying) return
        isPlaying = shouldBePlaying
        updateRightPadding()
        view.invalidate()
    }

    /** Should be called from the host's [View.onSizeChanged]. */
    fun onSizeChanged(w: Int, h: Int) {
        rebuildSelectionGeometry(w.toFloat(), h.toFloat(), isPlaying, isInSelection)
        updateRightPadding()
    }

    /** Should be called from the host's [View.onLayout]. */
    fun onLayout() {
        updateRightPadding()
    }

    /**
     * Should be called from the host's `View.onMeasure()`, wrapping the real
     * `super.onMeasure(widthMeasureSpec, heightMeasureSpec)` call in [measure].
     *
     * Host rows are `wrap_content` in height — the *real* row height (driven by the
     * title/artist text lines + cover art) isn't known until **after** children have been
     * measured. That ruled out computing the icon padding from `heightMeasureSpec` directly:
     * for a `wrap_content` view inside a `RecyclerView` the spec is `AT_MOST` with the
     * *parent's available height* as its size — not the row's own final height — so padding
     * computed from it was frequently wrong.
     *
     * It also ruled out correcting the padding afterward from [onSizeChanged]/[onLayout]:
     * [View.setPadding] internally calls [View.requestLayout], and that call happens *while a
     * layout traversal is already in progress* (we're inside the parent's `layout()` call).
     * `RecyclerView` can swallow or indefinitely defer a `requestLayout()` requested
     * mid-traversal, so the corrected padding never actually re-measures the text — it stays
     * wrong until something else (e.g. a full rebind after the app is stopped and resumed)
     * forces a fresh measure pass.
     *
     * The fix is a manual two-pass measure, entirely local to this single `onMeasure` call:
     *  1. Measure once (with whatever padding is currently applied) purely to learn the real,
     *     final `measuredHeight` — width doesn't affect it since all text rows are
     *     `maxLines="1"` + `ellipsize="end"`, so they can't grow taller from less width.
     *  2. Compute the correct padding from that real height and apply it if it changed.
     *  3. If it changed, measure a *second* time so children (whose width depends on the
     *     padding) get laid out against the correct value immediately — no extra traversal,
     *     no race, no dependency on some later event to "fix itself".
     *
     * ### Why this doesn't cost scroll performance
     * The second pass only runs when the padding actually changes — which, in steady-state
     * scrolling/recycling, is virtually never:
     *  - Row height is stable for a given concrete view type for the whole process lifetime
     *    (same layout, same font size), so [knownRowHeights] lets a **brand new** ViewHolder
     *    get the right padding pre-applied *before* it's ever measured — the only view that
     *    ever actually pays for a second pass is the very first instance of each row type
     *    created in the app, not every recycled row on every fling.
     *  - For already-created (recycled) rows, [setAudioID] already re-applies padding
     *    synchronously during `onBindViewHolder` — *before* this measure call — using the
     *    view's still-set-from-last-bind height, which is the same stable height. So by the
     *    time this method runs, padding already matches and the extra pass is skipped.
     *  - Even in the rare case both fall through (e.g. right after a font-size preference
     *    change alters row height process-wide), it's one extra cheap measurement of a
     *    handful of single-line `TextView`s — negligible next to everything else a
     *    `RecyclerView` already does per newly bound row.
     */
    fun onMeasure(measure: () -> Unit) {
        if (enableGridMode) {
            measure()
            return
        }
        if (!hasMeasuredOnce) {
            // Best-effort guess so a never-before-measured view is already correct by the
            // time the real measurement below runs, sparing it the second pass too.
            knownRowHeights[view.javaClass]?.let {
                applyRightPadding(it)
            }
        }
        measure()
        hasMeasuredOnce = true
        val h = view.measuredHeight.toFloat()
        if (h <= 0f) return
        if (applyRightPadding(h)) {
            measure()
        }
        knownRowHeights[view.javaClass] = h
    }

    /**
     * Applies right padding equal to the total width of all visible icons so that child
     * views in list mode are never hidden behind the drawn indicators. In grid mode this
     * is skipped entirely because the icons float centered and don't displace content.
     *
     * Used outside the measure pass — e.g. when playback/selection state changes while
     * the row is already on screen. In that case [View.setPadding]'s internal
     * `requestLayout()` is *not* racing an in-progress traversal (the state change arrives
     * asynchronously, not from inside `layout()`), so it reliably schedules and completes a
     * fresh traversal on its own.
     */
    fun updateRightPadding() {
        if (enableGridMode) return
        if (view.height == 0) return
        applyRightPadding(view.height.toFloat())
    }

    /**
     * Shared padding math. Returns `true` if the padding actually changed (i.e. a caller doing
     * its own measure pass, like [onMeasure], needs to re-measure to account for it).
     */
    private fun applyRightPadding(h: Float): Boolean {
        val iconSize = (h * 0.45f).toInt()
        val iconPadding = (h * 0.10f).toInt()
        val iconCount = (if (enableDragHandle) 1 else 0) +
                (if (isPlaying) 1 else 0) +
                (if (isInSelection) 1 else 0)
        val newPaddingRight = iconCount * (iconSize + iconPadding)
        if (newPaddingRight == view.paddingRight) return false
        view.setPadding(view.paddingLeft, view.paddingTop, newPaddingRight, view.paddingBottom)
        // WARN: do not call invalidate here
        return true
    }

    /**
     * Pre-computes the gradient shader and clip path for the list-mode icon strip.
     * Doing this outside of the draw pass keeps [onDraw] and [dispatchDraw] allocation-free.
     */
    fun rebuildSelectionGeometry(w: Float, h: Float, playing: Boolean, inSelection: Boolean) {
        if (w <= 0f || h <= 0f) return

        val accentColor = ThemeManager.accent.secondaryAccentColor
        val maxAlpha = 60

        var slotCount = if (playing && inSelection) 2f else if (playing || inSelection) 1f else 0f
        if (enableDragHandle) slotCount += 1f

        if (slotCount == 0f) {
            cachedShaderW = w
            cachedShaderH = h
            cachedShaderColor = accentColor
            @Suppress("KotlinConstantConditions")
            cachedIsPlaying = playing
            @Suppress("KotlinConstantConditions")
            cachedIsInSelection = inSelection
            cachedDragHandle = enableDragHandle
            return
        }

        val indicatorW = h * slotCount

        // Cubic ease-in color stops — alpha ramps up quickly near the right edge,
        // giving the strip a smooth, intentional feel rather than a hard cut.
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
     * Should be called from the host's [View.onDraw]. Draws the gradient strip
     * background in list mode. In grid mode the active-state effect is handled
     * entirely in [dispatchDraw] so this is a no-op.
     */
    fun onDraw(canvas: Canvas) {
        if (enableGridMode) return
        if (!isPlaying && !isInSelection && !enableDragHandle) return

        val w = view.width.toFloat()
        val h = view.height.toFloat()

        if (cachedShaderW != w || cachedShaderH != h ||
                cachedShaderColor != ThemeManager.accent.secondaryAccentColor ||
                cachedIsPlaying != isPlaying || cachedIsInSelection != isInSelection ||
                cachedDragHandle != enableDragHandle) {
            rebuildSelectionGeometry(w, h, isPlaying, isInSelection)
        }

        if (!selectionRect.isEmpty) {
            canvas.withClip(selectionClipPath) {
                drawRect(selectionRect, selectionBgPaint)
            }
        }
    }

    /**
     * Should be called from the host's [View.dispatchDraw], passing a lambda that
     * invokes the real `super.dispatchDraw(canvas)`.
     *
     * In **list mode** the super call happens first (drawing children), then the
     * indicators are drawn on top.
     *
     * In **grid mode** the children are rendered into a translucent layer so they
     * appear faded, then the play / check icons are drawn at full opacity on top.
     * No extra paint or rectangle is needed — the alpha layer does all the work.
     */
    fun dispatchDraw(canvas: Canvas, superDispatch: () -> Unit) {
        val w = view.width.toFloat()
        val h = view.height.toFloat()
        if (w <= 0f || h <= 0f) {
            superDispatch()
            return
        }

        val hasActiveState = isPlaying || isInSelection
        val needsGeometry = cachedShaderW != w || cachedShaderH != h ||
                cachedShaderColor != ThemeManager.accent.secondaryAccentColor ||
                cachedIsPlaying != isPlaying || cachedIsInSelection != isInSelection ||
                cachedDragHandle != enableDragHandle

        if (needsGeometry) rebuildSelectionGeometry(w, h, isPlaying, isInSelection)

        if (enableGridMode && hasActiveState) {
            // Draw children faded so the icons on top feel like they're in the spotlight.
            canvas.saveLayerAlpha(0f, 0f, w, h, GRID_CONTENT_ALPHA)
            superDispatch()
            canvas.restore()
            drawGridModeIcons(canvas, w, h)
        } else {
            superDispatch()
            if (isPlaying || isInSelection || enableDragHandle) {
                drawListModeIcons(canvas, w, h)
            }
        }

        updateRightPadding()
    }

    /**
     * Draws play and check icons centered on the host view. Called after the children
     * have already been rendered at reduced alpha, so the icons always sit on top.
     */
    private fun drawGridModeIcons(canvas: Canvas, w: Float, h: Float) {
        val accentColor = ThemeManager.accent.secondaryAccentColor
        val iconSize = (h * 0.30f).toInt()
        val iconGap = (h * 0.08f).toInt()

        val visibleCount = (if (isPlaying) 1 else 0) + (if (isInSelection) 1 else 0)
        val totalW = visibleCount * iconSize + (visibleCount - 1) * iconGap
        val startLeft = ((w - totalW) / 2f).toInt()
        val iconTop = ((h - iconSize) / 2f).toInt()

        var slot = 0

        fun nextLeft(): Int = startLeft + slot++ * (iconSize + iconGap)

        if (isPlaying) {
            val left = nextLeft()
            playDrawable?.let {
                it.setTint(accentColor)
                it.setBounds(left, iconTop, left + iconSize, iconTop + iconSize)
                it.alpha = 255
                it.draw(canvas)
            }
        }

        if (isInSelection) {
            val left = nextLeft()
            checkDrawable?.let {
                it.setTint(accentColor)
                it.setBounds(left, iconTop, left + iconSize, iconTop + iconSize)
                it.alpha = 255
                it.draw(canvas)
            }
        }
    }

    /**
     * Draws play, check, and drag-handle icons packed from the right edge in list mode.
     * The drag handle occupies the rightmost slot when enabled, pushing the others left.
     */
    private fun drawListModeIcons(canvas: Canvas, w: Float, h: Float) {
        val accentColor = ThemeManager.accent.secondaryAccentColor
        val iconSize = (h * 0.45f).toInt()
        val iconPadding = (h * 0.10f).toInt()
        val iconTop = ((h - iconSize) / 2f).toInt()

        var nextSlot = 0

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
    }

    /** Should be called from the host's [View.onAttachedToWindow]. */
    fun onAttachedToWindow() {
        MediaPlaybackManager.registerListener(this)

        selectionScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        selectionScope?.launch {
            SelectionManager.selectedAudios.collect { selected ->
                val nowSelected = selected.any { it.id == audioID }
                if (nowSelected != isInSelection) {
                    isInSelection = nowSelected
                    updateRightPadding()
                    view.invalidate()
                }
            }
        }
    }

    /** Should be called from the host's [View.onDetachedFromWindow]. */
    fun onDetachedFromWindow() {
        MediaPlaybackManager.unregisterListener(this)
        selectionScope?.cancel()
        selectionScope = null
    }

    companion object {
        /**
         * Alpha applied to the child layer in grid mode when the song is active.
         * 80 out of 255 is about 31% — dim enough for the icons to pop but not so
         * dark that you can't recognize the album art at all.
         */
        const val GRID_CONTENT_ALPHA = 80

        /**
         * Last known-good measured row height per concrete host view class (e.g.
         * [MediaAwareRippleConstraintLayout] used for the list style vs.
         * [MediaAwareRippleLinearLayout] used for the labels style each get their own entry,
         * so they never clobber each other even though they share this delegate).
         *
         * Used by [onMeasure] to pre-apply a best-effort correct padding to brand new,
         * never-before-measured instances *before* their first real measurement — since row
         * height for a given type is effectively constant for the process lifetime, this
         * means only the very first instance of each type ever created has to pay for the
         * safety-net second measure pass; every other recycled/created row after it is
         * correct in a single pass.
         */
        private val knownRowHeights = ConcurrentHashMap<Class<*>, Float>()
    }
}

