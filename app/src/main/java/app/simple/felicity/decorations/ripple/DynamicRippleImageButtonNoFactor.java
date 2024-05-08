package app.simple.felicity.decorations.ripple;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;
import app.simple.felicity.R;
import app.simple.felicity.decorations.corners.LayoutBackground;
import app.simple.felicity.decorations.theme.ThemeButton;
import app.simple.felicity.preferences.AccessibilityPreferences;
import app.simple.felicity.preferences.AppearancePreferences;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.models.Accent;
import app.simple.felicity.theme.themes.Theme;
import app.simple.felicity.utils.ViewUtils;

public class DynamicRippleImageButtonNoFactor extends ThemeButton {
    
    private float radius = AppearancePreferences.INSTANCE.getCornerRadius();
    
    public DynamicRippleImageButtonNoFactor(Context context, AttributeSet attrs) {
        super(context, attrs);
        setBackgroundColor(Color.TRANSPARENT);
    }
    
    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {
        super.setOnClickListener(l);
        setHighlightBackgroundColor();
    }
    
    @SuppressLint ("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                if (AccessibilityPreferences.INSTANCE.isHighlightMode()) {
                    animate()
                            .scaleY(0.8F)
                            .scaleX(0.8F)
                            .alpha(0.5F)
                            .setInterpolator(new LinearOutSlowInInterpolator())
                            .setDuration(getResources().getInteger(R.integer.animation_duration))
                            .start();
                }
                
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
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                if (AccessibilityPreferences.INSTANCE.isHighlightMode()) {
                    animate()
                            .scaleY(1F)
                            .scaleX(1F)
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
    public void onThemeChanged(@NonNull Theme theme, boolean animate) {
        if (isClickable()) {
            setHighlightBackgroundColor();
        }
    }
    
    private void setHighlightBackgroundColor() {
        if (AccessibilityPreferences.INSTANCE.isHighlightMode()) {
            LayoutBackground.setBackground(getContext(), this, null);
            setBackgroundTintList(ColorStateList.valueOf(ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getHighlightColor()));
        } else {
            setBackground(null);
            setBackground(Utils.getRippleDrawable(getBackground(), radius));
        }
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (Objects.equals(key, AppearancePreferences.accentColor)) {
            setHighlightBackgroundColor();
        }
    }
    
    @Override
    public void onAccentChanged(@NonNull Accent accent) {
        super.onAccentChanged(accent);
        setHighlightBackgroundColor();
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        clearAnimation();
        setScaleX(1);
        setScaleY(1);
    }
    
    public float getRadius() {
        return radius;
    }
    
    public void setRadius(float radius) {
        this.radius = radius;
        setHighlightBackgroundColor();
    }
}
