package app.simple.felicity.decorations.theme;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.util.AttributeSet;

import com.google.android.material.card.MaterialCardView;

import java.util.Objects;

import androidx.annotation.NonNull;
import app.simple.felicity.preferences.AppearancePreferences;
import app.simple.felicity.theme.interfaces.ThemeChangedListener;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.models.Accent;
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
        setCardBackgroundColor(ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getBackgroundColor());
        setBackground(false);
        setRipple();
    }
    
    private void setRipple() {
        setRippleColor(ColorStateList.valueOf(ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor()));
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
    public void onThemeChanged(@NonNull Theme theme, boolean animate) {
        setBackground(animate);
    }
    
    @Override
    public void onAccentChanged(@NonNull Accent accent) {
        ThemeChangedListener.super.onAccentChanged(accent);
        setRipple();
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ThemeManager.INSTANCE.removeListener(this);
        app.simple.felicity.manager.SharedPreferences.INSTANCE.getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
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
