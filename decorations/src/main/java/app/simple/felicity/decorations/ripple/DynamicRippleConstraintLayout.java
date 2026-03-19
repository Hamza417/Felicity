package app.simple.felicity.decorations.ripple;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import app.simple.felicity.decorations.corners.LayoutBackground;
import app.simple.felicity.preferences.AppearancePreferences;
import app.simple.felicity.shared.utils.ColorUtils;
import app.simple.felicity.shared.utils.ViewUtils;
import app.simple.felicity.theme.interfaces.ThemeChangedListener;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.models.Accent;

public class DynamicRippleConstraintLayout extends ConstraintLayout implements SharedPreferences.OnSharedPreferenceChangeListener, ThemeChangedListener {
    
    private float radius = 0;
    private boolean isSelected = false;
    
    public static final long RIPPLE_DURATION = 500;
    private ValueAnimator backgroundAnimator;
    
    public DynamicRippleConstraintLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public DynamicRippleConstraintLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        if (!isInEditMode()) {
            radius = AppearancePreferences.INSTANCE.getCornerRadius();
            setDefaultBackground(isSelected, false);
        }
    }
    
    /**
     * Use this method to track selection in {@link androidx.recyclerview.widget.RecyclerView}.
     * This will change the background according to the accent color and will also keep
     * save the ripple effect.
     *
     * @param selected true for selected item
     */
    public void setDefaultBackground(boolean selected, boolean animate) {
        int accentColor = ColorUtils.INSTANCE.changeAlpha(ThemeManager.INSTANCE.getAccent().getSecondaryAccentColor(), 100);
        int transparentColor = Color.TRANSPARENT;
        int currentColor = getBackgroundTintList() != null ? getBackgroundTintList().getDefaultColor()
                : selected ? accentColor : transparentColor;
        
        if (animate) {
            if (selected) {
                if (backgroundAnimator != null && backgroundAnimator.isRunning()) {
                    backgroundAnimator.cancel();
                }
                backgroundAnimator = ValueAnimator.ofArgb(currentColor, accentColor);
                backgroundAnimator.setDuration(RIPPLE_DURATION);
                backgroundAnimator.setInterpolator(new DecelerateInterpolator());
                backgroundAnimator.addUpdateListener(animation -> {
                    int animatedValue = (int) animation.getAnimatedValue();
                    setBackgroundTintList(ColorStateList.valueOf(animatedValue));
                    LayoutBackground.setBackground(getContext(), this, null, radius);
                });
                backgroundAnimator.start();
            } else {
                if (backgroundAnimator != null && backgroundAnimator.isRunning()) {
                    backgroundAnimator.cancel();
                }
                backgroundAnimator = ValueAnimator.ofArgb(currentColor, transparentColor);
                backgroundAnimator.setInterpolator(new DecelerateInterpolator());
                backgroundAnimator.setDuration(RIPPLE_DURATION);
                backgroundAnimator.addUpdateListener(animation -> {
                    int animatedValue = (int) animation.getAnimatedValue();
                    setBackgroundTintList(ColorStateList.valueOf(animatedValue));
                    LayoutBackground.setBackground(getContext(), this, null, radius);
                });
                backgroundAnimator.start();
            }
        } else {
            if (selected) {
                setBackgroundTintList(ColorStateList.valueOf(transparentColor));
                setBackgroundTintList(ColorStateList.valueOf(accentColor));
                LayoutBackground.setBackground(getContext(), this, null, radius);
            } else {
                setBackground(null);
                setBackground(RippleUtils.getRippleDrawable());
            }
        }
    }
    
    @SuppressLint ("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        try {
            if (event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (isLongClickable()) {
                        if (event.getButtonState() == MotionEvent.BUTTON_SECONDARY) {
                            performLongClick();
                            return true;
                        } else {
                            return super.onTouchEvent(event);
                        }
                    } else {
                        return super.onTouchEvent(event);
                    }
                } else {
                    return super.onTouchEvent(event);
                }
            } else {
                return super.onTouchEvent(event);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return super.onTouchEvent(event);
        }
    }
    
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        ViewUtils.INSTANCE.triggerHover(this, event);
        return super.onGenericMotionEvent(event);
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isInEditMode()) {
            return;
        }
        ThemeManager.INSTANCE.addListener(this);
        app.simple.felicity.manager.SharedPreferences.INSTANCE.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (backgroundAnimator != null && backgroundAnimator.isRunning()) {
            backgroundAnimator.cancel();
        }
        
        ThemeManager.INSTANCE.removeListener(this);
        app.simple.felicity.manager.SharedPreferences.INSTANCE.getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        clearAnimation();
        setScaleX(1);
        setScaleY(1);
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (Objects.equals(key, AppearancePreferences.ACCENT_COLOR)) {
            init();
        }
    }
    
    @Override
    public void onAccentChanged(@NonNull Accent accent) {
        ThemeChangedListener.super.onAccentChanged(accent);
        init();
    }
    
    public float getRadius() {
        return radius;
    }
    
    public void setRadius(float radius) {
        this.radius = radius;
        setDefaultBackground(isSelected(), false);
    }
    
    public boolean isSelected() {
        return isSelected;
    }
    
    public void setSelected(boolean selected) {
        isSelected = selected;
        setDefaultBackground(selected, false);
    }
    
    public void setSelected(boolean selected, boolean animate) {
        isSelected = selected;
        setDefaultBackground(selected, animate);
    }
}
