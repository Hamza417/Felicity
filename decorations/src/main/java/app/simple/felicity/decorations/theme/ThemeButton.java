package app.simple.felicity.decorations.theme;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Build;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import app.simple.felicity.decoration.R;
import app.simple.felicity.theme.interfaces.ThemeChangedListener;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.themes.Theme;

public class ThemeButton extends AppCompatImageButton implements ThemeChangedListener, SharedPreferences.OnSharedPreferenceChangeListener {
    
    private final int REGULAR = 0;
    private final int SECONDARY = 1;
    private final int ACCENT = 2;
    private final int WHITE = 3;
    private final int GRAY = 4;
    private final int CUSTOM = -1;
    
    protected int tintMode;
    private ValueAnimator valueAnimator;
    
    public ThemeButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }
    
    public ThemeButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }
    
    private void init(AttributeSet attrs) {
        if (isInEditMode()) {
            return;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try (TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.ThemeButton)) {
                tintMode = typedArray.getInteger(R.styleable.ThemeButton_buttonTintType, 0);
                setTint(getTintColor(tintMode), false);
            }
        } else {
            TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.ThemeButton);
            tintMode = typedArray.getInteger(R.styleable.ThemeButton_buttonTintType, 0);
            setTint(getTintColor(tintMode), false);
            typedArray.recycle();
        }
    }
    
    private void setTint(int endColor, boolean animate) {
        if (animate) {
            valueAnimator = ValueAnimator.ofArgb(Objects.requireNonNull(getImageTintList()).getDefaultColor(), endColor);
            valueAnimator.setDuration(getResources().getInteger(R.integer.animation_duration));
            valueAnimator.setInterpolator(new DecelerateInterpolator(1.5F));
            valueAnimator.addUpdateListener(animation -> setImageTintList(ColorStateList.valueOf((int) animation.getAnimatedValue())));
            valueAnimator.start();
        } else {
            setImageTintList(ColorStateList.valueOf(endColor));
        }
    }
    
    private int getTintColor(int tintMode) {
        return switch (tintMode) {
            case REGULAR ->
                    ThemeManager.INSTANCE.getTheme().getIconTheme().getRegularIconColor();
            case SECONDARY ->
                    ThemeManager.INSTANCE.getTheme().getIconTheme().getSecondaryIconColor();
            case ACCENT ->
                    ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor();
            case WHITE ->
                    Color.WHITE;
            case GRAY ->
                    Color.GRAY;
            default ->
                    Objects.requireNonNull(getImageTintList()).getDefaultColor();
        };
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (enabled) {
            setTint(getTintColor(tintMode), false);
        } else {
            setTint(getTintColor(GRAY), false);
        }
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isInEditMode()) {
            return;
        }
        app.simple.felicity.preferences.SharedPreferences.INSTANCE.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        ThemeManager.INSTANCE.addListener(this);
    }
    
    @Override
    public void onThemeChanged(@NonNull Theme theme, boolean animate) {
        setTint(getTintColor(tintMode), animate);
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
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    
    }
}
