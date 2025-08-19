package app.simple.felicity.decorations.views;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;

import com.smarteist.autoimageslider.IndicatorView.animation.type.IndicatorAnimationType;
import com.smarteist.autoimageslider.SliderAnimations;
import com.smarteist.autoimageslider.SliderView;

import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import app.simple.felicity.decoration.R;
import app.simple.felicity.theme.managers.ThemeManager;

public class FelicityImageSlider extends SliderView {
    public FelicityImageSlider(Context context) {
        super(context);
        init();
    }
    
    public FelicityImageSlider(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public FelicityImageSlider(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        if (isInEditMode()) {
            return;
        }
        
        setSliderAnimationDuration(getResources().getInteger(R.integer.art_flow_anim_duration), new FastOutSlowInInterpolator());
        setSliderTransformAnimation(SliderAnimations.SIMPLETRANSFORMATION);
        setScrollTimeInSec(3);
        setAutoCycleDirection(SliderView.AUTO_CYCLE_DIRECTION_RIGHT);
        setIndicatorSelectedColor(ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor());
        setIndicatorUnselectedColor(ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getHighlightColor());
        setIndicatorVisibility(true);
        setIndicatorGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        setIndicatorAnimation(IndicatorAnimationType.DROP);
        setIndicatorAnimationDuration(getResources().getInteger(R.integer.art_flow_anim_duration));
        setIndicatorRadius(2);
        setAutoCycle(true);
        startAutoCycle();
    }
    
    public void removeIndicators() {
        setIndicatorVisibility(false);
        setIndicatorSelectedColor(Color.TRANSPARENT);
        setIndicatorUnselectedColor(Color.TRANSPARENT);
    }
    
    public void restartCycle() {
        if (isAutoCycle()) {
            stopAutoCycle();
            startAutoCycle();
        }
    }
}
