package app.simple.felicity.decorations.views;

import android.content.Context;
import android.util.AttributeSet;

import com.smarteist.autoimageslider.SliderAnimations;
import com.smarteist.autoimageslider.SliderView;

public class FelicityArtPager extends SliderView {
    public FelicityArtPager(Context context) {
        super(context);
        init();
    }
    
    public FelicityArtPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public FelicityArtPager(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        if (isInEditMode()) {
            return;
        }
        
        // setSliderAnimationDuration(getResources().getInteger(R.integer.art_flow_anim_duration), new LinearOutSlowInInterpolator());
        setSliderTransformAnimation(SliderAnimations.SIMPLETRANSFORMATION);
        // setScrollTimeInSec(3);
        // setAutoCycleDirection(SliderView.AUTO_CYCLE_DIRECTION_RIGHT);
        // setIndicatorAnimationDuration(getResources().getInteger(R.integer.art_flow_anim_duration));
        // setIndicatorSelectedColor(ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor());
        // setIndicatorUnselectedColor(ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getHighlightColor());
        setIndicatorVisibility(false);
        // setIndicatorGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        // setIndicatorAnimation(IndicatorAnimationType.DROP);
        // setIndicatorAnimationDuration(getResources().getInteger(R.integer.art_flow_anim_duration));
        setIndicatorRadius(0);
        // setAutoCycle(true);
        // startAutoCycle();
    }
}
