package app.simple.felicity.decorations.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;

import app.simple.felicity.preferences.AccessibilityPreferences;

public class PopupLinearLayout extends LinearLayout {
    
    private static final String TAG = "PopupLinearLayout";
    
    private final long DURATION = 200;
    
    public PopupLinearLayout(Context context) {
        super(context);
        init();
    }
    
    public PopupLinearLayout(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        init();
    }
    
    public PopupLinearLayout(Context context, int orientation) {
        super(context);
        init(orientation);
    }
    
    public PopupLinearLayout(Context context, AttributeSet attributeSet, int diff) {
        super(context, attributeSet, diff);
        init(diff);
    }
    
    private void init() {
        setClipToPadding(false);
        setClipChildren(false);
        setOrientation(LinearLayout.VERTICAL);
        animateChildren();
    }
    
    private void init(int orientation) {
        setOrientation(orientation);
        animateChildren();
    }
    
    private void animateChildren() {
        if (!isInEditMode()) {
            if (!AccessibilityPreferences.INSTANCE.isAnimationReduced()) {
                post(() -> {
                    for (int i = 0; i < getChildCount(); i++) {
                        getChildAt(i).setAlpha(0);
                        getChildAt(i).setScaleY(0.9F);
                        getChildAt(i).setScaleX(0.9F);
                        
                        getChildAt(i).animate()
                                .setInterpolator(new DecelerateInterpolator())
                                .scaleX(1)
                                .scaleY(1)
                                .alpha(1)
                                .setDuration(DURATION)
                                .setStartDelay(DURATION + (i * 35L));
                    }
                });
            }
        }
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).animate().cancel();
            getChildAt(i).clearAnimation();
        }
    }
}