package app.simple.felicity.extensions.popup;

import android.content.Context;
import android.widget.LinearLayout;

import app.simple.felicity.R;
import app.simple.felicity.decorations.corners.DynamicCornerLinearLayout;

public class PopupLinearLayout extends DynamicCornerLinearLayout {
    public PopupLinearLayout(Context context) {
        super(context);
        init();
    }
    
    public PopupLinearLayout(Context context, int orientation) {
        super(context);
        init(orientation);
    }
    
    private void init() {
        int p = getResources().getDimensionPixelOffset(R.dimen.popup_padding);
        setPadding(p, p, p, p);
        setOrientation(LinearLayout.VERTICAL);
    }
    
    private void init(int orientation) {
        int p = getResources().getDimensionPixelOffset(R.dimen.popup_padding);
        setPadding(p, p, p, p);
        setOrientation(orientation);
    }
}
