package app.simple.felicity.decorations.circular;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import app.simple.felicity.theme.interfaces.ThemeChangedListener;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.models.Accent;

public class CircularImageImageButton extends AppCompatImageButton implements ThemeChangedListener {
    
    private ShapeDrawable backgroundDrawable;
    private RippleDrawable rippleDrawable;
    private ValueAnimator valueAnimator;
    
    public CircularImageImageButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public CircularImageImageButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        backgroundDrawable = new ShapeDrawable(new OvalShape());
        rippleDrawable = Utils.getRippleDrawable(ThemeManager.INSTANCE.getAccent().getSecondaryAccentColor());
        backgroundDrawable.getPaint().setColor(ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor());
        setBackground(backgroundDrawable);
        setScaleType(ScaleType.FIT_CENTER);
        setImageTintList(ColorStateList.valueOf(Color.WHITE));
        setForeground(rippleDrawable);
    }
    
    @Override
    public void onAccentChanged(@NonNull Accent accent) {
        animateColorChange(accent.getPrimaryAccentColor());
        rippleDrawable.setColor(ColorStateList.valueOf(accent.getSecondaryAccentColor()));
    }
    
    private void animateColorChange(int color) {
        if (valueAnimator != null && valueAnimator.isRunning()) {
            valueAnimator.cancel();
        }
        
        valueAnimator = ValueAnimator.ofArgb(backgroundDrawable.getPaint().getColor(), color);
        valueAnimator.addUpdateListener(animation -> {
            backgroundDrawable.getPaint().setColor((int) animation.getAnimatedValue());
            invalidate();
        });
        
        valueAnimator.setDuration(500);
        valueAnimator.start();
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (valueAnimator != null) {
            valueAnimator.cancel();
            valueAnimator = null;
        }
    }
}
