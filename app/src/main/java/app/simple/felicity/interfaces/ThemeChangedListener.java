package app.simple.felicity.interfaces;

import androidx.annotation.NonNull;

import app.simple.felicity.theme.models.Accent;
import app.simple.felicity.theme.themes.Theme;

public interface ThemeChangedListener {
    default void onThemeChanged(@NonNull Theme theme, boolean animate) {
    
    }

    default void onAccentChanged(@NonNull Accent accent) {

    }
}
