package app.simple.felicity.decorations.carouselview.ui.transformer;

/**
 * <p>Linear carousel. </p>
 *
 * <p>Default it is a horizontal linear carousel.
 * You may set parameter offsetXPercent=0, offsetYPercent=1 for a vertical linear carousel.</p>
 * <p>
 * Available parameters: <br/>
 * <ul>
 *     <li>{@link #setOffsetXPercent(float)} linear X-axis translation rate. Default 1</li>
 *     <li>{@link #setOffsetYPercent(float)} linear Y-axis translation rate. Default 0</li>
 *     <li>{@link #setScaleXFactor(float)} linear X-axis scale rate</li>
 *     <li>{@link #setScaleYFactor(float)} linear Y-axis scale rate</li>
 *     <li>{@link #setMinScaleX(float)} minimum X-axis scale</li>
 *     <li>{@link #setMinScaleY(float)} minimum Y-axis scale</li>
 *     <li>{@link #setMaxScaleX(float)} maximum X-axis scale</li>
 *     <li>{@link #setMaxScaleY(float)} maximum Y-axis scale</li>
 *     <li>{@link #setScaleLargestAtCenter(boolean)} whether the current item should be scaled largest (inversely to scale rate). Default false</li>
 * </ul>
 *
 * @author sunny-chung
 */

public class LinearViewTransformer extends ParameterizableViewTransformer {
    public LinearViewTransformer() {
        setOffsetXPercent(1);
        setOffsetYPercent(0);
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
    
    @Override
    public boolean isScaleLargestAtCenter() {
        return super.isScaleLargestAtCenter();
    }
    
    @Override
    public void setScaleLargestAtCenter(boolean scaleLargestAtCenter) {
        super.setScaleLargestAtCenter(scaleLargestAtCenter);
    }
}
