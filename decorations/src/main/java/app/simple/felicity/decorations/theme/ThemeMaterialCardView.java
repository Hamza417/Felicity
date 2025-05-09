package app.simple.felicity.decorations.theme;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.AttributeSet;

import com.google.android.material.card.MaterialCardView;

import java.util.Objects;

import androidx.annotation.NonNull;
import app.simple.felicity.theme.interfaces.ThemeChangedListener;
import app.simple.felicity.preferences.AppearancePreferences;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.themes.Theme;

public class ThemeMaterialCardView extends MaterialCardView implements ThemeChangedListener, SharedPreferences.OnSharedPreferenceChangeListener {
    
    private ValueAnimator valueAnimator;
    
    public ThemeMaterialCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public ThemeMaterialCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        setCardBackgroundColor(Color.WHITE);
        setBackground(false);
        setRipple();
    }
    
    private void setRipple() {
        if (isClickable()) {
            setRippleColor(ColorStateList.valueOf(AppearancePreferences.INSTANCE.getAccentColor()));
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
    public void onThemeChanged(@NonNull Theme theme, boolean animate) {
        setBackground(animate);
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ThemeManager.INSTANCE.removeListener(this);
        app.simple.felicity.preferences.SharedPreferences.INSTANCE.getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        if (valueAnimator != null) {
            valueAnimator.cancel();
        }
    }
    
    private void setBackground(boolean animate) {
        if (animate) {
            valueAnimator = Utils.animateBackgroundColor(this,
                    ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getBackgroundColor());
        } else {
            setBackgroundTintList(ColorStateList.valueOf(ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getBackgroundColor()));
        }
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (Objects.equals(key, AppearancePreferences.ACCENT_COLOR)) {
            setRipple();
        }
    }
}
