package app.simple.felicity.decorations.ripple;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.simple.felicity.core.utils.ViewUtils;
import app.simple.felicity.preferences.AppearancePreferences;
import app.simple.felicity.theme.interfaces.ThemeChangedListener;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.models.Accent;

public class DynamicRippleFrameLayout extends FrameLayout implements SharedPreferences.OnSharedPreferenceChangeListener, ThemeChangedListener {
    
    private float radius = AppearancePreferences.INSTANCE.getCornerRadius();
    
    public DynamicRippleFrameLayout(@NonNull Context context) {
        super(context);
        init();
    }
    
    public DynamicRippleFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public DynamicRippleFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    public DynamicRippleFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }
    
    private void init() {
        setBackgroundColor(Color.TRANSPARENT);
        setBackground(null);
        setBackground(Utils.getRippleDrawable(getBackground(), radius));
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
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
    
    }
    
    @Override
    public void onAccentChanged(@NonNull Accent accent) {
        ThemeChangedListener.super.onAccentChanged(accent);
        init();
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
    
    public float getRadius() {
        return radius;
    }
    
    public void setRadius(float radius) {
        this.radius = radius;
        init();
    }
}
