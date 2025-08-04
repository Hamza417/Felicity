package app.simple.felicity.decorations.circular;

import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;

import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.Arrays;

import app.simple.felicity.preferences.AppearancePreferences;

public class Utils {
    public static RippleDrawable getRippleDrawable(int color) {
        float[] outerRadii = new float[8];
        float[] innerRadii = new float[8];
        
        try {
            Arrays.fill(outerRadii, AppearancePreferences.INSTANCE.getCornerRadius());
            Arrays.fill(innerRadii, AppearancePreferences.INSTANCE.getCornerRadius());
        } catch (NullPointerException e) {
            // Fallback to default corner radius if AppearancePreferences is not initialized
            Arrays.fill(outerRadii, 16F);
            Arrays.fill(innerRadii, 16F);
        }
        
        RoundRectShape shape = new RoundRectShape(outerRadii, null, innerRadii);
        ShapeDrawable mask = new ShapeDrawable(shape);
        
        ColorStateList stateList = ColorStateList.valueOf(color);
        
        return new RippleDrawable(stateList, null, mask);
    }
    
    public static ShapeDrawable getCircularBackgroundDrawable(int color) {
        float[] outerRadii = new float[8];
        float[] innerRadii = new float[8];
        
        try {
            Arrays.fill(outerRadii, AppearancePreferences.INSTANCE.getCornerRadius());
            Arrays.fill(innerRadii, AppearancePreferences.INSTANCE.getCornerRadius());
        } catch (NullPointerException e) {
            // Fallback to default corner radius if AppearancePreferences is not initialized
            Arrays.fill(outerRadii, 16F);
            Arrays.fill(innerRadii, 16F);
        }
        
        RoundRectShape shape = new RoundRectShape(outerRadii, null, innerRadii);
        ShapeDrawable drawable = new ShapeDrawable(shape);
        drawable.getPaint().setColor(color);
        
        return drawable;
    }
    
    public static ValueAnimator animateColorChange(ShapeDrawable drawable, int color) {
        ValueAnimator valueAnimator = ValueAnimator.ofArgb(drawable.getPaint().getColor(), color);
        valueAnimator.addUpdateListener(animation -> {
            drawable.getPaint().setColor((int) animation.getAnimatedValue());
            drawable.invalidateSelf();
        });
        valueAnimator.setDuration(300); // Set duration for the animation
        valueAnimator.start();
        return valueAnimator;
    }
    
    public static MaterialShapeDrawable getRoundedBackground(float divisiveFactor) {
        return new MaterialShapeDrawable(new ShapeAppearanceModel()
                .toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, AppearancePreferences.INSTANCE.getCornerRadius() / divisiveFactor)
                .build());
    }
}