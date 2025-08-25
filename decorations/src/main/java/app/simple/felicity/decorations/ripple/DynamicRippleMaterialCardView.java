package app.simple.felicity.decorations.ripple;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;

import app.simple.felicity.decorations.corners.DynamicCornerMaterialCardView;
import app.simple.felicity.theme.managers.ThemeManager;

public class DynamicRippleMaterialCardView extends DynamicCornerMaterialCardView {
    
    public DynamicRippleMaterialCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public DynamicRippleMaterialCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        if (isInEditMode()) {
            return;
        }
        
        setRippleColor(ColorStateList.valueOf(ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor()));
    }
}
