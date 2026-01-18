package app.simple.felicity.decorations.corners;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.simple.felicity.decorations.typeface.TypeFaceEditText;
import app.simple.felicity.shared.utils.ViewUtils;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.themes.Theme;

public class DynamicCornerEditText extends TypeFaceEditText {
    
    public DynamicCornerEditText(@Nullable Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setProps(attrs);
    }
    
    public DynamicCornerEditText(@Nullable Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setProps(attrs);
    }
    
    private void setProps(AttributeSet attrs) {
        if (!isInEditMode()) {
            setFocusableInTouchMode(true);
            setFocusable(true);
            setSaveEnabled(true);
            setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES);
            LayoutBackground.setBackground(getContext(), this, attrs, 2F);
            setBackground(false, ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getSelectedBackgroundColor());
            ViewUtils.INSTANCE.addShadow(this, ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor());
        }
    }
    
    @Override
    public void onThemeChanged(@NonNull Theme theme, boolean animate) {
        super.onThemeChanged(theme, animate);
        setBackground(animate, theme.getViewGroupTheme().getSelectedBackgroundColor());
    }
    
    @Override
    protected void onDetachedFromWindow() {
        hideInput();
        super.onDetachedFromWindow();
    }
}
