package app.simple.felicity.decorations.highlight;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.util.AttributeSet;

import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.simple.felicity.decorations.theme.ThemeIcon;
import app.simple.felicity.preferences.AppearancePreferences;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.themes.Theme;

public class HighlightIcon extends ThemeIcon {
    
    public HighlightIcon(@NonNull Context context) {
        super(context);
        init();
    }
    
    public HighlightIcon(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public HighlightIcon(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        if (isInEditMode()) {
            return;
        }
        applyChipBackground();
        setClickable(false);
        setFocusable(false);
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
    
    private float getGlobalRoundedRadius() {
        return AppearancePreferences.INSTANCE.getCornerRadius();
    }
    
    // Theme / accent change callbacks
    
    @Override
    public void onThemeChanged(@NonNull Theme theme, boolean animate) {
        super.onThemeChanged(theme, animate);
        applyChipBackground();
    }
    
    @Override
    public void onSharedPreferenceChanged(@Nullable SharedPreferences sharedPreferences,
            @Nullable String key) {
        super.onSharedPreferenceChanged(sharedPreferences, key);
        if (Objects.equals(key, AppearancePreferences.ACCENT_COLOR)
                || Objects.equals(key, AppearancePreferences.APP_CORNER_RADIUS)) {
            applyChipBackground();
        }
    }
}
