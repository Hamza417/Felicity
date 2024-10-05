package app.simple.felicity.decorations.fastscroll;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.util.Consumer;
import app.simple.felicity.core.constants.TypeFaceConstants;
import app.simple.felicity.core.constants.TypeFaceConstants.TypefaceStyle;
import app.simple.felicity.core.utils.ViewUtils;
import app.simple.felicity.decoration.R;
import app.simple.felicity.decorations.fastscroll.backgrounds.CircularBackground;
import app.simple.felicity.preferences.AppearancePreferences;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.utils.TypeFace;

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
        ViewUtils.INSTANCE.addShadow(popupView, ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor());
        popupView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        popupView.setGravity(Gravity.CENTER);
        popupView.setIncludeFontPadding(false);
        popupView.setSingleLine(true);
        popupView.setTypeface(TypeFace.INSTANCE.getTypeFace(
                AppearancePreferences.INSTANCE.getAppFont(),
                TypefaceStyle.BOLD.getStyle(),
                context));
        popupView.setTextColor(Color.WHITE);
        popupView.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.fast_scroller_popup_text_size));
    };
    
    private PopupStyles() {
    
    }
}
