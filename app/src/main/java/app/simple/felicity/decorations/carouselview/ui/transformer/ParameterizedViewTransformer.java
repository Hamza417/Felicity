package app.simple.felicity.decorations.carouselview.ui.transformer;

import android.view.View;

/**
 * <p>A carousel with limited set of customizable parameters.
 * Refer to {@link ParameterizableViewTransformer#transform(View, float)} for implementation details. </p>
 * <p>
 * Available parameters: <br/>
 * <ul>
 *     <li>{@link #setOffsetXPercent(float)} linear X-axis translation rate. Default 1</li>
 *     <li>{@link #setOffsetYPercent(float)} linear Y-axis translation rate. Default 0</li>
 *     <li>{@link #setScaleXFactor(float)} linear X-axis scale rate</li>
 *     <li>{@link #setScaleYFactor(float)} linear Y-axis scale rate</li>
 *     <li>{@link #setScaleXOffset(float)} X-axis scale offset after multiplied by scaleXFactor</li>
 *     <li>{@link #setScaleYOffset(float)} Y-axis scale offset after multiplied by scaleYFactor</li>
 *     <li>{@link #setMinScaleX(float)} minimum X-axis scale</li>
 *     <li>{@link #setMinScaleY(float)} minimum Y-axis scale</li>
 *     <li>{@link #setMaxScaleX(float)} maximum X-axis scale</li>
 *     <li>{@link #setMaxScaleY(float)} maximum Y-axis scale</li>
 *     <li>{@link #setScaleLargestAtCenter(boolean)} whether the current item should be scaled largest (inversely to scale rate). Default false</li>
 *     <li>{@link #setRotateDegree(float)} rotation  in degree</li>
 * </ul>
 * <p>
 * Explanations: <br/>
 * <ul>
 *     <li>TranslationX = item view width * offsetXPercent * position</li>
 *     <li>ScaleX = [MinScaleX |<=| ScaleXFactor * position + ScaleXOffset |<=| MaxScaleX]</li>
 *     <li>similar for those applied to Y-axis</li>
 * </ul>
 *
 * @author sunny-chung
 */

public class ParameterizedViewTransformer extends ParameterizableViewTransformer {
    
    @Override
    public float getMaxScaleX() {
        return super.getMaxScaleX();
    }
    
    @Override
    public void setMaxScaleX(float maxScaleX) {
        super.setMaxScaleX(maxScaleX);
    }
    
    @Override
    public float getMaxScaleY() {
        return super.getMaxScaleY();
    }
    
    @Override
    public void setMaxScaleY(float maxScaleY) {
        super.setMaxScaleY(maxScaleY);
    }
    
    @Override
    public float getMinScaleX() {
        return super.getMinScaleX();
    }
    
    @Override
    public void setMinScaleX(float minScaleX) {
        super.setMinScaleX(minScaleX);
    }
    
    @Override
    public float getMinScaleY() {
        return super.getMinScaleY();
    }
    
    @Override
    public void setMinScaleY(float minScaleY) {
        super.setMinScaleY(minScaleY);
    }
    
    @Override
    public float getOffsetXPercent() {
        return super.getOffsetXPercent();
    }
    
    @Override
    public void setOffsetXPercent(float offsetXPercent) {
        super.setOffsetXPercent(offsetXPercent);
    }
    
    @Override
    public float getOffsetYPercent() {
        return super.getOffsetYPercent();
    }
    
    @Override
    public void setOffsetYPercent(float offsetYPercent) {
        super.setOffsetYPercent(offsetYPercent);
    }
    
    @Override
    public float getRotateDegree() {
        return super.getRotateDegree();
    }
    
    @Override
    public void setRotateDegree(float rotateDegree) {
        super.setRotateDegree(rotateDegree);
    }
    
    @Override
    public float getScaleXFactor() {
        return super.getScaleXFactor();
    }
    
    @Override
    public void setScaleXFactor(float scaleXFactor) {
        super.setScaleXFactor(scaleXFactor);
    }
    
    @Override
    public float getScaleXOffset() {
        return super.getScaleXOffset();
    }
    
    @Override
    public void setScaleXOffset(float scaleXOffset) {
        super.setScaleXOffset(scaleXOffset);
    }
    
    @Override
    public float getScaleYFactor() {
        return super.getScaleYFactor();
    }
    
    @Override
    public void setScaleYFactor(float scaleYFactor) {
        super.setScaleYFactor(scaleYFactor);
    }
    
    @Override
    public float getScaleYOffset() {
        return super.getScaleYOffset();
    }
    
    @Override
    public void setScaleYOffset(float scaleYOffset) {
        super.setScaleYOffset(scaleYOffset);
    }
    
    @Override
    public boolean isScaleLargestAtCenter() {
        return super.isScaleLargestAtCenter();
    }
    
    @Override
    public void setScaleLargestAtCenter(boolean scaleLargestAtCenter) {
        super.setScaleLargestAtCenter(scaleLargestAtCenter);
    }
}
