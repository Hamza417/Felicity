package app.simple.felicity.decorations.ripple;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.RelativeLayout;

import java.util.Objects;

import androidx.annotation.NonNull;
import app.simple.felicity.core.utils.ViewUtils;
import app.simple.felicity.decorations.corners.LayoutBackground;
import app.simple.felicity.preferences.AccessibilityPreferences;
import app.simple.felicity.preferences.AppearancePreferences;
import app.simple.felicity.theme.interfaces.ThemeChangedListener;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.models.Accent;

public class DynamicRippleRelativeLayout extends RelativeLayout implements SharedPreferences.OnSharedPreferenceChangeListener, ThemeChangedListener {
    
    private float radius = AppearancePreferences.INSTANCE.getCornerRadius();
    
    public DynamicRippleRelativeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public DynamicRippleRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        if (isInEditMode()) {
            return;
        }
        setBackgroundColor(Color.TRANSPARENT);
        setBackground(null);
        setBackground(Utils.getRippleDrawable(getBackground()));
    }
    
    private void setHighlightBackgroundColor() {
        if (AccessibilityPreferences.INSTANCE.isHighlightMode()) {
            LayoutBackground.setBackground(getContext(), this, null, AppearancePreferences.INSTANCE.getCornerRadius());
            setBackgroundTintList(ColorStateList.valueOf(ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getHighlightColor()));
        } else {
            setBackground(null);
            setBackground(Utils.getRippleDrawable(getBackground()));
        }
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isInEditMode()) {
            return;
        }
        ThemeManager.INSTANCE.addListener(this);
        app.simple.felicity.preferences.SharedPreferences.INSTANCE.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ThemeManager.INSTANCE.removeListener(this);
        app.simple.felicity.preferences.SharedPreferences.INSTANCE.getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
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
    public void onAccentChanged(@NonNull Accent accent) {
        ThemeChangedListener.super.onAccentChanged(accent);
        setHighlightBackgroundColor();
    }
    
    public float getRadius() {
        return radius;
    }
    
    public void setRadius(float radius) {
        this.radius = radius;
        init();
    }
}
