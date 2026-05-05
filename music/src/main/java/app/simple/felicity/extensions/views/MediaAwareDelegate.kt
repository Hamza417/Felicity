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
 * Holds all of the media-aware drawing and state logic shared between
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
            appliedIconCount = -1
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

    /** Tracks the last padding we applied so we don't trigger unnecessary layout passes. */
    private var appliedIconCount = -1

    /**
     * Binds an audio ID to the host view. Playing and selection state are resolved
     * immediately so a recycled view never shows stale indicators.
     */
    fun setAudioID(audioID: Long) {
        if (audioID == -1L) return
        this.audioID = audioID
        isPlaying = audioID == MediaPlaybackManager.getCurrentSongId()
        isInSelection = SelectionManager.selectedAudios.value.any { it.id == audioID }
        appliedIconCount = -1
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
     * Applies right padding equal to the total width of all visible icons so that child
     * views in list mode are never hidden behind the drawn indicators. In grid mode this
     * is skipped entirely because the icons float centered and don't displace content.
     */
    fun updateRightPadding() {
        if (enableGridMode) return
        if (view.height == 0) return
        val h = view.height.toFloat()
        val iconSize = (h * 0.45f).toInt()
        val iconPadding = (h * 0.10f).toInt()
        val iconCount = (if (enableDragHandle) 1 else 0) +
                (if (isPlaying) 1 else 0) +
                (if (isInSelection) 1 else 0)
        if (iconCount == appliedIconCount) return
        appliedIconCount = iconCount
        view.setPadding(view.paddingLeft, view.paddingTop, iconCount * (iconSize + iconPadding), view.paddingBottom)
        // WARN: do not call invalidate here
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
            cachedIsPlaying = playing
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
    }
}

