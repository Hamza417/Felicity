package app.simple.felicity.extensions.popup;

import android.content.Context;
import android.content.res.ColorStateList;

import androidx.annotation.NonNull;
import app.simple.felicity.R;
import app.simple.felicity.decorations.corners.DynamicCornerFrameLayout;
import app.simple.felicity.theme.managers.ThemeManager;

public class PopupFrameLayout extends DynamicCornerFrameLayout {
    public PopupFrameLayout(@NonNull Context context) {
        super(context);
        init();
    }
    
    private void init() {
        int p = getResources().getDimensionPixelOffset(R.dimen.padding_10);
        setPadding(p, p, p, p);
        setBackgroundTintList(ColorStateList.valueOf(ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getBackgroundColor()));
    }
}
