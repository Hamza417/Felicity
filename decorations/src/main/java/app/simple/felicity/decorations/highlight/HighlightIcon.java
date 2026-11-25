package app.simple.felicity.decorations.highlight;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.simple.felicity.decoration.R;
import app.simple.felicity.decorations.theme.ThemeIcon;
import app.simple.felicity.preferences.AppearancePreferences;
import app.simple.felicity.theme.models.Theme;

import static app.simple.felicity.shared.utils.UnitUtils.INSTANCE;

/**
 * A simple icon view with a pill-shaped highlight background that always uses the
 * theme's highlight color. It does not support click interactions or outline modes —
 * it is purely decorative. The background updates automatically when the theme or
 * corner radius preference changes.
 *
 * @author Hamza417
 */
public class HighlightIcon extends ThemeIcon {
    
    private HighlightViewDelegate delegate;
    
    public HighlightIcon(@NonNull Context context) {
        super(context);
        init(context, null);
    }
    
    public HighlightIcon(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }
    
    public HighlightIcon(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }
    
    private void init(@NonNull Context context, @Nullable AttributeSet attrs) {
        delegate = new HighlightViewDelegate(INSTANCE.dpToPx(context, 0.5f));
        delegate.setUseAccentColor(true); // Default to accent color unless explicitly overridden
        
        if (isInEditMode()) {
            return;
        }
        
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.HighlightIcon);
            
            try {
                delegate.setHighlightMode(a.getInt(R.styleable.HighlightIcon_highlightMode, HighlightViewDelegate.MODE_FLAT));
                delegate.setStrokeWidthPx(a.getDimension(R.styleable.HighlightIcon_highlightStrokeWidth, delegate.getStrokeWidthPx()));
                if (a.hasValue(R.styleable.HighlightIcon_highlightCustomColor)) {
                    delegate.setCustomColor(a.getColor(R.styleable.HighlightIcon_highlightCustomColor, Color.TRANSPARENT));
                    delegate.setUseCustomColor(true);
                }
                delegate.setUseAccentColor(a.getBoolean(R.styleable.HighlightIcon_useAccentColor, true));
            } finally {
                a.recycle();
            }
        }
        
        applyChipBackground();
        setClickable(false);
        setFocusable(false);
    }
    
    private void applyChipBackground() {
        setBackground(delegate.buildBackground(delegate.getCornerRadius()));
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        applyChipBackground();
    }
    
    @Override
    public void onThemeChanged(@NonNull Theme theme, boolean animate) {
        super.onThemeChanged(theme, animate);
        applyChipBackground();
    }
    
    @Override
    public void onAccentChanged(@NonNull app.simple.felicity.theme.models.Accent accent) {
        super.onAccentChanged(accent);
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
