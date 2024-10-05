package app.simple.felicity.decorations.theme;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.simple.felicity.theme.interfaces.ThemeChangedListener;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.themes.Theme;

public class ThemeLinearLayout extends LinearLayout implements ThemeChangedListener {
    
    private ValueAnimator valueAnimator;
    
    public ThemeLinearLayout(Context context) {
        super(context);
        init();
    }
    
    public ThemeLinearLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public ThemeLinearLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    public ThemeLinearLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }
    
    private void init() {
        if (isInEditMode()) {
            return;
        }
        
        // TODO - try ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getBackground()
        setBackgroundColor(Color.WHITE);
        setBackground(false);
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
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ThemeManager.INSTANCE.removeListener(this);
        if (valueAnimator != null) {
            valueAnimator.cancel();
        }
    }
}
