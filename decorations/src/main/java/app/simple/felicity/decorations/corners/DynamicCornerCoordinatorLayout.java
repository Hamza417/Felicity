package app.simple.felicity.decorations.corners;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.simple.felicity.core.utils.ViewUtils;
import app.simple.felicity.decorations.theme.ThemeCoordinatorLayout;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.themes.Theme;

public class DynamicCornerCoordinatorLayout extends ThemeCoordinatorLayout {
    public DynamicCornerCoordinatorLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(null);
    }
    
    public DynamicCornerCoordinatorLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }
    
    private void init(AttributeSet attributeSet) {
        if (isInEditMode()) {
            return;
        }
        LayoutBackground.setBackground(getContext(), this, attributeSet);
        ViewUtils.INSTANCE.addShadow(this, ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor());
    }
}
