package app.simple.felicity.decorations.fastscroll;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.util.Consumer;
import app.simple.felicity.R;
import app.simple.felicity.decorations.fastscroll.backgrounds.CircularBackground;
import app.simple.felicity.preferences.AppearancePreferences;
import app.simple.felicity.utils.TypeFace;
import app.simple.felicity.utils.ViewUtils;

public class PopupStyles {
    
    public static Consumer <TextView> Inure = popupView -> {
        Resources resources = popupView.getResources();
        popupView.setMinimumWidth(resources.getDimensionPixelSize(R.dimen.fast_scroller_dimen));
        popupView.setMinimumHeight(resources.getDimensionPixelSize(R.dimen.fast_scroller_dimen));
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) popupView.getLayoutParams();
        layoutParams.gravity = Gravity.CENTER;
        layoutParams.setMarginEnd(resources.getDimensionPixelOffset(R.dimen.fast_scroller_popup_margin_end));
        popupView.setLayoutParams(layoutParams);
        Context context = popupView.getContext();
        popupView.setBackground(new CircularBackground(context));
        popupView.setElevation(resources.getDimensionPixelOffset(R.dimen.app_views_elevation));
        ViewUtils.INSTANCE.addShadow(popupView);
        popupView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        popupView.setGravity(Gravity.CENTER);
        popupView.setIncludeFontPadding(false);
        popupView.setSingleLine(true);
        popupView.setTypeface(TypeFace.INSTANCE.getTypeFace(
                AppearancePreferences.INSTANCE.getAppFont(),
                TypeFace.TypefaceStyle.BOLD.getStyle(),
                context));
        popupView.setTextColor(Utils.getColorFromAttrRes(android.R.attr.textColorPrimaryInverse, context));
        popupView.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.fast_scroller_popup_text_size));
    };
    
    private PopupStyles() {
    
    }
}
