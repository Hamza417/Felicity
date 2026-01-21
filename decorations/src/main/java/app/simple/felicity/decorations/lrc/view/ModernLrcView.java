package app.simple.felicity.decorations.lrc.view;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.simple.felicity.decoration.R;
import app.simple.felicity.decorations.lrc.model.LrcData;
import app.simple.felicity.decorations.lrc.model.LrcEntry;
import app.simple.felicity.decorations.typeface.TypeFace;
import app.simple.felicity.theme.interfaces.ThemeChangedListener;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.models.Accent;
import app.simple.felicity.theme.themes.Theme;

/**
 * Modern LRC lyrics view with center prominence and smooth scrolling
 */
public class ModernLrcView extends View implements ThemeChangedListener {
    
    // Default values
    private static final float DEFAULT_TEXT_SIZE = 16f; // sp
    private static final float DEFAULT_CURRENT_TEXT_SIZE = 20f; // sp
    private static final float DEFAULT_LINE_SPACING = 24f; // dp
    private static final int DEFAULT_NORMAL_COLOR = Color.GRAY;
    private static final int DEFAULT_CURRENT_COLOR = Color.WHITE;
    private static final String DEFAULT_EMPTY_TEXT = "No lyrics";
    private static final long AUTO_SCROLL_DELAY = 3000; // 3 seconds after manual scroll
    // Data
    private LrcData lrcData;
    private int currentLineIndex = -1;
    private static final float SCROLL_DAMPING = 0.15f; // Damping factor for smooth scrolling
    private int nextLineIndex = -1;
    private float lineTransitionProgress = 0f; // 0.0 to 1.0 for smooth transition
    private long currentTime = 0;
    private long lastSyncTime = 0; // Last time we received from updateTime()
    private long animationStartTime = 0; // System time when animation frame started
    // Paint objects
    private TextPaint normalPaint;
    private TextPaint currentPaint;
    private boolean isContinuousAnimationRunning = false;
    // Styling properties
    private float normalTextSize;
    private float currentTextSize;
    private float lineSpacing;
    private int normalTextColor;
    private int currentTextColor;
    private Alignment textAlignment = Alignment.CENTER;
    private String emptyText;
    // Scrolling
    private OverScroller scroller;
    private GestureDetector gestureDetector;
    private float scrollY = 0f;
    private float targetScrollY = 0f;
    // Continuous animation runnable for smooth scrolling
    private final Runnable continuousAnimationRunnable = new Runnable() {
        @Override
        public void run() {
            if (isContinuousAnimationRunning && !isUserScrolling && isAutoScrollEnabled) {
                // Calculate predicted time based on elapsed system time since last sync
                long currentSystemTime = System.currentTimeMillis();
                long elapsedSinceSync = currentSystemTime - animationStartTime;
                long predictedTime = lastSyncTime + elapsedSinceSync;
                
                // Update transition progress based on predicted time
                updateTransitionProgress(predictedTime);
                
                // Update smooth scroll based on current progress
                updateSmoothScroll();
                
                invalidate();
                postOnAnimation(this);
            }
        }
    };
    private boolean isUserScrolling = false;
    private boolean isAutoScrollEnabled = true;
    private ValueAnimator scrollAnimator;
    private TextPaint transitionPaint; // For smooth color/size transitions
    // Auto scroll resume
    private final Runnable autoScrollRunnable = () -> {
        isUserScrolling = false;
        if (isAutoScrollEnabled && currentLineIndex >= 0) {
            scrollToLine(currentLineIndex);
        }
    };
    private float previousTargetScrollY = 0f; // Track previous target for smooth transitions
    // Callbacks
    private OnLrcClickListener onLrcClickListener;
    
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
            currentPaint.setTypeface(TypeFace.INSTANCE.getBoldTypeFace(context));
        }
        
        // Initialize transition paint
        transitionPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        if (!isInEditMode()) {
            transitionPaint.setTypeface(TypeFace.INSTANCE.getMediumTypeFace(context));
        }
        
        updateTextAlignment();
        
        // Initialize scroller
        scroller = new OverScroller(context, new DecelerateInterpolator());
        
        // Initialize gesture detector
        gestureDetector = new GestureDetector(context, new GestureListener());
        
        if (!isInEditMode()) {
            updateColorsFromTheme(ThemeManager.INSTANCE.getTheme());
        }
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (lrcData == null || lrcData.isEmpty()) {
            drawEmptyText(canvas);
            return;
        }
        
        drawLyrics(canvas);
    }
    
    private void drawEmptyText(Canvas canvas) {
        normalPaint.setTextAlign(Paint.Align.CENTER);
        float x = getWidth() / 2f;
        float y = getHeight() / 2f - ((normalPaint.descent() + normalPaint.ascent()) / 2);
        canvas.drawText(emptyText, x, y, normalPaint);
    }
    
    private void drawLyrics(Canvas canvas) {
        float centerY = getHeight() / 2f;
        float offsetY = centerY - scrollY;
        
        int entryCount = lrcData.size();
        
        for (int i = 0; i < entryCount; i++) {
            LrcEntry entry = lrcData.getEntries().get(i);
            String text = entry.getText();
            
            // Calculate Y position for this line
            float y = offsetY + getLineOffset(i);
            
            // Skip lines that are off-screen
            if (y < -currentTextSize || y > getHeight() + currentTextSize) {
                continue;
            }
            
            // Choose paint and apply smooth transitions
            TextPaint paint;
            if (i == currentLineIndex) {
                // Current line fading out
                paint = getTransitionPaint(currentPaint, normalPaint, lineTransitionProgress);
            } else if (i == nextLineIndex) {
                // Next line fading in
                paint = getTransitionPaint(normalPaint, currentPaint, lineTransitionProgress);
            } else {
                // Normal line
                paint = normalPaint;
            }
            
            // Calculate X position based on alignment
            float x = calculateXPosition(text, paint);
            
            canvas.drawText(text, x, y, paint);
        }
    }
    
    /**
     * Get interpolated paint between two paints based on progress
     */
    private TextPaint getTransitionPaint(TextPaint fromPaint, TextPaint toPaint, float progress) {
        // Use ease-in-out for smoother size transitions
        float smoothProgress = easeInOutCubic(progress);
        transitionPaint.setTextSize(interpolate(fromPaint.getTextSize(), toPaint.getTextSize(), smoothProgress));
        transitionPaint.setColor(interpolateColor(fromPaint.getColor(), toPaint.getColor(), progress));
        transitionPaint.setFakeBoldText(progress > 0.5f ? toPaint.isFakeBoldText() : fromPaint.isFakeBoldText());
        return transitionPaint;
    }
    
    /**
     * Linear interpolation between two values
     */
    private float interpolate(float start, float end, float progress) {
        return start + (end - start) * progress;
    }
    
    /**
     * Ease-in-out cubic interpolation for smoother animations
     */
    private float easeInOutCubic(float t) {
        if (t < 0.5f) {
            return 4 * t * t * t;
        } else {
            float f = (2 * t - 2);
            return 0.5f * f * f * f + 1;
        }
    }
    
    /**
     * Interpolate between two colors
     */
    private int interpolateColor(int startColor, int endColor, float progress) {
        int startA = (startColor >> 24) & 0xff;
        int startR = (startColor >> 16) & 0xff;
        int startG = (startColor >> 8) & 0xff;
        int startB = startColor & 0xff;
        
        int endA = (endColor >> 24) & 0xff;
        int endR = (endColor >> 16) & 0xff;
        int endG = (endColor >> 8) & 0xff;
        int endB = endColor & 0xff;
        
        int a = (int) (startA + (endA - startA) * progress);
        int r = (int) (startR + (endR - startR) * progress);
        int g = (int) (startG + (endG - startG) * progress);
        int b = (int) (startB + (endB - startB) * progress);
        
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    
    /**
     * Calculate cumulative Y offset for a given line
     * Uses average line height for stable scrolling while allowing visual size transitions
     */
    private float getLineOffset(int lineIndex) {
        float offset = 0f;
        
        // Use a consistent average line height for spacing calculations
        // This prevents size changes from affecting scroll position stability
        float avgLineHeight = (normalTextSize + currentTextSize) / 2f;
        
        // Calculate base offset using consistent spacing
        for (int i = 0; i < lineIndex; i++) {
            offset += avgLineHeight + lineSpacing;
        }
        
        // Add half height of current line to center it
        offset += avgLineHeight / 2f;
        
        // Apply fine adjustment based on actual size transitions
        // This creates smooth visual size changes without affecting scroll stability
        if (lineIndex == currentLineIndex || lineIndex == nextLineIndex) {
            float actualHeight;
            if (lineIndex == currentLineIndex) {
                actualHeight = interpolate(currentTextSize, normalTextSize, lineTransitionProgress);
            } else {
                actualHeight = interpolate(normalTextSize, currentTextSize, lineTransitionProgress);
            }
            // Add small offset adjustment to keep visual centering smooth
            float adjustment = (actualHeight - avgLineHeight) * 0.5f;
            offset += adjustment;
        }
        
        return offset;
    }
    
    /**
     * Calculate X position based on alignment
     */
    private float calculateXPosition(String text, TextPaint paint) {
        return switch (textAlignment) {
            case LEFT ->
                    getPaddingLeft();
            case RIGHT ->
                    getWidth() - getPaddingRight();
            default ->
                    getWidth() / 2f;
        };
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
        transitionPaint.setTextAlign(align);
    }
    
    /**
     * Set lyrics data
     */
    public void setLrcData(LrcData data) {
        this.lrcData = data;
        this.currentLineIndex = -1;
        this.scrollY = 0f;
        this.targetScrollY = 0f;
        invalidate();
    }
    
    /**
     * Update current playback time - acts as a sync point to correct drift
     */
    public void updateTime(long timeInMillis) {
        if (lrcData == null || lrcData.isEmpty()) {
            return;
        }
        
        // Apply offset if present
        timeInMillis += lrcData.getOffset();
        
        // Store the sync time and reset animation reference
        lastSyncTime = timeInMillis;
        animationStartTime = System.currentTimeMillis();
        
        // Update transition progress (this will also update line indices)
        updateTransitionProgress(timeInMillis);
        
        // Start continuous animation if not already running
        if (!isContinuousAnimationRunning && !isUserScrolling && isAutoScrollEnabled) {
            startContinuousAnimation();
        }
    }
    
    /**
     * Update transition progress based on time (can be called with predicted time)
     */
    private void updateTransitionProgress(long timeInMillis) {
        if (lrcData == null) {
            lineTransitionProgress = 0f;
            return;
        }
        
        // Find what the indices should be based on current time
        int expectedCurrentLine = findLineIndexByTime(timeInMillis);
        int expectedNextLine = (expectedCurrentLine >= 0 && expectedCurrentLine < lrcData.size() - 1)
                ? expectedCurrentLine + 1
                : -1;
        
        // Detect line change and update indices smoothly
        if (expectedCurrentLine != currentLineIndex) {
            // Line changed - update indices
            currentLineIndex = expectedCurrentLine;
            nextLineIndex = expectedNextLine;
        } else if (expectedNextLine != nextLineIndex) {
            nextLineIndex = expectedNextLine;
        }
        
        // Calculate smooth transition progress between current and next line
        if (currentLineIndex >= 0 && nextLineIndex >= 0) {
            LrcEntry currentEntry = lrcData.getEntries().get(currentLineIndex);
            LrcEntry nextEntry = lrcData.getEntries().get(nextLineIndex);
            
            long currentStartTime = currentEntry.getTimeInMillis();
            long nextStartTime = nextEntry.getTimeInMillis();
            long duration = nextStartTime - currentStartTime;
            
            if (duration > 0) {
                long elapsed = timeInMillis - currentStartTime;
                lineTransitionProgress = Math.max(0f, Math.min(1f, (float) elapsed / duration));
            } else {
                lineTransitionProgress = 0f;
            }
        } else {
            lineTransitionProgress = 0f;
        }
    }
    
    /**
     * Start continuous frame-by-frame animation
     */
    private void startContinuousAnimation() {
        if (!isContinuousAnimationRunning) {
            isContinuousAnimationRunning = true;
            postOnAnimation(continuousAnimationRunnable);
        }
    }
    
    /**
     * Stop continuous animation
     */
    private void stopContinuousAnimation() {
        isContinuousAnimationRunning = false;
        removeCallbacks(continuousAnimationRunnable);
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
     * Update smooth scrolling based on transition progress
     */
    private void updateSmoothScroll() {
        if (currentLineIndex < 0) {
            return;
        }
        
        // Calculate target scroll position based on transition between current and next line
        float currentLineOffset = getLineOffset(currentLineIndex);
        
        if (nextLineIndex >= 0 && lineTransitionProgress > 0) {
            // Interpolate scroll position between current and next line
            float nextLineOffset = getLineOffset(nextLineIndex);
            targetScrollY = interpolate(currentLineOffset, nextLineOffset, lineTransitionProgress);
        } else {
            targetScrollY = currentLineOffset;
        }
        
        // Smoothly interpolate scrollY towards targetScrollY to prevent jerks
        // This creates fluid motion even when line indices change
        float delta = targetScrollY - scrollY;
        if (Math.abs(delta) > 0.5f) {
            scrollY += delta * SCROLL_DAMPING;
        } else {
            scrollY = targetScrollY;
        }
    }
    
    /**
     * Scroll to specific line (used for manual navigation)
     */
    private void scrollToLine(int lineIndex) {
        if (lineIndex < 0 || lrcData == null || lineIndex >= lrcData.size()) {
            return;
        }
        
        targetScrollY = getLineOffset(lineIndex);
        animateScroll(scrollY, targetScrollY);
    }
    
    /**
     * Animate scroll to target position (used for manual navigation)
     */
    private void animateScroll(float from, float to) {
        if (scrollAnimator != null && scrollAnimator.isRunning()) {
            scrollAnimator.cancel();
        }
        
        scrollAnimator = ValueAnimator.ofFloat(from, to);
        scrollAnimator.setDuration(300);
        scrollAnimator.setInterpolator(new DecelerateInterpolator());
        scrollAnimator.addUpdateListener(animation -> {
            scrollY = (float) animation.getAnimatedValue();
            invalidate();
        });
        scrollAnimator.start();
    }
    
    @SuppressLint ("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (lrcData == null || lrcData.isEmpty()) {
            return super.onTouchEvent(event);
        }
        
        boolean handled = gestureDetector.onTouchEvent(event);
        
        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (isUserScrolling) {
                // Resume auto-scrolling and continuous animation after delay
                postDelayed(() -> {
                    isUserScrolling = false;
                    if (isAutoScrollEnabled && currentLineIndex >= 0) {
                        scrollToLine(currentLineIndex);
                        startContinuousAnimation();
                    }
                }, AUTO_SCROLL_DELAY);
            }
        }
        
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
    
    public void setAutoScrollEnabled(boolean enabled) {
        this.isAutoScrollEnabled = enabled;
    }
    
    public void reset() {
        this.lrcData = null;
        this.currentLineIndex = -1;
        this.nextLineIndex = -1;
        this.lineTransitionProgress = 0f;
        this.currentTime = 0;
        this.lastSyncTime = 0;
        this.animationStartTime = 0;
        this.scrollY = 0f;
        this.targetScrollY = 0f;
        this.isUserScrolling = false;
        stopContinuousAnimation();
        removeCallbacks(autoScrollRunnable);
        if (scrollAnimator != null) {
            scrollAnimator.cancel();
        }
        invalidate();
    }
    
    public void setOnLrcClickListener(OnLrcClickListener listener) {
        this.onLrcClickListener = listener;
    }
    
    @Override
    public void onAccentChanged(@NonNull Accent accent) {
        ThemeChangedListener.super.onAccentChanged(accent);
    }
    
    @Override
    public void onThemeChanged(@NonNull Theme theme, boolean animate) {
        ThemeChangedListener.super.onThemeChanged(theme, animate);
        updateColorsFromTheme(theme);
    }
    
    private void updateColorsFromTheme(Theme theme) {
        setCurrentTextColor(theme.getTextViewTheme().getPrimaryTextColor());
        setNormalTextColor(theme.getTextViewTheme().getTertiaryTextColor());
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isInEditMode()) {
            ThemeManager.INSTANCE.addListener(this);
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
            stopContinuousAnimation();
            if (scrollAnimator != null && scrollAnimator.isRunning()) {
                scrollAnimator.cancel();
            }
            if (!scroller.isFinished()) {
                scroller.abortAnimation();
            }
            return true;
        }
        
        @Override
        public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
            isUserScrolling = true;
            scrollY += distanceY;
            
            // Apply boundaries
            float maxScroll = getMaxScrollY();
            if (scrollY < 0) {
                scrollY = 0;
            } else if (scrollY > maxScroll) {
                scrollY = maxScroll;
            }
            
            invalidate();
            return true;
        }
        
        @Override
        public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
            scroller.fling(
                    0, (int) scrollY,
                    0, (int) -velocityY,
                    0, 0,
                    0, (int) getMaxScrollY()
                          );
            invalidate();
            return true;
        }
        
        @Override
        public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
            if (onLrcClickListener != null && currentLineIndex >= 0 && lrcData != null) {
                LrcEntry entry = lrcData.getEntries().get(currentLineIndex);
                onLrcClickListener.onLrcClick(entry.getTimeInMillis(), entry.getText());
            }
            return true;
        }
    }
}
