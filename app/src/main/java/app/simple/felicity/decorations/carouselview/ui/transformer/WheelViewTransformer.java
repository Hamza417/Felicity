package app.simple.felicity.decorations.carouselview.ui.transformer;

/**
 * <p>Simulates the effect of a wheel.</p>
 * <p>
 * Available parameters: <br/>
 * <ul>
 *     <li>{@link #setRotateDegree(float)} rotation in degree. Default 30</li>
 *     <li>{@link #setScaleLargestAtCenter(boolean)} whether the current item should be scaled largest (inversely to scale rate). Default false</li>
 *     <li>{@link #setScaleXFactor(float)} linear X-axis scale rate</li>
 *     <li>{@link #setScaleYFactor(float)} linear Y-axis scale rate</li>
 *     <li>{@link #setMinScaleX(float)} minimum X-axis scale</li>
 *     <li>{@link #setMinScaleY(float)} minimum Y-axis scale</li>
 *     <li>{@link #setMaxScaleX(float)} maximum X-axis scale</li>
 *     <li>{@link #setMaxScaleY(float)} maximum Y-axis scale</li>
 * </ul>
 *
 * @author sunny-chung
 */

public class WheelViewTransformer extends ParameterizableViewTransformer {
    
    public WheelViewTransformer() {
        setRotateDegree(30);
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
    public boolean isScaleLargestAtCenter() {
        return super.isScaleLargestAtCenter();
    }
    
    @Override
    public void setScaleLargestAtCenter(boolean scaleLargestAtCenter) {
        super.setScaleLargestAtCenter(scaleLargestAtCenter);
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
    public float getScaleYFactor() {
        return super.getScaleYFactor();
    }
    
    @Override
    public void setScaleYFactor(float scaleYFactor) {
        super.setScaleYFactor(scaleYFactor);
    }
    
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
}
