package app.simple.felicity.decorations.lrc.view;

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
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;

import java.util.HashMap;

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
import app.simple.felicity.decorations.lrc.parser.TxtParser;
import app.simple.felicity.decorations.ripple.FelicityRippleDrawable;
import app.simple.felicity.decorations.typeface.TypeFace;
import app.simple.felicity.preferences.AppearancePreferences;
import app.simple.felicity.theme.interfaces.ThemeChangedListener;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.models.Accent;
import app.simple.felicity.theme.themes.Theme;

/**
 * Modern LRC lyrics view with center prominence and smooth scrolling
 */
public class ModernLrcView extends View implements ThemeChangedListener {
    
    // Default values
    private static final float DEFAULT_TEXT_SIZE = 22f; // sp
    private static final float DEFAULT_CURRENT_TEXT_SIZE = 28f; // sp
    private static final float DEFAULT_LINE_SPACING = 16f; // dp
    public static final float DEFAULT_FADE_LENGTH = 140f; // dp - length of vertical fade
    private static final float DEFAULT_SCROLL_MULTIPLIER = 1f; // Accelerated scroll multiplier
    private static final float DEFAULT_OVERSCROLL_DISTANCE = 250f; // dp - maximum overscroll distance
    private static final float OVERSCROLL_DAMPING = 0.25f; // Rubber band damping factor (0-1)
    private static final float SPRING_STIFFNESS = SpringForce.STIFFNESS_LOW; // Spring stiffness for overscroll
    private static final float SPRING_DAMPING_RATIO = SpringForce.DAMPING_RATIO_NO_BOUNCY; // Spring damping
    private static final float FLING_FRICTION = 1.5f; // Friction for fling animation
    private static final float TEXT_SIZE_SPRING_STIFFNESS = SpringForce.STIFFNESS_LOW; // Text size animation stiffness
    private static final float TEXT_SIZE_SPRING_DAMPING = SpringForce.DAMPING_RATIO_NO_BOUNCY; // Text size animation damping
    private static final float MAX_BLUR_RADIUS = 15f; // Maximum blur radius at edges (in pixels)
    private static final int DEFAULT_NORMAL_COLOR = Color.GRAY;
    private static final int DEFAULT_CURRENT_COLOR = Color.WHITE;
    private static final String DEFAULT_EMPTY_TEXT = "No lyrics";
    private static final long AUTO_SCROLL_DELAY = 3000; // 3 seconds after manual scroll
    // Data
    private LrcData lrcData;
    private int currentLineIndex = -1;
    
    // Cache for wrapped text layouts
    private final HashMap<Integer, StaticLayout> layoutCache = new HashMap<>();
    
    // Cache for wrapped text layouts at normal size
    private final HashMap<Integer, StaticLayout> normalLayoutCache = new HashMap<>();
    
    // Cache for wrapped text layouts at current (highlighted) size
    private final HashMap<Integer, StaticLayout> currentLayoutCache = new HashMap<>();
    
    // Cache for layout heights (to properly calculate spacing)
    private final HashMap<Integer, Float> layoutHeights = new HashMap<>();
    
    // Cache for pre-highlight heights (to restore original state)
    private final HashMap<Integer, Float> preHighlightHeights = new HashMap<>();
    
    // Cache for animated heights (smoothly interpolated values for positioning)
    private final HashMap <Integer, Float> animatedHeights = new HashMap <>();
    
    // Height animations
    private final HashMap <Integer, SpringAnimation> heightAnimations = new HashMap <>();
    
    // Text size animation
    private final java.util.HashMap <Integer, Float> animatedTextSizes = new java.util.HashMap <>();
    // Paint objects
    private TextPaint normalPaint;
    private TextPaint currentPaint;
    private Paint fadePaint;
    // Cache for blur mask filters to avoid constant recreation
    private final HashMap <Float, BlurMaskFilter> blurMaskFilters = new HashMap <>();
    // Styling properties
    private float normalTextSize;
    private float currentTextSize;
    private float lineSpacing;
    private int normalTextColor;
    private int currentTextColor;
    private Alignment textAlignment = Alignment.LEFT;
    private String emptyText;
    private float fadeLength;
    private boolean enableFade = true;
    private final HashMap <Integer, SpringAnimation> textSizeAnimations = new java.util.HashMap <>();
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
    private SpringAnimation springAnimation; // For overscroll snap-back
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
    
    public ModernLrcView(Context context) {
        this(context, null);
    }
    
    public ModernLrcView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public ModernLrcView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }
    
    private void init(Context context, @Nullable AttributeSet attrs) {
        // Initialize default values
        normalTextSize = sp2px(context, DEFAULT_TEXT_SIZE);
        currentTextSize = sp2px(context, DEFAULT_CURRENT_TEXT_SIZE);
        lineSpacing = dp2px(context, DEFAULT_LINE_SPACING);
        fadeLength = dp2px(context, DEFAULT_FADE_LENGTH);
        scrollMultiplier = DEFAULT_SCROLL_MULTIPLIER;
        maxOverscrollDistance = dp2px(context, DEFAULT_OVERSCROLL_DISTANCE);
        normalTextColor = DEFAULT_NORMAL_COLOR;
        currentTextColor = DEFAULT_CURRENT_COLOR;
        emptyText = DEFAULT_EMPTY_TEXT;
        
        // Read attributes if provided
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ModernLrcView);
            try {
                normalTextSize = a.getDimension(R.styleable.ModernLrcView_lrcTextSize, normalTextSize);
                normalTextColor = a.getColor(R.styleable.ModernLrcView_lrcNormalTextColor, normalTextColor);
                currentTextColor = a.getColor(R.styleable.ModernLrcView_lrcCurrentTextColor, currentTextColor);
            } finally {
                a.recycle();
            }
        }
        
        // Initialize paints
        normalPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        normalPaint.setTextSize(normalTextSize);
        normalPaint.setColor(normalTextColor);
        if (!isInEditMode()) {
            normalPaint.setTypeface(TypeFace.INSTANCE.getRegularTypeFace(context));
        }
        
        currentPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        currentPaint.setTextSize(currentTextSize);
        currentPaint.setColor(currentTextColor);
        currentPaint.setFakeBoldText(true);
        if (!isInEditMode()) {
            currentPaint.setTypeface(TypeFace.INSTANCE.getMediumTypeFace(context));
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
    
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        
        if (lrcData == null || lrcData.isEmpty()) {
            drawEmptyText(canvas);
            return;
        }
        
        drawLyrics(canvas);
    }
    
    private void drawEmptyText(Canvas canvas) {
        // Save current alignment
        Paint.Align savedAlign = normalPaint.getTextAlign();
        
        // Temporarily set to center for empty text
        normalPaint.setTextAlign(Paint.Align.CENTER);
        float x = getWidth() / 2f;
        float y = getHeight() / 2f - ((normalPaint.descent() + normalPaint.ascent()) / 2);
        canvas.drawText(emptyText, x, y, normalPaint);
        
        // Restore original alignment
        normalPaint.setTextAlign(savedAlign);
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
                getHeight()
                       );
        
        for (int i = 0; i < entryCount; i++) {
            LrcEntry entry = lrcData.getEntries().get(i);
            String text = entry.getText();
            
            // Get animated text size for this line
            float animatedSize = getAnimatedTextSize(i);
            
            // Calculate Y position for this line
            float y = offsetY + getLineOffset(i);
            
            // Choose paint and set animated size
            TextPaint paint;
            // Don't highlight empty lines; in static mode all lines use the normal paint
            if (!isStaticMode() && i == currentLineIndex && text != null && !text.trim().isEmpty()) {
                paint = currentPaint;
            } else {
                paint = normalPaint;
            }
            
            // Apply animated text size
            paint.setTextSize(animatedSize);
            
            // Get or create StaticLayout for this line
            StaticLayout layout = getOrCreateLayout(text, paint, i);
            
            // Skip lines that are completely off-screen (accounting for multi-line height)
            float lineHeight = layout.getHeight();
            if (y < -lineHeight || y > getHeight() + lineHeight) {
                continue;
            }
            
            // Calculate and apply blur based on distance from center (proportional to fade)
            if (enableFade) {
                float blurAmount = calculateBlurAmount(y, getHeight());
                if (blurAmount > 0.5f) { // Only apply blur if significant
                    // Round to nearest 0.5 to reduce unique filter instances
                    float roundedBlur = Math.round(blurAmount * 2f) / 2f;
                    
                    // Get or create cached blur filter
                    BlurMaskFilter blurFilter = blurMaskFilters.get(roundedBlur);
                    if (blurFilter == null) {
                        blurFilter = new BlurMaskFilter(roundedBlur, BlurMaskFilter.Blur.NORMAL);
                        blurMaskFilters.put(roundedBlur, blurFilter);
                    }
                    paint.setMaskFilter(blurFilter);
                } else {
                    paint.setMaskFilter(null);
                }
            } else {
                paint.setMaskFilter(null);
            }
            
            // Draw ripple effect for tapped line (behind the text)
            if (i == tappedLineIndex && rippleDrawable != null) {
                float yOffset = y - (lineHeight / 2f);
                int paddingLeft = getPaddingLeft();
                int paddingRight = getPaddingRight();
                int availableWidth = getWidth() - paddingLeft - paddingRight;
                
                // Set ripple bounds to cover the full width of the text area
                rippleDrawable.setBounds(
                        paddingLeft,
                        (int) yOffset,
                        paddingLeft + availableWidth,
                        (int) (yOffset + lineHeight)
                                        );
                rippleDrawable.draw(canvas);
            }
            
            // Draw the wrapped text using StaticLayout
            canvas.save();
            
            // Calculate X position for the layout based on alignment
            float x = calculateXPositionForLayout(layout, paint);
            
            // Position the text vertically (centered on y position)
            float yOffset = y - (lineHeight / 2f);
            canvas.translate(x, yOffset);
            
            layout.draw(canvas);
            canvas.restore();
        }
        
        // Clear mask filters
        normalPaint.setMaskFilter(null);
        currentPaint.setMaskFilter(null);
        
        // Restore original paint sizes
        normalPaint.setTextSize(normalTextSize);
        currentPaint.setTextSize(currentTextSize);
        
        // Restore canvas state
        canvas.restore();
        
        // Apply vertical fade effect
        if (enableFade && fadeLength > 0) {
            drawVerticalFade(canvas);
            canvas.restoreToCount(layerId);
        }
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
     * Get animated text size for a line
     */
    private float getAnimatedTextSize(int lineIndex) {
        // In static (plain-text) mode, all lines have the same normal size.
        if (isStaticMode()) {
            return normalTextSize;
        }

        // Return animated size if it exists, otherwise return default size
        Float animatedSize = animatedTextSizes.get(lineIndex);
        if (animatedSize != null) {
            return animatedSize;
        }
        
        // Check if this line is empty - empty lines should never be highlighted
        if (lrcData != null && lineIndex >= 0 && lineIndex < lrcData.size()) {
            LrcEntry entry = lrcData.getEntries().get(lineIndex);
            String text = entry.getText();
            if (text == null || text.trim().isEmpty()) {
                return normalTextSize;
            }
        }
        
        // No animation in progress, return static size based on current state
        // Only the current line should be highlighted
        if (lineIndex == currentLineIndex) {
            return currentTextSize;
        }
        
        // For all other cases, return normal size
        return normalTextSize;
    }
    
    /**
     * Animate text size change for a line
     */
    private void animateTextSize(int lineIndex, float targetSize) {
        // Get current size (either animated or default)
        float currentSize = getAnimatedTextSize(lineIndex);
        
        // Skip animation if we're already at the target size
        if (Math.abs(currentSize - targetSize) < 0.1f) {
            animatedTextSizes.put(lineIndex, targetSize);
            return;
        }
        
        // Cancel any existing animation for this line
        SpringAnimation existingAnimation = textSizeAnimations.get(lineIndex);
        if (existingAnimation != null && existingAnimation.isRunning()) {
            existingAnimation.cancel();
        }
        
        // Create holder for text size animation
        FloatValueHolder holder = new FloatValueHolder();
        holder.setValue(currentSize);
        
        // Create spring animation for text size
        SpringAnimation animation = new SpringAnimation(holder, holder.getProperty());
        animation.setSpring(new SpringForce(targetSize)
                .setStiffness(TEXT_SIZE_SPRING_STIFFNESS)
                .setDampingRatio(TEXT_SIZE_SPRING_DAMPING));
        
        animation.addUpdateListener((anim, value, velocity) -> {
            animatedTextSizes.put(lineIndex, value);
            invalidate();
        });
        
        animation.addEndListener((anim, canceled, value, velocity) -> {
            // Always set final value and clean up
            animatedTextSizes.put(lineIndex, targetSize);
            textSizeAnimations.remove(lineIndex);
            
            // If we're animating to normal size, and we're done, remove from cache entirely
            // This ensures fresh state next time
            if (Math.abs(targetSize - normalTextSize) < 0.1f && lineIndex != currentLineIndex) {
                animatedTextSizes.remove(lineIndex);
            }
            
            invalidate();
        });
        
        // Store animation reference
        textSizeAnimations.put(lineIndex, animation);
        animation.start();
    }
    
    /**
     * Animate height change smoothly
     */
    private void animateHeight(int lineIndex, float fromHeight, float toHeight) {
        // Cancel any existing height animation for this line
        SpringAnimation existingAnimation = heightAnimations.get(lineIndex);
        if (existingAnimation != null && existingAnimation.isRunning()) {
            existingAnimation.cancel();
        }
        
        // Create holder for height animation
        FloatValueHolder holder = new FloatValueHolder();
        holder.setValue(fromHeight);
        
        // Create spring animation for height with very low stiffness for smooth, slow transition
        SpringAnimation animation = new SpringAnimation(holder, holder.getProperty());
        animation.setSpring(new SpringForce(toHeight)
                .setStiffness(SpringForce.STIFFNESS_VERY_LOW) // Very slow, smooth transition
                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
        
        animation.addUpdateListener((anim, value, velocity) -> {
            // Update animated height - this affects positioning
            animatedHeights.put(lineIndex, value);
            invalidate();
        });
        
        animation.addEndListener((anim, canceled, value, velocity) -> {
            animatedHeights.put(lineIndex, toHeight);
            heightAnimations.remove(lineIndex);
            invalidate();
        });
        
        // Store animation reference
        heightAnimations.put(lineIndex, animation);
        animation.start();
    }
    
    /**
     * Calculate cumulative Y offset for a given line
     * Uses animated heights for smooth positioning
     */
    private float getLineOffset(int lineIndex) {
        float offset = 0f;
        
        for (int i = 0; i < lineIndex; i++) {
            // Use animated height for smooth positioning
            Float animHeight = animatedHeights.get(i);
            if (animHeight != null) {
                offset += animHeight + lineSpacing;
            } else {
                // Fallback to cached height or text size
                Float cachedHeight = layoutHeights.get(i);
                if (cachedHeight != null) {
                    offset += cachedHeight + lineSpacing;
                } else {
                    // Ultimate fallback to text size if height not cached yet
                    if (i == currentLineIndex) {
                        offset += currentTextSize + lineSpacing;
                    } else {
                        offset += normalTextSize + lineSpacing;
                    }
                }
            }
        }
        
        // Add half height of current line to center it
        Float animCurrentHeight = animatedHeights.get(lineIndex);
        if (animCurrentHeight != null) {
            offset += animCurrentHeight / 2f;
        } else {
            Float currentHeight = layoutHeights.get(lineIndex);
            if (currentHeight != null) {
                offset += currentHeight / 2f;
            } else {
                // Fallback to text size
                if (lineIndex == currentLineIndex) {
                    offset += currentTextSize / 2f;
                } else {
                    offset += normalTextSize / 2f;
                }
            }
        }
        
        return offset;
    }
    
    /**
     * Get or create a StaticLayout for wrapped text
     * Uses cached layouts at fixed sizes to avoid constant recreation
     */
    @SuppressWarnings("unused")
    private StaticLayout getOrCreateLayout(String text, TextPaint paint, int lineIndex) {
        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();
        int availableWidth = getWidth() - paddingLeft - paddingRight;
        
        Layout.Alignment alignment = switch (textAlignment) {
            case LEFT -> Layout.Alignment.ALIGN_NORMAL;
            case RIGHT -> Layout.Alignment.ALIGN_OPPOSITE;
            default -> Layout.Alignment.ALIGN_CENTER;
        };
        
        // Determine which size we're rendering at by checking the paint's actual size
        float currentPaintSize = paint.getTextSize();
        boolean isUsingCurrentSize = Math.abs(currentPaintSize - currentTextSize) < 1f;
        
        // Try to use cached layout for the correct size
        HashMap<Integer, StaticLayout> cache = isUsingCurrentSize ? currentLayoutCache : normalLayoutCache;
        StaticLayout cachedLayout = cache.get(lineIndex);
        
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
            // Text fits on one line - create single-line layout
            layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, availableWidth)
                    .setAlignment(alignment)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)
                    .setMaxLines(1)
                    .build();
        } else {
            // Text needs wrapping - create multi-line layout
            layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, availableWidth)
                    .setAlignment(alignment)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)
                    .build();
        }
        
        // Cache it
        cache.put(lineIndex, layout);
        
        // Update height tracking
        float newHeight = (float) layout.getHeight();
        Float previousHeight = layoutHeights.get(lineIndex);
        
        // If layoutHeights was cleared, but we still have an animated height, use that as the "previous"
        if (previousHeight == null) {
            previousHeight = animatedHeights.get(lineIndex);
        }
        
        if (previousHeight != null && Math.abs(previousHeight - newHeight) > 1f) {
            // Height changed - animate the transition
            animateHeight(lineIndex, previousHeight, newHeight);
        } else if (previousHeight == null) {
            // First time - set directly without animation
            animatedHeights.put(lineIndex, newHeight);
        } else {
            // Height hasn't changed significantly
            animatedHeights.put(lineIndex, newHeight);
        }
        
        // Update cached height
        layoutHeights.put(lineIndex, newHeight);
        
        return layout;
    }
    
    /**
     * Calculate X position for StaticLayout based on alignment
     */
    @SuppressWarnings ("unused")
    private float calculateXPositionForLayout(StaticLayout layout, TextPaint paint) {
        // StaticLayout handles alignment internally, so we just position it at the left padding
        return getPaddingLeft();
    }
    
    /**
     * Update text alignment for paints
     */
    private void updateTextAlignment() {
        Paint.Align align = switch (textAlignment) {
            case LEFT ->
                    Paint.Align.LEFT;
            case RIGHT ->
                    Paint.Align.RIGHT;
            default ->
                    Paint.Align.CENTER;
        };
        normalPaint.setTextAlign(align);
        currentPaint.setTextAlign(align);
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
        return lrcData.getEntries().get(0).getTimeInMillis() == TxtParser.NO_TIMESTAMP;
    }

    /**
     * Set lyrics data
     */
    public void setLrcData(LrcData data) {
        this.lrcData = data;
        this.currentLineIndex = -1;
        this.scrollY = 0f;
        this.targetScrollY = 0f;
        this.layoutCache.clear();
        this.normalLayoutCache.clear();
        this.currentLayoutCache.clear();
        this.layoutHeights.clear();
        this.preHighlightHeights.clear();
        this.animatedHeights.clear();
        this.animatedTextSizes.clear();
        this.blurMaskFilters.clear();
        
        // Cancel all height animations - create a copy to avoid ConcurrentModificationException
        for (SpringAnimation animation : new java.util.ArrayList <>(heightAnimations.values())) {
            if (animation != null && animation.isRunning()) {
                animation.cancel();
            }
        }
        heightAnimations.clear();
        
        // Cancel all text size animations - create a copy to avoid ConcurrentModificationException
        for (SpringAnimation animation : new java.util.ArrayList <>(textSizeAnimations.values())) {
            if (animation != null && animation.isRunning()) {
                animation.cancel();
            }
        }
        textSizeAnimations.clear();
        
        invalidate();
    }
    
    /**
     * Update current playback time.
     * This is a no-op when the view is in static (plain-text) mode.
     */
    public void updateTime(long timeInMillis) {
        if (lrcData == null || lrcData.isEmpty()) {
            return;
        }
        
        // Plain-text lyrics have no timestamps; skip time-driven logic entirely.
        if (isStaticMode()) {
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
            
            // Animate text size changes
            if (previousLineIndex >= 0 && previousLineIndex < lrcData.size()) {
                // Animate previous line to normal size
                animateTextSize(previousLineIndex, normalTextSize);
                // Clear cached layouts for the previous line to force recalculation at new size
                normalLayoutCache.remove(previousLineIndex);
                currentLayoutCache.remove(previousLineIndex);
            }
            
            if (currentLineIndex >= 0 && currentLineIndex < lrcData.size()) {
                // Animate current line to large size (only if not empty)
                LrcEntry currentEntry = lrcData.getEntries().get(currentLineIndex);
                String currentText = currentEntry.getText();
                if (currentText != null && !currentText.trim().isEmpty()) {
                    animateTextSize(currentLineIndex, currentTextSize);
                } else {
                    // For empty lines, keep normal text size
                    animateTextSize(currentLineIndex, normalTextSize);
                }
                // Clear cached layouts for the current line to force recalculation at new size
                normalLayoutCache.remove(currentLineIndex);
                currentLayoutCache.remove(currentLineIndex);
            }
            
            // Only auto-scroll if not user scrolling, auto-scroll is enabled, and not from a tap seek
            if (!isUserScrolling && isAutoScrollEnabled && !isTapSeek) {
                scrollToLine(currentLineIndex);
            }
            
            // Reset tap seek flag after processing
            isTapSeek = false;
            
            // Only invalidate once per line change, not on every time update
            invalidate();
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
            Float cachedHeight = layoutHeights.get(i);
            float lineHeight = cachedHeight != null ? cachedHeight :
                    (i == currentLineIndex ? currentTextSize : normalTextSize);
            
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
        
        // Also cancel spring/fling if they're running (user interaction)
        if (springAnimation != null && springAnimation.isRunning()) {
            springAnimation.cancel();
        }
        if (flingAnimation != null && flingAnimation.isRunning()) {
            flingAnimation.cancel();
        }
        
        // Create a holder for the scroll position
        FloatValueHolder holder = new FloatValueHolder();
        holder.setValue(from);
        
        // Use Spring animation for natural, smooth scrolling
        scrollSpringAnimation = new SpringAnimation(holder, holder.getProperty());
        scrollSpringAnimation.setSpring(new SpringForce(to)
                .setStiffness(SpringForce.STIFFNESS_LOW) // Smooth, gentle scroll
                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)); // No bounce for auto-scroll
        
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

            // Find which line was touched and show ripple immediately
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
     * Apply rubber band damping to overscroll distance
     * Uses a logarithmic damping curve for realistic feel
     */
    private float applyOverscrollDamping(float overscroll) {
        // Use a damping formula: dampedDistance = maxDistance * (1 - e^(-overscroll / dampingFactor))
        // This creates a logarithmic curve that asymptotically approaches maxOverscrollDistance
        float dampingFactor = maxOverscrollDistance * OVERSCROLL_DAMPING;
        return maxOverscrollDistance * (1f - (float) Math.exp(-overscroll / dampingFactor));
    }
    
    /**
     * Snap back from overscroll to valid bounds using Spring animation
     */
    private void snapBackFromOverscroll() {
        float maxScroll = getMaxScrollY();
        float targetY = scrollY;
        
        if (scrollY < 0) {
            targetY = 0;
        } else if (scrollY > maxScroll) {
            targetY = maxScroll;
        }
        
        // Only animate if we're actually in overscroll
        if (targetY != scrollY) {
            isInOverscroll = false;
            
            // Cancel any existing animations
            if (springAnimation != null) {
                springAnimation.cancel();
            }
            if (flingAnimation != null) {
                flingAnimation.cancel();
            }
            
            final float finalTargetY = targetY;
            
            // Create spring animation for natural bounce-back using FloatPropertyCompat
            FloatValueHolder holder = new FloatValueHolder();
            holder.setValue(scrollY);
            
            springAnimation = new SpringAnimation(holder, holder.getProperty());
            springAnimation.setSpring(new SpringForce(finalTargetY)
                    .setStiffness(SPRING_STIFFNESS)
                    .setDampingRatio(SPRING_DAMPING_RATIO));
            
            springAnimation.addUpdateListener((animation, value, velocity) -> {
                scrollY = value;
                invalidate();
            });
            
            springAnimation.addEndListener((animation, canceled, value, velocity) -> {
                if (!canceled) {
                    scrollY = finalTargetY;
                    invalidate();
                }
            });
            
            springAnimation.start();
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
        long duration = target == 1f ? 600L : 450L;
        
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
    
    // Utility methods
    
    public void setTextAlignment(Alignment alignment) {
        this.textAlignment = alignment;
        updateTextAlignment();
        invalidate();
    }
    
    public void setNormalTextSize(float size) {
        this.normalTextSize = sp2px(getContext(), size);
        normalPaint.setTextSize(normalTextSize);
        invalidate();
    }
    
    // Public API
    
    public void setCurrentTextSize(float size) {
        this.currentTextSize = sp2px(getContext(), size);
        currentPaint.setTextSize(currentTextSize);
        invalidate();
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
    
    public void reset() {
        this.lrcData = null;
        this.currentLineIndex = -1;
        this.scrollY = 0f;
        this.targetScrollY = 0f;
        this.isUserScrolling = false;
        this.layoutCache.clear();
        this.normalLayoutCache.clear();
        this.currentLayoutCache.clear();
        this.layoutHeights.clear();
        this.preHighlightHeights.clear();
        this.animatedHeights.clear();
        this.animatedTextSizes.clear();
        this.blurMaskFilters.clear();
        
        // Cancel all height animations - create a copy to avoid ConcurrentModificationException
        for (SpringAnimation animation : new java.util.ArrayList <>(heightAnimations.values())) {
            if (animation != null && animation.isRunning()) {
                animation.cancel();
            }
        }
        heightAnimations.clear();
        
        // Cancel all text size animations - create a copy to avoid ConcurrentModificationException
        for (SpringAnimation animation : new java.util.ArrayList <>(textSizeAnimations.values())) {
            if (animation != null && animation.isRunning()) {
                animation.cancel();
            }
        }
        textSizeAnimations.clear();
        
        removeCallbacks(autoScrollRunnable);
        if (blurAnimator != null && blurAnimator.isRunning()) {
            blurAnimator.cancel();
        }
        blurInterpolation = 0f;
        if (scrollSpringAnimation != null && scrollSpringAnimation.isRunning()) {
            scrollSpringAnimation.cancel();
        }
        if (springAnimation != null && springAnimation.isRunning()) {
            springAnimation.cancel();
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
            if (springAnimation != null && springAnimation.isRunning()) {
                springAnimation.cancel();
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
            
            // Cancel any ongoing spring or fling animations
            if (springAnimation != null && springAnimation.isRunning()) {
                springAnimation.cancel();
            }
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
            // Use exponential decay: resistance = e^(-k * distance)
            // This creates smooth, progressive resistance that gets stronger as you drag further
            float k = 0.004f; // Decay rate - higher = faster resistance increase
            float resistance = (float) Math.exp(-k * currentOverscroll);
            
            // Ensure minimum resistance of 5% to prevent complete lockup
            return Math.max(0.05f, resistance);
        }
        
        @Override
        public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
            // Cancel any existing animations
            if (springAnimation != null && springAnimation.isRunning()) {
                springAnimation.cancel();
            }
            if (flingAnimation != null && flingAnimation.isRunning()) {
                flingAnimation.cancel();
            }
            
            // Apply scroll multiplier to fling velocity
            float acceleratedVelocity = -velocityY * scrollMultiplier;
            
            // Create fling animation with custom friction
            FloatValueHolder holder = new FloatValueHolder();
            holder.setValue(scrollY);
            
            flingAnimation = new FlingAnimation(holder, holder.getProperty());
            flingAnimation.setStartVelocity(acceleratedVelocity);
            flingAnimation.setFriction(FLING_FRICTION);
            
            // Set min/max values for fling with overscroll
            float maxScroll = getMaxScrollY();
            flingAnimation.setMinValue(-maxOverscrollDistance);
            flingAnimation.setMaxValue(maxScroll + maxOverscrollDistance);
            
            flingAnimation.addUpdateListener((animation, value, velocity) -> {
                scrollY = value;
                
                // Track if we're in overscroll
                isInOverscroll = scrollY < 0 || scrollY > maxScroll;
                
                invalidate();
            });
            
            flingAnimation.addEndListener((animation, canceled, value, velocity) -> {
                if (!canceled && isInOverscroll) {
                    // Snap back from overscroll after fling ends
                    snapBackFromOverscroll();
                }
            });
            
            flingAnimation.start();
            return true;
        }
        
        @Override
        public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
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
