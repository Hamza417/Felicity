package app.simple.felicity.statusbar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import app.simple.felicity.decorations.lrc.model.LrcEntry
import app.simple.felicity.decorations.lrc.model.WordEntry
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A single-purpose, paint-reusing view that renders one lyric line (plus its
 * optional translation) for the floating status bar overlay.
 *
 * The view deliberately has no background, no click handling and no insets of its
 * own — the hosting service is responsible for putting it in the right place. Its
 * only job is to make the current lyric look good on top of whatever the system
 * status bar happens to be showing:
 *
 * - long lines scroll horizontally after a short pause;
 * - word-synced LRC lines get a karaoke wipe;
 * - an optional outline keeps light text readable on light wallpapers;
 * - the whole thing fades out instead of popping when playback pauses.
 *
 * @author Hamza417
 */
class StatusBarLyricsView(context: Context) : View(context) {

    companion object {
        /** How long a new line stays still before it starts scrolling. */
        private const val SCROLL_START_DELAY_MS = 700L

        /** Scroll speed relative to the text size, in pixels per second. */
        private const val SCROLL_SPEED_FACTOR = 2.2f

        /** Translation lines are rendered a little smaller and dimmer than the main line. */
        private const val TRANSLATION_SCALE = 0.86f
        private const val TRANSLATION_ALPHA = 0.86f
        private const val TRANSLATION_GAP_DP = 1f

        /** Unsung karaoke text stays visible but recedes. */
        private const val KARAOKE_UNSUNG_ALPHA = 0.45f

    /** Fallback outline colour; the actual value comes from preferences. */
    private const val STROKE_COLOR = 0xD9000000.toInt()

        private const val VERTICAL_PADDING_DP = 1f
    }

    /**
     * Immutable snapshot of everything the view needs from preferences. The service
     * rebuilds it whenever a relevant preference changes.
     */
    data class Config(
            val textSizeSp: Float = 14f,
            val textColor: Int = Color.WHITE,
            val stroke: Boolean = true,
            /** 0 = left, 1 = centre, 2 = right. */
            val alignment: Int = 1,
            val strokeColor: Int = STROKE_COLOR,
            /** Outline thickness in dp. */
            val strokeWidthDp: Float = 1f,
            val hideTranslation: Boolean = false,
            /** 1 keeps the original scrolling line; >1 wraps long subtitles. */
            val lineCount: Int = 1,
    )

    /** One visual line produced by wrapping, with its range in the original text. */
    private data class WrappedLine(val text: String, val startChar: Int, val endChar: Int)

    private var config = Config()
    private var entry: LrcEntry? = null
    private var text: String = ""
    private var translation: String? = null
    private var words: List<WordEntry> = emptyList()
    private var lineStartMs: Long = 0L

    /** Cached wrap result, recomputed whenever width, text or config changes. */
    private var wrappedLines: List<WrappedLine> = emptyList()
    private var wrappedWidth: Int = -1

    /** Latest playback position supplied by the service. */
    private var basePositionMs: Long = 0L

    /** Uptime at which [basePositionMs] was captured, used for frame-rate interpolation. */
    private var baseUptimeMs: Long = 0L

    private var playing: Boolean = false

    private val density = resources.displayMetrics.density

    private val fillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        isSubpixelText = true
    }

    private val strokePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = STROKE_COLOR
        isSubpixelText = true
    }

    private val translationFillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        isSubpixelText = true
    }

    private val translationStrokePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = STROKE_COLOR
        isSubpixelText = true
    }

    private val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    private var fadeShaderWidth: Int = 0
    private var fadeShader: LinearGradient? = null

    init {
        // The overlay is small, but it repaints every frame while scrolling. Keeping a
        // software layer would cost more than it saves, so hardware rendering stays on.
        setWillNotDraw(false)
        isFocusable = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun applyConfig(config: Config) {
        this.config = config
        translation = if (config.hideTranslation) null else entry?.romanization?.takeIf { it.isNotBlank() }

        val textSizePx = sp(config.textSizeSp)
        val strokeWidthPx = dp(config.strokeWidthDp)
        fillPaint.textSize = textSizePx
        fillPaint.color = config.textColor
        strokePaint.textSize = textSizePx
        strokePaint.color = config.strokeColor
        strokePaint.strokeWidth = strokeWidthPx

        translationFillPaint.textSize = textSizePx * TRANSLATION_SCALE
        translationFillPaint.color = withAlpha(config.textColor, TRANSLATION_ALPHA)
        translationStrokePaint.textSize = textSizePx * TRANSLATION_SCALE
        translationStrokePaint.color = config.strokeColor
        translationStrokePaint.strokeWidth = max(0.5f, strokeWidthPx * TRANSLATION_SCALE)

        wrappedWidth = -1
        requestLayout()
        invalidate()
    }

    /**
     * Replaces the currently displayed line. Passing `null` clears the overlay content.
     */
    fun setLyric(entry: LrcEntry?) {
        this.entry = entry
        text = entry?.text.orEmpty()
        translation = if (config.hideTranslation) null else entry?.romanization?.takeIf { it.isNotBlank() }
        words = entry?.words.orEmpty()
        lineStartMs = entry?.timeInMillis ?: 0L
        wrappedWidth = -1

        requestLayout()
        invalidate()
    }

    /**
     * Updates the playback clock. The service pushes this roughly ten times per second;
     * between pushes the view interpolates on its own so scrolling and karaoke stay smooth.
     */
    fun setPosition(positionMs: Long) {
        basePositionMs = positionMs
        baseUptimeMs = SystemClock.uptimeMillis()
        invalidate()
    }

    fun setPlaying(playing: Boolean) {
        if (this.playing == playing) return
        this.playing = playing
        baseUptimeMs = SystemClock.uptimeMillis()
        invalidate()
    }

    /**
     * Exact height the view wants for the current line. The service uses this to size
     * the floating window before it positions the view.
     */
    fun requiredHeightPx(): Int {
        return (verticalPadding() * 2f + contentHeight()).roundToInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // A plain View treats AT_MOST as "take all available space", which would make
        // the overlay window cover the whole screen. The lyric strip has an exact
        // intrinsic height, so use it regardless of the incoming height spec.
        val widthPx = MeasureSpec.getSize(widthMeasureSpec)
        if (config.lineCount > 1 && widthPx > 0 && widthPx != wrappedWidth) {
            wrappedWidth = widthPx
            wrappedLines = wrapText(text, widthPx.toFloat(), config.lineCount)
        }
        setMeasuredDimension(widthPx, requiredHeightPx())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (text.isBlank()) return

        val availableWidth = width.toFloat()
        if (availableWidth <= 0f) return

        if (config.lineCount > 1 && width != wrappedWidth) {
            wrappedWidth = width
            wrappedLines = wrapText(text, availableWidth, config.lineCount)
        }

        val positionMs = effectivePositionMs()
        val mainMetrics = fillPaint.fontMetrics
        val mainLineHeight = mainMetrics.descent - mainMetrics.ascent

        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.translate(0f, verticalPadding())

        var scrolling = false

        if (config.lineCount > 1 && wrappedLines.isNotEmpty()) {
            // Wrapped mode: every visual line is drawn in place, so long audiobook
            // subtitles can be read without waiting for a marquee.
            for (line in wrappedLines) {
                drawWrappedLine(
                        canvas = canvas,
                        line = line,
                        positionMs = positionMs,
                        availableWidth = availableWidth,
                        lineHeight = mainLineHeight
                )
                canvas.translate(0f, mainLineHeight)
            }
        } else {
            scrolling = drawLyricLine(
                    canvas = canvas,
                    line = text,
                    paint = fillPaint,
                    outlinePaint = strokePaint,
                    positionMs = positionMs,
                    availableWidth = availableWidth,
                    lineHeight = mainLineHeight
            )
            canvas.translate(0f, mainLineHeight)
        }

        if (!translation.isNullOrBlank()) {
            canvas.translate(0f, dp(TRANSLATION_GAP_DP))
            drawTranslationLine(
                    canvas = canvas,
                    availableWidth = availableWidth
            )
        }

        if (scrolling) {
            drawEdgeFade(canvas)
        }

        canvas.restoreToCount(layer)

        // Keep the marquee and karaoke moving at display refresh rate. The service
        // re-syncs the base position every ~100 ms, so drift never becomes visible.
        if (playing && (scrolling || words.isNotEmpty())) {
            postInvalidateOnAnimation()
        }
    }

    /** Horizontal start position for a line of the given width under the configured alignment. */
    private fun alignedX(textWidth: Float, availableWidth: Float): Float {
        return when (config.alignment) {
            0 -> 0f
            2 -> availableWidth - textWidth
            else -> (availableWidth - textWidth) / 2f
        }
    }

    /**
     * Draws the main lyric, optionally with a karaoke wipe, and returns whether it
     * had to scroll (so the caller can apply the edge fade).
     */
    private fun drawLyricLine(
            canvas: Canvas,
            line: String,
            paint: TextPaint,
            outlinePaint: TextPaint,
            positionMs: Long,
            availableWidth: Float,
            lineHeight: Float
    ): Boolean {
        val textWidth = paint.measureText(line)
        val overflow = max(0f, textWidth - availableWidth)
        val scroll = if (overflow > 0f) {
            val elapsed = max(0L, positionMs - lineStartMs - SCROLL_START_DELAY_MS)
            val speedPxPerSecond = paint.textSize * SCROLL_SPEED_FACTOR
            min(overflow, elapsed / 1000f * speedPxPerSecond)
        } else {
            0f
        }

        val x = when {
            overflow > 0f -> -scroll
            else -> alignedX(textWidth, availableWidth)
        }
        val baseline = -paint.fontMetrics.top

        if (config.stroke) {
            canvas.drawText(line, x, baseline, outlinePaint)
        }

        if (words.isEmpty()) {
            paint.color = config.textColor
            canvas.drawText(line, x, baseline, paint)
            return overflow > 0f
        }

        val sungChars = karaokeCharacterCount(positionMs)
        if (sungChars <= 0) {
            paint.color = withAlpha(config.textColor, KARAOKE_UNSUNG_ALPHA)
            canvas.drawText(line, x, baseline, paint)
            return overflow > 0f
        }

        if (sungChars >= line.length) {
            paint.color = config.textColor
            canvas.drawText(line, x, baseline, paint)
            return overflow > 0f
        }

        // Unsung part first, then clip the sung part on top. Clipping against the
        // measured prefix keeps the wipe aligned with proportional fonts.
        paint.color = withAlpha(config.textColor, KARAOKE_UNSUNG_ALPHA)
        canvas.drawText(line, x, baseline, paint)

        val clipRight = x + paint.measureText(line, 0, sungChars)
        val save = canvas.save()
        canvas.clipRect(x, 0f, clipRight, lineHeight)
        paint.color = config.textColor
        canvas.drawText(line, x, baseline, paint)
        canvas.restoreToCount(save)

        return overflow > 0f
    }

    /**
     * Breaks a long subtitle into at most [maxLines] visual lines that fit [maxWidth].
     * Wrapping prefers word boundaries (whitespace) so English words are never split
     * in the middle; scripts without spaces (e.g. Chinese) still wrap per character.
     * Uses the same paint as the main line so karaoke character offsets stay valid.
     * The final line is ellipsized at a word boundary when it cannot fit the budget.
     */
    private fun wrapText(text: String, maxWidth: Float, maxLines: Int): List<WrappedLine> {
        if (text.isEmpty()) return emptyList()

        val result = mutableListOf<WrappedLine>()
        var start = 0

        while (start < text.length && result.size < maxLines) {
            val isLastLine = result.size == maxLines - 1
            var count = fillPaint.breakText(text, start, text.length, true, maxWidth, null)
            if (count <= 0) count = 1 // never loop forever on a single over-wide glyph

            var end = (start + count).coerceAtMost(text.length)

            if (isLastLine) {
                if (end < text.length) {
                    result.add(WrappedLine(ellipsizeAtWordBoundary(text.substring(start), maxWidth), start, text.length))
                } else {
                    result.add(WrappedLine(text.substring(start, end), start, end))
                }
                break
            }

            if (end < text.length) {
                // Back off to the last whitespace inside this line. When there is no
                // whitespace (CJK, or one word wider than the whole line) fall back to
                // the pixel-perfect break so the text still fits.
                val boundary = lastWhitespaceBefore(text, start, end)
                if (boundary > start) {
                    end = boundary
                }
            }

            // Drop trailing spaces and don't carry leading spaces into the next line.
            var lineEnd = end
            while (lineEnd > start && text[lineEnd - 1].isWhitespace()) lineEnd--
            if (lineEnd <= start) lineEnd = end // a line made only of spaces: keep it

            result.add(WrappedLine(text.substring(start, lineEnd), start, lineEnd))

            var next = lineEnd
            while (next < text.length && text[next].isWhitespace()) next++
            // Guarantee forward progress even for pathological whitespace runs.
            start = if (next > lineEnd) next else max(lineEnd, start + 1)
        }

        return result
    }

    /** Index of the last whitespace character in [start, end), or -1 if none. */
    private fun lastWhitespaceBefore(text: String, start: Int, end: Int): Int {
        for (i in (end - 1) downTo start) {
            if (text[i].isWhitespace()) return i
        }
        return -1
    }

    /**
     * Ellipsize without chopping an English word in half: keep the largest whole-word
     * prefix that fits and append an ellipsis. Scripts without word separators fall
     * back to the platform ellipsizer, which breaks per character.
     */
    private fun ellipsizeAtWordBoundary(text: String, maxWidth: Float): String {
        if (fillPaint.measureText(text) <= maxWidth) return text

        val ellipsis = "…"
        val available = maxWidth - fillPaint.measureText(ellipsis)
        var count = if (available > 0f) {
            fillPaint.breakText(text, 0, text.length, true, available, null)
        } else {
            0
        }
        if (count <= 0) return TextUtils.ellipsize(text, fillPaint, maxWidth, TextUtils.TruncateAt.END).toString()

        val boundary = lastWhitespaceBefore(text, 0, count)
        if (boundary > 0) {
            val prefix = text.substring(0, boundary).trimEnd()
            if (prefix.isNotEmpty()) return prefix + ellipsis
        }

        return TextUtils.ellipsize(text, fillPaint, maxWidth, TextUtils.TruncateAt.END).toString()
    }

    /**
     * Draws one wrapped visual line. Karaoke highlighting is mapped back onto the
     * original text offsets so word-synced lines still work when wrapped.
     */
    private fun drawWrappedLine(
            canvas: Canvas,
            line: WrappedLine,
            positionMs: Long,
            availableWidth: Float,
            lineHeight: Float
    ) {
        val lineText = line.text
        val textWidth = fillPaint.measureText(lineText)
        val x = alignedX(textWidth, availableWidth)
        val baseline = -fillPaint.fontMetrics.top

        if (config.stroke) {
            canvas.drawText(lineText, x, baseline, strokePaint)
        }

        if (words.isEmpty()) {
            fillPaint.color = config.textColor
            canvas.drawText(lineText, x, baseline, fillPaint)
            return
        }

        val sungGlobal = karaokeCharacterCount(positionMs)
        val sungLocal = (sungGlobal - line.startChar).coerceIn(0, lineText.length)

        if (sungLocal <= 0) {
            fillPaint.color = withAlpha(config.textColor, KARAOKE_UNSUNG_ALPHA)
            canvas.drawText(lineText, x, baseline, fillPaint)
            return
        }

        if (sungLocal >= lineText.length) {
            fillPaint.color = config.textColor
            canvas.drawText(lineText, x, baseline, fillPaint)
            return
        }

        fillPaint.color = withAlpha(config.textColor, KARAOKE_UNSUNG_ALPHA)
        canvas.drawText(lineText, x, baseline, fillPaint)

        val clipRight = x + fillPaint.measureText(lineText, 0, sungLocal)
        val save = canvas.save()
        canvas.clipRect(x, 0f, clipRight, lineHeight)
        fillPaint.color = config.textColor
        canvas.drawText(lineText, x, baseline, fillPaint)
        canvas.restoreToCount(save)
    }

    private fun drawTranslationLine(canvas: Canvas, availableWidth: Float) {
        val line = translation ?: return
        val paint = translationFillPaint
        val ellipsized = TextUtils.ellipsize(line, paint, availableWidth, TextUtils.TruncateAt.END)
        val textWidth = paint.measureText(ellipsized, 0, ellipsized.length)
        val x = alignedX(textWidth, availableWidth)
        val baseline = -paint.fontMetrics.top

        if (config.stroke) {
            canvas.drawText(ellipsized, 0, ellipsized.length, x, baseline, translationStrokePaint)
        }

        canvas.drawText(ellipsized, 0, ellipsized.length, x, baseline, paint)
    }

    /**
     * Softens the left and right edges while the line is scrolling, so text appears
     * to slide out of the box instead of being chopped off by a hard clip.
     */
    private fun drawEdgeFade(canvas: Canvas) {
        val widthPx = width
        if (widthPx <= 0) return

        if (fadeShader == null || fadeShaderWidth != widthPx) {
            fadeShaderWidth = widthPx
            fadeShader = LinearGradient(
                    0f, 0f, widthPx.toFloat(), 0f,
                    intArrayOf(Color.TRANSPARENT, Color.WHITE, Color.WHITE, Color.TRANSPARENT),
                    floatArrayOf(0f, 0.08f, 0.92f, 1f),
                    Shader.TileMode.CLAMP
            )
        }

        fadePaint.shader = fadeShader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fadePaint)
    }

    /**
     * Returns how many characters of the line should be lit up at [positionMs].
     * For ordinary LRC lines (no word timing) the whole line is considered sung.
     */
    private fun karaokeCharacterCount(positionMs: Long): Int {
        if (words.isEmpty()) return text.length

        var chars = 0
        for (word in words) {
            if (positionMs >= word.endMs) {
                chars += word.text.length
                continue
            }

            if (positionMs > word.startMs) {
                val span = max(1L, word.endMs - word.startMs)
                val fraction = (positionMs - word.startMs).toFloat() / span.toFloat()
                chars += (word.text.length * fraction).roundToInt()
            }
            break
        }

        return chars.coerceIn(0, text.length)
    }

    private fun effectivePositionMs(): Long {
        if (!playing) return basePositionMs
        val delta = SystemClock.uptimeMillis() - baseUptimeMs
        return basePositionMs + max(0L, delta)
    }

    private fun contentHeight(): Float {
        val mainMetrics = fillPaint.fontMetrics
        val mainLineHeight = mainMetrics.descent - mainMetrics.ascent
        val height = if (config.lineCount > 1 && wrappedLines.isNotEmpty()) {
            mainLineHeight * wrappedLines.size
        } else {
            mainLineHeight
        }

        if (!translation.isNullOrBlank()) {
            val transMetrics = translationFillPaint.fontMetrics
            return height + dp(TRANSLATION_GAP_DP) + (transMetrics.descent - transMetrics.ascent)
        }

        return height
    }

    private fun verticalPadding(): Float = dp(VERTICAL_PADDING_DP)

    private fun sp(value: Float): Float = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics
    )

    private fun dp(value: Float): Float = value * density

    private fun withAlpha(color: Int, alpha: Float): Int {
        val alphaInt = (Color.alpha(color) * alpha).roundToInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (alphaInt shl 24)
    }
}
