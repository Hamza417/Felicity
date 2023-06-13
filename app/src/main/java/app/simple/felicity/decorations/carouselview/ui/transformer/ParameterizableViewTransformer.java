package app.simple.felicity.decorations.carouselview.ui.transformer;

import android.view.View;

import app.simple.felicity.decorations.carouselview.ui.manager.CarouselLayoutManager;
import app.simple.felicity.decorations.carouselview.ui.widget.CarouselView;

/**
 * A super-class implementation of ViewTransformer allowing to alter common transform properties.
 * <p>
 * Developers to choose what methods to be public for users to alter.
 *
 * @author sunny-chung
 * @see ParameterizedViewTransformer
 * @see LinearViewTransformer
 * @see WheelViewTransformer
 * @see CoverFlowViewTransformer
 */

public abstract class ParameterizableViewTransformer implements CarouselView.ViewTransformer {
    protected static final float EPS = 1e-3f;
    
    protected float mOffsetXPercent = 0f, mOffsetYPercent = 0f;
    protected float mMinScaleX = Float.NaN, mMaxScaleX = Float.NaN, mScaleXOffset = Float.NaN, mScaleXFactor = Float.NaN;
    protected float mMinScaleY = Float.NaN, mMaxScaleY = Float.NaN, mScaleYOffset = Float.NaN, mScaleYFactor = Float.NaN;
    protected float mRotateDegree = Float.NaN;
    protected boolean mScaleLargestAtCenter = false;
    
    protected float mRotateDistFactor = Float.NaN; // depends on mRotateDegree
    
    protected ParameterizableViewTransformer() {
    
    }
    
    private static boolean isNonZero(float f) {
        return f > EPS || f < -EPS;
    }
    
    @Override
    public void onAttach(CarouselLayoutManager layoutManager) {
    
    }
    
    protected float getOffsetXPercent() {
        return mOffsetXPercent;
    }
    
    protected void setOffsetXPercent(float offsetXPercent) {
        mOffsetXPercent = offsetXPercent;
    }
    
    protected float getOffsetYPercent() {
        return mOffsetYPercent;
    }
    
    protected void setOffsetYPercent(float offsetYPercent) {
        mOffsetYPercent = offsetYPercent;
    }
    
    protected float getRotateDegree() {
        return mRotateDegree;
    }
    
    protected void setRotateDegree(float rotateDegree) {
        mRotateDegree = rotateDegree;
        if (Float.isNaN(rotateDegree)) {
            mRotateDistFactor = Float.NaN;
        } else if (!isNonZero(rotateDegree)) {
            mRotateDistFactor = 0;
        } else {
            mRotateDistFactor = (float) (1 / Math.sin(Math.toRadians(rotateDegree)));
        }
    }
    
    protected float getMinScaleX() {
        return mMinScaleX;
    }
    
    protected void setMinScaleX(float minScaleX) {
        mMinScaleX = minScaleX;
    }
    
    protected float getMaxScaleX() {
        return mMaxScaleX;
    }
    
    protected void setMaxScaleX(float maxScaleX) {
        mMaxScaleX = maxScaleX;
    }
    
    protected float getScaleXFactor() {
        return mScaleXFactor;
    }
    
    protected void setScaleXFactor(float scaleXFactor) {
        mScaleXFactor = scaleXFactor;
    }
    
    protected float getMinScaleY() {
        return mMinScaleY;
    }
    
    protected void setMinScaleY(float minScaleY) {
        mMinScaleY = minScaleY;
    }
    
    protected float getMaxScaleY() {
        return mMaxScaleY;
    }
    
    protected void setMaxScaleY(float maxScaleY) {
        mMaxScaleY = maxScaleY;
    }
    
    protected float getScaleYFactor() {
        return mScaleYFactor;
    }
    
    protected void setScaleYFactor(float scaleYFactor) {
        mScaleYFactor = scaleYFactor;
    }
    
    protected float getScaleXOffset() {
        return mScaleXOffset;
    }
    
    protected void setScaleXOffset(float scaleXOffset) {
        mScaleXOffset = scaleXOffset;
    }
    
    protected float getScaleYOffset() {
        return mScaleYOffset;
    }
    
    protected void setScaleYOffset(float scaleYOffset) {
        mScaleYOffset = scaleYOffset;
    }
    
    protected boolean isScaleLargestAtCenter() {
        return mScaleLargestAtCenter;
    }
    
    protected void setScaleLargestAtCenter(boolean scaleLargestAtCenter) {
        mScaleLargestAtCenter = scaleLargestAtCenter;
    }
    
    @Override
    public void transform(View view, float position) {
        int width = view.getMeasuredWidth(), height = view.getMeasuredHeight();
        //			int width = view.getWidth(), height = view.getHeight();
        
        if (!Float.isNaN(mScaleXFactor)) {
            float scale = (mScaleLargestAtCenter ? 1 - Math.abs(position) : position) * mScaleXFactor;
            if (!Float.isNaN(mScaleXOffset)) {
                scale += mScaleXOffset;
            }
            if (!Float.isNaN(mMinScaleX)) {
                scale = Math.max(mMinScaleX, scale);
            }
            if (!Float.isNaN(mMaxScaleX)) {
                scale = Math.min(mMaxScaleX, scale);
            }
            view.setPivotX(width / 2.0f);
            view.setPivotY(height / 2.0f);
            view.setScaleX(scale);
        }
        
        if (!Float.isNaN(mScaleYFactor)) {
            float scale = (mScaleLargestAtCenter ? 1 - Math.abs(position) : position) * mScaleYFactor;
            if (!Float.isNaN(mScaleYOffset)) {
                scale += mScaleYOffset;
            }
            if (!Float.isNaN(mMinScaleY)) {
                scale = Math.max(mMinScaleY, scale);
            }
            if (!Float.isNaN(mMaxScaleY)) {
                scale = Math.min(mMaxScaleY, scale);
            }
            view.setPivotX(width / 2.0f);
            view.setPivotY(height / 2.0f);
            view.setScaleY(scale);
        }
        
        if (isNonZero(mRotateDegree)) {
            view.setPivotX(width / 2.0f);
            view.setPivotY(height + width * mRotateDistFactor);
            view.setRotation(position * mRotateDegree);
        }
        
        if (isNonZero(mOffsetXPercent)) {
            view.setTranslationX(position * width * mOffsetXPercent);
        }
        
        if (isNonZero(mOffsetYPercent)) {
            view.setTranslationY(position * height * mOffsetYPercent);
        }
    }
}
