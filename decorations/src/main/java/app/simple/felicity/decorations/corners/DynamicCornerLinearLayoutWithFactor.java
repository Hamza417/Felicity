package app.simple.felicity.decorations.corners;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import app.simple.felicity.core.utils.ViewUtils;
import app.simple.felicity.decorations.theme.ThemeLinearLayout;
import app.simple.felicity.theme.managers.ThemeManager;

public class DynamicCornerLinearLayoutWithFactor extends ThemeLinearLayout {
    public DynamicCornerLinearLayoutWithFactor(Context context) {
        super(context);
        init(null);
        setOrientation(LinearLayout.VERTICAL);
    }
    
    public DynamicCornerLinearLayoutWithFactor(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }
    
    public DynamicCornerLinearLayoutWithFactor(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }
    
    private void init(AttributeSet attributeSet) {
        LayoutBackground.setBackground(getContext(), this, attributeSet, 2F);
        ViewUtils.INSTANCE.addShadow(this, ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor());
    }
}
