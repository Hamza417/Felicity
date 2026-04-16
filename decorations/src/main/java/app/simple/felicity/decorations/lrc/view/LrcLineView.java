package app.simple.felicity.decorations.lrc.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristic;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.simple.felicity.decorations.lrc.model.LrcData;
import app.simple.felicity.decorations.lrc.model.LrcEntry;
import app.simple.felicity.decorations.lrc.model.WordEntry;
import app.simple.felicity.decorations.typeface.TypeFace;
import app.simple.felicity.preferences.AppearancePreferences;
import app.simple.felicity.theme.interfaces.ThemeChangedListener;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.models.Accent;
import app.simple.felicity.theme.models.Theme;

/**
 * A feather-light, single-line lyrics overlay designed to sit on top of album art
 * without stealing the show. It just shows the current lyric line — nothing more,
 * nothing less. No scrolling, no animations, no drama.
 *
 * <p>It supports both plain LRC (whole-line highlight) and word-by-word LRC
 * (each word lights up as it's sung). It also handles RTL and LTR text
 * automatically, so it works for any language without extra setup.</p>
 *
 * <p>Usage is simple: call {@link #setLrcData(LrcData)} to load your lyrics,
 * then keep calling {@link #updateTime(long)} as the song plays and this view
 * takes care of the rest.</p>
 *
 * @author Hamza417
 */
public class LrcLineView extends View implements ThemeChangedListener {
    
    /**
     * Default text size in SP — large enough to read on album art but not so big
     * it covers everything interesting.
     */
    private static final float DEFAULT_TEXT_SIZE_SP = 16f;
    
    /**
     * The color for words that haven't been sung yet. A bit dimmer than the
     * highlight so the active word stands out nicely.
     */
    private static final int DEFAULT_NORMAL_COLOR = 0xCCFFFFFF;
    
    /**
     * What to show when no lyrics are loaded or there's a gap between lines.
     * An empty string keeps things clean — no ugly placeholder text on album art.
     */
    private static final String DEFAULT_EMPTY_TEXT = "";
    // Paint for measuring and drawing text
    private final TextPaint textPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    // Reusable rect and paint for the background rectangle — allocated once to keep onDraw allocation-free
    private final RectF pillRect = new RectF();
    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    // The loaded lyrics data — null means "nothing to show"
    private LrcData lrcData;
    // Which line we're on right now (-1 = before the first line)
    private int currentLineIndex = -1;
    // Which word within the current line is active (-1 = whole-line mode or no word yet)
    private int currentWordIndex = -1;
    // The pre-built layout for whatever we're about to draw — rebuilt only when something changes
    private StaticLayout cachedLayout;
    // The line index and word index that produced the cached layout
    private int cachedLineIndex = Integer.MIN_VALUE;
    private int cachedWordIndex = Integer.MIN_VALUE;
    /**
     * The color for words that haven't been sung yet. Dimmer than the highlight color
     * so the active word stands out nicely against the album art.
     */
    @ColorInt
    private int normalColor = DEFAULT_NORMAL_COLOR;
    /**
     * The color for the currently active word or line — pulled from the theme so it
     * always matches the rest of the app. Falls back to white if no theme is set yet.
     */
    @ColorInt
    private int highlightColor = Color.WHITE;
    // Text shown when there's nothing to display
    private String emptyText = DEFAULT_EMPTY_TEXT;
    /**
     * When true, a rounded pill is drawn behind the lyrics text. Useful on screens
     * where the album art can be both very light and very dark, making white or black
     * text equally problematic — the pill gives the text its own readable backdrop.
     */
    private boolean showBackgroundPill = false;
    /**
     * The fill color of the rounded pill background. Pulled from the theme's highlight
     * color automatically, but can be overridden via {@link #setBackgroundPillColor(int)}.
     */
    @ColorInt
    private int backgroundPillColor = Color.BLACK; // updated from theme in onAttachedToWindow
    
    public LrcLineView(@NonNull Context context) {
        this(context, null);
    }
    
    public LrcLineView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public LrcLineView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    
    /**
     * Sets up the paint with sensible defaults. We do this once so the rest of the
     * lifecycle is as cheap as possible.
     */
    private void init(Context context) {
        float textSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                DEFAULT_TEXT_SIZE_SP,
                context.getResources().getDisplayMetrics()
                                                    );
        textPaint.setTextSize(textSizePx);
        textPaint.setColor(highlightColor);
        if (!isInEditMode()) {
            textPaint.setTypeface(TypeFace.INSTANCE.getBoldTypeFace(context));
        }
        pillPaint.setStyle(Paint.Style.FILL);
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isInEditMode()) {
            // Sign up for theme changes so we stay in sync with the rest of the app
            ThemeManager.INSTANCE.addListener(this);
            // Apply the current theme right away so the first draw looks correct
            highlightColor = ThemeManager.INSTANCE.getTheme().getTextViewTheme().getPrimaryTextColor();
            backgroundPillColor = ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getHighlightColor();
            pillPaint.setColor(backgroundPillColor);
        }
        invalidateCache();
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // Clean up so we don't leak memory or receive callbacks after we're gone
        ThemeManager.INSTANCE.removeListener(this);
    }
    
    @Override
    public void onThemeChanged(@NonNull Theme theme, boolean animate) {
        // The user switched themes — grab the new primary text color and repaint
        highlightColor = theme.getTextViewTheme().getPrimaryTextColor();
        backgroundPillColor = theme.getViewGroupTheme().getHighlightColor();
        pillPaint.setColor(backgroundPillColor);
        invalidateCache();
        invalidate();
    }
    
    @Override
    public void onAccentChanged(@NonNull Accent accent) {
        // We don't use the accent color directly, but the interface requires this.
        // Nothing to do here — move along.
    }
    
    /**
     * Loads a full set of lyrics. Call this whenever you have a new song, or pass
     * {@code null} to clear the view back to its empty state.
     *
     * @param lrcData the parsed lyrics, or null to clear
     */
    public void setLrcData(@Nullable LrcData lrcData) {
        this.lrcData = lrcData;
        currentLineIndex = -1;
        currentWordIndex = -1;
        invalidateCache();
        requestLayout();
        invalidate();
    }
    
    /**
     * Convenience overload that loads lyrics and immediately seeks to the given position.
     * Handy when you're resuming playback mid-song.
     *
     * @param lrcData           the parsed lyrics, or null to clear
     * @param initialPositionMs the playback position to jump to right away
     */
    public void setLrcData(@Nullable LrcData lrcData, long initialPositionMs) {
        setLrcData(lrcData);
        updateTime(initialPositionMs);
    }
    
    /**
     * Tell the view what time the song is at right now. Call this frequently
     * (e.g. from a playback callback) — the view only redraws when the visible
     * content actually changes, so there's no wasted work.
     *
     * @param positionMs current playback position in milliseconds
     */
    public void updateTime(long positionMs) {
        if (lrcData == null || lrcData.isEmpty()) {
            return;
        }
        
        int newLineIndex = findCurrentLineIndex(positionMs);
        int newWordIndex = -1;
        
        if (newLineIndex >= 0) {
            LrcEntry entry = lrcData.getEntries().get(newLineIndex);
            if (entry.hasWordSync()) {
                newWordIndex = findCurrentWordIndex(entry, positionMs);
            }
        }
        
        // Only do any work if something actually changed
        if (newLineIndex != currentLineIndex || newWordIndex != currentWordIndex) {
            boolean lineChanged = newLineIndex != currentLineIndex;
            currentLineIndex = newLineIndex;
            currentWordIndex = newWordIndex;
            // When the line itself changes the text content may wrap differently,
            // so we need a full remeasure — not just a redraw.
            if (lineChanged) {
                invalidateCache();
                requestLayout();
            }
            invalidate();
        }
    }
    
    public void reset() {
        currentLineIndex = -1;
        currentWordIndex = -1;
        invalidateCache();
        requestLayout();
        invalidate();
    }
    
    public void clear() {
        setLrcData(null);
        reset();
    }
    
    /**
     * Scans the lyric entries to find which line is active at {@code positionMs}.
     * We stop as soon as we find an entry that's in the future — since the list is
     * sorted, everything after that point can be skipped.
     */
    private int findCurrentLineIndex(long positionMs) {
        List <LrcEntry> entries = lrcData.getEntries();
        int result = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getTimeInMillis() <= positionMs) {
                result = i;
            } else {
                break;
            }
        }
        return result;
    }
    
    /**
     * Within a word-synced line, finds which word is currently being sung.
     * Returns -1 if no word has started yet.
     */
    private int findCurrentWordIndex(@NonNull LrcEntry entry, long positionMs) {
        List <WordEntry> words = entry.getWords();
        int result = -1;
        for (int i = 0; i < words.size(); i++) {
            if (words.get(i).getStartMs() <= positionMs) {
                result = i;
            } else {
                break;
            }
        }
        return result;
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int resolvedWidth = resolveSize(width, widthMeasureSpec);
        
        // We need to know how tall the actual text will be, so we build a temporary
        // layout using the resolved width. This accounts for line wrapping properly —
        // a two-line lyric really does need twice the height, no surprises.
        int textHeight;
        if (resolvedWidth > 0) {
            CharSequence text = buildDisplayText();
            if (TextUtils.isEmpty(text)) {
                text = TextUtils.isEmpty(emptyText) ? " " : emptyText;
            }
            StaticLayout measureLayout = StaticLayout.Builder
                    .obtain(text, 0, text.length(), textPaint, resolvedWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                    .setMaxLines(2)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setIncludePad(false)
                    .build();
            textHeight = measureLayout.getHeight();
        } else {
            // No width yet — fall back to a single-line estimate so we're not zero-height
            android.graphics.Paint.FontMetrics fm = textPaint.getFontMetrics();
            textHeight = (int) Math.ceil(fm.descent - fm.ascent);
        }
        
        int desiredHeight = textHeight + getPaddingTop() + getPaddingBottom();
        int resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(resolvedWidth, resolvedHeight);
    }
    
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        StaticLayout layout = getOrBuildLayout();
        if (layout == null) {
            return; // Nothing to draw — stay invisible
        }

        int width = getWidth();
        int height = getHeight();
        
        // Draw the rounded rectangle background first so it sits behind the text.
        // It spans the full view width and height, minus the view's own padding on each
        // side — exactly the same area that HighlightTextView's MaterialShapeDrawable covers.
        if (showBackgroundPill) {
            float themeRadius = isInEditMode()
                    ? TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f,
                    getResources().getDisplayMetrics())
                    : AppearancePreferences.INSTANCE.getCornerRadius();
            
            pillRect.set(
                    getPaddingLeft(),
                    getPaddingTop(),
                    width - getPaddingRight(),
                    height - getPaddingBottom()
                        );
            canvas.drawRoundRect(pillRect, themeRadius, themeRadius, pillPaint);
        }
        
        // Center the text vertically within the padded content area
        float contentHeight = height - getPaddingTop() - getPaddingBottom();
        float textHeight = layout.getHeight();
        float textTop = getPaddingTop() + (contentHeight - textHeight) / 2f;

        // The layout handles horizontal alignment internally (including RTL),
        // so we just need to drop down to the right vertical position.
        canvas.save();
        canvas.translate(0, textTop);
        layout.draw(canvas);
        canvas.restore();
    }
    
    /**
     * Returns the cached layout if it's still valid, or builds a new one if the
     * line or word has changed. Building a {@link StaticLayout} is relatively
     * expensive so we only do it when the content actually changes.
     */
    @Nullable
    private StaticLayout getOrBuildLayout() {
        if (cachedLayout != null
                && cachedLineIndex == currentLineIndex
                && cachedWordIndex == currentWordIndex) {
            return cachedLayout;
        }
        
        cachedLayout = buildLayout();
        cachedLineIndex = currentLineIndex;
        cachedWordIndex = currentWordIndex;
        return cachedLayout;
    }
    
    /**
     * Builds a fresh {@link StaticLayout} for whatever the view needs to show right now.
     * Returns null when there's genuinely nothing to display, so {@link #onDraw} can
     * skip the draw call entirely without doing any extra work.
     */
    @Nullable
    private StaticLayout buildLayout() {
        int width = getWidth();
        if (width <= 0) {
            return null; // We'll be called again once the view is properly laid out
        }
        
        CharSequence text = buildDisplayText();
        
        if (TextUtils.isEmpty(text)) {
            if (TextUtils.isEmpty(emptyText)) {
                return null;
            }
            // Use the dimmer normal color for the fallback text — it's not important enough
            // to fight for attention with the album art
            textPaint.setColor(normalColor);
            text = emptyText;
        }
        
        // Let Android detect the text direction automatically. It handles Arabic, Hebrew,
        // mixed scripts, and everything in between — we just stay out of the way.
        TextDirectionHeuristic directionHeuristic = TextDirectionHeuristics.FIRSTSTRONG_LTR;
        
        return StaticLayout.Builder
                .obtain(text, 0, text.length(), textPaint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(directionHeuristic)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .build();
    }
    
    /**
     * Assembles the text (and optional color spans) for the current moment in the song.
     *
     * <p>For plain LRC, the whole line is returned in the theme's primary text color.
     * For word-by-word LRC, sung words are highlighted and future words stay dimmer —
     * like a tiny karaoke prompt right on the album art.</p>
     */
    @Nullable
    private CharSequence buildDisplayText() {
        if (lrcData == null || lrcData.isEmpty() || currentLineIndex < 0) {
            return null;
        }
        
        LrcEntry entry = lrcData.getEntries().get(currentLineIndex);
        
        if (!entry.hasWordSync()) {
            // Plain line mode — paint the whole line with the theme's text color
            textPaint.setColor(highlightColor);
            return entry.getText();
        }
        
        List <WordEntry> words = entry.getWords();
        if (words.isEmpty()) {
            textPaint.setColor(highlightColor);
            return entry.getText();
        }
        
        // Word-by-word mode — build a spannable so each word can have its own color
        SpannableStringBuilder builder = new SpannableStringBuilder();
        for (int i = 0; i < words.size(); i++) {
            String wordText = words.get(i).getText();
            int start = builder.length();
            builder.append(wordText);
            int end = builder.length();
            
            // Already-sung and current words get the full highlight; upcoming words
            // get the dimmer normal color so the active word stands out clearly
            @ColorInt int color = (i <= currentWordIndex) ? highlightColor : normalColor;
            builder.setSpan(new ForegroundColorSpan(color), start, end,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        
        textPaint.setColor(highlightColor);
        return builder;
    }
    
    /**
     * Marks the cached layout as stale so the next draw rebuilds it from scratch.
     */
    private void invalidateCache() {
        cachedLayout = null;
        cachedLineIndex = Integer.MIN_VALUE;
        cachedWordIndex = Integer.MIN_VALUE;
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // If the width changed, the text might reflow, so we need a fresh layout
        invalidateCache();
    }
    
    /**
     * Sets the text size in SP. The layout is rebuilt on the next draw automatically.
     *
     * @param textSizeSp the desired text size in scale-independent pixels
     */
    public void setTextSizeSp(float textSizeSp) {
        float px = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                textSizeSp,
                getResources().getDisplayMetrics()
                                            );
        textPaint.setTextSize(px);
        invalidateCache();
        // Height might change too, so ask the parent to re-measure us
        requestLayout();
        invalidate();
    }
    
    /**
     * Overrides the color used for words that haven't been sung yet. The default is a
     * semi-transparent white which looks good over most album art without being too noisy.
     *
     * @param color the color as an ARGB integer
     */
    public void setNormalColor(@ColorInt int color) {
        this.normalColor = color;
        invalidateCache();
        invalidate();
    }
    
    /**
     * Manually overrides the highlight color. Normally this is pulled from the theme
     * automatically, but you can call this if you need a custom color for a specific screen.
     *
     * @param color the color as an ARGB integer
     */
    public void setHighlightColor(@ColorInt int color) {
        this.highlightColor = color;
        invalidateCache();
        invalidate();
    }
    
    /**
     * Turns the rounded pill background on or off. When on, a filled rounded rectangle is
     * drawn behind the lyrics text using the theme's highlight color. This is handy on
     * player screens where album art contrast is unpredictable — the pill ensures the
     * text is always readable regardless of what's behind it.
     *
     * @param show true to show the pill, false to hide it (default is false)
     */
    public void setShowBackgroundPill(boolean show) {
        this.showBackgroundPill = show;
        invalidate();
    }
    
    /**
     * Overrides the pill background color. Normally this is kept in sync with the theme's
     * highlight color automatically, but you can pin it to any color you like here.
     *
     * @param color the ARGB color to use for the pill fill
     */
    public void setBackgroundPillColor(@ColorInt int color) {
        this.backgroundPillColor = color;
        pillPaint.setColor(color);
        if (showBackgroundPill) {
            invalidate();
        }
    }
    
    /**
     * Sets the text to show when no lyrics are available or when we're in a gap between lines.
     * Pass an empty string (or null) to show nothing at all — which is the default.
     *
     * @param text the fallback text
     */
    public void setEmptyText(@Nullable String text) {
        this.emptyText = text != null ? text : "";
        if (currentLineIndex < 0) {
            invalidateCache();
            invalidate();
        }
    }
}
