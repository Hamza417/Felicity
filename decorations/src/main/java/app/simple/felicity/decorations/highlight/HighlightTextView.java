package app.simple.felicity.decorations.highlight;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;

import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.Objects;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;
import app.simple.felicity.decoration.R;
import app.simple.felicity.decorations.ripple.FelicityRippleDrawable;
import app.simple.felicity.decorations.typeface.TypeFaceTextView;
import app.simple.felicity.preferences.AccessibilityPreferences;
import app.simple.felicity.preferences.AppearancePreferences;
import app.simple.felicity.shared.utils.ViewUtils;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.models.Accent;
import app.simple.felicity.theme.models.Theme;

/**
 * A button-like {@link TypeFaceTextView} that renders a pill-shaped highlight with a
 * {@link FelicityRippleDrawable} ripple on click. Three visual modes are supported and can
 * be selected via the {@code highlightMode} XML attribute or
 * {@link #setHighlightMode(int)} at runtime:
 * <ul>
 *     <li>{@link #MODE_FLAT} – filled pill background using the theme highlight color (default).</li>
 *     <li>{@link #MODE_OUTLINE} – stroke-only border around the view, no fill.</li>
 *     <li>{@link #MODE_BOTH} – filled background with an accent-colored stroke on top.</li>
 * </ul>
 *
 * <p>The optional {@code highlightCustomColor} XML attribute pins the fill and stroke color to
 * an explicit value that is never overridden by theme or accent changes. Omit the attribute to
 * use the active theme colors. The custom color can also be changed at runtime via
 * {@link #setCustomHighlightColor(int)}; passing {@link Color#TRANSPARENT} reverts back to
 * theme-driven colors.</p>
 *
 * <p>Use wherever an image button is not appropriate but a tappable, visually distinct
 * label-button is needed.</p>
 *
 * @author Hamza417
 */
public class HighlightTextView extends TypeFaceTextView {
    
    /**
     * Filled pill background using the theme highlight color. This is the default mode.
     */
    public static final int MODE_FLAT = 0;
    
    /**
     * Stroke-only border; no fill. The stroke color follows the accent (or custom color).
     */
    public static final int MODE_OUTLINE = 1;
    
    /**
     * Filled pill background plus an accent-colored (or custom-color) stroke.
     */
    public static final int MODE_BOTH = 2;
    
    private static final float DEFAULT_STROKE_DP = 0.5f;
    
    private int highlightMode = MODE_FLAT;
    private boolean useCustomColor = false;
    @ColorInt
    private int customColor = Color.TRANSPARENT;
    private float strokeWidth;
    
    public HighlightTextView(@NonNull Context context) {
        super(context);
        strokeWidth = dpToPx(DEFAULT_STROKE_DP);
        init(null);
    }
    
    public HighlightTextView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        strokeWidth = dpToPx(DEFAULT_STROKE_DP);
        init(attrs);
    }
    
    public HighlightTextView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        strokeWidth = dpToPx(DEFAULT_STROKE_DP);
        init(attrs);
    }
    
    private void init(@Nullable AttributeSet attrs) {
        if (isInEditMode()) {
            return;
        }
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.HighlightTextView);
            try {
                highlightMode = a.getInt(R.styleable.HighlightTextView_highlightMode, MODE_FLAT);
                strokeWidth = a.getDimension(R.styleable.HighlightTextView_highlightStrokeWidth, strokeWidth);
                if (a.hasValue(R.styleable.HighlightTextView_highlightCustomColor)) {
                    customColor = a.getColor(R.styleable.HighlightTextView_highlightCustomColor, Color.TRANSPARENT);
                    useCustomColor = true;
                }
            } finally {
                a.recycle();
            }
        }
        applyChipBackground();
        applyRippleForeground();
        setClickable(true);
        setFocusable(true);
    }
    
    /**
     * Builds and applies the pill-shaped {@link MaterialShapeDrawable} background according to
     * {@link #highlightMode} and the active color (theme or custom).
     */
    private void applyChipBackground() {
        float cornerRadius = getGlobalRoundedRadius();
        MaterialShapeDrawable background = new MaterialShapeDrawable(
                new ShapeAppearanceModel()
                        .toBuilder()
                        .setAllCorners(CornerFamily.ROUNDED, cornerRadius)
                        .build());
        
        int fillColor = resolveFillColor();
        int strokeColor = resolveStrokeColor();
        
        switch (highlightMode) {
            case MODE_OUTLINE:
                background.setFillColor(ColorStateList.valueOf(Color.TRANSPARENT));
                background.setStroke(strokeWidth, strokeColor);
                break;
            case MODE_BOTH:
                background.setFillColor(ColorStateList.valueOf(fillColor));
                background.setStroke(strokeWidth, strokeColor);
                break;
            case MODE_FLAT:
            default:
                background.setFillColor(ColorStateList.valueOf(fillColor));
                break;
        }
        setBackground(background);
    }
    
    /**
     * Builds and applies the accent-colored {@link FelicityRippleDrawable} foreground clipped
     * to the same pill shape as the background.
     */
    private void applyRippleForeground() {
        int rippleColor = useCustomColor
                ? customColor
                : ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor();
        FelicityRippleDrawable ripple = new FelicityRippleDrawable(rippleColor);
        ripple.setCornerRadius(getGlobalRoundedRadius());
        int startColor = (highlightMode == MODE_OUTLINE) ? Color.TRANSPARENT : resolveFillColor();
        ripple.setStartColor(startColor);
        setForeground(ripple);
    }
    
    /**
     * Returns the fill color: the custom color when pinned, or the theme highlight color.
     */
    @ColorInt
    private int resolveFillColor() {
        return useCustomColor
                ? customColor
                : ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getHighlightColor();
    }
    
    /**
     * Returns the stroke color: the custom color when pinned, or the active accent color.
     */
    @ColorInt
    private int resolveStrokeColor() {
        return useCustomColor
                ? customColor
                : ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor();
    }
    
    private float getGlobalRoundedRadius() {
        return AppearancePreferences.INSTANCE.getCornerRadius();
    }
    
    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics());
    }
    
    /**
     * Sets the highlight display mode at runtime.
     *
     * @param mode one of {@link #MODE_FLAT}, {@link #MODE_OUTLINE}, or {@link #MODE_BOTH}.
     */
    public void setHighlightMode(int mode) {
        this.highlightMode = mode;
        applyChipBackground();
        applyRippleForeground();
    }
    
    /**
     * Pins the fill and stroke color to {@code color}, bypassing any theme or accent entirely.
     * Pass {@link Color#TRANSPARENT} to revert to theme-driven colors.
     *
     * @param color the ARGB color to use, or {@link Color#TRANSPARENT} to clear.
     */
    public void setCustomHighlightColor(@ColorInt int color) {
        if (color == Color.TRANSPARENT) {
            useCustomColor = false;
            customColor = Color.TRANSPARENT;
        } else {
            useCustomColor = true;
            customColor = color;
        }
        applyChipBackground();
        applyRippleForeground();
    }
    
    @Override
    public void onThemeChanged(@NonNull Theme theme, boolean animate) {
        super.onThemeChanged(theme, animate);
        if (!useCustomColor) {
            applyChipBackground();
            applyRippleForeground();
        }
    }
    
    @Override
    public void onAccentChanged(@NonNull Accent accent) {
        super.onAccentChanged(accent);
        if (!useCustomColor) {
            applyChipBackground();
            applyRippleForeground();
        }
    }
    
    @Override
    public void onSharedPreferenceChanged(@Nullable SharedPreferences sharedPreferences,
            @Nullable String key) {
        super.onSharedPreferenceChanged(sharedPreferences, key);
        if (Objects.equals(key, AppearancePreferences.APP_CORNER_RADIUS)) {
            applyChipBackground();
            applyRippleForeground();
        } else if (!useCustomColor && Objects.equals(key, AppearancePreferences.ACCENT_COLOR)) {
            applyChipBackground();
            applyRippleForeground();
        }
    }
    
    @SuppressLint ("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                if (AccessibilityPreferences.INSTANCE.isHighlightMode() && isClickable()) {
                    animate()
                            .scaleX(0.9F)
                            .scaleY(0.9F)
                            .alpha(0.7F)
                            .setInterpolator(new LinearOutSlowInInterpolator())
                            .setDuration(getResources().getInteger(R.integer.animation_duration))
                            .start();
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (AccessibilityPreferences.INSTANCE.isHighlightMode() && isClickable()) {
                    animate()
                            .scaleX(1F)
                            .scaleY(1F)
                            .alpha(1F)
                            .setStartDelay(50)
                            .setInterpolator(new LinearOutSlowInInterpolator())
                            .setDuration(getResources().getInteger(R.integer.animation_duration))
                            .start();
                }
                break;
            }
        }
        return super.onTouchEvent(event);
    }
    
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        ViewUtils.INSTANCE.triggerHover(this, event);
        return super.onGenericMotionEvent(event);
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        clearAnimation();
        setScaleX(1f);
        setScaleY(1f);
        setAlpha(1f);
    }
}
