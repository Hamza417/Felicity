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

    /** True when this song is currently sitting in the selection basket. */
    private var isInSelection: Boolean = false

    /** Coroutine scope that lives exactly as long as this view is attached to a window. */
    private var selectionScope: CoroutineScope? = null

    /** The checkmark drawable scaled up to cover the right-side indicator strip. */
    private val checkDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_check)

    /** Paint used to draw the gradient background strip behind the check icon. */
    private val selectionBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /**
     * Reusable clip path for the gradient strip — rebuilt whenever the view size changes
     * so we never allocate inside onDraw. The right corners are rounded to match the app's
     * global corner radius, giving the indicator the same polished feel as the rest of the UI.
     */
    private val selectionClipPath = Path()

    /** Reusable RectF so we don't allocate a new one in every layout pass. */
    private val selectionRect = RectF()

    /**
     * The last width and height used to build the gradient shader.
     * We only rebuild the shader when these change, keeping onDraw allocation-free.
     */
    private var cachedShaderW = -1f
    private var cachedShaderH = -1f
    private var cachedShaderColor = 0

    init {
        // ViewGroups skip onDraw by default. We need it to draw the indicator strip
        // behind the child views, so we have to opt in explicitly.
        setWillNotDraw(false)
    }

    /**
     * Binds the given [audioID] to this view. The initial selection state is applied
     * instantly (no animation) so the recycled view reflects the correct state immediately.
     *
     * @param audioID the ID of the audio item this view represents.
     */
    fun setAudioID(audioID: Long) {
        if (audioID == -1L) {
            return
        }
        this.audioID = audioID
        isSelected = audioID == MediaPlaybackManager.getCurrentSongId()
        isInSelection = SelectionManager.selectedAudios.value.any { it.id == audioID }
        invalidate()
    }

    /**
     * Called by [MediaPlaybackManager] on the main thread whenever the playing song changes.
     * Smoothly animates the background tint between the transparent and selected states.
     *
     * @param audio the newly playing [Audio], or null if playback stopped.
     */
    override fun onAudioChange(audio: Audio?) {
        val shouldBeSelected = audio?.id == audioID
        if (isSelected == shouldBeSelected) return
        setSelected(shouldBeSelected, true)
    }

    /**
     * Rebuilds the cached gradient shader and clip path whenever the view size or accent
     * color changes. Calling this from [onSizeChanged] keeps [onDraw] completely
     * allocation-free — no new objects are created per frame.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildSelectionGeometry(w.toFloat(), h.toFloat())
    }

    /**
     * Computes the gradient shader and the rounded clip path for the selection strip.
     * Called from [onSizeChanged] and whenever the accent color changes.
     */
    private fun rebuildSelectionGeometry(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        // Use the secondary accent color — it tends to be softer and reads better in both
        // light and dark themes, unlike the primary accent which can be too harsh.
        val accentColor = ThemeManager.accent.secondaryAccentColor
        val accentSemi = ColorUtils.setAlphaComponent(accentColor, 60)
        val indicatorW = h  // square strip matching the full row height

        // Rebuild the gradient that fades from transparent on the left into a soft accent wash.
        selectionBgPaint.shader = LinearGradient(
                w - indicatorW, 0f,
                w, 0f,
                intArrayOf(0x00000000, accentSemi),
                null,
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
    }

    /**
     * Draws a semi-transparent check indicator at the right edge of the view, sitting
     * behind all the child views (album art, text, etc.). It fades in from transparent
     * on the left to a soft accent-colored wash on the right, with the check icon on top.
     *
     * This only draws when [isInSelection] is true — otherwise we skip it entirely
     * so there is zero performance cost for non-selected rows.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isInSelection) return

        val w = width.toFloat()
        val h = height.toFloat()

        // Lazily rebuild geometry if the size somehow changed without onSizeChanged firing
        // (rare, but defensive programming never hurt anyone).
        if (cachedShaderW != w || cachedShaderH != h ||
                cachedShaderColor != ThemeManager.accent.secondaryAccentColor) {
            rebuildSelectionGeometry(w, h)
        }

        // Draw the gradient strip clipped to the rounded rectangle — no allocations here.
        canvas.withClip(selectionClipPath) {
            drawRect(selectionRect, selectionBgPaint)
        }

        // Draw the check icon centered in the right-side strip.
        val iconSize = (h * 0.45f).toInt()
        val iconLeft = (w - iconSize - h * 0.18f).toInt()
        val iconTop = ((h - iconSize) / 2f).toInt()
        checkDrawable?.let {
            // Use the secondary accent color to tint the checkmark so it's always visible,
            // even in dark mode where the default black tint would blend into the background.
            it.setTint(ThemeManager.accent.secondaryAccentColor)
            it.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
            it.alpha = 200
            it.draw(canvas)
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