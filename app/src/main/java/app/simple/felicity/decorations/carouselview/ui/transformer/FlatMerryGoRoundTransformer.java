package app.simple.felicity.decorations.carouselview.ui.transformer;

import android.view.View;

import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import app.simple.felicity.decorations.carouselview.ui.manager.CarouselLayoutManager;
import app.simple.felicity.decorations.carouselview.ui.widget.CarouselView;

/**
 * Simulate a Merry-go-round carousel. <br/>
 * <br/>
 * This is a 'flat' version that item views are not rotated and always face front. <br/>
 * <br/>
 * Available parameters: <br/>
 * <ul>
 *   <li>{@link #setNumPies(int)} number of item views to be distributed across the circle evenly. Default 7</li>
 *   <li>{@link #setHorizontalViewPort(double)} % of parent width to be used for display. Default 80%</li>
 *   <li>{@link #setViewPerspective(double)} normalized camera height. Default 0.6</li>
 *   <li>{@link #setFarScale(double)} scale of itemview when it is layout at a far distance (y = 0). Default 0.35</li>
 *   <li>{@link #setFarAlpha(double)} alpha of itemview when it is layout at a far distance (y = 0). Default 0</li>
 * </ul>
 *
 * @author sunny-chung
 */

public class FlatMerryGoRoundTransformer implements CarouselView.ViewTransformer {
    private int mNumPies = 7;
    private double mPieRad = Math.PI * 2.0 / mNumPies; // depends on mNumPies
    
    private double mHorizontalViewPort = 0.8;
    private double mViewPerspective = 0.6;
    private double mFarScale = 0.35;
    private double mFarAlpha = 0;
    
    @Override
    public void onAttach(CarouselLayoutManager layoutManager) {
        layoutManager.setDrawOrder(CarouselView.DrawOrder.CenterFront);
    }
    
    public int getNumPies() {
        return mNumPies;
    }
    
    public void setNumPies(@IntRange (from = 1) int numPies) {
        mNumPies = numPies;
        mPieRad = Math.PI * 2.0 / numPies;
    }
    
    public double getHorizontalViewPort() {
        return mHorizontalViewPort;
    }
    
    public void setHorizontalViewPort(@FloatRange (from = 0.0, to = 1.0, fromInclusive = false) double horizontalViewPort) {
        mHorizontalViewPort = horizontalViewPort;
    }
    
    public double getViewPerspective() {
        return mViewPerspective;
    }
    
    public void setViewPerspective(double viewPerspective) {
        mViewPerspective = viewPerspective;
    }
    
    public double getFarAlpha() {
        return mFarAlpha;
    }
    
    public void setFarAlpha(@FloatRange (from = 0.0, to = 1.0) double farAlpha) {
        mFarAlpha = farAlpha;
    }
    
    public double getFarScale() {
        return mFarScale;
    }
    
    public void setFarScale(double farScale) {
        mFarScale = farScale;
    }
    
    @Override
    public void transform(View view, float position) {
        int width = view.getMeasuredWidth(), height = view.getMeasuredHeight();
        int parentWidth = ((View) view.getParent()).getMeasuredWidth();
        int parentHeight = ((View) view.getParent()).getMeasuredHeight();
        
        // create perspective view by
        // compressing height of circle linearly to ellipse
        // and proportioning scale and alpha linearly
        
        // ellipse formula:
        //   x(t) = a cos t
        //   y(t) = b sin t
        //  where t ∈ [0, 2π]
        
        double rotateRad = Math.PI * 1.5 + position * mPieRad;
        double a = parentWidth * mHorizontalViewPort / 2.0;
        double b = a * mViewPerspective;
        
        double x = a * Math.cos(rotateRad);
        double y = b * (1 - Math.sin(rotateRad)); // (1 - sin t) because y axis is reversed
        
        double maxY = 2 * b; // since maximum of (1 - sin t) => 2
        
        // TODO scale should depend on mViewPerspective as well
        double scale = Math.max(0, (mFarScale - 1) * (y - maxY) / (0 - maxY) + 1);
        double alpha = Math.max(0, (mFarAlpha - 1) * (y - maxY) / (0 - maxY) + 1);
        
        y -= maxY / 2; // reposition center so that y ∈ [-maxY/2, maxY/2]
        
        //        y += (parentHeight - maxY - height) / 2; // center vertically
        
        view.setTranslationX((float) x);
        view.setTranslationY((float) y);
        view.setScaleX((float) scale);
        view.setScaleY((float) scale);
        view.setAlpha((float) alpha);
    }
}
