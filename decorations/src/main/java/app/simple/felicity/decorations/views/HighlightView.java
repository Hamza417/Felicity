package app.simple.felicity.decorations.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;
import app.simple.felicity.decoration.R;
import app.simple.felicity.decorations.ripple.FelicityRippleDrawable;
import app.simple.felicity.preferences.AccessibilityPreferences;
import app.simple.felicity.preferences.AppearancePreferences;
import app.simple.felicity.shared.utils.ViewUtils;
import app.simple.felicity.theme.interfaces.ThemeChangedListener;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.models.Accent;
import app.simple.felicity.theme.themes.Theme;

/**
 * A tappable {@link FrameLayout} container that renders a pill-shaped highlight
 * background (matching the color used by {@link HighlightTextView}) together with
 * a {@link FelicityRippleDrawable} ripple foreground.
 * <p>
 * Use as a wrapper around icon views in panel grids and similar layouts where a
 * visually distinct, tappable container is needed without embedding any text directly
 * in the view itself.
 *
 * @author Hamza417
 */
public class HighlightView extends FrameLayout
        implements SharedPreferences.OnSharedPreferenceChangeListener, ThemeChangedListener {
    
    public HighlightView(@NonNull Context context) {
        super(context);
        init();
    }
    
    public HighlightView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public HighlightView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        if (isInEditMode()) {
            return;
        }
        applyChipBackground();
        applyRippleForeground();
        setClickable(true);
        setFocusable(true);
    }
    
    // Background — pill shape tinted with the theme highlight color
    
    private void applyChipBackground() {
        float cornerRadius = getGlobalRoundedRadius();
        MaterialShapeDrawable background = new MaterialShapeDrawable(
                new ShapeAppearanceModel()
                        .toBuilder()
                        .setAllCorners(CornerFamily.ROUNDED, cornerRadius)
                        .build());
        background.setFillColor(
                ColorStateList.valueOf(
                        ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getHighlightColor()));
        setBackground(background);
    }
    
    // Foreground — accent-colored ripple clipped to the same pill shape
    
    private void applyRippleForeground() {
        FelicityRippleDrawable ripple = new FelicityRippleDrawable(
                ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor());
        ripple.setCornerRadius(getGlobalRoundedRadius());
        ripple.setStartColor(ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getHighlightColor());
        setForeground(ripple);
    }
    
    private float getGlobalRoundedRadius() {
        return AppearancePreferences.INSTANCE.getCornerRadius();
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ThemeManager.INSTANCE.addListener(this);
        app.simple.felicity.manager.SharedPreferences.INSTANCE.getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ThemeManager.INSTANCE.removeListener(this);
        app.simple.felicity.manager.SharedPreferences.INSTANCE.getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
        clearAnimation();
        setScaleX(1f);
        setScaleY(1f);
        setAlpha(1f);
    }
    
    // Theme / accent change callbacks
    
    @Override
    public void onThemeChanged(@NonNull Theme theme, boolean animate) {
        applyChipBackground();
        applyRippleForeground();
    }
    
    @Override
    public void onAccentChanged(@NonNull Accent accent) {
        applyRippleForeground();
    }
    
    @Override
    public void onSharedPreferenceChanged(@Nullable SharedPreferences sharedPreferences,
            @Nullable String key) {
        if (Objects.equals(key, AppearancePreferences.ACCENT_COLOR)
                || Objects.equals(key, AppearancePreferences.APP_CORNER_RADIUS)) {
            applyChipBackground();
            applyRippleForeground();
        }
    }
    
    // Touch — accessibility highlight-mode scale animation (matches HighlightTextView)
    
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
}

