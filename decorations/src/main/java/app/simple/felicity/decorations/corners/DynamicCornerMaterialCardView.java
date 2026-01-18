package app.simple.felicity.decorations.corners;

import android.content.Context;
import android.util.AttributeSet;

import app.simple.felicity.decorations.theme.ThemeMaterialCardView;
import app.simple.felicity.preferences.AppearancePreferences;
import app.simple.felicity.shared.utils.ViewUtils;
import app.simple.felicity.theme.managers.ThemeManager;

public class DynamicCornerMaterialCardView extends ThemeMaterialCardView {
    public DynamicCornerMaterialCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public DynamicCornerMaterialCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        if (isInEditMode()) {
            return;
        }
        
        setElevation(48F);
        setRadius(Math.min(AppearancePreferences.INSTANCE.getCornerRadius(), 75F));
        ViewUtils.INSTANCE.addShadow(this, ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor());
    }
}