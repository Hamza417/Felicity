package app.simple.felicity.decorations.theme;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import app.simple.felicity.theme.interfaces.ThemeChangedListener;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.themes.Theme;

public class ThemeCoordinatorLayout extends CoordinatorLayout implements ThemeChangedListener {
    
    private ValueAnimator valueAnimator;
    
    public ThemeCoordinatorLayout(@NonNull Context context) {
        super(context);
        init();
    }
    
    public ThemeCoordinatorLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public ThemeCoordinatorLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        setBackgroundColor(ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getBackgroundColor());
        setBackground(false);
    }
    
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        ThemeManager.INSTANCE.addListener(this);
    }
    
    @Override
    public void onThemeChanged(@NonNull Theme theme, boolean animate) {
        setBackground(animate);
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
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ThemeManager.INSTANCE.removeListener(this);
        if (valueAnimator != null) {
            valueAnimator.cancel();
        }
    }
}
