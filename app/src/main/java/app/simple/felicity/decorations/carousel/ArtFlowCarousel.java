package app.simple.felicity.decorations.carousel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

/**
 * @author Hamza417
 */
public class ArtFlowCarousel extends Carousel implements ViewTreeObserver.OnPreDrawListener {
    
    private static final String TAG = "ArtFlowCarousel";
    //reflection
    private final Camera camera = new Camera();
    private final Matrix reflectionMatrix = new Matrix();
    private final Matrix tempMatrix = new Matrix();
    
    private final Matrix tempHit = new Matrix();
    private final Paint paint = new Paint();
    private final Paint reflectionPaint = new Paint();
    
    private final PorterDuffXfermode porterDuffXfermode = new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
    private final Canvas reflectionCanvas = new Canvas();
    private final RectF touchRect = new RectF();
    private final Rect tempRect = new Rect();
    
    private OnScrollPositionListener onScrollPositionListener;
    /**
     * The adaptor position of the first visible item
     */
    protected int firstItemPosition;
    private View motionTarget;
    private float targetLeft;
    private float targetTop;
    private int scrollToPositionOnNextInvalidate = -1;
    
    /**
     * How long will alignment animation take
     */
    private int alignTime = 400;
    private int centerItemOffset;
    /**
     * Index of view in center of screen, which is most in foreground
     */
    private int reverseOrderIndex = -1;
    private int lastCenterItemIndex = -1;
    
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
    
    /**
     * Radius of circle path which covers follow in coordinate space of matrix transformation. Used to scale offset
     */
    private float radiusInMatrixSpace = 350F;
    
    /**
     * Gap between reflection and original image in pixels
     */
    private int reflectionGap = 3;
    
    private int coverWidth = 350;
    private int coverHeight = 240;
    private boolean invalidated = false;
    private int lastTouchState = -1;
    
    private int lastCenterItemPosition = -1;
    
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
    
        invalidated = false; // last invalidate which marked redrawInProgress, caused this dispatchDraw. Clear flag to prevent creating loop
    
        reverseOrderIndex = -1;
    
        canvas.getClipBounds(tempRect);
        tempRect.top = 0;
        tempRect.bottom = getHeight();
        canvas.clipRect(tempRect);
    
        super.dispatchDraw(canvas);
    
        if (scrollToPositionOnNextInvalidate != -1 && adapter != null && adapter.getCount() > 0) {
            final int lastCenterItemPosition = (firstItemPosition + lastCenterItemIndex) % adapter.getCount();
            final int di = lastCenterItemPosition - scrollToPositionOnNextInvalidate;
            scrollToPositionOnNextInvalidate = -1;
            if (di != 0) {
                final int dst = (int) (di * coverWidth * spacing) - centerItemOffset;
                scrollBy(-dst, 0);
                postInvalidate();
                return;
            }
        }
    
        if (touchState == TOUCH_STATE_RESTING) {
            if (adapter != null && adapter.getCount() > 0) {
                final int lastCenterItemPosition = (firstItemPosition + lastCenterItemIndex) % adapter.getCount();
                if (lastTouchState != TOUCH_STATE_RESTING || this.lastCenterItemPosition != lastCenterItemPosition) {
                    lastTouchState = TOUCH_STATE_RESTING;
                    this.lastCenterItemPosition = lastCenterItemPosition;
                    if (onScrollPositionListener != null) {
                        onScrollPositionListener.onScrolledToPosition(lastCenterItemPosition);
                    }
                }
            }
        }
    
        if (touchState == TOUCH_STATE_SCROLLING && lastTouchState != TOUCH_STATE_SCROLLING) {
            lastTouchState = TOUCH_STATE_SCROLLING;
            if (onScrollPositionListener != null) {
                onScrollPositionListener.onScrolling();
            }
        }
        if (touchState == TOUCH_STATE_FLING && lastTouchState != TOUCH_STATE_FLING) {
            lastTouchState = TOUCH_STATE_FLING;
            if (onScrollPositionListener != null) {
                onScrollPositionListener.onScrolling();
            }
        }
    
        //make sure we never stay unaligned after last draw in resting state
        if (touchState == TOUCH_STATE_RESTING && centerItemOffset != 0) {
            Log.d(TAG, "we are in resting state, but centerItemOffset is not 0, so we need to scroll to align");
            scrollBy(centerItemOffset, 0);
            postInvalidate();
        }
    
        try {
            View v = getChildAt(lastCenterItemIndex);
            if (v != null) {
                v.requestFocus(FOCUS_FORWARD);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        super.getChildDrawingOrder(childCount, i);
    
        final int screenCenter = getWidth() / 2 + getScrollX();
        final int myCenter = getChildCenter(i);
        final int d = myCenter - screenCenter;
    
        final View v = getChildAt(i);
        final int sz = (int) (spacing * v.getWidth() / 2f);
    
        /*
         * This is a hack to fix issue with centerItemOffset being 1 or -1 when it should be 0
         * This happens when we are scrolling and we are in resting state, but we are not aligned
         * to center. This is because we are not in resting state, but we are not scrolling either.
         * We are in between. So we need to scroll to align to center.
         */
        if (centerItemOffset == 1 || centerItemOffset == -1) {
            centerItemOffset = 0;
        }
    
        if (reverseOrderIndex == -1 && (Math.abs(d) < sz || d >= 0)) {
            reverseOrderIndex = i;
            centerItemOffset = d;
            lastCenterItemIndex = i;
            Log.d(TAG, "reverseOrderIndex = " + reverseOrderIndex + ", centerItemOffset = " + centerItemOffset + ", lastCenterItemIndex = " + lastCenterItemIndex);
            return childCount - 1;
        }
    
        if (reverseOrderIndex == -1) {
            return i;
        } else {
            if (i == childCount - 1) {
                final int x = reverseOrderIndex;
                reverseOrderIndex = -1;
                return x;
            }
            return childCount - 1 - (i - reverseOrderIndex);
        }
    }
    
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();
        final float xf = ev.getX();
        final float yf = ev.getY();
        final RectF frame = touchRect;
        
        if (action == MotionEvent.ACTION_DOWN) {
            if (motionTarget != null) {
                // this is weird, we got a pen down, but we thought it was
                // already down!
                // We should probably send an ACTION_UP to the current
                // target.
                motionTarget = null;
            }
            // If we're disallowing intercept or if we're allowing and we didn't
            // intercept
            if (!onInterceptTouchEvent(ev)) {
                // reset this event's action (just to protect ourselves)
                ev.setAction(MotionEvent.ACTION_DOWN);
                // We know we want to dispatch the event down, find a child
                // who can handle it, start with the front-most child.
                
                final int count = getChildCount();
                final int[] childOrder = new int[count];
                
                for (int i = 0; i < count; i++) {
                    childOrder[i] = getChildDrawingOrder(count, i);
                }
                
                for (int i = count - 1; i >= 0; i--) {
                    final View child = getChildAt(childOrder[i]);
                    if (child.getVisibility() == VISIBLE || child.getAnimation() != null) {
                        getScrolledTransformedChildRectangle(child, frame);
                        
                        if (frame.contains(xf, yf)) {
                            // offset the event to the view's coordinate system
                            final float xc = xf - frame.left;
                            final float yc = yf - frame.top;
                            ev.setLocation(xc, yc);
                            if (child.dispatchTouchEvent(ev)) {
                                // Event handled, we have a target now.
                                motionTarget = child;
                                targetTop = frame.top;
                                targetLeft = frame.left;
                                return true;
                            }
                            
                            break;
                        }
                    }
                }
            }
        }
        
        boolean isUpOrCancel = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
        
        // The event wasn't an ACTION_DOWN, dispatch it to our target if
        // we have one.
        final View target = motionTarget;
        if (target == null) {
            // We don't have a target, this means we're handling the
            // event as a regular view.
            ev.setLocation(xf, yf);
            return onTouchEvent(ev);
        }
        
        // if have a target, see if we're allowed to and want to intercept its
        // events
        if (onInterceptTouchEvent(ev)) {
            final float xc = xf - targetLeft;
            final float yc = yf - targetTop;
            ev.setAction(MotionEvent.ACTION_CANCEL);
            ev.setLocation(xc, yc);
            if (!target.dispatchTouchEvent(ev)) {
                // target didn't handle ACTION_CANCEL. not much we can do
                // but they should have.
            }
            // clear the target
            motionTarget = null;
            // Don't dispatch this event to our own view, because we already
            // saw it when intercepting; we just want to give the following
            // event to the normal onTouchEvent().
            return true;
        }
        
        if (isUpOrCancel) {
            motionTarget = null;
            targetTop = -1;
            targetLeft = -1;
        }
        
        // finally offset the event to the target's coordinate system and
        // dispatch the event.
        final float xc = xf - targetLeft;
        final float yc = yf - targetTop;
        ev.setLocation(xc, yc);
        
        return target.dispatchTouchEvent(ev);
    }
    
    private void getScrolledTransformedChildRectangle(View child, RectF r) {
        transformChildHitRectangle(child, r);
        final int offset = child.getLeft() - getScrollX();
        r.offset(offset, child.getTop());
    }
    
    /**
     * Fill outRect with transformed child hit rectangle. Rectangle is not moved to its position on screen, neither getSroolX is accounted for
     *
     * @param child   child view
     * @param outRect output rectangle
     */
    protected void transformChildHitRectangle(View child, RectF outRect) {
        outRect.left = 0;
        outRect.top = 0;
        outRect.right = child.getWidth();
        outRect.bottom = child.getHeight();
        
        setChildTransformation(child, tempHit);
        tempHit.mapRect(outRect);
    }
    
    @Override
    public void computeScroll() {
        // if we don't have an adapter, we don't need to do anything
        if (adapter == null) {
            return;
        }
        if (adapter.getCount() == 0) {
            return;
        }
    
        if (getChildCount() == 0) { // release memory resources was probably called before, and onLayout didn't get called to fill container again
            requestLayout();
        }
    
        //        if (touchState == TOUCH_STATE_ALIGN) {
        //            if (scroller.computeScrollOffset()) {
        //                if (scroller.getFinalX() == scroller.getCurrX()) {
        //                    scroller.abortAnimation();
        //                    touchState = TOUCH_STATE_RESTING;
        //                    clearChildrenCache();
        //                    return;
        //                }
        //
        //                int x = scroller.getCurrX();
        //                scrollTo(x, 0);
        //
        //                postInvalidate();
        //            } else {
        //                touchState = TOUCH_STATE_RESTING;
        //                clearChildrenCache();
        //            }
        //            return;
        //        }
        //
    
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
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                scroll((int) (-1 * coverWidth * spacing) - centerItemOffset);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                scroll((int) (coverWidth * spacing) - centerItemOffset);
                return true;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
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
    
    @Override
    protected boolean checkScrollPosition() {
        if (centerItemOffset != 0) {
            scroller.startScroll(getScrollX(), 0, centerItemOffset, 0, alignTime);
            touchState = TOUCH_STATE_ALIGN;
            invalidate();
            return true;
        }
        return false;
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
     * @param pixelPos Absolute position in pixels including scroll offset
     * @return relative position
     */
    private float getRelativePosition(int pixelPos) {
        final int half = getWidth() / 2;
        final int centerPos = getScrollX() + half;
        
        return (pixelPos - centerPos) / ((float) half);
    }
    
    private float getWidgetSizeMultiplier() {
        return ((float) tuningWidgetSize) / ((float) getWidth());
    }
    
    private float getChildAdjustPosition(View child) {
        final int c = getChildCenter(child);
        final float crp = getClampedRelativePosition(getRelativePosition(c), adjustPositionThreshold * getWidgetSizeMultiplier());
    
        return childWidth * adjustPositionMultiplier * spacing * crp * getSpacingMultiplierOnCircle(c);
    }
    
    private float getSpacingMultiplierOnCircle(int childCenter) {
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
    
    //    @Override
    //    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
    //        canvas.save();
    //
    //        //set matrix to child's transformation
    //        setChildTransformation(child, matrix);
    //
    //        //Generate child bitmap
    //        Bitmap bitmap = child.getDrawingCache();
    //
    //        //initialize canvas state. Child 0,0 coordinates will match canvas 0,0
    //        canvas.translate(child.getLeft(), child.getTop());
    //
    //        //set child transformation on canvas
    //        canvas.concat(matrix);
    //
    //        final Bitmap rfCache = ((ArtFlowCarousel.CoverFrame) child).reflectionCache;
    //
    ////        if (reflectionBackgroundColor != Color.TRANSPARENT) {
    ////            final int top = bitmap.getHeight() + reflectionGap - 2;
    ////            final float frame = 1.0f;
    ////            reflectionPaint.setColor(reflectionBackgroundColor);
    ////            canvas.drawRect(frame, top + frame, rfCache.getWidth() - frame, top + rfCache.getHeight() - frame, reflectionPaint);
    ////        }
    //
    //        paint.reset();
    //        paint.setAntiAlias(true);
    //        paint.setFilterBitmap(true);
    //
    //        //Draw child bitmap with applied transforms
    //        canvas.drawBitmap(bitmap, 0.0f, 0.0f, paint);
    //
    //        //Draw reflection
    //        canvas.drawBitmap(rfCache, 0.0f, bitmap.getHeight() - 2 + reflectionGap, paint);
    //
    //        canvas.restore();
    //
    //        return false;
    //    }
    
    private void setChildTransformation(View child, Matrix m) {
        m.reset();
    
        addChildRotation(child, m);
        addChildScale(child, m);
        addChildCircularPathZOffset(child, m);
        addChildAdjustPosition(child, m);
    
        //set coordinate system origin to center of child
        m.preTranslate(-child.getWidth() / 2f, -child.getHeight() / 2f);
        //move back
        m.postTranslate(child.getWidth() / 2f, child.getHeight() / 2f);
    }
    
    public void scrollToPosition(int position) {
        if (adapter == null || adapter.getCount() == 0) {
            throw new IllegalStateException("You are trying to scroll container with no adapter set. Set adapter first.");
        }
        
        if (lastCenterItemIndex != -1) {
            final int lastCenterItemPosition = (firstItemPosition + lastCenterItemIndex) % adapter.getCount();
            final int di = lastCenterItemPosition - position;
            final int dst = (int) (di * coverWidth * spacing);
            scrollToPositionOnNextInvalidate = -1;
            scrollTo(-dst, 0);
        } else {
            scrollToPositionOnNextInvalidate = position;
        }
    
        invalidate();
    }
    
    public void scrollToPositionSmooth(int position) {
        if (adapter == null || adapter.getCount() == 0) {
            throw new IllegalStateException("You are trying to scroll container with no adapter set. Set adapter first.");
        }
        
        if (lastCenterItemIndex != -1) {
            final int lastCenterItemPosition = (firstItemPosition + lastCenterItemIndex) % adapter.getCount();
            final int di = lastCenterItemPosition - position;
            final int dst = (int) (di * coverWidth * spacing);
            scrollToPositionOnNextInvalidate = -1;
            scrollBy(-dst, 0);
        } else {
            scrollToPositionOnNextInvalidate = position;
        }
        
        invalidate();
    }
    
    private void addChildRotation(View v, Matrix m) {
        camera.save();
        
        final int c = getChildCenter(v);
        camera.rotateY(getRotationAngle(c) - getAngleOnCircle(c));
        
        camera.getMatrix(tempMatrix);
        m.postConcat(tempMatrix);
        
        camera.restore();
    }
    
    private void addChildScale(View v, Matrix m) {
        final float f = getScaleFactor(getChildCenter(v));
        m.postScale(f, f);
    }
    
    private void addChildCircularPathZOffset(View child, Matrix m) {
        camera.save();
        
        final float v = getOffsetOnCircle(getChildCenter(child));
        final float z = radiusInMatrixSpace * v;
        
        camera.translate(0.0f, 0.0f, z);
        
        camera.getMatrix(tempMatrix);
        m.postConcat(tempMatrix);
        
        camera.restore();
    }
    
    private void addChildAdjustPosition(View child, Matrix m) {
        final int c = getChildCenter(child);
        final float crp = getClampedRelativePosition(getRelativePosition(c), adjustPositionThreshold * getWidgetSizeMultiplier());
        final float d = coverWidth * adjustPositionMultiplier * spacing * crp * getSpacingMultiplierOnCircle(c);
        
        m.postTranslate(d, 0f);
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
        // this is hack, no idea now is possible that this works, but fixes problem where not all area was redrawn
        if (!invalidated) {
            invalidated = true;
            invalidate();
            return false;
        }
    
        return true;
    }
    
    public interface OnScrollPositionListener {
        public void onScrolledToPosition(int position);
        
        public void onScrolling();
    }
    
    /**
     * sets listener for center item position
     *
     * @param onScrollPositionListener listener
     */
    public void setOnScrollPositionListener(OnScrollPositionListener onScrollPositionListener) {
        this.onScrollPositionListener = onScrollPositionListener;
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
                     * This is because during flinging and aligning, views are moving very fast and
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