package app.simple.felicity.decorations.views;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.AttributeSet;

import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.ShapeAppearanceModel;

import app.simple.felicity.core.utils.ViewUtils;
import app.simple.felicity.decorations.typeface.TypeFace;
import app.simple.felicity.preferences.AppearancePreferences;
import app.simple.felicity.theme.managers.ThemeManager;

public class Chip extends com.google.android.material.chip.Chip {
    
    public Chip(Context context) {
        super(context);
        init();
    }
    
    public Chip(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public Chip(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        setCheckable(true);
        
        if (isInEditMode()) {
            return;
        }
        
        setCheckedIcon(null);
        setTypeface(TypeFace.INSTANCE.getBoldTypeFace(getContext()));
        setTextColor(ColorStateList.valueOf(ThemeManager.INSTANCE.getTheme().getTextViewTheme().getPrimaryTextColor()));
        setChipBackgroundColor(new ColorStateList(new int[][] {
                new int[] {
                        android.R.attr.state_checked
                },
                new int[] {
                
                }},
                new int[] {
                        ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor(),
                        ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getHighlightColor()
                }
        ));
        
        setShapeAppearanceModel(new ShapeAppearanceModel()
                .toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, AppearancePreferences.INSTANCE.getCornerRadius() / 2)
                .build());
        
        ViewUtils.INSTANCE.addShadow(this, ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor());
        setRippleColor(ColorStateList.valueOf(ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor()));
        setChipStrokeColor(ColorStateList.valueOf(ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor()));
        setChipStrokeWidth(2);
    }
    
    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
        if (checked) {
            setTextColor(Color.WHITE);
        } else {
            setTextColor(ThemeManager.INSTANCE.getTheme().getTextViewTheme().getPrimaryTextColor());
        }
    }
    
    public void setChipBackgroundColor(int color) {
        setChipBackgroundColor(ColorStateList.valueOf(color));
    }
    
    public void setCheckedIconTint(int color) {
        setCheckedIconTint(ColorStateList.valueOf(color));
    }
    
    public void setChipStrokeColor(int color) {
        setChipStrokeColor(ColorStateList.valueOf(color));
    }
    
    public void setTextColor(int color) {
        setTextColor(ColorStateList.valueOf(color));
    }
    
    public void setRippleColor(int color) {
        setRippleColor(ColorStateList.valueOf(color));
    }
    
    public void setCornerRadius(float radius) {
        setShapeAppearanceModel(new ShapeAppearanceModel()
                .toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, radius)
                .build());
    }
    
    public void setIcon(int icon) {
        setCheckedIconResource(icon);
    }
    
    public void useRegularTypeface() {
        setTypeface(TypeFace.INSTANCE.getRegularTypeFace(getContext()));
    }
}