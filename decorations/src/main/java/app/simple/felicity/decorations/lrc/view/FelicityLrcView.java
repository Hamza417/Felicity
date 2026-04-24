package app.simple.felicity.decorations.lrc.view;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;

import java.util.HashMap;
import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.FlingAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import app.simple.felicity.decoration.R;
import app.simple.felicity.decorations.lrc.model.LrcData;
import app.simple.felicity.decorations.lrc.model.LrcEntry;
import app.simple.felicity.decorations.lrc.model.WordEntry;
import app.simple.felicity.decorations.lrc.parser.TxtParser;
import app.simple.felicity.decorations.ripple.FelicityRippleDrawable;
import app.simple.felicity.decorations.typeface.TypeFace;
import app.simple.felicity.preferences.AppearancePreferences;
import app.simple.felicity.theme.interfaces.ThemeChangedListener;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.models.Accent;
import app.simple.felicity.theme.models.Theme;

/**
 * Modern LRC lyrics view with center prominence and smooth scrolling
 */
public class FelicityLrcView extends View implements ThemeChangedListener {
    
    // Default values
    private static final float DEFAULT_TEXT_SIZE = 22f; // sp
    private static final float DEFAULT_LINE_SPACING = 16f; // dp
    public static final float DEFAULT_FADE_LENGTH = 140f; // dp - length of vertical fade
    private static final float DEFAULT_SCROLL_MULTIPLIER = 1f; // Accelerated scroll multiplier
    private static final float DEFAULT_OVERSCROLL_DISTANCE = 250f; // dp - maximum overscroll distance
    private static final float OVERSCROLL_DAMPING = 0.25f; // Rubber band damping factor (0-1)
    private static final float FLING_FRICTION = 1.5f; // Friction for fling animation
    // How quickly the highlighted line cross-fades its accent color in and out (ms).
    private static final long HIGHLIGHT_FADE_IN_MS = 350;
    private static final long HIGHLIGHT_FADE_OUT_MS = 250;
    // Lower stiffness = more relaxed, unhurried auto-scroll animation.
    private static final float SCROLL_SPRING_STIFFNESS = 80f;
    private static final float MAX_BLUR_RADIUS = 15f; // Maximum blur radius at edges (in pixels)
    private static final int DEFAULT_NORMAL_COLOR = Color.GRAY;
    private static final int DEFAULT_CURRENT_COLOR = Color.WHITE;
    private static final String DEFAULT_EMPTY_TEXT = "No lyrics";
    private static final long AUTO_SCROLL_DELAY = 3000; // 3 seconds after manual scroll
    private static final float CURTAIN_INITIAL_BLUR = 30f; // Starting blur radius for curtain effect
    private static final float CURTAIN_INITIAL_SCALE = 1.2f; // Starting scale for curtain effect
    private static final long CURTAIN_STAGGER_MS = 40; // Delay between each line (ms)
    private static final long CURTAIN_DURATION_MS = 450; // Duration of each line's animation
    /**
     * Duration used for the plain fade-in animation that runs on subsequent lyric loads
     * (i.e., after the first curtain has already played). We keep this short so the new
     * song's lyrics appear quickly without a jarring swap.
     */
    private static final long FADE_IN_DURATION_MS = 300;
    /**
     * Extra overscroll room (in pixels, derived from dp) that the fling animation is allowed
     * to enter before the spring pulls everything back. This prevents the abrupt hard-stop
     * that happens when a fling hits the min/max boundary.
     */
    private static final float FLING_OVERSCROLL_DP = 100f;
    /**
     * Spring stiffness used specifically for snapping back from an overscroll position.
     * Higher than the auto-scroll stiffness so the rubber-band feel is snappy but not jarring.
     */
    private static final float OVERSCROLL_SPRING_STIFFNESS = 400f;
    // Data
    private LrcData lrcData;
    private int currentLineIndex = -1;
    
    /**
     * Index of the word currently being sung within the active word-sync line.
     * Stays at -1 when the line has no word-sync data or nothing has started yet.
     */
    private int currentWordIndex = -1;
    
    /**
     * Cached {@link StaticLayout} for the active word-sync line, backed by a
     * {@link SpannableString} so each word gets its own color.  Rebuilt whenever
     * the active word changes — no more, no less.
     */
    private StaticLayout wordSyncCurrentLayout = null;
    
    /**
     * Remembers which line index the {@link #wordSyncCurrentLayout} was built for
     * so we can detect when a line change should force a rebuild.
     */
    private int wordSyncLayoutLineIndex = -1;
    
    // Cache for wrapped text layouts — one pass, one cache, no drama.
    private final HashMap <Integer, StaticLayout> normalLayoutCache = new HashMap <>();
    
    // Animated color fractions for smooth highlight cross-fade.
    // highlightFraction goes 0→1 when a line becomes active (fade in accent color).
    // dehighlightFraction goes 1→0 when the previously active line moves off (fade back to normal).
    private float highlightFraction = 0f;
    private float dehighlightFraction = 0f;
    private android.animation.ValueAnimator highlightAnimator;
    private android.animation.ValueAnimator dehighlightAnimator;
    
    // Curtain reveal animation: per-line blur radius (X -> 0), scale (1.2 -> 1), alpha (0 -> 1)
    private final java.util.HashMap <Integer, Float> curtainBlur = new java.util.HashMap <>();
    private final java.util.HashMap <Integer, Float> curtainScale = new java.util.HashMap <>();
    private final java.util.HashMap <Integer, Float> curtainAlpha = new java.util.HashMap <>();
    private final java.util.ArrayList <android.animation.Animator> curtainAnimators = new java.util.ArrayList <>();
    
    // Paint objects
    private TextPaint normalPaint;
    private TextPaint currentPaint;
    private Paint fadePaint;
    // Cache for blur mask filters to avoid constant recreation
    private final HashMap <Float, BlurMaskFilter> blurMaskFilters = new HashMap <>();
    // Styling properties
    private float normalTextSize;
    private float lineSpacing;
    private int normalTextColor;
    private int currentTextColor;
    private Alignment textAlignment = Alignment.LEFT;
    // 0.0f = LEFT, 0.5f = CENTER, 1.0f = RIGHT – animated on alignment changes
    private float alignmentFraction = 0f;
    private android.animation.ValueAnimator alignmentAnimator;
    private String emptyText;
    private float fadeLength;
    private boolean enableFade = true;
    private int previousLineIndex = -1;
    // Scrolling
    private OverScroller scroller;
    private GestureDetector gestureDetector;
    private float scrollY = 0f;
    private float targetScrollY = 0f;
    private boolean isUserScrolling = false;
    private boolean isAutoScrollEnabled = true;
    private boolean isTapSeek = true; // Flag to prevent auto-scroll after tap seek
    private float scrollMultiplier;
    private float maxOverscrollDistance;
    private SpringAnimation scrollSpringAnimation; // For auto-scroll
    private FlingAnimation flingAnimation;
    private boolean isInOverscroll = false;
    
    // Blur/fade interaction state
    // 0f = fully blurred (default), 1f = fully unblurred (finger down)
    private float blurInterpolation = 0f;
    private android.animation.ValueAnimator blurAnimator;
    
    /**
     * When true the view will yield touch control to its parent (e.g. BottomSheetBehavior) once
     * the user has scrolled past the bottom of the lyrics so the sheet can be dragged to dismiss.
     * Set to false (default) in a Fragment where there is no dismissible parent.
     */
    private boolean parentDismissEnabled = false;
    
    /**
     * Total song duration in milliseconds, used for proportional scrolling in static (TXT) mode.
     * Set via {@link #setDuration(long)}.
     */
    private long durationMs = 0L;
    
    // Auto scroll resume
    private final Runnable autoScrollRunnable = () -> {
        isUserScrolling = false;
        if (isAutoScrollEnabled && currentLineIndex >= 0) {
            scrollToLine(currentLineIndex);
        }
    };
    // Callbacks
    private OnLrcClickListener onLrcClickListener;
    
    // Ripple effect
    private FelicityRippleDrawable rippleDrawable;
    private int tappedLineIndex = -1;
    private float rippleX = 0f;
    private float rippleY = 0f;
    
    public FelicityLrcView(Context context) {
        this(context, null);
    }
    
    public FelicityLrcView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public FelicityLrcView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }
    
    /**
     * Maps an {@link Alignment} to its canonical fraction (LEFT=0, CENTER=0.5, RIGHT=1).
     */
    private static float alignmentFractionFor(Alignment alignment) {
        return switch (alignment) {
            case LEFT ->
                    0f;
            case CENTER ->
                    0.5f;
            case RIGHT ->
                    1f;
        };
    }
    
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        
        if (lrcData == null || lrcData.isEmpty()) {
            drawEmptyText(canvas);
            return;
        }
        
        drawLyrics(canvas);
    }
    
    private void init(Context context, @Nullable AttributeSet attrs) {
        // Initialize default values
        normalTextSize = sp2px(context, DEFAULT_TEXT_SIZE);
        lineSpacing = dp2px(context, DEFAULT_LINE_SPACING);
        fadeLength = dp2px(context, DEFAULT_FADE_LENGTH);
        scrollMultiplier = DEFAULT_SCROLL_MULTIPLIER;
        maxOverscrollDistance = dp2px(context, DEFAULT_OVERSCROLL_DISTANCE);
        normalTextColor = DEFAULT_NORMAL_COLOR;
        currentTextColor = DEFAULT_CURRENT_COLOR;
        emptyText = DEFAULT_EMPTY_TEXT;
        alignmentFraction = alignmentFractionFor(textAlignment);
        
        // Read attributes if provided
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FelicityLrcView);
            try {
                normalTextSize = a.getDimension(R.styleable.FelicityLrcView_lrcTextSize, normalTextSize);
                normalTextColor = a.getColor(R.styleable.FelicityLrcView_lrcNormalTextColor, normalTextColor);
                currentTextColor = a.getColor(R.styleable.FelicityLrcView_lrcCurrentTextColor, currentTextColor);
            } finally {
                a.recycle();
            }
        }
        
        // Initialize paints
        // All lyrics lines use the bold typeface — no fake bold tricks needed anywhere.
        // The highlight is purely a color change, so every line looks consistent and sits
        // at exactly the same vertical position regardless of whether it's active or not.
        normalPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        normalPaint.setTextSize(normalTextSize);
        normalPaint.setColor(normalTextColor);
        if (!isInEditMode()) {
            normalPaint.setTypeface(TypeFace.INSTANCE.getBlackTypeFace(context));
        }
        
        currentPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        currentPaint.setTextSize(normalTextSize);
        currentPaint.setColor(currentTextColor);
        if (!isInEditMode()) {
            currentPaint.setTypeface(TypeFace.INSTANCE.getBlackTypeFace(context));
        }
        
        // Initialize fade paint for vertical gradient effect
        fadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        
        updateTextAlignment();
        
        // Initialize scroller
        scroller = new OverScroller(context, new DecelerateInterpolator());
        
        // Initialize gesture detector
        gestureDetector = new GestureDetector(context, new GestureListener());
        
        // Initialize ripple drawable
        if (!isInEditMode()) {
            rippleDrawable = new FelicityRippleDrawable(ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor());
            rippleDrawable.setCornerRadius(AppearancePreferences.INSTANCE.getCornerRadius());
            rippleDrawable.setCallback(this);
        }
        
        if (!isInEditMode()) {
            updateColorsFromTheme(ThemeManager.INSTANCE.getTheme());
            updateColorsFromAccent(ThemeManager.INSTANCE.getAccent());
        }
    }
    
    private void drawEmptyText(Canvas canvas) {
        normalPaint.setTextAlign(Paint.Align.CENTER);
        float x = getWidth() / 2f;
        float y = getHeight() / 2f - ((normalPaint.descent() + normalPaint.ascent()) / 2);
        canvas.drawText(emptyText, x, y, normalPaint);
        normalPaint.setTextAlign(Paint.Align.LEFT);
    }
    
    /**
     * Draw vertical fade gradient at top and bottom
     */
    private void drawVerticalFade(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        
        // Scale the opaque end of the gradient by (1 - blurInterpolation).
        // When blurInterpolation = 0 (idle) the gradient is fully opaque (0xFF) → normal fade.
        // When blurInterpolation = 1 (finger down) the gradient is transparent (0x00) → no fade.
        int alpha = Math.round(0xFF * (1f - blurInterpolation));
        int opaqueBlack = (alpha << 24);
        
        // Top fade gradient
        LinearGradient topGradient = new LinearGradient(
                0, 0,
                0, fadeLength,
                opaqueBlack, 0x00000000,
                Shader.TileMode.CLAMP
        );
        fadePaint.setShader(topGradient);
        canvas.drawRect(0, 0, width, fadeLength, fadePaint);
        
        // Bottom fade gradient
        LinearGradient bottomGradient = new LinearGradient(
                0, height - fadeLength,
                0, height,
                0x00000000, opaqueBlack,
                Shader.TileMode.CLAMP
        );
        fadePaint.setShader(bottomGradient);
        canvas.drawRect(0, height - fadeLength, width, height, fadePaint);
    }
    
    /**
     * Calculate blur amount based on Y position
     * Returns blur radius (0 = no blur, MAX_BLUR_RADIUS = maximum blur)
     * Blur is proportional to fade effect
     */
    private float calculateBlurAmount(float y, int viewHeight) {
        float blurAmount = 0f;
        
        // Top edge blur (within fadeLength from top)
        if (y < fadeLength) {
            float fadeProgress = y / fadeLength;
            blurAmount = (1f - fadeProgress) * MAX_BLUR_RADIUS;
        }
        // Bottom edge blur (within fadeLength from bottom)
        else if (y > viewHeight - fadeLength) {
            float distanceFromBottom = viewHeight - y;
            float fadeProgress = distanceFromBottom / fadeLength;
            blurAmount = (1f - fadeProgress) * MAX_BLUR_RADIUS;
        }
        
        // Scale down towards zero as the user's finger is down (blurInterpolation → 1)
        blurAmount *= (1f - blurInterpolation);
        
        return blurAmount;
    }
    
    /**
     * Calculate cumulative Y offset for a given line.
     * Since all lines share the same text size, we just look up the cached layout height
     * (which is stable once measured) and sum them up — no per-frame recalculation needed.
     */
    private float getLineOffset(int lineIndex) {
        float offset = 0f;
        
        for (int i = 0; i < lineIndex; i++) {
            StaticLayout layout = normalLayoutCache.get(i);
            float height = (layout != null) ? layout.getHeight() : normalTextSize;
            offset += height + lineSpacing;
        }
        
        // Add half height of the target line so the view centers on that line.
        StaticLayout targetLayout = normalLayoutCache.get(lineIndex);
        float targetHeight = (targetLayout != null) ? targetLayout.getHeight() : normalTextSize;
        offset += targetHeight / 2f;
        
        return offset;
    }
    
    /**
     * Linearly blend two ARGB colors by a fraction (0 = fully "from", 1 = fully "to").
     * This is the same math that Android's ArgbEvaluator uses, just inlined here so we
     * don't need to allocate an object every frame.
     */
    private static int blendColors(int from, int to, float fraction) {
        float inv = 1f - fraction;
        int a = Math.round(Color.alpha(from) * inv + Color.alpha(to) * fraction);
        int r = Math.round(Color.red(from) * inv + Color.red(to) * fraction);
        int g = Math.round(Color.green(from) * inv + Color.green(to) * fraction);
        int b = Math.round(Color.blue(from) * inv + Color.blue(to) * fraction);
        return Color.argb(a, r, g, b);
    }
    
    /**
     * Kick off the color-transition animations when the highlighted line changes.
     * The new active line fades in to accent color; the old one fades back to normal.
     */
    private void startHighlightTransition() {
        // Fade the new line in from normal → accent
        if (highlightAnimator != null && highlightAnimator.isRunning()) {
            highlightAnimator.cancel();
        }
        highlightFraction = 0f;
        highlightAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f);
        highlightAnimator.setDuration(HIGHLIGHT_FADE_IN_MS);
        highlightAnimator.setInterpolator(new DecelerateInterpolator());
        highlightAnimator.addUpdateListener(a -> {
            highlightFraction = (float) a.getAnimatedValue();
            invalidate();
        });
        highlightAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                highlightFraction = 1f;
                invalidate();
            }
        });
        highlightAnimator.start();
        
        // Fade the old line out from accent → normal (only if there was a previous line)
        if (dehighlightAnimator != null && dehighlightAnimator.isRunning()) {
            dehighlightAnimator.cancel();
        }
        dehighlightFraction = 1f;
        dehighlightAnimator = android.animation.ValueAnimator.ofFloat(1f, 0f);
        dehighlightAnimator.setDuration(HIGHLIGHT_FADE_OUT_MS);
        dehighlightAnimator.setInterpolator(new DecelerateInterpolator());
        dehighlightAnimator.addUpdateListener(a -> {
            dehighlightFraction = (float) a.getAnimatedValue();
            invalidate();
        });
        dehighlightAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                dehighlightFraction = 0f;
                invalidate();
            }
        });
        dehighlightAnimator.start();
    }
    
    private void drawLyrics(Canvas canvas) {
        float centerY = getHeight() / 2f;
        float offsetY = centerY - scrollY;
        
        int entryCount = lrcData.size();
        
        // Use hardware layer for fade effect
        int layerId = -1;
        if (enableFade) {
            layerId = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        }
        
        // Save canvas state and apply clipping to respect padding
        canvas.save();
        canvas.clipRect(
                getPaddingLeft(),
                0,
                getWidth() - getPaddingRight(),
                getHeight());
        
        for (int i = 0; i < entryCount; i++) {
            LrcEntry entry = lrcData.getEntries().get(i);
            String text = entry.getText();
            
            // Calculate Y position for this line
            float y = offsetY + getLineOffset(i);
            
            // All layouts are always built with normalPaint so every line has identical font
            // metrics and there is zero vertical shift between normal and highlighted lines.
            // The highlight color is applied at draw time (see below), not baked into the layout.
            // This also fixes a caching bug where accent-colored layouts would linger on lines
            // that had already moved past the highlight.
            StaticLayout layout;
            if (!isStaticMode() && i == currentLineIndex && entry.hasWordSync()) {
                layout = getOrCreateWordSyncLayout(entry, normalPaint, i);
            } else {
                layout = getOrCreateLayout(text, normalPaint, i);
            }
            
            // Skip lines that are completely off-screen (accounting for multi-line height)
            float lineHeight = layout.getHeight();
            if (y < -lineHeight || y > getHeight() + lineHeight) {
                continue;
            }
            
            // Calculate and apply blur based on distance from center (proportional to fade)
            // Also blend in any curtain-reveal blur for this line
            Float cBlur = curtainBlur.get(i);
            if (enableFade) {
                float blurAmount = calculateBlurAmount(y, getHeight());
                if (cBlur != null) {
                    blurAmount = Math.max(blurAmount, cBlur);
                }
                if (blurAmount > 0.5f) {
                    float roundedBlur = Math.round(blurAmount * 2f) / 2f;
                    BlurMaskFilter blurFilter = blurMaskFilters.get(roundedBlur);
                    if (blurFilter == null) {
                        blurFilter = new BlurMaskFilter(roundedBlur, BlurMaskFilter.Blur.NORMAL);
                        blurMaskFilters.put(roundedBlur, blurFilter);
                    }
                    normalPaint.setMaskFilter(blurFilter);
                } else {
                    normalPaint.setMaskFilter(null);
                }
            } else {
                if (cBlur != null && cBlur > 0.5f) {
                    float roundedBlur = Math.round(cBlur * 2f) / 2f;
                    BlurMaskFilter blurFilter = blurMaskFilters.get(roundedBlur);
                    if (blurFilter == null) {
                        blurFilter = new BlurMaskFilter(roundedBlur, BlurMaskFilter.Blur.NORMAL);
                        blurMaskFilters.put(roundedBlur, blurFilter);
                    }
                    normalPaint.setMaskFilter(blurFilter);
                } else {
                    normalPaint.setMaskFilter(null);
                }
            }
            
            // Draw ripple effect for tapped line (behind the text)
            if (i == tappedLineIndex && rippleDrawable != null) {
                float yOffset = y - (lineHeight / 2f);
                int paddingLeft = getPaddingLeft();
                int paddingRight = getPaddingRight();
                int availableWidth = getWidth() - paddingLeft - paddingRight;
                
                rippleDrawable.setBounds(
                        paddingLeft,
                        (int) yOffset,
                        paddingLeft + availableWidth,
                        (int) (yOffset + lineHeight));
                rippleDrawable.draw(canvas);
            }
            
            // Draw the wrapped text using StaticLayout
            canvas.save();
            
            // Calculate X position for the layout based on alignment
            float x = calculateXPositionForLayout(layout, normalPaint, i);
            
            // Position the text vertically (centered on y position)
            float yOffset = y - (lineHeight / 2f);
            canvas.translate(x, yOffset);
            
            // Apply curtain reveal scale (centered on the line's mid-point)
            Float cScale = curtainScale.get(i);
            if (cScale != null && Math.abs(cScale - 1f) > 0.001f) {
                float halfW = layout.getWidth() / 2f;
                float halfH = lineHeight / 2f;
                canvas.scale(cScale, cScale, halfW, halfH);
            }
            
            // Decide whether this line is the current highlight or the one that just stepped down.
            // Only the color changes here — typeface is the same bold weight for every line,
            // so nothing shifts vertically as the highlight moves through the lyrics.
            boolean isCurrentHighlight = !isStaticMode()
                    && i == currentLineIndex
                    && text != null && !text.trim().isEmpty();
            boolean isPreviousHighlight = !isStaticMode()
                    && i == previousLineIndex
                    && text != null && !text.trim().isEmpty()
                    && dehighlightFraction > 0f;
            
            int savedColor = normalPaint.getColor();
            if (isCurrentHighlight) {
                // Blend from normal gray toward accent color as highlightFraction rises (0→1).
                normalPaint.setColor(blendColors(normalTextColor, currentTextColor, highlightFraction));
            } else if (isPreviousHighlight) {
                // Blend back from accent color to normal gray as dehighlightFraction falls (1→0).
                normalPaint.setColor(blendColors(normalTextColor, currentTextColor, dehighlightFraction));
            }
            
            // Apply curtain reveal alpha (0 -> 1 spawn-in effect)
            Float cAlpha = curtainAlpha.get(i);
            int savedAlpha = normalPaint.getAlpha();
            if (cAlpha != null) {
                normalPaint.setAlpha(Math.round(savedAlpha * cAlpha));
            }
            
            layout.draw(canvas);
            
            // Restore paint state so nothing leaks between lines
            if (cAlpha != null) {
                normalPaint.setAlpha(savedAlpha);
            }
            if (isCurrentHighlight || isPreviousHighlight) {
                normalPaint.setColor(savedColor);
            }
            
            canvas.restore();
        }
        
        // Clear mask filter so the paint is clean for the next draw pass
        normalPaint.setMaskFilter(null);
        
        // Restore canvas state
        canvas.restore();
        
        // Apply vertical fade effect
        if (enableFade && fadeLength > 0) {
            drawVerticalFade(canvas);
            canvas.restoreToCount(layerId);
        }
    }
    
    /**
     * Get or create a StaticLayout for wrapped text
     * Uses cached layouts at fixed sizes to avoid constant recreation
     */
    @SuppressWarnings ("unused")
    private StaticLayout getOrCreateLayout(String text, TextPaint paint, int lineIndex) {
        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();
        int availableWidth = getWidth() - paddingLeft - paddingRight;
        
        // Single-line layouts always use ALIGN_NORMAL; X positioning is handled manually in
        // calculateXPositionForLayout() so the smooth alignment animation works.
        // Multi-line (wrapped) layouts delegate alignment to StaticLayout itself because each
        // wrapped line has a different width and can't be shifted as a single block.
        Layout.Alignment multiLineAlignment = switch (textAlignment) {
            case RIGHT ->
                    Layout.Alignment.ALIGN_OPPOSITE;
            case CENTER ->
                    Layout.Alignment.ALIGN_CENTER;
            default ->
                    Layout.Alignment.ALIGN_NORMAL;
        };
        
        // All lines are laid out at normalTextSize now, so one cache covers everything.
        StaticLayout cachedLayout = normalLayoutCache.get(lineIndex);
        
        // Use cached layout if it exists and was created at the same text size
        if (cachedLayout != null) {
            return cachedLayout;
        }
        
        // No cached layout - create new one at the current paint size
        // Check if text will fit on one line at current size
        float textWidth = paint.measureText(text);
        boolean needsWrapping = textWidth > availableWidth;
        
        StaticLayout layout;
        
        if (!needsWrapping) {
            // Single-line: always ALIGN_NORMAL, X is computed by calculateXPositionForLayout
            layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, availableWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)
                    .setMaxLines(1)
                    .build();
        } else {
            // Multi-line: let StaticLayout handle per-line alignment internally
            layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, availableWidth)
                    .setAlignment(multiLineAlignment)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)
                    .build();
        }
        
        // Cache it — one layout per line, stable for the lifetime of this lyrics data.
        normalLayoutCache.put(lineIndex, layout);
        
        return layout;
    }
    
    /**
     * Calculate X position for StaticLayout based on alignment, with full RTL support.
     *
     * <p>Multi-line layouts have their alignment baked in by StaticLayout and are always
     * placed at {@code paddingLeft}.</p>
     *
     * <p>For single-line layouts the canvas is translated so that:</p>
     * <ul>
     *   <li><b>LTR text</b> – {@code ALIGN_NORMAL} draws text starting at x=0 in the layout.
     *       Canvas is shifted right by {@code fraction * slack}:<br>
     *       LEFT → {@code paddingLeft}, CENTER → {@code paddingLeft + slack/2},
     *       RIGHT → {@code paddingLeft + slack}</li>
     *   <li><b>RTL text</b> – {@code ALIGN_NORMAL} draws text starting at x={@code slack} in
     *       the layout (natural right-side origin). The same fraction is instead subtracted so
     *       that the net rendered position is correct:<br>
     *       LEFT → natural right edge ({@code paddingLeft}),
     *       CENTER → centered ({@code paddingLeft - slack/2}),
     *       RIGHT → left edge ({@code paddingLeft - slack})</li>
     * </ul>
     *
     * <p>{@link #alignmentFraction} (0=LEFT, 0.5=CENTER, 1=RIGHT) is animated on alignment
     * changes so the text slides smoothly between positions.</p>
     */
    @SuppressWarnings ("unused")
    private float calculateXPositionForLayout(StaticLayout layout, TextPaint paint, int lineIndex) {
        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();
        int availableWidth = getWidth() - paddingLeft - paddingRight;
        
        // Multi-line layouts have their alignment baked in by StaticLayout (ALIGN_NORMAL /
        // ALIGN_CENTER / ALIGN_OPPOSITE), so they are always placed at paddingLeft.
        if (layout.getLineCount() > 1) {
            return paddingLeft;
        }
        
        String text = layout.getText().toString();
        float textWidth = Math.min(paint.measureText(text), availableWidth);
        // Amount of free horizontal space beside the text within the available width.
        float slack = availableWidth - textWidth;
        float fraction = alignmentFraction;
        
        // Detect the paragraph direction of this individual line.
        // FIRSTSTRONG_LTR returns true for RTL when the first strongly directional character
        // (e.g. an Arabic letter) is right-to-left.
        boolean isRtl = TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(text, 0, text.length());
        
        if (isRtl) {
            // For RTL text, ALIGN_NORMAL places the text at x = slack within the layout
            // (its natural right-side origin). The canvas translation is inverted so that:
            //   fraction=0.0 (LEFT)   → canvas at paddingLeft         → text renders at paddingLeft + slack (right edge)
            //   fraction=0.5 (CENTER) → canvas at paddingLeft - slack/2 → text renders at paddingLeft + slack/2 (centered)
            //   fraction=1.0 (RIGHT)  → canvas at paddingLeft - slack    → text renders at paddingLeft (left edge)
            return paddingLeft - fraction * slack;
        }
        
        // For LTR text, ALIGN_NORMAL places text at x=0. Canvas is shifted right by fraction * slack:
        //   fraction=0.0 (LEFT)   → paddingLeft
        //   fraction=0.5 (CENTER) → paddingLeft + slack/2
        //   fraction=1.0 (RIGHT)  → paddingLeft + slack
        return paddingLeft + fraction * slack;
    }
    
    /**
     * Returns {@code true} when the loaded lyrics data was parsed from a plain-text file
     * (no timestamps).  In this mode the view acts as a scrollable text display rather than
     * a time-synced karaoke view – no line is highlighted and {@link #updateTime} is a no-op.
     */
    public boolean isStaticMode() {
        if (lrcData == null || lrcData.isEmpty()) {
            return false;
        }
        //noinspection SequencedCollectionMethodCanBeUsed
        return lrcData.getEntries().get(0).getTimeInMillis() == TxtParser.NO_TIMESTAMP;
    }
    
    /**
     * Figures out which word inside {@code entry} is currently active based on
     * the playback position.  We return the index of the last word whose start
     * time is at or before {@code timeMs}, or -1 if the line hasn't started yet.
     *
     * <p>Think of it as the "last word the singer has begun" — once a word starts,
     * it stays lit until the next one takes over.</p>
     */
    private int findCurrentWordIndex(LrcEntry entry, long timeMs) {
        List <WordEntry> words = entry.getWords();
        int wordIdx = -1;
        for (int i = 0; i < words.size(); i++) {
            if (timeMs >= words.get(i).getStartMs()) {
                wordIdx = i;
            } else {
                break;
            }
        }
        return wordIdx;
    }
    
    /**
     * Builds a {@link SpannableString} for the given word-sync entry where words
     * that have already been reached (index ≤ {@link #currentWordIndex}) are painted
     * in the highlight color and upcoming words stay in the normal dimmed color.
     *
     * <p>Character positions in the spannable are derived directly from each
     * {@link WordEntry}'s text length, so they always match what was parsed — no
     * trimming or other transformations that could shift the boundaries.</p>
     */
    private CharSequence buildWordSyncSpannable(LrcEntry entry) {
        List <WordEntry> words = entry.getWords();
        String fullText = entry.getText();
        SpannableString spannable = new SpannableString(fullText);
        
        int charPos = 0;
        for (int i = 0; i < words.size(); i++) {
            WordEntry word = words.get(i);
            int wordStart = charPos;
            int wordEnd = Math.min(charPos + word.getText().length(), fullText.length());
            
            if (wordStart < wordEnd) {
                // Words up to and including the active one glow in the accent color.
                int color = (i <= currentWordIndex) ? currentTextColor : normalTextColor;
                spannable.setSpan(
                        new ForegroundColorSpan(color),
                        wordStart, wordEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                 );
            }
            
            charPos += word.getText().length();
            if (charPos >= fullText.length()) {
                break;
            }
        }
        
        return spannable;
    }
    
    /**
     * Gets (or lazily creates) a {@link StaticLayout} for the currently highlighted
     * word-sync line.  The layout is backed by a colored {@link SpannableString}
     * so each word carries its own foreground color.
     *
     * <p>The layout is cached between frames and only rebuilt when
     * {@link #wordSyncCurrentLayout} has been set to {@code null} — which happens
     * whenever the active word changes or the line changes.</p>
     */
    private StaticLayout getOrCreateWordSyncLayout(LrcEntry entry, TextPaint paint, int lineIndex) {
        // Reuse the cached layout if it is still valid for this line.
        if (wordSyncCurrentLayout != null && wordSyncLayoutLineIndex == lineIndex) {
            return wordSyncCurrentLayout;
        }
        
        CharSequence spannable = buildWordSyncSpannable(entry);
        
        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();
        int availableWidth = getWidth() - paddingLeft - paddingRight;
        if (availableWidth <= 0) {
            availableWidth = 1;
        }
        
        String plainText = entry.getText();
        float textWidth = paint.measureText(plainText);
        boolean needsWrapping = textWidth > availableWidth;
        
        Layout.Alignment multiLineAlignment = switch (textAlignment) {
            case RIGHT ->
                    Layout.Alignment.ALIGN_OPPOSITE;
            case CENTER ->
                    Layout.Alignment.ALIGN_CENTER;
            default ->
                    Layout.Alignment.ALIGN_NORMAL;
        };
        
        StaticLayout layout;
        if (!needsWrapping) {
            layout = StaticLayout.Builder
                    .obtain(spannable, 0, spannable.length(), paint, availableWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)
                    .setMaxLines(1)
                    .build();
        } else {
            layout = StaticLayout.Builder
                    .obtain(spannable, 0, spannable.length(), paint, availableWidth)
                    .setAlignment(multiLineAlignment)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)
                    .build();
        }
        
        // Keep word-sync layout cached so we don't rebuild it on every frame.
        wordSyncCurrentLayout = layout;
        wordSyncLayoutLineIndex = lineIndex;
        return layout;
    }
    
    /**
     * Updates the lyrics data reference and re-evaluates the highlighted line without
     * resetting the user's manual scroll position or blowing away existing caches.
     * @noinspection unused
     *
     * <p>Unlike {@link #setLrcData(LrcData)}, caches are kept intact so that lines which
     * have already been measured keep their correct positions.  Only the highlighted line
     * is re-evaluated and its text-size animated accordingly.</p>
     *
     * <p>The scroll snap is deferred via {@link #post} so that layout heights are fully
     * populated from the first draw pass before {@link #getLineOffset} is consulted.
     * This prevents the highlight from landing at a wrong position when the view's cache
     * is empty (e.g. immediately after {@link #reset()}).</p>
     *
     * @param data         new {@link LrcData} (typically with shifted timestamps for sync
     *                     offset adjustments; same logical line count as the previous data)
     * @param timeInMillis current playback position in milliseconds
     */
    public void updateLrcDataInPlace(LrcData data, long timeInMillis) {
        this.lrcData = data;
        int newLineIndex = findLineIndexByTime(timeInMillis + (data != null ? data.getOffset() : 0));
        
        if (newLineIndex != currentLineIndex) {
            previousLineIndex = currentLineIndex;
            currentLineIndex = newLineIndex;
            
            // Drop the word-sync layout so it is rebuilt for the new active line.
            currentWordIndex = -1;
            wordSyncCurrentLayout = null;
            wordSyncLayoutLineIndex = -1;
        }
        
        invalidate();
        
        // Defer the scroll snap until after at least one draw pass so that animatedHeights
        // are populated and getLineOffset() returns the correct value.
        if (isAutoScrollEnabled && currentLineIndex >= 0 && !isUserScrolling) {
            final int lineToSnap = currentLineIndex;
            post(() -> {
                if (!isUserScrolling && isAutoScrollEnabled) {
                    float targetY = getLineOffset(lineToSnap);
                    scrollY = targetY;
                    targetScrollY = targetY;
                    if (scrollSpringAnimation != null && scrollSpringAnimation.isRunning()) {
                        scrollSpringAnimation.cancel();
                    }
                    invalidate();
                }
            });
        }
        // No curtain or ripple here — this method is called during normal playback
        // (e.g. when the sync offset is nudged) and should be completely invisible to the user.
    }
    
    /**
     * Set lyrics data. The first time this is called with actual lyrics (coming from an empty
     * or null state), it plays the full curtain reveal animation — blur, scale, stagger and all.
     * For every subsequent song change it just fades lines in smoothly so nobody gets a seizure
     * from the constant motion overload.
     */
    public void setLrcData(LrcData data) {
        // Remember whether we had any lyrics before so we know which animation to play.
        boolean wasEmpty = (this.lrcData == null || this.lrcData.isEmpty());

        this.lrcData = data;
        this.currentLineIndex = -1;
        this.scrollY = 0f;
        this.targetScrollY = 0f;
        this.normalLayoutCache.clear();
        this.blurMaskFilters.clear();
        this.currentWordIndex = -1;
        this.wordSyncCurrentLayout = null;
        this.wordSyncLayoutLineIndex = -1;

        // Cancel any running curtain animations
        for (android.animation.Animator anim : curtainAnimators) {
            if (anim != null && anim.isRunning()) {
                anim.cancel();
            }
        }
        curtainAnimators.clear();
        curtainBlur.clear();
        curtainScale.clear();
        curtainAlpha.clear();
        
        // Pre-populate curtainAlpha = 0 for every line BEFORE we call invalidate(),
        // so the very first drawn frame already shows transparent lines rather than
        // a flash of fully-visible text that disappears once the animation kicks in.
        if (data != null && !data.isEmpty()) {
            for (int i = 0; i < data.size(); i++) {
                curtainAlpha.put(i, 0f);
            }
        }

        invalidate();

        if (data != null && !data.isEmpty()) {
            if (wasEmpty) {
                // First arrival of lyrics — play the grand curtain entrance.
                post(this :: triggerCurtainAnimation);
            } else {
                // Song changed while we already had lyrics — a subtle fade is plenty.
                post(this :: triggerFadeInAnimation);
            }
        }
    }
    
    public void setEmptyLrcData() {
        setLrcData(new LrcData());
    }
    
    /**
     * Trigger the curtain reveal animation for all lines top-to-bottom (used by {@link #setLrcData}).
     * Each line animates individually with a staggered delay:
     * - alpha:  0 → 1
     * - blur:   CURTAIN_INITIAL_BLUR → 0
     * - scale:  CURTAIN_INITIAL_SCALE → 1.0
     */
    private void triggerCurtainAnimation() {
        if (lrcData == null || lrcData.isEmpty()) {
            return;
        }
        
        // Cancel any previous curtain animations
        for (android.animation.Animator anim : curtainAnimators) {
            if (anim != null && anim.isRunning()) {
                anim.cancel();
            }
        }
        curtainAnimators.clear();
        
        int count = lrcData.size();
        for (int i = 0; i < count; i++) {
            animateCurtainLine(i, (long) i * CURTAIN_STAGGER_MS);
        }
    }
    
    /**
     * Trigger the ripple-from-highlight curtain when {@link #setLrcData} is used.
     * Lines radiate outward from {@code anchorLine}:
     * - Lines at/below anchor stagger downward (anchorLine first, then anchor+1, anchor+2 …)
     * - Lines above anchor stagger upward   (anchor-1, anchor-2 …)
     *
     * @param anchorLine the currently highlighted line index to ripple from
     */
    private void triggerRippleCurtainAnimation(int anchorLine) {
        if (lrcData == null || lrcData.isEmpty()) {
            return;
        }
        
        for (android.animation.Animator anim : curtainAnimators) {
            if (anim != null && anim.isRunning()) {
                anim.cancel();
            }
        }
        curtainAnimators.clear();
        
        int count = lrcData.size();
        int clampedAnchor = Math.max(0, Math.min(anchorLine, count - 1));
        
        // Lines from anchor downward
        for (int i = clampedAnchor; i < count; i++) {
            long delay = (long) (i - clampedAnchor) * CURTAIN_STAGGER_MS;
            animateCurtainLine(i, delay);
        }
        
        // Lines above anchor, going up
        for (int i = clampedAnchor - 1; i >= 0; i--) {
            long delay = (long) (clampedAnchor - i) * CURTAIN_STAGGER_MS;
            animateCurtainLine(i, delay);
        }
    }
    
    /**
     * A lighter alternative to the full curtain that runs when switching songs after the
     * first load. Every line simply fades from invisible to fully visible at once — no blur,
     * no scale, no staggered chaos. It is fast, calm, and friendly to people who are sensitive
     * to heavy motion effects.
     */
    private void triggerFadeInAnimation() {
        if (lrcData == null || lrcData.isEmpty()) {
            return;
        }
        
        for (android.animation.Animator anim : curtainAnimators) {
            if (anim != null && anim.isRunning()) {
                anim.cancel();
            }
        }
        curtainAnimators.clear();
        
        // Make sure every line starts at 0 (they should already be from setLrcData, but
        // defensive programming never hurt anyone).
        int count = lrcData.size();
        for (int i = 0; i < count; i++) {
            curtainAlpha.put(i, 0f);
        }
        
        android.animation.ValueAnimator fadeAnim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        fadeAnim.setDuration(FADE_IN_DURATION_MS);
        fadeAnim.setInterpolator(new DecelerateInterpolator(1.5f));
        fadeAnim.addUpdateListener(anim -> {
            float alpha = (float) anim.getAnimatedValue();
            for (int i = 0; i < count; i++) {
                curtainAlpha.put(i, alpha);
            }
            invalidate();
        });
        fadeAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                // Lines are fully visible now — remove the per-line alpha entries so
                // normal rendering (no extra alpha multiplier) takes over.
                curtainAlpha.clear();
                invalidate();
            }
        });
        
        curtainAnimators.add(fadeAnim);
        fadeAnim.start();
    }

    /**
     * Animate a single line's curtain reveal (alpha 0→1, blur X→0, scale 1.2→1) after the given delay.
     * Each line blooms into view on its own schedule, which is what gives the curtain effect its
     * satisfying cascade feel.
     */
    private void animateCurtainLine(int index, long startDelay) {
        // Set initial state
        curtainAlpha.put(index, 0f);
        curtainBlur.put(index, CURTAIN_INITIAL_BLUR);
        curtainScale.put(index, CURTAIN_INITIAL_SCALE);
        
        // Alpha: 0 -> 1
        android.animation.ValueAnimator alphaAnim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        alphaAnim.setDuration(CURTAIN_DURATION_MS);
        alphaAnim.setStartDelay(startDelay);
        alphaAnim.setInterpolator(new DecelerateInterpolator(1.5f));
        alphaAnim.addUpdateListener(anim -> {
            curtainAlpha.put(index, (float) anim.getAnimatedValue());
            invalidate();
        });
        alphaAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                curtainAlpha.remove(index);
                invalidate();
            }
        });
        
        // Blur: CURTAIN_INITIAL_BLUR -> 0
        android.animation.ValueAnimator blurAnim = android.animation.ValueAnimator.ofFloat(CURTAIN_INITIAL_BLUR, 0f);
        blurAnim.setDuration(CURTAIN_DURATION_MS);
        blurAnim.setStartDelay(startDelay);
        blurAnim.setInterpolator(new DecelerateInterpolator(1.5f));
        blurAnim.addUpdateListener(anim -> {
            curtainBlur.put(index, (float) anim.getAnimatedValue());
            invalidate();
        });
        blurAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                curtainBlur.remove(index);
                invalidate();
            }
        });
        
        // Scale: CURTAIN_INITIAL_SCALE -> 1.0
        android.animation.ValueAnimator scaleAnim = android.animation.ValueAnimator.ofFloat(CURTAIN_INITIAL_SCALE, 1f);
        scaleAnim.setDuration(CURTAIN_DURATION_MS);
        scaleAnim.setStartDelay(startDelay);
        scaleAnim.setInterpolator(new DecelerateInterpolator(1.5f));
        scaleAnim.addUpdateListener(anim -> {
            curtainScale.put(index, (float) anim.getAnimatedValue());
            invalidate();
        });
        scaleAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                curtainScale.remove(index);
                invalidate();
            }
        });
        
        curtainAnimators.add(alphaAnim);
        curtainAnimators.add(blurAnim);
        curtainAnimators.add(scaleAnim);
        alphaAnim.start();
        blurAnim.start();
        scaleAnim.start();
    }
    
    /**
     * Convenience method that sets lyrics data <em>and</em> immediately positions the view at
     * the given playback time in a single call.
     * <p>
     * Unlike calling {@link #setLrcData} followed by {@link #setPosition}, this method uses a
     * <b>ripple curtain</b> reveal animation instead of the top-to-bottom curtain:
     * <ul>
     *   <li>The highlighted line and all lines below it animate outward downward.</li>
     *   <li>All lines above the highlighted line animate outward upward.</li>
     * </ul>
     * The scroll position is snapped immediately to the correct line so there is no
     * "stuck-at-top" delay.
     *
     * @param data         the {@link LrcData} to display
     * @param timeInMillis current playback position in milliseconds
     */
    public void setLrcData(LrcData data, long timeInMillis) {
        // Remember if we had lyrics before so we know which animation to use afterward.
        boolean wasEmpty = (this.lrcData == null || this.lrcData.isEmpty());

        this.lrcData = data;
        this.currentLineIndex = -1;
        this.previousLineIndex = -1;
        this.scrollY = 0f;
        this.targetScrollY = 0f;
        this.normalLayoutCache.clear();
        this.blurMaskFilters.clear();
        this.currentWordIndex = -1;
        this.wordSyncCurrentLayout = null;
        this.wordSyncLayoutLineIndex = -1;

        for (android.animation.Animator anim : curtainAnimators) {
            if (anim != null && anim.isRunning()) {
                anim.cancel();
            }
        }
        curtainAnimators.clear();
        curtainBlur.clear();
        curtainScale.clear();
        curtainAlpha.clear();

        if (data == null || data.isEmpty()) {
            invalidate();
            return;
        }
        
        // Pre-populate curtainAlpha = 0 so the very first frame shows invisible lines
        // rather than a flash of opaque text that vanishes once the animation begins.
        for (int i = 0; i < data.size(); i++) {
            curtainAlpha.put(i, 0f);
        }

        // All lines share normalTextSize, so no per-line size priming is needed here.
        if (!isStaticMode()) {
            long adjustedTime = timeInMillis + data.getOffset();
            currentLineIndex = findLineIndexByTime(adjustedTime);
        } else if (durationMs > 0) {
            float maxScroll = getMaxScrollY();
            if (maxScroll > 0) {
                float fraction = Math.max(0f, Math.min(1f, (float) timeInMillis / (float) durationMs));
                scrollY = fraction * maxScroll;
                targetScrollY = scrollY;
            }
        }

        invalidate();
        
        // Post so layout heights are measured before we snap the scroll or animate.
        final int anchorLine = currentLineIndex;
        final boolean firstLoad = wasEmpty;
        post(() -> {
            // Snap scroll to the active line now that heights are known.
            if (anchorLine >= 0) {
                float targetY = getLineOffset(anchorLine);
                scrollY = targetY;
                targetScrollY = targetY;
                if (scrollSpringAnimation != null && scrollSpringAnimation.isRunning()) {
                    scrollSpringAnimation.cancel();
                }
            }
            if (firstLoad) {
                // Grand entrance — ripple outward from the current line.
                triggerRippleCurtainAnimation(anchorLine);
            } else {
                // Just fade everything in quietly; the scroll already moved to the right spot.
                triggerFadeInAnimation();
            }
        });
    }
    
    /**
     * <p>
     * This is the recommended method to call when opening a fragment / activity so that the
     * view instantly shows the correct highlighted line and scroll position rather than
     * staying at the top until the first {@code updateTime()} fires.
     * <p>
     * Unlike {@link #updateTime}, this method:
     * <ul>
     *   <li>Immediately applies {@code currentLineIndex} and text-size state.</li>
     *   <li>Snaps the scroll position directly to the correct line (no spring animation delay).</li>
     *   <li>Does NOT override user manual scrolling.</li>
     * </ul>
     *
     * @param timeInMillis current playback position in milliseconds
     */
    public void setPosition(long timeInMillis) {
        if (lrcData == null || lrcData.isEmpty()) {
            return;
        }
        
        if (isStaticMode()) {
            if (durationMs > 0) {
                float maxScroll = getMaxScrollY();
                if (maxScroll > 0) {
                    float fraction = Math.max(0f, Math.min(1f, (float) timeInMillis / (float) durationMs));
                    scrollY = fraction * maxScroll;
                    targetScrollY = scrollY;
                    invalidate();
                }
            }
            return;
        }
        
        long adjustedTime = timeInMillis + lrcData.getOffset();
        int newLineIndex = findLineIndexByTime(adjustedTime);
        
        // Apply line change immediately (same logic as updateTime but synchronous)
        if (newLineIndex != currentLineIndex) {
            previousLineIndex = currentLineIndex;
            currentLineIndex = newLineIndex;
        }
        
        invalidate();
        
        // If the current line has word-sync data, prime the active word index immediately
        // so the first drawn frame already shows the correct word highlighted.
        if (currentLineIndex >= 0 && currentLineIndex < lrcData.size()) {
            LrcEntry posEntry = lrcData.getEntries().get(currentLineIndex);
            if (posEntry.hasWordSync()) {
                currentWordIndex = findCurrentWordIndex(posEntry, adjustedTime);
                wordSyncCurrentLayout = null;
                wordSyncLayoutLineIndex = -1;
            }
        }

        // Post the scroll snap so that layout heights are guaranteed to be populated
        // (getLineOffset relies on animatedHeights which are filled during the first draw pass)
        final int lineToSnap = currentLineIndex;
        post(() -> {
            if (lineToSnap >= 0) {
                float targetY = getLineOffset(lineToSnap);
                scrollY = targetY;
                targetScrollY = targetY;
                if (scrollSpringAnimation != null && scrollSpringAnimation.isRunning()) {
                    scrollSpringAnimation.cancel();
                }
                invalidate();
            }
        });
    }
    
    /**
     * Update current playback time.
     * <p>
     * For time-synced (LRC) lyrics: highlights the current line and auto-scrolls to it.
     * <p>
     * For static (plain-text / TXT) lyrics: scrolls the view proportionally from top to
     * bottom based on the current position relative to the total song duration. Call
     * {@link #setDuration} with the song length before calling this method.
     * Manual user scrolling is respected – auto-scroll resumes after
     * {@link #AUTO_SCROLL_DELAY} ms of inactivity.
     */
    public void updateTime(long timeInMillis) {
        if (lrcData == null || lrcData.isEmpty()) {
            return;
        }
        
        // Plain-text lyrics have no timestamps; scroll proportionally through the content.
        if (isStaticMode()) {
            if (!isUserScrolling && isAutoScrollEnabled && durationMs > 0) {
                float maxScroll = getMaxScrollY();
                if (maxScroll > 0) {
                    float fraction = Math.max(0f, Math.min(1f, (float) timeInMillis / (float) durationMs));
                    float targetY = fraction * maxScroll;
                    if (Math.abs(targetY - scrollY) > 1f) {
                        animateScroll(scrollY, targetY);
                    }
                }
            }
            return;
        }
        
        // Apply offset if present
        timeInMillis += lrcData.getOffset();
        
        // Find the current line based on time
        int newLineIndex = findLineIndexByTime(timeInMillis);
        
        if (newLineIndex != currentLineIndex) {
            // Store previous line index
            previousLineIndex = currentLineIndex;
            currentLineIndex = newLineIndex;
            
            // A new line means the active word from the old line is gone — start fresh.
            currentWordIndex = -1;
            wordSyncCurrentLayout = null;
            wordSyncLayoutLineIndex = -1;
            
            // Kick off a smooth color cross-fade between the old and new highlighted lines.
            startHighlightTransition();
            
            // Only auto-scroll if not user scrolling, auto-scroll is enabled, and not from a tap seek
            if (!isUserScrolling && isAutoScrollEnabled && !isTapSeek) {
                scrollToLine(currentLineIndex);
            }
            
            // Reset tap seek flag after processing
            isTapSeek = false;
            
            // Only invalidate once per line change, not on every time update
            invalidate();
        }
        
        // Track the active word within the current line (for word-sync files).
        // This runs on every updateTime call — words change inside the same line, so
        // we can't limit this check to when the line index changes.
        if (!isStaticMode() && currentLineIndex >= 0 && lrcData != null
                && currentLineIndex < lrcData.size()) {
            LrcEntry wordSyncEntry = lrcData.getEntries().get(currentLineIndex);
            if (wordSyncEntry.hasWordSync()) {
                int newWordIdx = findCurrentWordIndex(wordSyncEntry, timeInMillis);
                if (newWordIdx != currentWordIndex) {
                    currentWordIndex = newWordIdx;
                    // Drop the cached layout so it is rebuilt with the fresh coloring.
                    wordSyncCurrentLayout = null;
                    wordSyncLayoutLineIndex = -1;
                    invalidate();
                }
            }
        }
    }
    
    /**
     * Find line index for given time
     */
    private int findLineIndexByTime(long timeInMillis) {
        if (lrcData == null || lrcData.isEmpty()) {
            return -1;
        }
        
        int lineIndex = -1;
        for (int i = 0; i < lrcData.size(); i++) {
            LrcEntry entry = lrcData.getEntries().get(i);
            if (timeInMillis >= entry.getTimeInMillis()) {
                lineIndex = i;
            } else {
                break;
            }
        }
        
        return lineIndex;
    }
    
    /**
     * Find which line was tapped based on Y coordinate
     */
    private int findTappedLineIndex(float touchY) {
        if (lrcData == null || lrcData.isEmpty()) {
            return -1;
        }
        
        float centerY = getHeight() / 2f;
        float offsetY = centerY - scrollY;
        
        for (int i = 0; i < lrcData.size(); i++) {
            float lineY = offsetY + getLineOffset(i);
            
            // Get layout height for this line
            StaticLayout layout = normalLayoutCache.get(i);
            float lineHeight = (layout != null) ? layout.getHeight() : normalTextSize;
            
            float topBound = lineY - (lineHeight / 2f);
            float bottomBound = lineY + (lineHeight / 2f);
            
            if (touchY >= topBound && touchY <= bottomBound) {
                return i;
            }
        }
        
        return -1;
    }
    
    /**
     * Scroll to specific line with animation
     */
    private void scrollToLine(int lineIndex) {
        if (lineIndex < 0 || lrcData == null || lineIndex >= lrcData.size()) {
            return;
        }
        
        targetScrollY = getLineOffset(lineIndex);
        animateScroll(scrollY, targetScrollY);
    }
    
    /**
     * Animate scroll to target position using Spring animation
     */
    private void animateScroll(float from, float to) {
        // Cancel any existing scroll spring animation
        if (scrollSpringAnimation != null && scrollSpringAnimation.isRunning()) {
            scrollSpringAnimation.cancel();
        }
        
        // Also cancel fling if it's running (user interaction)
        if (flingAnimation != null && flingAnimation.isRunning()) {
            flingAnimation.cancel();
        }
        
        // Create a holder for the scroll position
        FloatValueHolder holder = new FloatValueHolder();
        holder.setValue(from);
        
        // Use a relaxed spring so the scroll glides in gently rather than rushing.
        scrollSpringAnimation = new SpringAnimation(holder, holder.getProperty());
        scrollSpringAnimation.setSpring(new SpringForce(to)
                .setStiffness(SCROLL_SPRING_STIFFNESS)
                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
        
        scrollSpringAnimation.addUpdateListener((animation, value, velocity) -> {
            scrollY = value;
            invalidate();
        });
        
        scrollSpringAnimation.addEndListener((animation, canceled, value, velocity) -> {
            if (!canceled) {
                scrollY = to;
                invalidate();
            }
        });
        
        scrollSpringAnimation.start();
    }
    
    @SuppressLint ("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (lrcData == null || lrcData.isEmpty()) {
            return super.onTouchEvent(event);
        }
        
        // Handle ripple effect based on touch events
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            // When parentDismissEnabled the parent (BottomSheet) must not steal our events until
            // we decide to hand back control at the bottom boundary.
            if (parentDismissEnabled && getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            
            // Smoothly remove blur/fade while the finger is touching
            animateBlurTo(1f);
            
            // Find which line was touched and show ripple immediately.
            // In static (plain-text) mode lines are not seekable, so skip the ripple.
            if (!isStaticMode()) {
                int touchedIndex = findTappedLineIndex(event.getY());
                if (touchedIndex >= 0) {
                    tappedLineIndex = touchedIndex;
                    rippleX = event.getX();
                    rippleY = event.getY();
                    
                    // Set hotspot and trigger ripple press state
                    if (rippleDrawable != null) {
                        rippleDrawable.setHotspot(rippleX, rippleY);
                        rippleDrawable.setState(new int[] {android.R.attr.state_pressed, android.R.attr.state_enabled});
                        invalidate();
                    }
                }
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // Smoothly reapply blur/fade once the finger lifts
            animateBlurTo(0f);
            
            // Release ripple state
            if (rippleDrawable != null) {
                rippleDrawable.setState(new int[] {});
                // Clear tapped line after ripple animation completes
                postDelayed(() -> {
                    tappedLineIndex = -1;
                    invalidate();
                }, 600);
            }
            
            // Snap back from overscroll if needed
            snapBackFromOverscroll();
            
            if (isUserScrolling) {
                // Resume auto-scrolling after delay
                postDelayed(autoScrollRunnable, AUTO_SCROLL_DELAY);
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            // If user starts scrolling, cancel the ripple
            if (isUserScrolling && rippleDrawable != null) {
                rippleDrawable.setState(new int[] {});
                tappedLineIndex = -1;
                invalidate();
            }
        }
        
        boolean handled = gestureDetector.onTouchEvent(event);
        
        return handled || super.onTouchEvent(event);
    }
    
    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollY = scroller.getCurrY();
            invalidate();
        }
    }
    
    /**
     * Get maximum scroll Y value
     */
    private float getMaxScrollY() {
        if (lrcData == null || lrcData.isEmpty()) {
            return 0f;
        }
        return getLineOffset(lrcData.size() - 1);
    }
    
    /**
     * When the finger lifts while the content is stretched past its natural boundary,
     * we spring it back smoothly — like a rubber band gently releasing rather than
     * a door slamming shut. Nobody likes abrupt stops.
     */
    private void snapBackFromOverscroll() {
        float maxScroll = getMaxScrollY();
        float targetY = scrollY;

        if (scrollY < 0) {
            targetY = 0;
        } else if (scrollY > maxScroll) {
            targetY = maxScroll;
        }
        
        // Only spring back if we are actually past the valid range.
        if (targetY != scrollY) {
            isInOverscroll = false;
            
            // Cancel any pending fling so they don't fight the spring.
            if (flingAnimation != null && flingAnimation.isRunning()) {
                flingAnimation.cancel();
            }
            if (scrollSpringAnimation != null && scrollSpringAnimation.isRunning()) {
                scrollSpringAnimation.cancel();
            }
            
            // Use a snappy spring so the rubber-band release feels quick but not jarring.
            final float snapTarget = targetY;
            FloatValueHolder holder = new FloatValueHolder();
            holder.setValue(scrollY);
            
            scrollSpringAnimation = new SpringAnimation(holder, holder.getProperty());
            scrollSpringAnimation.setSpring(new SpringForce(snapTarget)
                    .setStiffness(OVERSCROLL_SPRING_STIFFNESS)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
            
            scrollSpringAnimation.addUpdateListener((anim, value, velocity) -> {
                scrollY = value;
                invalidate();
            });
            scrollSpringAnimation.addEndListener((anim, canceled, value, velocity) -> {
                if (!canceled) {
                    scrollY = snapTarget;
                    invalidate();
                }
            });
            scrollSpringAnimation.start();
        }
    }
    
    public void setScrollMultiplier(float multiplier) {
        this.scrollMultiplier = Math.max(0.1f, multiplier); // Ensure positive value
        invalidate();
    }
    
    /**
     * Smoothly animate the blur/fade overlay towards {@code target}.
     * target = 0f → fully blurred (normal idle state, finger lifted)
     * target = 1f → fully unblurred (finger touching the view)
     * <p>
     * Uses a ValueAnimator with DecelerateInterpolator for a slow, natural deceleration.
     * Unblur (finger down) takes ~1500 ms; re-blur (finger up) takes ~800 ms.
     */
    private void animateBlurTo(float target) {
        if (blurAnimator != null && blurAnimator.isRunning()) {
            blurAnimator.cancel();
        }
        
        //
        long duration = 750;
        
        blurAnimator = android.animation.ValueAnimator.ofFloat(blurInterpolation, target);
        blurAnimator.setDuration(duration);
        blurAnimator.setInterpolator(new DecelerateInterpolator());
        blurAnimator.addUpdateListener(anim -> {
            blurInterpolation = (float) anim.getAnimatedValue();
            blurMaskFilters.clear();
            invalidate();
        });
        blurAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                blurInterpolation = target;
                blurMaskFilters.clear();
                invalidate();
            }
        });
        blurAnimator.start();
    }
    
    private float dp2px(Context context, float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }
    
    private float sp2px(Context context, float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                context.getResources().getDisplayMetrics());
    }
    
    /**
     * Update text alignment for paints.
     * Paint.Align only affects canvas.drawText() (used for the empty-text placeholder).
     * StaticLayout.draw() also honors Paint.Align internally, so we always keep it LEFT
     * and handle alignment entirely via canvas translation in calculateXPositionForLayout().
     */
    private void updateTextAlignment() {
        normalPaint.setTextAlign(Paint.Align.LEFT);
        currentPaint.setTextAlign(Paint.Align.LEFT);
    }
    
    // Utility methods
    public void setTextAlignment(Alignment alignment, boolean animate) {
        if (this.textAlignment == alignment) {
            return;
        }
        this.textAlignment = alignment;
        
        // Clear layout caches so new layouts are built with the correct StaticLayout alignment
        normalLayoutCache.clear();
        wordSyncCurrentLayout = null;
        wordSyncLayoutLineIndex = -1;
        
        if (animate) {
            // Animate alignmentFraction toward the target value for a smooth visual shift
            float targetFraction = alignmentFractionFor(alignment);
            if (alignmentAnimator != null && alignmentAnimator.isRunning()) {
                alignmentAnimator.cancel();
            }
            alignmentAnimator = ValueAnimator.ofFloat(alignmentFraction, targetFraction);
            alignmentAnimator.setDuration(350);
            alignmentAnimator.setInterpolator(new DecelerateInterpolator());
            alignmentAnimator.addUpdateListener(anim -> {
                alignmentFraction = (float) anim.getAnimatedValue();
                invalidate();
            });
            alignmentAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    alignmentFraction = targetFraction;
                    invalidate();
                }
            });
            alignmentAnimator.start();
            
        } else {
            // No animation, just update immediately
            alignmentFraction = alignmentFractionFor(alignment);
        }
        
        updateTextAlignment();
        invalidate();
    }
    
    /**
     * Update both normal and current (highlight) text sizes in one atomic call.
     *
     * <p>Clears the layout caches once so that every line's {@link StaticLayout} is rebuilt
     * at the new size on the next draw pass.  The existing per-line height spring-animations
     * then automatically transition the spacing from the old layout heights to the new ones,
     * giving the elastic resize feel without any extra work here.</p>
     *
     * <p>Text-size spring animations are restarted for every visible line so the font itself
     * also scales smoothly rather than snapping.</p>
     *
     * @param normalSizeSp  new normal-line text size in <b>sp</b>
     * @param currentSizeSp new highlighted-line text size in <b>sp</b>
     */
    public void setTextSizes(float normalSizeSp, @SuppressWarnings ("unused") float currentSizeSp) {
        float newNormal = sp2px(getContext(), normalSizeSp);
        
        if (Math.abs(newNormal - normalTextSize) < 0.1f) {
            return;
        }
        
        normalTextSize = newNormal;
        
        // Both paints use the same size — only the color and boldness differ.
        normalPaint.setTextSize(normalTextSize);
        currentPaint.setTextSize(normalTextSize);
        
        // Clear the layout cache so every line gets remeasured at the new size.
        normalLayoutCache.clear();
        wordSyncCurrentLayout = null;
        wordSyncLayoutLineIndex = -1;
        
        invalidate();
    }
    
    public void setNormalTextSize(float size) {
        setTextSizes(size, size);
    }
    
    // Public API
    
    public void setCurrentTextSize(float size) {
        setTextSizes(size, size);
    }
    
    public void setLineSpacing(float spacing) {
        this.lineSpacing = dp2px(getContext(), spacing);
        invalidate();
    }
    
    public void setNormalTextColor(@ColorInt int color) {
        this.normalTextColor = color;
        normalPaint.setColor(color);
        invalidate();
    }
    
    public void setCurrentTextColor(@ColorInt int color) {
        this.currentTextColor = color;
        currentPaint.setColor(color);
        invalidate();
    }
    
    public void setEmptyText(String text) {
        this.emptyText = text;
        invalidate();
    }
    
    public void setFadeEnabled(boolean enabled) {
        this.enableFade = enabled;
        invalidate();
    }
    
    public void setFadeLength(float lengthInDp) {
        this.fadeLength = dp2px(getContext(), lengthInDp);
        invalidate();
    }
    
    public void setMaxOverscrollDistance(float distanceInDp) {
        this.maxOverscrollDistance = dp2px(getContext(), distanceInDp);
        invalidate();
    }
    
    /**
     * Enable/disable yielding touch control to the parent when the user scrolls past the
     * bottom of the lyrics content.  Enable this inside a BottomSheetDialog so the sheet can
     * be dragged to dismiss after the user reaches the end of the lyrics.  Leave disabled (the
     * default) inside a plain Fragment where there is no dismissible parent.
     */
    public void setParentDismissEnabled(boolean enabled) {
        this.parentDismissEnabled = enabled;
    }
    
    /**
     * FloatValueHolder for Spring and Fling animations
     * Uses FloatPropertyCompat for proper DynamicAnimation support
     */
    private static class FloatValueHolder {
        private final FloatPropertyCompat <FloatValueHolder> property;
        private float value;
        
        FloatValueHolder() {
            property = new FloatPropertyCompat <>("scrollY") {
                @Override
                public float getValue(FloatValueHolder object) {
                    return object.value;
                }
                
                @Override
                public void setValue(FloatValueHolder object, float value) {
                    object.value = value;
                }
            };
        }
        
        float getValue() {
            return value;
        }
        
        void setValue(float value) {
            this.value = value;
        }
        
        FloatPropertyCompat <FloatValueHolder> getProperty() {
            return property;
        }
    }
    
    public void setAutoScrollEnabled(boolean enabled) {
        this.isAutoScrollEnabled = enabled;
    }
    
    /**
     * Set the total song duration in milliseconds.
     * This is used in static (plain-text / TXT) mode to drive proportional auto-scrolling.
     * Call this whenever a new song is loaded, before (or immediately after) calling
     * {@link #setLrcData(LrcData)}.
     *
     * @param durationMs song duration in milliseconds (0 disables static auto-scroll)
     */
    public void setDuration(long durationMs) {
        this.durationMs = durationMs;
    }
    
    public void reset() {
        this.lrcData = null;
        this.currentLineIndex = -1;
        this.scrollY = 0f;
        this.targetScrollY = 0f;
        this.isUserScrolling = false;
        this.durationMs = 0L;
        this.normalLayoutCache.clear();
        this.blurMaskFilters.clear();
        this.currentWordIndex = -1;
        this.wordSyncCurrentLayout = null;
        this.wordSyncLayoutLineIndex = -1;
        
        removeCallbacks(autoScrollRunnable);
        if (highlightAnimator != null && highlightAnimator.isRunning()) {
            highlightAnimator.cancel();
        }
        if (dehighlightAnimator != null && dehighlightAnimator.isRunning()) {
            dehighlightAnimator.cancel();
        }
        highlightFraction = 0f;
        dehighlightFraction = 0f;
        if (blurAnimator != null && blurAnimator.isRunning()) {
            blurAnimator.cancel();
        }
        blurInterpolation = 0f;
        
        // Cancel and clear curtain animations
        for (android.animation.Animator anim : curtainAnimators) {
            if (anim != null && anim.isRunning()) {
                anim.cancel();
            }
        }
        curtainAnimators.clear();
        curtainBlur.clear();
        curtainScale.clear();
        curtainAlpha.clear();
        
        if (scrollSpringAnimation != null && scrollSpringAnimation.isRunning()) {
            scrollSpringAnimation.cancel();
        }
        if (flingAnimation != null && flingAnimation.isRunning()) {
            flingAnimation.cancel();
        }
        invalidate();
    }
    
    public void setOnLrcClickListener(OnLrcClickListener listener) {
        this.onLrcClickListener = listener;
    }
    
    @Override
    public void onAccentChanged(@NonNull Accent accent) {
        ThemeChangedListener.super.onAccentChanged(accent);
        updateColorsFromAccent(accent);
    }
    
    @Override
    public void onThemeChanged(@NonNull Theme theme, boolean animate) {
        ThemeChangedListener.super.onThemeChanged(theme, animate);
        updateColorsFromTheme(theme);
    }
    
    private void updateColorsFromTheme(Theme theme) {
        setNormalTextColor(theme.getTextViewTheme().getTertiaryTextColor());
    }
    
    private void updateColorsFromAccent(Accent accent) {
        setCurrentTextColor(accent.getPrimaryAccentColor());
        
        // Update ripple color
        if (rippleDrawable != null) {
            rippleDrawable.setRippleColor(accent.getPrimaryAccentColor());
        }
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isInEditMode()) {
            ThemeManager.INSTANCE.addListener(this);
        }
    }
    
    @Override
    protected boolean verifyDrawable(@NonNull android.graphics.drawable.Drawable who) {
        return who == rippleDrawable || super.verifyDrawable(who);
    }
    
    @Override
    public void invalidateDrawable(@NonNull android.graphics.drawable.Drawable drawable) {
        if (drawable == rippleDrawable) {
            invalidate();
        } else {
            super.invalidateDrawable(drawable);
        }
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (!isInEditMode()) {
            ThemeManager.INSTANCE.removeListener(this);
        }
    }
    
    // Alignment options
    public enum Alignment {
        LEFT, CENTER, RIGHT
    }
    
    /**
     * Callback interface for LRC click events
     */
    public interface OnLrcClickListener {
        void onLrcClick(long timeInMillis, String text);
    }
    
    /**
     * Gesture listener for touch events
     */
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        
        @Override
        public boolean onDown(@NonNull MotionEvent e) {
            removeCallbacks(autoScrollRunnable);
            
            // Reset user scrolling flag
            isUserScrolling = false;
            
            // Immediately cancel all animations
            if (scrollSpringAnimation != null && scrollSpringAnimation.isRunning()) {
                scrollSpringAnimation.cancel();
            }
            if (flingAnimation != null && flingAnimation.isRunning()) {
                flingAnimation.cancel();
            }
            if (!scroller.isFinished()) {
                scroller.abortAnimation();
            }
            
            // Reset overscroll state
            isInOverscroll = false;
            
            return true;
        }
        
        @Override
        public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
            isUserScrolling = true;
            
            // Cancel any ongoing fling animations
            if (flingAnimation != null && flingAnimation.isRunning()) {
                flingAnimation.cancel();
            }
            
            // Get max scroll bounds
            float maxScroll = getMaxScrollY();
            
            // When parentDismissEnabled: hand control back to parent (BottomSheet) once the
            // user has scrolled to the very bottom and keeps dragging downward (distanceY < 0
            // means finger moving up → content scrolling down / sheet dragging down to dismiss).
            if (parentDismissEnabled && getParent() != null) {
                boolean atBottom = scrollY >= maxScroll;
                boolean draggingPastBottom = distanceY < 0; // negative = finger moving up = scroll-down
                if (atBottom && draggingPastBottom) {
                    // Release the parent so BottomSheetBehavior can take over and dismiss
                    getParent().requestDisallowInterceptTouchEvent(false);
                    return false; // Don't consume; let the parent handle it
                } else {
                    // Re-claim the touch so we can scroll freely
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
            }
            
            // Apply scroll multiplier for base scrolling
            float acceleratedDistance = distanceY * scrollMultiplier;
            
            // Calculate where we would be after applying the distance
            float newScrollY = scrollY + acceleratedDistance;
            
            // Apply progressive resistance when trying to scroll beyond bounds
            if (newScrollY < 0) {
                // Trying to overscroll at top
                float currentOverscroll = Math.abs(scrollY); // How far we already are in overscroll
                float resistance = calculateDragResistance(currentOverscroll);
                acceleratedDistance *= resistance; // Apply decay to the drag movement itself
                isInOverscroll = true;
            } else if (newScrollY > maxScroll) {
                // Trying to overscroll at bottom
                float currentOverscroll = scrollY - maxScroll; // How far we already are in overscroll
                if (currentOverscroll < 0) {
                    currentOverscroll = 0; // Just crossing the boundary
                }
                float resistance = calculateDragResistance(currentOverscroll);
                acceleratedDistance *= resistance; // Apply decay to the drag movement itself
                isInOverscroll = true;
            } else {
                // Within normal bounds - no resistance
                isInOverscroll = false;
            }
            
            // Apply the resistance-adjusted distance
            scrollY += acceleratedDistance;
            
            // Hard cap at maximum overscroll distance (but don't jump there)
            if (scrollY < -maxOverscrollDistance) {
                scrollY = -maxOverscrollDistance;
            } else if (scrollY > maxScroll + maxOverscrollDistance) {
                scrollY = maxScroll + maxOverscrollDistance;
            }
            
            invalidate();
            return true;
        }
        
        /**
         * Calculate progressive drag resistance based on current overscroll distance
         * Returns a multiplier between 0 and 1 that decreases as overscroll increases
         */
        private float calculateDragResistance(float currentOverscroll) {
            // Use exponential decay -> resistance = e^(-k * distance)
            // This creates smooth, progressive resistance that gets stronger as you drag further
            float k = 0.004f; // Decay rate - higher = faster resistance increase
            float resistance = (float) Math.exp(-k * currentOverscroll);
            
            // Ensure minimum resistance of 5% to prevent complete lockup
            return Math.max(0.05f, resistance);
        }
        
        @Override
        public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
            // Cancel any existing fling before starting a fresh one.
            if (flingAnimation != null && flingAnimation.isRunning()) {
                flingAnimation.cancel();
            }
            
            // Apply scroll multiplier to fling velocity.
            float acceleratedVelocity = -velocityY * scrollMultiplier;
            
            float maxScroll = getMaxScrollY();
            // Use the full drag-overscroll range as fling bounds so that a fling started
            // while the content is already stretched (e.g. user swiped past the edge then
            // flicked) never puts the starting value outside the allowed range — that would
            // cause an IllegalArgumentException inside DynamicAnimation.
            float flingBoundExtra = maxOverscrollDistance;
            float flingMin = -flingBoundExtra;
            float flingMax = maxScroll + flingBoundExtra;
            
            // Make sure scrollY itself is inside the bounds we're about to hand the animator.
            // It could be just slightly past them due to floating-point drift during dragging.
            float clampedStart = Math.max(flingMin, Math.min(flingMax, scrollY));
            scrollY = clampedStart;

            FloatValueHolder holder = new FloatValueHolder();
            holder.setValue(clampedStart);

            flingAnimation = new FlingAnimation(holder, holder.getProperty());
            flingAnimation.setStartVelocity(acceleratedVelocity);
            flingAnimation.setFriction(FLING_FRICTION);
            
            flingAnimation.setMinValue(flingMin);
            flingAnimation.setMaxValue(flingMax);

            flingAnimation.addUpdateListener((animation, value, velocity) -> {
                scrollY = value;
                invalidate();
            });
            
            flingAnimation.addEndListener((animation, canceled, value, velocity) -> {
                if (!canceled) {
                    // If the fling coasted into overscroll territory, spring it back gently.
                    if (scrollY < 0 || scrollY > getMaxScrollY()) {
                        isInOverscroll = true;
                        snapBackFromOverscroll();
                    } else {
                        isInOverscroll = false;
                    }
                }
            });
            
            flingAnimation.start();
            return true;
        }
        
        @Override
        public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
            // Plain-text lyrics are not seekable — ignore taps entirely.
            if (isStaticMode()) {
                return true;
            }
            
            // Find which line was tapped
            int tappedIndex = findTappedLineIndex(e.getY());
            
            if (tappedIndex >= 0 && lrcData != null && tappedIndex < lrcData.size()) {
                // Get the entry for the tapped line
                LrcEntry entry = lrcData.getEntries().get(tappedIndex);
                
                // Set flag to prevent auto-scroll on the next updateTime call
                isTapSeek = true;
                
                // Notify listener to seek
                if (onLrcClickListener != null) {
                    onLrcClickListener.onLrcClick(entry.getTimeInMillis(), entry.getText());
                }
                
                return true;
            }
            
            // Fallback to old behavior if no specific line was tapped
            if (onLrcClickListener != null && currentLineIndex >= 0 && lrcData != null) {
                LrcEntry entry = lrcData.getEntries().get(currentLineIndex);
                isTapSeek = true;
                onLrcClickListener.onLrcClick(entry.getTimeInMillis(), entry.getText());
            }
            return true;
        }
    }
}
