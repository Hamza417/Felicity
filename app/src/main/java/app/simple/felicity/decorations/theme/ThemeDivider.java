package app.simple.felicity.decorations.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.simple.felicity.interfaces.ThemeChangedListener;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.themes.Theme;
import app.simple.felicity.utils.ColorUtils;

public class ThemeDivider extends View implements ThemeChangedListener {

    public ThemeDivider(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThemeDivider(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public ThemeDivider(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        setBackgroundColor(Color.WHITE);
        setTint(false);
        setTranslationZ(1F);
        setFocusable(View.NOT_FOCUSABLE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isInEditMode()) {
            ThemeManager.INSTANCE.addListener(this);
        }
    }

    @Override
    public void onThemeChanged(@NonNull Theme theme, boolean animate) {
        setTint(animate);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ThemeManager.INSTANCE.removeListener(this);
    }

    private void setTint(boolean animate) {
        if (animate) {
            ColorUtils.INSTANCE.animateColorChange(
                    this,
                    ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getDividerColor());
        } else {
            setBackgroundTintList(ColorStateList
                    .valueOf(ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getDividerColor()));
        }
    }
}
