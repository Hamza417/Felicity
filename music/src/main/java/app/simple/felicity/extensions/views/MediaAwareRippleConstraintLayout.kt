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
 * [MediaPlaybackManager] to reflect the currently playing song state. When the playing
 * song changes, the selection highlight transitions smoothly via a color animation.
 *
 * It also draws a semi-transparent check indicator at the right end (behind all child
 * views) whenever this song is sitting in the [SelectionManager] selection basket.
 * Think of it as a sticky note on the right side that says "yep, this one's picked!"
 *
 * Call [setAudioID] once per bind cycle to associate a song ID with this view.
 * All subsequent highlight updates are handled internally without adapter callbacks.
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

    /** Coroutine scope that lives exactly as long as this view is attached to a window. */
    private var selectionScope: CoroutineScope? = null

    /**
     * The little play icon that shows up when this row is the currently playing song.
     * It's like a tiny spotlight — subtle but clear.
     */
    private val playDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_play)

    /** The checkmark that appears when this song is selected — "yep, this one's picked!" */
    private val checkDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_check)

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

    init {
        // ViewGroups skip onDraw by default. We need it to draw the indicator strip
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
        invalidate()
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
    }

    /**
     * Computes the gradient shader and the rounded clip path for the icon strip.
     * The gradient follows a cubic ease-in Bézier curve — it starts nearly invisible
     * and ramps up quickly near the right edge, which feels much more natural than
     * a boring straight linear fade.
     *
     * The strip width adapts to how many icons need to be shown: one slot when only
     * one icon is visible, two slots when the song is both playing and selected.
     */
    private fun rebuildSelectionGeometry(w: Float, h: Float, playing: Boolean, inSelection: Boolean) {
        if (w <= 0f || h <= 0f) return

        // Use the secondary accent color — it tends to be softer and reads better in both
        // light and dark themes, unlike the primary accent which can be too harsh.
        val accentColor = ThemeManager.accent.secondaryAccentColor
        val maxAlpha = 60

        // When both icons are on duty, we need a wider strip to fit them side by side.
        val slotCount = if (playing && inSelection) 2f else 1f
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
    }

    /**
     * Draws a semi-transparent icon strip at the right edge of the view, sitting behind
     * all the child views (text, album art, etc.). The strip shows:
     *
     * - A play icon when this song is currently playing.
     * - A check icon when this song is selected.
     * - Both icons side by side when the song is both playing and selected
     *   (they're great together, like bread and butter).
     *
     * The gradient behind the icons uses a cubic ease-in bezier curve so it fades in
     * gradually and looks intentional rather than slapped on. When neither icon is
     * needed, the whole thing is skipped so non-active rows have zero overhead.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isPlaying && !isInSelection) return

        val w = width.toFloat()
        val h = height.toFloat()

        // Rebuild geometry if the size, accent color, or icon combination changed.
        // This is a lazy fallback — most of the time onSizeChanged already took care of it.
        if (cachedShaderW != w || cachedShaderH != h ||
                cachedShaderColor != ThemeManager.accent.secondaryAccentColor ||
                cachedIsPlaying != isPlaying || cachedIsInSelection != isInSelection) {
            rebuildSelectionGeometry(w, h, isPlaying, isInSelection)
        }

        // Draw the bezier-faded gradient strip clipped to the rounded rectangle.
        canvas.withClip(selectionClipPath) {
            drawRect(selectionRect, selectionBgPaint)
        }

        // These sizing constants keep the icons proportional regardless of row height.
        val iconSize = (h * 0.45f).toInt()
        val iconPadding = (h * 0.18f).toInt()
        val iconTop = ((h - iconSize) / 2f).toInt()
        val accentColor = ThemeManager.accent.secondaryAccentColor

        if (isPlaying && isInSelection) {
            // Both icons are here — play on the left, check on the right,
            // each sitting in its own half of the double-wide strip.
            val checkLeft = (w - iconSize - iconPadding).toInt()
            val playLeft = checkLeft - iconSize - iconPadding

            playDrawable?.let {
                it.setTint(accentColor)
                it.setBounds(playLeft, iconTop, playLeft + iconSize, iconTop + iconSize)
                it.alpha = 200
                it.draw(canvas)
            }

            checkDrawable?.let {
                it.setTint(accentColor)
                it.setBounds(checkLeft, iconTop, checkLeft + iconSize, iconTop + iconSize)
                it.alpha = 200
                it.draw(canvas)
            }
        } else if (isPlaying) {
            // Only the play icon — song is playing but not selected.
            val iconLeft = (w - iconSize - iconPadding).toInt()
            playDrawable?.let {
                it.setTint(accentColor)
                it.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                it.alpha = 200
                it.draw(canvas)
            }
        } else {
            // Only the check icon — song is selected but not currently playing.
            val iconLeft = (w - iconSize - iconPadding).toInt()
            checkDrawable?.let {
                it.setTint(accentColor)
                it.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                it.alpha = 200
                it.draw(canvas)
            }
        }
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