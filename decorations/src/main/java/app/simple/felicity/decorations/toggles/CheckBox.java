package app.simple.felicity.decorations.toggles;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import app.simple.felicity.decoration.R;
import app.simple.felicity.preferences.AppearancePreferences;
import app.simple.felicity.theme.interfaces.ThemeChangedListener;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.models.Accent;
import app.simple.felicity.theme.themes.Theme;

public class CheckBox extends View implements ThemeChangedListener, SharedPreferences.OnSharedPreferenceChangeListener {
    
    private final Paint background = new Paint();
    private final Paint elevationPaint = new Paint();
    private final Paint check = new Paint();
    
    /**
     * Paint used to stroke the animated tick path.
     * Style is STROKE with round caps/joins for a smooth checkmark appearance.
     */
    private final Paint checkStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    
    /**
     * Raw two-segment path that describes the checkmark shape in view-space.
     */
    private final Path checkPath = new Path();
    
    /**
     * Reusable destination path for {@link PathMeasure#getSegment} — reset each frame.
     */
    private final Path segmentPath = new Path();
    
    /**
     * Measures {@link #checkPath} so we can animate the visible stroke length.
     */
    private PathMeasure pathMeasure = null;
    
    /**
     * Total arc length of {@link #checkPath}, cached after each {@link #buildCheckPath()} call.
     */
    private float pathLength = 0f;
    
    /**
     * Fraction of the checkmark stroke that is currently visible, in [0, 1].
     * 0 = nothing drawn (unchecked), 1 = fully drawn (checked).
     * Driven by {@link #animator}.
     */
    private float checkPhase = 0f;
    
    private final RectF backgroundRect = new RectF();
    
    private Drawable checkedIcon;
    
    private ValueAnimator animator = null;
    private ValueAnimator colorAnimator = null;
    private ValueAnimator elevationAnimator = null;
    
    private OnCheckedChangeListener listener;
    
    private int backgroundColor;
    private int elevationColor;
    
    private boolean isChecked = false;
    
    private float x;
    private float y;
    private float checkIconRatio = 0.7f;
    private int duration = 150;
    private float cornerRadius = 10;
    private float shadowRadius = 10F;
    
    public CheckBox(Context context) {
        super(context);
        init();
    }
    
    public CheckBox(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public CheckBox(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    public CheckBox(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }
    
    private void init() {
        setClipToOutline(false);
        
        background.setAntiAlias(true);
        background.setStyle(Paint.Style.FILL);
        
        check.setAntiAlias(true);
        check.setStyle(Paint.Style.FILL);
        
        checkStrokePaint.setStyle(Paint.Style.STROKE);
        checkStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        checkStrokePaint.setStrokeJoin(Paint.Join.ROUND);
        checkStrokePaint.setColor(Color.WHITE);
        
        checkedIcon = ContextCompat.getDrawable(getContext(), R.drawable.ic_check);
        
        if (checkedIcon != null) {
            checkedIcon.setTint(Color.WHITE);
        }
        
        if (!isInEditMode()) {
            cornerRadius = AppearancePreferences.INSTANCE.getCornerRadius() / 4F;
        } else {
            cornerRadius = 10;
        }
        
        if (!isInEditMode()) {
            backgroundColor = ThemeManager.INSTANCE.getTheme().getSwitchTheme().getSwitchOffColor();
        } else {
            backgroundColor = Color.LTGRAY;
        }
        
        duration = getResources().getInteger(R.integer.animation_duration);
        
        setLayoutParams(new LayoutParams(
                getResources().getDimensionPixelSize(R.dimen.checkbox_dimensions),
                getResources().getDimensionPixelSize(R.dimen.checkbox_dimensions)));
        
        setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.checkbox_dimensions));
        setMinimumWidth(getResources().getDimensionPixelSize(R.dimen.checkbox_dimensions));
        
        if (!isInEditMode()) {
            if (AppearancePreferences.INSTANCE.isShadowEffectOn()) {
                shadowRadius = 10F;
            } else {
                shadowRadius = 0F;
            }
        } else {
            shadowRadius = 10F;
        }
        
        if (!isInEditMode()) {
            if (AppearancePreferences.INSTANCE.isShadowEffectOn()) {
                elevationColor = ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor();
            } else {
                elevationColor = Color.DKGRAY;
            }
        } else {
            elevationColor = Color.DKGRAY;
        }
        
        post(() -> {
            x = getWidth() / 2f;
            y = getHeight() / 2f;
            buildCheckPath();
            updateChecked(); // Update everything post layout to avoid missing graphics issues
            
            try { // I like cheating :)
                ((ViewGroup) getParent()).setClipToOutline(false);
                ((ViewGroup) getParent()).setClipChildren(false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        
        setOnClickListener(v -> toggle(true));
        
        updateChecked();
    }
    
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        // Draw the shadow
        // elevationPaint.setColor(elevationColor);
        // elevationPaint.setShadowLayer(shadowRadius, 0, 0, elevationColor);
        
        // Draw the background based on checked state
        background.setColor(backgroundColor);
        backgroundRect.set(0, 0, getWidth(), getHeight());
        background.setShadowLayer(shadowRadius, 0, 0, elevationColor);
        canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, background);
        
        // Draw the animated tick mark as a stroked path.
        if (checkPhase > 0f && pathMeasure != null && pathLength > 0f) {
            segmentPath.reset();
            pathMeasure.getSegment(0f, checkPhase * pathLength, segmentPath, true);
            checkStrokePaint.setStrokeWidth(Math.min(getWidth(), getHeight()) * 0.12f);
            canvas.drawPath(segmentPath, checkStrokePaint);
        }
        
        super.onDraw(canvas);
    }
    
    private void animateFinalState() {
        clearAnimation();
        
        if (isChecked) {
            // Animate the tick stroke drawing itself from start to end.
            animator = ValueAnimator.ofFloat(checkPhase, 1f);
            animator.setDuration(duration);
            animator.setInterpolator(new DecelerateInterpolator(3));
            animator.addUpdateListener(animation -> {
                checkPhase = (float) animation.getAnimatedValue();
                invalidate();
            });
            
            colorAnimator = ValueAnimator.ofArgb(ThemeManager.INSTANCE.getTheme().getSwitchTheme().getSwitchOffColor(),
                    ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor());
            colorAnimator.setDuration(duration);
            colorAnimator.setInterpolator(new DecelerateInterpolator());
            colorAnimator.addUpdateListener(animation -> {
                backgroundColor = (int) animation.getAnimatedValue();
                invalidate();
            });
            
            int endColor;
            
            if (AppearancePreferences.INSTANCE.isShadowEffectOn()) {
                endColor = ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor();
            } else {
                endColor = Color.DKGRAY;
            }
            
            elevationAnimator = ValueAnimator.ofArgb(elevationColor, endColor);
            elevationAnimator.setDuration(duration);
            elevationAnimator.setInterpolator(new DecelerateInterpolator());
            elevationAnimator.addUpdateListener(animation -> {
                elevationColor = (int) animation.getAnimatedValue();
                invalidate();
            });
        } else {
            // Animate the tick stroke erasing itself from end back toward the start.
            animator = ValueAnimator.ofFloat(checkPhase, 0f);
            animator.setDuration(duration);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(animation -> {
                checkPhase = (float) animation.getAnimatedValue();
                invalidate();
            });
            
            colorAnimator = ValueAnimator.ofArgb(ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor(),
                    ThemeManager.INSTANCE.getTheme().getSwitchTheme().getSwitchOffColor());
            colorAnimator.setDuration(duration);
            colorAnimator.setInterpolator(new DecelerateInterpolator());
            colorAnimator.addUpdateListener(animation -> {
                backgroundColor = (int) animation.getAnimatedValue();
                invalidate();
            });
            
            elevationAnimator = ValueAnimator.ofArgb(elevationColor, Color.TRANSPARENT);
            elevationAnimator.setDuration(duration);
            elevationAnimator.setInterpolator(new DecelerateInterpolator());
            elevationAnimator.addUpdateListener(animation -> {
                elevationColor = (int) animation.getAnimatedValue();
                invalidate();
            });
        }
        
        animator.start();
        colorAnimator.start();
        elevationAnimator.start();
    }
    
    private void updateChecked() {
        clearAnimation();
        
        if (isChecked) {
            backgroundColor = ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor();
            elevationColor = ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor();
            checkPhase = 1f;
        } else {
            if (!isInEditMode()) {
                backgroundColor = ThemeManager.INSTANCE.getTheme().getSwitchTheme().getSwitchOffColor();
            }
            elevationColor = Color.TRANSPARENT;
            checkPhase = 0f;
        }
        
        invalidate();
    }
    
    /**
     * Rebuilds the two-segment checkmark {@link Path} in view-space coordinates.
     *
     * <p>The path occupies the inner bounding box defined by {@link #checkIconRatio} and the
     * current view dimensions. Three key points describe the classic ✓ shape:</p>
     * <ol>
     *   <li>Start  — left-center of the bounding box</li>
     *   <li>Dip    — lower-center (the bottom vertex of the V)</li>
     *   <li>End    — upper-right of the bounding box</li>
     * </ol>
     *
     * <p>Must be called whenever {@code x}, {@code y}, or {@link #checkIconRatio} changes.</p>
     */
    private void buildCheckPath() {
        if (x == 0f || y == 0f) {
            return;
        }
        
        float bLeft = x * (1f - checkIconRatio);
        float bTop = y * (1f - checkIconRatio);
        float bW = 2f * x * checkIconRatio;
        float bH = 2f * y * checkIconRatio;
        
        float sx = bLeft + bW * 0.15f;
        float sy = bTop + bH * 0.52f;
        float mx = bLeft + bW * 0.42f;
        float my = bTop + bH * 0.78f;
        float ex = bLeft + bW * 0.85f;
        float ey = bTop + bH * 0.22f;
        
        checkPath.reset();
        checkPath.moveTo(sx, sy);
        checkPath.lineTo(mx, my);
        checkPath.lineTo(ex, ey);
        
        pathMeasure = new PathMeasure(checkPath, false);
        pathLength = pathMeasure.getLength();
    }
    
    public int getDuration() {
        return duration;
    }
    
    public void setDuration(int duration) {
        this.duration = duration;
    }
    
    public boolean isChecked() {
        return isChecked;
    }
    
    public void setChecked(boolean checked) {
        isChecked = checked;
        updateChecked();
        
        // This method shouldn't notify the listener
    }
    
    public void setChecked(boolean checked, boolean animate) {
        isChecked = checked;
        if (animate) {
            animateFinalState();
        } else {
            updateChecked();
        }
    }
    
    public void toggle() {
        isChecked = !isChecked;
        
        if (listener != null) {
            listener.onCheckedChanged(isChecked);
        }
        
        animateFinalState();
    }
    
    public void toggle(boolean animate) {
        isChecked = !isChecked;
        
        if (listener != null) {
            listener.onCheckedChanged(isChecked);
        }
        
        if (animate) {
            animateFinalState();
        } else {
            updateChecked();
        }
    }
    
    public void animateToggle() {
        isChecked = !isChecked;
        animateFinalState();
        
        if (listener != null) {
            listener.onCheckedChanged(isChecked);
        }
    }
    
    public void check() {
        isChecked = true;
        animateFinalState();
        
        if (listener != null) {
            listener.onCheckedChanged(isChecked);
        }
    }
    
    public void check(boolean animate) {
        isChecked = true;
        
        if (listener != null) {
            listener.onCheckedChanged(isChecked);
        }
        
        if (animate) {
            animateFinalState();
        } else {
            updateChecked();
        }
    }
    
    public void uncheck() {
        isChecked = false;
        animateFinalState();
        
        if (listener != null) {
            listener.onCheckedChanged(isChecked);
        }
    }
    
    public void uncheck(boolean animate) {
        isChecked = false;
        
        if (listener != null) {
            listener.onCheckedChanged(isChecked);
        }
        
        if (animate) {
            animateFinalState();
        } else {
            updateChecked();
        }
    }
    
    public float getCheckIconRatio() {
        return checkIconRatio;
    }
    
    public void setCheckIconRatio(float ratio) {
        checkIconRatio = ratio;
        buildCheckPath();
        invalidate();
    }
    
    public float getCornerRadius() {
        return cornerRadius;
    }
    
    public void setCornerRadius(float cornerRadius) {
        this.cornerRadius = cornerRadius;
        invalidate();
    }
    
    public Drawable getCheckedIcon() {
        return checkedIcon;
    }
    
    public void setCheckedIcon(Drawable drawable) {
        checkedIcon = drawable;
        invalidate();
    }
    
    public void setCheckedIconColor(int color) {
        checkedIcon.setTint(color);
        invalidate();
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        x = w / 2f;
        y = h / 2f;
        buildCheckPath();
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = getResources().getDimensionPixelSize(R.dimen.checkbox_dimensions);
        int desiredHeight = getResources().getDimensionPixelSize(R.dimen.checkbox_dimensions);
        
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        
        int width;
        int height;
        
        //Measure Width
        if (widthMode == MeasureSpec.EXACTLY) {
            //Must be this size
            width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            //Can't be bigger than...
            width = Math.min(desiredWidth, widthSize);
        } else {
            //Be whatever you want
            width = desiredWidth;
        }
        
        //Measure Height
        if (heightMode == MeasureSpec.EXACTLY) {
            //Must be this size
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            //Can't be bigger than...
            height = Math.min(desiredHeight, heightSize);
        } else {
            //Be whatever you want
            height = desiredHeight;
        }
        
        //MUST CALL THIS
        setMeasuredDimension(width, height);
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isInEditMode()) {
            app.simple.felicity.manager.SharedPreferences.INSTANCE.registerSharedPreferenceChangeListener(this);
        }
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (!isInEditMode()) {
            app.simple.felicity.manager.SharedPreferences.INSTANCE.unregisterSharedPreferenceChangeListener(this);
        }
    }
    
    @Override
    public void clearAnimation() {
        if (animator != null) {
            animator.cancel();
        }
        
        if (colorAnimator != null) {
            colorAnimator.cancel();
        }
        
        if (elevationAnimator != null) {
            elevationAnimator.cancel();
        }
        
        super.clearAnimation();
    }
    
    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.listener = listener;
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        if (key != null) {
            switch (key) {
                case AppearancePreferences.ACCENT_COLOR,
                     AppearancePreferences.THEME -> {
                    animateFinalState();
                }
            }
        }
    }
    
    @Override
    public void onThemeChanged(@NonNull Theme theme, boolean animate) {
        ThemeChangedListener.super.onThemeChanged(theme, animate);
        animateFinalState();
    }
    
    @Override
    public void onAccentChanged(@NonNull Accent accent) {
        ThemeChangedListener.super.onAccentChanged(accent);
        animateFinalState();
    }
}
