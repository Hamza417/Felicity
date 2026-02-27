package app.simple.felicity.decorations.toggles;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.AttributeSet;

import com.google.android.material.button.MaterialButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.simple.felicity.decorations.typeface.TypeFace;
import app.simple.felicity.preferences.AppearancePreferences;
import app.simple.felicity.theme.interfaces.ThemeChangedListener;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.themes.Theme;

public class Button extends MaterialButton implements ThemeChangedListener {
    
    /**
     * @noinspection FieldCanBeLocal
     */
    private final int MAX_CORNER_RADIUS = 30;
    /**
     * @noinspection FieldCanBeLocal
     */
    private final int TEXT_SIZE = 10;
    
    public Button(@NonNull Context context) {
        super(context);
        init();
    }
    
    public Button(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public Button(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        if (!isInEditMode()) {
            setRippleColor(ColorStateList.valueOf(ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor()));
            setTypeface(TypeFace.INSTANCE.getMediumTypeFace(getContext()));
            setStrokeColor(ColorStateList.valueOf(ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getDividerColor()));
            setStrokeWidth(1);
            setElevation(0);
            
            int cornerRadius = (int) AppearancePreferences.INSTANCE.getCornerRadius();
            if (cornerRadius > MAX_CORNER_RADIUS) {
                cornerRadius = MAX_CORNER_RADIUS;
            }
            setCornerRadius(cornerRadius);
            
            setBackgroundTintList(new ColorStateList(new int[][] {
                    new int[] {-android.R.attr.state_checked}, // This is for the unchecked state
                    new int[] {android.R.attr.state_enabled}, // This is for the enabled state
                    new int[] {-android.R.attr.state_enabled} // This is for the disabled state
            },
                    new int[] {
                            ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getBackgroundColor(), // Color for the unchecked state
                            ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor(), // Color for the enabled state
                            ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getBackgroundColor() // Color for the disabled state
                    }));
            
            setTextColor(new ColorStateList(new int[][] {
                    new int[] {-android.R.attr.state_checked}, // This is for the unchecked state
                    new int[] {android.R.attr.state_enabled}, // This is for the enabled state
                    new int[] {-android.R.attr.state_enabled} // This is for the disabled state
            },
                    new int[] {
                            ThemeManager.INSTANCE.getTheme().getTextViewTheme().getPrimaryTextColor(), // Color for the unchecked state
                            Color.WHITE, // Color for the enabled state
                            ThemeManager.INSTANCE.getTheme().getTextViewTheme().getQuaternaryTextColor() // Color for the disabled state
                    }));
        }
        
        setTextSize(TEXT_SIZE);
        setAllCaps(false);
    }
    
    public void setButtonCheckedColor(int color) {
        setBackgroundTintList(new ColorStateList(new int[][] {
                new int[] {-android.R.attr.state_checked}, // This is for the unchecked state
                new int[] {android.R.attr.state_enabled}, // This is for the enabled state
                new int[] {-android.R.attr.state_enabled} // This is for the disabled state
        },
                new int[] {
                        ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getBackgroundColor(), // Color for the unchecked state
                        color, // Color for the enabled state
                        ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getBackgroundColor() // Color for the disabled state
                }));
        
        invalidate();
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ThemeManager.INSTANCE.addListener(this);
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ThemeManager.INSTANCE.removeListener(this);
    }
    
    @Override
    public void onThemeChanged(@NonNull Theme theme, boolean animate) {
        ThemeChangedListener.super.onThemeChanged(theme, animate);
        init();
        postInvalidate();
    }
}
