package app.simple.felicity.decorations.carouselview.ui.transformer;

import android.view.View;

import app.simple.felicity.decorations.carouselview.ui.manager.CarouselLayoutManager;
import app.simple.felicity.decorations.carouselview.ui.scrolltweaker.InverseScroller;
import app.simple.felicity.decorations.carouselview.ui.widget.CarouselView;

/**
 * Time-machine like carousel.
 *
 * @author sunny-chung
 */

public class TimeMachineViewTransformer implements CarouselView.ViewTransformer {
    protected static final float translationXRate = (-1) * 0.5f;
    
    @Override
    public void onAttach(CarouselLayoutManager layoutManager) {
        layoutManager.setScroller(new InverseScroller());
        layoutManager.setDrawOrder(CarouselView.DrawOrder.FirstFront);
    }
    
    @Override
    public void transform(View view, float position) {
        int width = view.getMeasuredWidth(), height = view.getMeasuredHeight();
        view.setTranslationX(width * position * translationXRate * (2f / (Math.abs(position) + 2)));
        view.setScaleX(2f / (position + 2));
        view.setScaleY(2f / (position + 2));
        view.setAlpha(position < 0 ? Math.max(1 + position, 0) : 1);
    }
}
