package app.simple.felicity.interfaces;

import androidx.annotation.NonNull;
import app.simple.felicity.models.AccentColor;
import app.simple.felicity.models.Theme;

public interface ThemeChangedListener {
    default void onThemeChanged(@NonNull Theme theme, boolean animate) {
    
    }
    
    default void onAccentChanged(@NonNull AccentColor accentColor) {
    
    }
}
