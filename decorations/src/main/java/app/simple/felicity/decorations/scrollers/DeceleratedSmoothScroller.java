package app.simple.felicity.decorations.scrollers;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.animation.DecelerateInterpolator;

import androidx.recyclerview.widget.LinearSmoothScroller;

public class DeceleratedSmoothScroller extends LinearSmoothScroller {
    
    private static final float MILLISECONDS_PER_INCH = 750F; //default is 25f (bigger = slower)
    private final DecelerateInterpolator interpolator = new DecelerateInterpolator();
    
    public DeceleratedSmoothScroller(Context context) {
        super(context);
    }
    
    @Override
    protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
        return MILLISECONDS_PER_INCH / displayMetrics.densityDpi;
    }
    
    @Override
    protected int calculateTimeForScrolling(int dx) {
        int time = super.calculateTimeForScrolling(dx);
        return (int) interpolator.getInterpolation(time);
    }
}
