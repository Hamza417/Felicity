package app.simple.felicity.decorations.views;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;
import app.simple.felicity.R;
import app.simple.felicity.preferences.BehaviourPreferences;
import app.simple.felicity.theme.managers.ThemeManager;

public class Loader extends AppCompatImageView {
    
    private ValueAnimator rotateAnimator;
    private static long ANIMATION_DURATION = BehaviourPreferences.INSTANCE.getAnimationDuration() * 2;
    
    public Loader(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public Loader(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        setImageResource(R.drawable.ic_felicity_full);
        animateRotation(ValueAnimator.INFINITE, new LinearInterpolator());
        int padding = getResources().getDimensionPixelSize(R.dimen.padding_5);
        setPadding(padding, padding, padding, padding);
        setImageTintList(ColorStateList.valueOf(ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor()));
        
        setFocusable(false);
        setClickable(false);
    }
    
    public void loaded() {
        clearAnimation();
        animateRotation(0, new LinearOutSlowInInterpolator());
        animateColor(Color.parseColor("#27ae60"));
    }
    
    public void error() {
        clearAnimation();
        animateRotation(0, new LinearOutSlowInInterpolator());
        animateColor(Color.parseColor("#a93226"));
    }
    
    public void reset() {
        clearAnimation();
        setImageResource(R.drawable.ic_felicity);
        startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.loader));
        // Clear tint
        setImageTintList(null);
        setVisibility(View.VISIBLE);
    }
    
    private void animateColor(int toColor) {
        ValueAnimator valueAnimator = ValueAnimator.ofArgb(getDefaultColor(), toColor);
        valueAnimator.setInterpolator(new LinearOutSlowInInterpolator());
        valueAnimator.setDuration(ANIMATION_DURATION);
        valueAnimator.addUpdateListener(animation -> setImageTintList(ColorStateList.valueOf((int) animation.getAnimatedValue())));
        valueAnimator.start();
    }
    
    private void animateRotation(int repeatCount, TimeInterpolator interpolator) {
        rotateAnimator = ValueAnimator.ofFloat(getRotation(), 360);
        rotateAnimator.setInterpolator(interpolator);
        rotateAnimator.setDuration(ANIMATION_DURATION);
        rotateAnimator.addUpdateListener(animation -> setRotation((float) animation.getAnimatedValue()));
        rotateAnimator.setRepeatCount(repeatCount);
        rotateAnimator.start();
    }
    
    private int getDefaultColor() {
        try {
            return Objects.requireNonNull(getImageTintList()).getDefaultColor();
        } catch (NullPointerException e) {
            return ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor();
        }
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        clearAnimation();
    }
    
    @Override
    public void clearAnimation() {
        rotateAnimator.cancel();
        super.clearAnimation();
    }
}
