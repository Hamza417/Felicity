package app.simple.felicity.decorations.carouselview.ui.scrolltweaker;

import app.simple.felicity.decorations.carouselview.ui.widget.CarouselView;

/**
 * Scroller with inverse scrolling directions.
 *
 * @author sunny-chung
 */

public class InverseScroller implements CarouselView.Scroller {
    @Override
    public int tweakScrollDx(int dx) {
        return -dx;
    }
    
    @Override
    public int tweakScrollDy(int dy) {
        return -dy;
    }
    
    @Override
    public int inverseTweakScrollDx(int dx) {
        return -dx;
    }
    
    @Override
    public int inverseTweakScrollDy(int dy) {
        return -dy;
    }
    
    @Override
    public float tweakScrollDx(float dx) {
        return -dx;
    }
    
    @Override
    public float tweakScrollDy(float dy) {
        return -dy;
    }
}
