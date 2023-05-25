package app.simple.felicity.decorations.carousel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

/**
 * @author Hamza417
 */
public class ArtFlowCarousel extends Carousel implements ViewTreeObserver.OnPreDrawListener {
    
    //reflection
    private final Matrix reflectionMatrix = new Matrix();
    private final Paint paint = new Paint();
    private final Paint reflectionPaint = new Paint();
    private final PorterDuffXfermode porterDuffXfermode = new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
    private final Canvas reflectionCanvas = new Canvas();
    /**
     * Widget size on which was tuning of parameters done. This value is used to scale parameters on when widgets has different size
     */
    private int tuningWidgetSize = 1280;
    /**
     * Distance from center as fraction of half of widget size where covers start to rotate into center
     * 1 means rotation starts on edge of widget, 0 means only center rotated
     */
    private float rotationThreshold = 0.3f;
    /**
     * Distance from center as fraction of half of widget size where covers start to zoom in
     * 1 means scaling starts on edge of widget, 0 means only center scaled
     */
    private float scalingThreshold = 1.3f;
    /**
     * Distance from center as fraction of half of widget size,
     * where covers start enlarge their spacing to allow for smooth passing each other without jumping over each other
     * 1 means edge of widget, 0 means only center
     */
    private float adjustPositionThreshold = 0.5f;
    /**
     * By enlarging this value, you can enlarge spacing in center of widget done by position adjustment
     */
    private float adjustPositionMultiplier = 1.5f;
    /**
     * Absolute value of rotation angle of cover at edge of widget in degrees
     */
    private float maxRotationAngle = 60.0f;
    /**
     * Scale factor of item in center
     */
    private float maxScaleFactor = 2.0f;
    /**
     * Radius of circle path which covers follow. Range of screen is -1 to 1, minimal radius is therefore 1
     */
    private float radius = 2f;
    /**
     * Size multiplier used to simulate perspective
     */
    private float perspectiveMultiplier = 1f;
    /**
     * Size of reflection as a fraction of original image (0-1)
     */
    private float reflectionHeight = 0.5f;
    /**
     * Starting opacity of reflection. Reflection fades from this value to transparency;
     */
    private int reflectionOpacity = 0x70;
    
    private boolean invalidated = false;
    
    public ArtFlowCarousel(Context context) {
        super(context);
    }
    
    public ArtFlowCarousel(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    public ArtFlowCarousel(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
    
    private void setTransformation(View v) {
        int c = getChildCenter(v);
        v.setRotationY(getRotationAngle(c) - getAngleOnCircle(c));
        v.setTranslationX(getChildAdjustPosition(v));
        float scale = getScaleFactor(c) - getChildCircularPathZOffset(c);
        v.setScaleX(scale);
        v.setScaleY(scale);
    }
    
    @Override
    protected void dispatchDraw(Canvas canvas) {
        int bitmask = Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG;
        canvas.setDrawFilter(new PaintFlagsDrawFilter(bitmask, bitmask));
        super.dispatchDraw(canvas);
    }
    
    @Override
    public void computeScroll() {
        super.computeScroll();
        for (int i = 0; i < getChildCount(); i++) {
            setTransformation(getChildAt(i));
        }
    }
    
    @Override
    protected int getPartOfViewCoveredBySibling() {
        return 0;
    }
    
    @Override
    protected View getViewFromAdapter(int position) {
        CoverFrame frame = (CoverFrame) viewCache.getCachedView();
        View recycled = null;
        if (frame != null) {
            recycled = frame.getChildAt(0);
        }
    
        View v = adapter.getView(position, recycled, this);
        if (frame == null) {
            frame = new CoverFrame(getContext(), v);
        } else {
            frame.setCover(v);
        }
        
        //to enable drawing cache
        frame.setLayerType(LAYER_TYPE_HARDWARE, null);
        frame.setDrawingCacheEnabled(true);
        
        return frame;
    }
    
    private float getRotationAngle(int childCenter) {
        return -maxRotationAngle * getClampedRelativePosition(getRelativePosition(childCenter), rotationThreshold * getWidgetSizeMultiplier());
    }
    
    private float getAngleOnCircle(int childCenter) {
        float x = getRelativePosition(childCenter) / radius;
        if (x < -1.0f) {
            x = -1.0f;
        }
        if (x > 1.0f) {
            x = 1.0f;
        }
        
        return (float) (Math.acos(x) / Math.PI * 180.0f - 90.0f);
    }
    
    private float getScaleFactor(int childCenter) {
        return 1 + (maxScaleFactor - 1) * (1 - Math.abs(getClampedRelativePosition(getRelativePosition(childCenter), scalingThreshold * getWidgetSizeMultiplier())));
    }
    
    /**
     * Clamps relative position by threshold, and produces values in range -1 to 1 directly usable for transformation computation
     *
     * @param position  value int range -1 to 1
     * @param threshold always positive value of threshold distance from center in range 0-1
     * @return clamped value in range -1 to 1
     */
    private float getClampedRelativePosition(float position, float threshold) {
        if (position < 0) {
            if (position < -threshold) {
                return -1f;
            } else {
                return position / threshold;
            }
        } else {
            if (position > threshold) {
                return 1;
            } else {
                return position / threshold;
            }
        }
    }
    
    /**
     * Calculates relative position on screen in range -1 to 1, widgets out of screen can have values ove 1 or -1
     *
     * @param pixexPos Absolute position in pixels including scroll offset
     * @return relative position
     */
    private float getRelativePosition(int pixexPos) {
        final int half = getWidth() / 2;
        final int centerPos = getScrollX() + half;
        
        return (pixexPos - centerPos) / ((float) half);
    }
    
    private float getWidgetSizeMultiplier() {
        return ((float) tuningWidgetSize) / ((float) getWidth());
    }
    
    private float getChildAdjustPosition(View child) {
        final int c = getChildCenter(child);
        final float crp = getClampedRelativePosition(getRelativePosition(c), adjustPositionThreshold * getWidgetSizeMultiplier());
    
        return childWidth * adjustPositionMultiplier * spacing * crp * getSpacingMultiplierOnCirlce(c);
    }
    
    private float getSpacingMultiplierOnCirlce(int childCenter) {
        float x = getRelativePosition(childCenter) / radius;
        return (float) Math.sin(Math.acos(x));
    }
    
    /**
     * Compute offset following path on circle
     *
     * @param childCenter center of child
     * @return offset from position on unitary circle
     */
    private float getOffsetOnCircle(int childCenter) {
        float x = getRelativePosition(childCenter) / radius;
        if (x < -1.0f) {
            x = -1.0f;
        }
        if (x > 1.0f) {
            x = 1.0f;
        }
        
        return (float) (1 - Math.sin(Math.acos(x)));
    }
    
    private float getChildCircularPathZOffset(int center) {
    
        final float v = getOffsetOnCircle(center);
        final float z = perspectiveMultiplier * v;
        
        return z;
    }
    
    /**
     * Adds a view as a child view and takes care of measuring it.
     * Wraps cover in its frame.
     *
     * @param child      The view to add
     * @param layoutMode Either LAYOUT_MODE_LEFT or LAYOUT_MODE_RIGHT
     * @return child which was actually added to container, subclasses can override to introduce frame views
     */
    protected View addAndMeasureChild(final View child, final int layoutMode) {
        if (child.getLayoutParams() == null) {
            child.setLayoutParams(new LayoutParams(childWidth,
                    childHeight));
        }
    
        final int index = layoutMode == LAYOUT_MODE_TO_BEFORE ? 0 : -1;
        addViewInLayout(child, index, child.getLayoutParams(), true);
    
        final int pwms = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY);
        final int phms = MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY);
        measureChild(child, pwms, phms);
        // child.setDrawingCacheEnabled(isChildrenDrawnWithCacheEnabled());
    
        return child;
    }
    
    private Bitmap createReflectionBitmap(Bitmap original) {
        final int w = original.getWidth();
        final int h = original.getHeight();
        final int rh = (int) (h * reflectionHeight);
        final int gradientColor = Color.argb(reflectionOpacity, 0xff, 0xff, 0xff);
    
        final Bitmap reflection = Bitmap.createBitmap(original, 0, rh, w, rh, reflectionMatrix, false);
    
        final LinearGradient shader = new LinearGradient(0, 0, 0, reflection.getHeight(), gradientColor, 0x00ffffff, Shader.TileMode.CLAMP);
        paint.reset();
        paint.setShader(shader);
        paint.setXfermode(porterDuffXfermode);
    
        reflectionCanvas.setBitmap(reflection);
        reflectionCanvas.drawRect(0, 0, reflection.getWidth(), reflection.getHeight(), paint);
    
        return reflection;
    }
    
    @Override
    public boolean onPreDraw() {
        if (!invalidated) { //this is hack, no idea now is possible that this works, but fixes problem where not all area was redrawn
            invalidated = true;
            invalidate();
            return false;
        }
        
        return true;
    }
    
    private class CoverFrame extends FrameLayout {
        private Bitmap reflectionCache;
        private boolean reflectionCacheInvalid = true;
        
        public CoverFrame(Context context, View cover) {
            super(context);
            setCover(cover);
        }
        
        public void setCover(View cover) {
            if (cover.getLayoutParams() != null) {
                setLayoutParams(cover.getLayoutParams());
            }
            
            final LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            lp.leftMargin = 1;
            lp.topMargin = 1;
            lp.rightMargin = 1;
            lp.bottomMargin = 1;
            
            if (cover.getParent() != null && cover.getParent() instanceof ViewGroup) {
                ViewGroup parent = (ViewGroup) cover.getParent();
                parent.removeView(cover);
            }
            
            // register observer to catch cover redraws
            cover.getViewTreeObserver().addOnPreDrawListener(ArtFlowCarousel.this);
            
            addView(cover, lp);
        }
        
        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            reflectionCacheInvalid = true;
        }
        
        @Override
        public Bitmap getDrawingCache(boolean autoScale) {
            final Bitmap b = super.getDrawingCache(autoScale);
            
            if (reflectionCacheInvalid) {
                //noinspection StatementWithEmptyBody
                if (touchState != TOUCH_STATE_FLING && touchState != TOUCH_STATE_ALIGN || reflectionCache == null) {
                    /*
                     * This block will disable reflection cache when user is flinging or aligning
                     * This is because during flinging and aligning views are moving very fast and
                     * it is not possible to use same reflection cache for all positions
                     * This will make reflections to flicker
                     */
                }
                
                /*
                 * We'll draw the reflection anyway
                 */
                try {
                    reflectionCache = createReflectionBitmap(b);
                    reflectionCacheInvalid = false;
                } catch (NullPointerException e) {
                    Log.e(VIEW_LOG_TAG, "Null pointer in createReflectionBitmap. Bitmap b=" + b, e);
                }
            }
            return b;
        }
        
        public void recycle() {
            if (reflectionCache != null) {
                reflectionCache.recycle();
                reflectionCache = null;
            }
            
            reflectionCacheInvalid = true;
            removeAllViewsInLayout();
        }
    }
    
}