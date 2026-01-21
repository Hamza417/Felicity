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
    private static final long SCROLL_ANIMATION_DURATION = 300;
    private static final long AUTO_SCROLL_DELAY = 3000; // 3 seconds after manual scroll
    // Data
    private LrcData lrcData;
    private int currentLineIndex = -1;
    // Paint objects
    private TextPaint normalPaint;
    private TextPaint currentPaint;
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
    private boolean isUserScrolling = false;
    private boolean isAutoScrollEnabled = true;
    private ValueAnimator scrollAnimator;
    // Auto scroll resume
    private final Runnable autoScrollRunnable = () -> {
        isUserScrolling = false;
        if (isAutoScrollEnabled && currentLineIndex >= 0) {
            scrollToLine(currentLineIndex);
        }
    };
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
            
            // Choose paint based on whether this is the current line
            TextPaint paint = (i == currentLineIndex) ? currentPaint : normalPaint;
            
            // Calculate X position based on alignment
            float x = calculateXPosition(text, paint);
            
            canvas.drawText(text, x, y, paint);
        }
    }
    
    /**
     * Calculate cumulative Y offset for a given line
     */
    private float getLineOffset(int lineIndex) {
        float offset = 0f;
        
        for (int i = 0; i < lineIndex; i++) {
            if (i == currentLineIndex) {
                offset += currentTextSize + lineSpacing;
            } else {
                offset += normalTextSize + lineSpacing;
            }
        }
        
        // Add half height of current line to center it
        if (lineIndex == currentLineIndex) {
            offset += currentTextSize / 2f;
        } else {
            offset += normalTextSize / 2f;
        }
        
        return offset;
    }
    
    /**
     * Calculate X position based on alignment
     */
    private float calculateXPosition(String text, TextPaint paint) {
        switch (textAlignment) {
            case LEFT:
                return getPaddingLeft();
            case RIGHT:
                return getWidth() - getPaddingRight();
            case CENTER:
            default:
                return getWidth() / 2f;
        }
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
     * Update current playback time
     */
    public void updateTime(long timeInMillis) {
        if (lrcData == null || lrcData.isEmpty()) {
            return;
        }
        
        // Apply offset if present
        timeInMillis += lrcData.getOffset();
        
        // Find the current line based on time
        int newLineIndex = findLineIndexByTime(timeInMillis);
        
        if (newLineIndex != currentLineIndex) {
            currentLineIndex = newLineIndex;
            
            if (!isUserScrolling && isAutoScrollEnabled) {
                scrollToLine(currentLineIndex);
            }
            
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
     * Animate scroll to target position
     */
    private void animateScroll(float from, float to) {
        if (scrollAnimator != null && scrollAnimator.isRunning()) {
            scrollAnimator.cancel();
        }
        
        scrollAnimator = ValueAnimator.ofFloat(from, to);
        scrollAnimator.setDuration(SCROLL_ANIMATION_DURATION);
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
                // Resume auto-scrolling after delay
                postDelayed(autoScrollRunnable, AUTO_SCROLL_DELAY);
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
        this.scrollY = 0f;
        this.targetScrollY = 0f;
        this.isUserScrolling = false;
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
        public boolean onDown(MotionEvent e) {
            removeCallbacks(autoScrollRunnable);
            if (scrollAnimator != null && scrollAnimator.isRunning()) {
                scrollAnimator.cancel();
            }
            if (!scroller.isFinished()) {
                scroller.abortAnimation();
            }
            return true;
        }
        
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
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
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
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
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (onLrcClickListener != null && currentLineIndex >= 0 && lrcData != null) {
                LrcEntry entry = lrcData.getEntries().get(currentLineIndex);
                onLrcClickListener.onLrcClick(entry.getTimeInMillis(), entry.getText());
            }
            return true;
        }
    }
}
