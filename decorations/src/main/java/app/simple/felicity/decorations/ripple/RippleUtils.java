package app.simple.felicity.decorations.ripple;

import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import com.google.android.material.animation.ArgbEvaluatorCompat;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

import app.simple.felicity.preferences.AppearancePreferences;
import app.simple.felicity.theme.managers.ThemeManager;

public class RippleUtils {
    
    static void animateBackground(int endColor, View view) {
        view.clearAnimation();
        ValueAnimator valueAnimator = ValueAnimator.ofObject(new ArgbEvaluatorCompat(),
                view.getBackgroundTintList().getDefaultColor(),
                endColor);
        valueAnimator.setDuration(300L);
        valueAnimator.setInterpolator(new DecelerateInterpolator(1.5F));
        valueAnimator.addUpdateListener(animation -> view.setBackgroundTintList(ColorStateList.valueOf((int) animation.getAnimatedValue())));
        valueAnimator.start();
    }
    
    public static void animateBackground(int endColor, ViewGroup view) {
        view.clearAnimation();
        ValueAnimator valueAnimator = ValueAnimator.ofObject(new ArgbEvaluatorCompat(),
                view.getBackgroundTintList().getDefaultColor(),
                endColor);
        valueAnimator.setDuration(300L);
        valueAnimator.setInterpolator(new DecelerateInterpolator(1.5F));
        valueAnimator.addUpdateListener(animation -> view.setBackgroundTintList(ColorStateList.valueOf((int) animation.getAnimatedValue())));
        valueAnimator.start();
    }
    
    public static FelicityRippleDrawable getRippleDrawable(Drawable backgroundDrawable) {
        FelicityRippleDrawable ripple = new FelicityRippleDrawable(ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor());
        ripple.setCornerRadius(AppearancePreferences.INSTANCE.getCornerRadius());
        ripple.setStartColor(ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getHighlightColor());
        return ripple;
    }
    
    public static void setForegroundDrawable(View view) {
        if (view.getForeground() instanceof FelicityRippleDrawable) {
            return;
        }
        
        FelicityRippleDrawable ripple = new FelicityRippleDrawable(ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor());
        ripple.setCornerRadius(AppearancePreferences.INSTANCE.getCornerRadius());
        ripple.setStartColor(ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getHighlightColor());
        
        view.setForeground(ripple);
        view.setClickable(true);
    }
    
    static MaterialShapeDrawable getRoundedBackground(float divisiveFactor) {
        return new MaterialShapeDrawable(new ShapeAppearanceModel()
                .toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, AppearancePreferences.INSTANCE.getCornerRadius() / divisiveFactor)
                .build());
    }
}
