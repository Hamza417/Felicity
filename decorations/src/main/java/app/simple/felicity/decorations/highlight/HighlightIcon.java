package app.simple.felicity.decorations.highlight;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.simple.felicity.decorations.theme.ThemeIcon;
import app.simple.felicity.preferences.AppearancePreferences;
import app.simple.felicity.theme.models.Theme;

/**
 * A simple icon view with a pill-shaped highlight background that always uses the
 * theme's highlight color. It does not support click interactions or outline modes —
 * it is purely decorative. The background updates automatically when the theme or
 * corner radius preference changes.
 *
 * @author Hamza417
 */
public class HighlightIcon extends ThemeIcon {
    
    private final HighlightViewDelegate delegate = new HighlightViewDelegate();

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
    
    private void applyChipBackground() {
        setBackground(delegate.buildBackground(delegate.getCornerRadius()));
    }
    
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
