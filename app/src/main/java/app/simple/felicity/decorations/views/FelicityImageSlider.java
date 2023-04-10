package app.simple.felicity.decorations.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.animation.DecelerateInterpolator;

import com.smarteist.autoimageslider.SliderAnimations;
import com.smarteist.autoimageslider.SliderView;

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
        setSliderAnimationDuration(500, new DecelerateInterpolator());
        setSliderTransformAnimation(SliderAnimations.SIMPLETRANSFORMATION);
        setScrollTimeInSec(3);
        setAutoCycleDirection(SliderView.AUTO_CYCLE_DIRECTION_BACK_AND_FORTH);
        setIndicatorAnimationDuration(500);
        setIndicatorSelectedColor(ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor());
        setIndicatorUnselectedColor(ThemeManager.INSTANCE.getTheme().getTextViewTheme().getSecondaryTextColor());
        setIndicatorVisibility(true);
        setIndicatorGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        setAutoCycle(true);
        startAutoCycle();
    }
}
