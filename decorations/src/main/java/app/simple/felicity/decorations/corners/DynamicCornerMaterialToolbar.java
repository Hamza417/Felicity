package app.simple.felicity.decorations.corners;

import android.content.Context;
import android.util.AttributeSet;

import com.google.android.material.appbar.MaterialToolbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.simple.felicity.core.utils.ViewUtils;
import app.simple.felicity.theme.managers.ThemeManager;

public class DynamicCornerMaterialToolbar extends MaterialToolbar {
    public DynamicCornerMaterialToolbar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }
    
    public DynamicCornerMaterialToolbar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }
    
    private void init(AttributeSet attributeSet) {
        LayoutBackground.setBackground(getContext(), this, attributeSet);
        ViewUtils.INSTANCE.addShadow(this, ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor());
    }
}
