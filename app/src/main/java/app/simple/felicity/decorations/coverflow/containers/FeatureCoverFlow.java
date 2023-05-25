package app.simple.felicity.decorations.coverflow.containers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader.TileMode;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.Scroller;

import java.lang.ref.WeakReference;
import java.util.LinkedList;

import androidx.annotation.NonNull;
import androidx.collection.LruCache;
import app.simple.felicity.R;
import app.simple.felicity.decorations.coverflow.general.Validate;

/**
 * @author Martin Appl
 * Note: Supports wrap content for height
 */
public class FeatureCoverFlow extends EndlessLoopAdapterContainer implements ViewTreeObserver.OnPreDrawListener {
    public static final int DEFAULT_MAX_CACHE_SIZE = 32;
    /**
     * A list of cached (re-usable) cover frames
     */
    protected final LinkedList <WeakReference <CoverFrame>> recycledCoverFrames = new LinkedList <>();
    /**
     * Graphics Camera used for generating transformation matrices;
     */
    private final Camera camera = new Camera();
    private final Scroller alignScroller = new Scroller(getContext(), new DecelerateInterpolator(1.5F));
    private final MyCache cachedFrames;
    private final Matrix matrix = new Matrix();
    private final Matrix tempMatrix = new Matrix();
    private final Matrix tempHit = new Matrix();
    private final Rect tempRect = new Rect();
    private final RectF touchRect = new RectF();
    //reflection
    private final Matrix reflectionMatrix = new Matrix();
    private final Paint paint = new Paint();
    private final Paint reflectionPaint = new Paint();
    private final PorterDuffXfermode porterDuffXfermode = new PorterDuffXfermode(Mode.DST_IN);
    private final Canvas reflectionCanvas = new Canvas();
    /**
     * Relative spacing value of Views in container. If <1 Views will overlap, if >1 Views will have spaces between them
     */
    private float spacing = 0.5f;
    /**
     * Index of view in center of screen, which is most in foreground
     */
    private int reverseOrderIndex = -1;
    private int lastCenterItemIndex = -1;
    /**
     * Distance from center as fraction of half of widget size where covers start to rotate into center
     * 1 means rotation starts on edge of widget, 0 means only center rotated
     */
    private float rotationThreshold = 0.3f;
    /**
     * Distance from center as fraction of half of widget size where covers start to zoom in
     * 1 means scaling starts on edge of widget, 0 means only center scaled
     */
    private float scalingThreshold = 0.3f;
    /**
     * Distance from center as fraction of half of widget size,
     * where covers start enlarge their spacing to allow for smooth passing each other without jumping over each other
     * 1 means edge of widget, 0 means only center
     */
    private float adjustPositionThreshold = 0.1f;
    /**
     * By enlarging this value, you can enlarge spacing in center of widget done by position adjustment
     */
    private float adjustPositionMultiplier = 1.0f;
    /**
     * Absolute value of rotation angle of cover at edge of widget in degrees
     */
    private float maxRotationAngle = 70.0f;
    /**
     * Scale factor of item in center
     */
    private float maxScaleFactor = 1.2f;
    /**
     * Radius of circle path which covers follow. Range of screen is -1 to 1, minimal radius is therefore 1
     */
    private float radius = 2f;
    /**
     * Radius of circle path which covers follow in coordinate space of matrix transformation. Used to scale offset
     */
    private float radiusInMatrixSpace = 350F;
    /**
     * Size of reflection as a fraction of original image (0-1)
     */
    private float reflectionHeight = 0.5f;
    /**
     * Gap between reflection and original image in pixels
     */
    private int reflectionGap = 2;
    /**
     * Starting opacity of reflection. Reflection fades from this value to transparency;
     */
    private int reflectionOpacity = 0x70;
    /**
     * Widget size on which was tuning of parameters done. This value is used to scale parameters on when widgets has different size
     */
    private int tuningWidgetSize = 1280;
    /**
     * How long will alignment animation take
     */
    private int alignTime = 1000;
    /**
     * If you don't want reflections to be transparent, you can set them background of same color as widget background
     */
    private int reflectionBackgroundColor = Color.TRANSPARENT;
    /**
     * A listener for center item position
     */
    private OnScrollPositionListener onScrollPositionListener;
    private int lastTouchState = -1;
    private int lastCenterItemPosition = -1;
    private int paddingTop = 0;
    private int paddingBottom = 0;
    private int centerItemOffset;
    private int coverWidth = 160;
    private int coverHeight = 240;
    private View motionTarget;
    private float targetLeft;
    private float targetTop;
    private int scrollToPositionOnNextInvalidate = -1;
    private boolean invalidated = false;
    
    public FeatureCoverFlow(Context context, AttributeSet attrs, int defStyle, int cacheSize) {
        super(context, attrs, defStyle);
        
        if (cacheSize <= 0) {
            cacheSize = DEFAULT_MAX_CACHE_SIZE;
        }
        cachedFrames = new MyCache(cacheSize);
        
        setChildrenDrawingOrderEnabled(true);
        setChildrenDrawingCacheEnabled(true);
        // setChildrenDrawnWithCacheEnabled(true);
        
        reflectionMatrix.preScale(1.0f, -1.0f);
        
        // init params from xml
        if (attrs != null) {
            try (TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FeatureCoverFlow, defStyle, 0)) {
                coverWidth = a.getDimensionPixelSize(R.styleable.FeatureCoverFlow_coverWidth, coverWidth);
                if (coverWidth % 2 == 1) {
                    coverWidth--;
                }
                coverHeight = a.getDimensionPixelSize(R.styleable.FeatureCoverFlow_coverHeight, coverHeight);
                spacing = a.getFloat(R.styleable.FeatureCoverFlow_spacing, spacing);
                rotationThreshold = a.getFloat(R.styleable.FeatureCoverFlow_rotationThreshold, rotationThreshold);
                scalingThreshold = a.getFloat(R.styleable.FeatureCoverFlow_scalingThreshold, scalingThreshold);
                adjustPositionThreshold = a.getFloat(R.styleable.FeatureCoverFlow_adjustPositionThreshold, adjustPositionThreshold);
                adjustPositionMultiplier = a.getFloat(R.styleable.FeatureCoverFlow_adjustPositionMultiplier, adjustPositionMultiplier);
                maxRotationAngle = a.getFloat(R.styleable.FeatureCoverFlow_maxRotationAngle, maxRotationAngle);
                maxScaleFactor = a.getFloat(R.styleable.FeatureCoverFlow_maxScaleFactor, maxScaleFactor);
                radius = a.getFloat(R.styleable.FeatureCoverFlow_circlePathRadius, radius);
                radiusInMatrixSpace = a.getFloat(R.styleable.FeatureCoverFlow_circlePathRadiusInMatrixSpace, radiusInMatrixSpace);
                reflectionHeight = a.getFloat(R.styleable.FeatureCoverFlow_reflectionHeight, reflectionHeight);
                reflectionGap = a.getDimensionPixelSize(R.styleable.FeatureCoverFlow_reflectionGap, reflectionGap);
                reflectionOpacity = a.getInteger(R.styleable.FeatureCoverFlow_reflectionOpacity, reflectionOpacity);
                tuningWidgetSize = a.getDimensionPixelSize(R.styleable.FeatureCoverFlow_tuningWidgetSize, tuningWidgetSize);
                alignTime = a.getInteger(R.styleable.FeatureCoverFlow_alignAnimationTime, alignTime);
                paddingTop = a.getDimensionPixelSize(R.styleable.FeatureCoverFlow_verticalPaddingTop, paddingTop);
                paddingBottom = a.getDimensionPixelSize(R.styleable.FeatureCoverFlow_verticalPaddingBottom, paddingBottom);
                reflectionBackgroundColor = a.getColor(R.styleable.FeatureCoverFlow_reflectionBackgroundColor, Color.TRANSPARENT);
            }
        }
    }
    
    public FeatureCoverFlow(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public FeatureCoverFlow(Context context) {
        this(context, null);
    }
    
    public FeatureCoverFlow(Context context, int cacheSize) {
        this(context, null, 0, cacheSize);
    }
    
    public FeatureCoverFlow(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs, defStyle, DEFAULT_MAX_CACHE_SIZE);
    }
    
    private float getWidgetSizeMultiplier() {
        return ((float) tuningWidgetSize) / ((float) getWidth());
    }
    
    @SuppressLint ("NewApi")
    @Override
    protected View addAndMeasureChildHorizontal(View child, int layoutMode) {
        final int index = layoutMode == LAYOUT_MODE_TO_BEFORE ? 0 : -1;
        final LoopLayoutParams lp = new LoopLayoutParams(coverWidth, coverHeight);
    
        if (child != null) {
            if (child instanceof CoverFrame) {
                addViewInLayout(child, index, lp, true);
                measureChild(child);
                return child;
            }
        }
    
        CoverFrame frame = getRecycledCoverFrame();
        if (frame == null) {
            frame = new CoverFrame(getContext(), child);
        } else {
            assert child != null;
            frame.setCover(child);
        }
    
        //to enable drawing cache
        frame.setLayerType(LAYER_TYPE_HARDWARE, null);
        frame.setDrawingCacheEnabled(true);
        
        addViewInLayout(frame, index, lp, true);
        measureChild(frame);
        return frame;
    }
    
    @Override
    protected int layoutChildHorizontal(View v, int left, LoopLayoutParams lp) {
        int l, t, r, b;
        
        l = left;
        r = l + v.getMeasuredWidth();
        t = ((getHeight() - paddingTop - paddingBottom) - v.getMeasuredHeight()) / 2 + paddingTop;
        b = t + v.getMeasuredHeight();
        
        v.layout(l, t, r, b);
        return l + (int) (v.getMeasuredWidth() * spacing);
    }
    
    /**
     * Layout children from right to left
     */
    protected int layoutChildHorizontalToBefore(View v, int right, LoopLayoutParams lp) {
        int left = right - v.getMeasuredWidth();
        ;
        left = layoutChildHorizontal(v, left, lp);
        return left;
    }
    
    private int getChildCenter(View v) {
        final int w = v.getRight() - v.getLeft();
        return v.getLeft() + w / 2;
    }
    
    private int getChildCenter(int i) {
        return getChildCenter(getChildAt(i));
    }
    
    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        final int screenCenter = getWidth() / 2 + getScrollX();
        final int myCenter = getChildCenter(i);
        final int d = myCenter - screenCenter;
        
        final View v = getChildAt(i);
        final int sz = (int) (spacing * v.getWidth() / 2f);
        
        if (reverseOrderIndex == -1 && (Math.abs(d) < sz || d >= 0)) {
            reverseOrderIndex = i;
            centerItemOffset = d;
            lastCenterItemIndex = i;
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
    protected void refillInternal(int lastItemPos, int firstItemPos) {
        super.refillInternal(lastItemPos, firstItemPos);
        
        final int c = getChildCount();
        for (int i = 0; i < c; i++) {
            getChildDrawingOrder(c, i); //go through children to fill center item offset
        }
        
    }
    
    @Override
    protected void dispatchDraw(Canvas canvas) {
        invalidated = false; //last invalidate which marked redrawInProgress, caused this dispatchDraw. Clear flag to prevent creating loop
    
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
                shouldRepeat = true;
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
    
    @Override
    protected void fillFirstTime(final int lastItemPos, final int firstItemPos) {
        final int leftScreenEdge = 0;
        final int rightScreenEdge = leftScreenEdge + getWidth();
    
        int right;
        int left;
        View child;
    
        boolean isRepeatingNow = false;
    
        //scrolling is enabled until we find out we don't have enough items
        isScrollingDisabled = false;
    
        lastItemPosition = lastItemPos;
        firstItemPosition = firstItemPos;
        leftChildEdge = (int) (-coverWidth * spacing);
        right = 0;
        left = leftChildEdge;
    
        while (right < rightScreenEdge) {
            lastItemPosition++;
        
            if (isRepeatingNow && lastItemPosition >= firstItemPos) {
                return;
            }
        
            if (lastItemPosition >= adapter.getCount()) {
                if (firstItemPos == 0 && shouldRepeat) {
                    lastItemPosition = 0;
                } else {
                    if (firstItemPos > 0) {
                        lastItemPosition = 0;
                        isRepeatingNow = true;
                    } else if (!shouldRepeat) {
                        lastItemPosition--;
                        isScrollingDisabled = true;
                        final int w = right - leftChildEdge;
                        final int dx = (getWidth() - w) / 2;
                        scrollTo(-dx, 0);
                        return;
                    }
                    
                }
            }
        
            if (lastItemPosition >= adapter.getCount()) {
                Log.wtf("EndlessLoop", "mLastItemPosition > mAdapter.getCount()");
                return;
            }
        
            child = adapter.getView(lastItemPosition, getCachedView(), this);
            Validate.notNull(child, "Your adapter has returned null from getView.");
            child = addAndMeasureChildHorizontal(child, LAYOUT_MODE_AFTER);
            left = layoutChildHorizontal(child, left, (LoopLayoutParams) child.getLayoutParams());
            right = child.getRight();
        
            //if selected view is going to screen, set selected state on him
            if (lastItemPosition == selectedPosition) {
                child.setSelected(true);
            }
        
        }
    
        if (scrollPositionIfEndless > 0) {
            final int p = scrollPositionIfEndless;
            scrollPositionIfEndless = -1;
            removeAllViewsInLayout();
            refillOnChange(p);
        }
    }
    
    /**
     * Checks and refills empty area on the right
     */
    @Override
    protected void refillRight() {
        if (!shouldRepeat && isScrollingDisabled) {
            return; //prevent next layout calls to override override first init to scrolling disabled by falling to this branch
        }
        if (getChildCount() == 0) {
            return;
        }
        
        final int leftScreenEdge = getScrollX();
        final int rightScreenEdge = leftScreenEdge + getWidth();
    
        View child = getChildAt(getChildCount() - 1);
        int currLayoutLeft = child.getLeft() + (int) (child.getWidth() * spacing);
        while (currLayoutLeft < rightScreenEdge) {
            lastItemPosition++;
            if (lastItemPosition >= adapter.getCount()) {
                lastItemPosition = 0;
            }
    
            child = getViewAtPosition(lastItemPosition);
            child = addAndMeasureChildHorizontal(child, LAYOUT_MODE_AFTER);
            currLayoutLeft = layoutChildHorizontal(child, currLayoutLeft, (LoopLayoutParams) child.getLayoutParams());
    
            //if selected view is going to screen, set selected state on him
            if (lastItemPosition == selectedPosition) {
                child.setSelected(true);
            }
        }
    }
    
    private boolean containsView(View v) {
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) == v) {
                return true;
            }
        }
        return false;
    }
    
    private View getViewAtPosition(int position) {
        View v = cachedFrames.remove(position);
        if (v == null) {
            v = adapter.getView(position, getCachedView(), this);
            Validate.notNull(v, "Your adapter has returned null from getView.");
            return v;
        }
        
        if (!containsView(v)) {
            return v;
        } else {
            v = adapter.getView(position, getCachedView(), this);
            Validate.notNull(v, "Your adapter has returned null from getView.");
            return v;
        }
    }
    
    /**
     * Checks and refills empty area on the left
     */
    @Override
    protected void refillLeft() {
        if (!shouldRepeat && isScrollingDisabled) {
            return; //prevent next layout calls to override override first init to scrolling disabled by falling to this branch
        }
        if (getChildCount() == 0) {
            return;
        }
        
        final int leftScreenEdge = getScrollX();
    
        View child = getChildAt(0);
        int currLayoutRight = child.getRight() - (int) (child.getWidth() * spacing);
        while (currLayoutRight > leftScreenEdge) {
            firstItemPosition--;
            if (firstItemPosition < 0) {
                firstItemPosition = adapter.getCount() - 1;
            }
    
            child = getViewAtPosition(firstItemPosition);
            if (child == getChildAt(getChildCount() - 1)) {
                removeViewInLayout(child);
            }
            child = addAndMeasureChildHorizontal(child, LAYOUT_MODE_TO_BEFORE);
            currLayoutRight = layoutChildHorizontalToBefore(child, currLayoutRight, (LoopLayoutParams) child.getLayoutParams());
    
            //update left edge of children in container
            leftChildEdge = child.getLeft();
            
            //if selected view is going to screen, set selected state on him
            if (firstItemPosition == selectedPosition) {
                child.setSelected(true);
            }
        }
    }
    
    /**
     * Removes view that are outside of the visible part of the list. Will not
     * remove all views.
     */
    protected void removeNonVisibleViews() {
        if (getChildCount() == 0) {
            return;
        }
    
        final int leftScreenEdge = getScrollX();
        final int rightScreenEdge = leftScreenEdge + getWidth();
    
        // check if we should remove any views in the left
        View firstChild = getChildAt(0);
        final int leftedge = firstChild.getLeft();
        if (leftedge != leftChildEdge) {
            Log.e("feature component", "firstChild.getLeft() != mLeftChildEdge, leftedge:" + leftedge + " ftChildEdge:" + leftChildEdge);
            View v = getChildAt(0);
            removeAllViewsInLayout();
            addAndMeasureChildHorizontal(v, LAYOUT_MODE_TO_BEFORE);
            layoutChildHorizontal(v, leftChildEdge, (LoopLayoutParams) v.getLayoutParams());
            return;
        }
        while (firstChild != null && firstChild.getRight() < leftScreenEdge) {
            //if selected view is going off screen, remove selected state
            firstChild.setSelected(false);
        
            // remove view
            removeViewInLayout(firstChild);
        
            cachedFrames.put(firstItemPosition, (CoverFrame) firstChild);
        
            firstItemPosition++;
            if (firstItemPosition >= adapter.getCount()) {
                firstItemPosition = 0;
            }
        
            // update left item position
            leftChildEdge = getChildAt(0).getLeft();
        
            // Continue to check the next child only if we have more than
            // one child left
            if (getChildCount() > 1) {
                firstChild = getChildAt(0);
            } else {
                firstChild = null;
            }
        }
        
        // check if we should remove any views in the right
        View lastChild = getChildAt(getChildCount() - 1);
        while (lastChild != null && lastChild.getLeft() > rightScreenEdge) {
            //if selected view is going off screen, remove selected state
            lastChild.setSelected(false);
    
            // remove the right view
            removeViewInLayout(lastChild);
    
            cachedFrames.put(lastItemPosition, (CoverFrame) lastChild);
    
            lastItemPosition--;
            if (lastItemPosition < 0) {
                lastItemPosition = adapter.getCount() - 1;
            }
    
            // Continue to check the next child only if we have more than
            // one child left
            if (getChildCount() > 1) {
                lastChild = getChildAt(getChildCount() - 1);
            } else {
                lastChild = null;
            }
        }
    }
    
    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        canvas.save();
        
        //set matrix to child's transformation
        setChildTransformation(child, matrix);
        
        //Generate child bitmap
        Bitmap bitmap = child.getDrawingCache();
    
        //initialize canvas state. Child 0,0 coordinates will match canvas 0,0
        canvas.translate(child.getLeft(), child.getTop());
    
        //set child transformation on canvas
        canvas.concat(matrix);
    
        final Bitmap rfCache = ((CoverFrame) child).reflectionCache;
    
        if (reflectionBackgroundColor != Color.TRANSPARENT) {
            final int top = bitmap.getHeight() + reflectionGap - 2;
            final float frame = 1.0f;
            reflectionPaint.setColor(reflectionBackgroundColor);
            canvas.drawRect(frame, top + frame, rfCache.getWidth() - frame, top + rfCache.getHeight() - frame, reflectionPaint);
        }
    
        paint.reset();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
    
        //Draw child bitmap with applied transforms
        canvas.drawBitmap(bitmap, 0.0f, 0.0f, paint);
    
        //Draw reflection
        canvas.drawBitmap(rfCache, 0.0f, bitmap.getHeight() - 2 + reflectionGap, paint);
    
        canvas.restore();
        return false;
    }
    
    private Bitmap createReflectionBitmap(Bitmap original) {
        final int w = original.getWidth();
        final int h = original.getHeight();
        final int rh = (int) (h * reflectionHeight);
        final int gradientColor = Color.argb(reflectionOpacity, 0xff, 0xff, 0xff);
    
        final Bitmap reflection = Bitmap.createBitmap(original, 0, rh, w, rh, reflectionMatrix, false);
    
        final LinearGradient shader = new LinearGradient(0, 0, 0, reflection.getHeight(), gradientColor, 0x00ffffff, TileMode.CLAMP);
        paint.reset();
        paint.setShader(shader);
        paint.setXfermode(porterDuffXfermode);
    
        reflectionCanvas.setBitmap(reflection);
        reflectionCanvas.drawRect(0, 0, reflection.getWidth(), reflection.getHeight(), paint);
    
        return reflection;
    }
    
    /**
     * Fill outRect with transformed child hit rectangle. Rectangle is not moved to its position on screen, neither getSroolX is accounted for
     *
     * @param child
     * @param outRect
     */
    protected void transformChildHitRectangle(View child, RectF outRect) {
        outRect.left = 0;
        outRect.top = 0;
        outRect.right = child.getWidth();
        outRect.bottom = child.getHeight();
    
        setChildTransformation(child, tempHit);
        tempHit.mapRect(outRect);
    }
    
    protected void transformChildHitRectangle(View child, RectF outRect, final Matrix transformation) {
        outRect.left = 0;
        outRect.top = 0;
        outRect.right = child.getWidth();
        outRect.bottom = child.getHeight();
        
        transformation.mapRect(outRect);
    }
    
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
    
    private void addChildCircularPathZOffset(View child, Matrix m) {
        camera.save();
    
        final float v = getOffsetOnCircle(getChildCenter(child));
        final float z = radiusInMatrixSpace * v;
    
        camera.translate(0.0f, 0.0f, z);
    
        camera.getMatrix(tempMatrix);
        m.postConcat(tempMatrix);
    
        camera.restore();
    }
    
    private void addChildScale(View v, Matrix m) {
        final float f = getScaleFactor(getChildCenter(v));
        m.postScale(f, f);
    }
    
    private void addChildRotation(View v, Matrix m) {
        camera.save();
    
        final int c = getChildCenter(v);
        camera.rotateY(getRotationAngle(c) - getAngleOnCircle(c));
    
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
    
    /**
     * Clamps relative position by threshold, and produces values in range -1 to 1 directly usable for transformation computation
     *
     * @param position  value int range -1 to 1
     * @param threshold always positive value of threshold distance from center in range 0-1
     * @return
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
    
    private float getRotationAngle(int childCenter) {
        return -maxRotationAngle * getClampedRelativePosition(getRelativePosition(childCenter), rotationThreshold * getWidgetSizeMultiplier());
    }
    
    private float getScaleFactor(int childCenter) {
        return 1 + (maxScaleFactor - 1) * (1 - Math.abs(getClampedRelativePosition(getRelativePosition(childCenter), scalingThreshold * getWidgetSizeMultiplier())));
    }
    
    /**
     * Compute offset following path on circle
     *
     * @param childCenter
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
    
    private float getSpacingMultiplierOnCircle(int childCenter) {
        float x = getRelativePosition(childCenter) / radius;
        return (float) Math.sin(Math.acos(x));
    }
    
    @Override
    protected void handleClick(Point p) {
        final int c = getChildCount();
        View v;
        final RectF r = new RectF();
        final int[] childOrder = new int[c];
        
        for (int i = 0; i < c; i++) {
            childOrder[i] = getChildDrawingOrder(c, i);
        }
        
        for (int i = c - 1; i >= 0; i--) {
            v = getChildAt(childOrder[i]); //we need reverse drawing order. Check children drawn last first
            getScrolledTransformedChildRectangle(v, r);
            if (r.contains(p.x, p.y)) {
                final View old = getSelectedView();
                if (old != null) {
                    old.setSelected(false);
                }
    
                int position = firstItemPosition + childOrder[i];
                if (position >= adapter.getCount()) {
                    position = position - adapter.getCount();
                }
    
                selectedPosition = position;
                v.setSelected(true);
    
                if (onItemClickListener != null) {
                    onItemClickListener.onItemClick(this, v, position, getItemIdAtPosition(position));
                }
                if (onItemSelectedListener != null) {
                    onItemSelectedListener.onItemSelected(this, v, position, getItemIdAtPosition(position));
                }
    
                break;
            }
        }
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
        
        if (getChildCount() == 0) { //release memory resources was probably called before, and onLayout didn't get called to fill container again
            requestLayout();
        }
        
        if (touchState == TOUCH_STATE_ALIGN) {
            if (alignScroller.computeScrollOffset()) {
                if (alignScroller.getFinalX() == alignScroller.getCurrX()) {
                    alignScroller.abortAnimation();
                    touchState = TOUCH_STATE_RESTING;
                    clearChildrenCache();
                    return;
                }
        
                int x = alignScroller.getCurrX();
                scrollTo(x, 0);
        
                postInvalidate();
                return;
            } else {
                touchState = TOUCH_STATE_RESTING;
                clearChildrenCache();
                return;
            }
        }
        
        super.computeScroll();
    }
    
    @Override
    protected boolean checkScrollPosition() {
        if (centerItemOffset != 0) {
            alignScroller.startScroll(getScrollX(), 0, centerItemOffset, 0, alignTime);
            touchState = TOUCH_STATE_ALIGN;
            invalidate();
            return true;
        }
        return false;
    }
    
    private void getScrolledTransformedChildRectangle(View child, RectF r) {
        transformChildHitRectangle(child, r);
        final int offset = child.getLeft() - getScrollX();
        r.offset(offset, child.getTop());
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
    
    @SuppressWarnings ("deprecation")
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
        final int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        final int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
        final int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec);
        
        int h, w;
        if (heightSpecMode == MeasureSpec.EXACTLY) {
            h = heightSpecSize;
        } else {
            h = (int) ((coverHeight + coverHeight * reflectionHeight + reflectionGap) * maxScaleFactor + paddingTop + paddingBottom);
            h = resolveSize(h, heightMeasureSpec);
        }
        
        if (widthSpecMode == MeasureSpec.EXACTLY) {
            w = widthSpecSize;
        } else {
            WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            w = display.getWidth();
            w = resolveSize(w, widthMeasureSpec);
        }
        
        setMeasuredDimension(w, h);
    }
    
    //disable turning caches of and on, we need them always on
    @Override
    protected void enableChildrenCache() {
    }
    
    @Override
    protected void clearChildrenCache() {
    }
    
    /**
     * How many items can remain in cache. Lower in case of memory issues
     *
     * @param size number of cached covers
     */
    public void trimChacheSize(int size) {
        cachedFrames.trimToSize(size);
    }
    
    /**
     * Clear internal cover cache
     */
    public void clearCache() {
        cachedFrames.evictAll();
    }
    
    /**
     * Returns widget spacing (as fraction of widget size)
     *
     * @return Widgets spacing
     */
    public float getSpacing() {
        return spacing;
    }
    
    /**
     * Set widget spacing (float means fraction of widget size, 1 = widget size)
     *
     * @param spacing the spacing to set
     */
    public void setSpacing(float spacing) {
        this.spacing = spacing;
    }
    
    /**
     * Return width of cover in pixels
     *
     * @return the Cover Width
     */
    public int getCoverWidth() {
        return coverWidth;
    }
    
    /**
     * Set width of cover in pixels
     *
     * @param coverWidth the Cover Width to set
     */
    public void setCoverWidth(int coverWidth) {
        if (coverWidth % 2 == 1) {
            coverWidth--;
        }
        this.coverWidth = coverWidth;
    }
    
    /**
     * Return cover height in pixels
     *
     * @return the Cover Height
     */
    public int getCoverHeight() {
        return coverHeight;
    }
    
    /**
     * Set cover height in pixels
     *
     * @param coverHeight the Cover Height to set
     */
    public void setCoverHeight(int coverHeight) {
        this.coverHeight = coverHeight;
    }
    
    /**
     * Sets distance from center as fraction of half of widget size where covers start to rotate into center
     * 1 means rotation starts on edge of widget, 0 means only center rotated
     *
     * @param rotationThreshold the rotation threshold to set
     */
    public void setRotationTreshold(float rotationThreshold) {
        this.rotationThreshold = rotationThreshold;
    }
    
    /**
     * Sets distance from center as fraction of half of widget size where covers start to zoom in
     * 1 means scaling starts on edge of widget, 0 means only center scaled
     *
     * @param scalingThreshold the scaling threshold to set
     */
    public void setScalingThreshold(float scalingThreshold) {
        this.scalingThreshold = scalingThreshold;
    }
    
    /**
     * Sets distance from center as fraction of half of widget size,
     * where covers start enlarge their spacing to allow for smooth passing each other without jumping over each other
     * 1 means edge of widget, 0 means only center
     *
     * @param adjustPositionThreshold the adjust position threshold to set
     */
    public void setAdjustPositionThreshold(float adjustPositionThreshold) {
        this.adjustPositionThreshold = adjustPositionThreshold;
    }
    
    /**
     * Sets adjust position multiplier. By enlarging this value, you can enlarge spacing in center of widget done by position adjustment
     *
     * @param adjustPositionMultiplier the adjust position multiplier to set
     */
    public void setAdjustPositionMultiplier(float adjustPositionMultiplier) {
        this.adjustPositionMultiplier = adjustPositionMultiplier;
    }
    
    /**
     * Sets absolute value of rotation angle of cover at edge of widget in degrees.
     * Rotation made by traveling around circle path is added to this value separately.
     * By enlarging this value you make covers more rotated. Max value without traveling on circle would be 90 degrees.
     * With small circle radius could go even over this value sometimes. Look depends also on other parameters.
     *
     * @param maxRotationAngle the max rotation angle to set
     */
    public void setMaxRotationAngle(float maxRotationAngle) {
        this.maxRotationAngle = maxRotationAngle;
    }
    
    /**
     * Sets scale factor of item in center. Normal size is multiplied with this value
     *
     * @param maxScaleFactor the max scale factor to set
     */
    public void setMaxScaleFactor(float maxScaleFactor) {
        this.maxScaleFactor = maxScaleFactor;
    }
    
    /**
     * Sets radius of circle path which covers follow. Range of screen is -1 to 1, minimal radius is therefore 1
     * This value affect how big part of circle path you see on screen and therefore how much away are covers at edge of screen.
     * And also how much they are rotated in direction of circle path.
     *
     * @param radius the radius to set
     */
    public void setRadius(float radius) {
        this.radius = radius;
    }
    
    /**
     * This value affects how far are covers at the edges of widget in Z coordinate in matrix space
     *
     * @param radiusInMatrixSpace the radius in matrix space to set
     */
    public void setRadiusInMatrixSpace(float radiusInMatrixSpace) {
        this.radiusInMatrixSpace = radiusInMatrixSpace;
    }
    
    /**
     * Reflection height as a fraction of cover height (1 means same size as original)
     *
     * @param reflectionHeight the reflection height to set
     */
    public void setReflectionHeight(float reflectionHeight) {
        this.reflectionHeight = reflectionHeight;
    }
    
    /**
     * @param reflectionGap Gap between original image and reflection in pixels
     */
    public void setReflectionGap(int reflectionGap) {
        this.reflectionGap = reflectionGap;
    }
    
    /**
     * @param reflectionOpacity Opacity at most opaque part of reflection fade out effect
     */
    public void setReflectionOpacity(int reflectionOpacity) {
        this.reflectionOpacity = reflectionOpacity;
    }
    
    /**
     * Widget size on which was tuning of parameters done. This value is used to scale parameters when widgets has different size
     *
     * @param size returned by widgets getWidth()
     */
    public void setTuningWidgetSize(int size) {
        this.tuningWidgetSize = size;
    }
    
    /**
     * @param alignTime How long takes center alignment animation in milliseconds
     */
    public void setAlignTime(int alignTime) {
        this.alignTime = alignTime;
    }
    
    /**
     * @param paddingTop Vertical padding in pixels
     */
    public void setVerticalPaddingTop(int paddingTop) {
        this.paddingTop = paddingTop;
    }
    
    public void setVerticalPaddingBottom(int paddingBottom) {
        this.paddingBottom = paddingBottom;
    }
    
    /**
     * Set this to some color if you don't want see through reflections other reflections. Preferably set to same color as background color
     *
     * @param reflectionBackgroundColor the Reflection Background Color to set
     */
    public void setReflectionBackgroundColor(int reflectionBackgroundColor) {
        this.reflectionBackgroundColor = reflectionBackgroundColor;
    }
    
    @Override
    /*
     * Get position of center item in adapter.
     * @return position of center item inside adapter date or -1 if there is no center item shown
     */
    public int getScrollPosition() {
        if (adapter == null || adapter.getCount() == 0) {
            return -1;
        }
    
        if (lastCenterItemIndex != -1) {
            return (firstItemPosition + lastCenterItemIndex) % adapter.getCount();
        } else {
            return (firstItemPosition + (getWidth() / ((int) (coverWidth * spacing))) / 2) % adapter.getCount();
        }
    }
    
    /**
     * Set new center item position
     */
    @Override
    public void scrollToPosition(int position) {
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
    
    /**
     * sets listener for center item position
     *
     * @param onScrollPositionListener listener
     */
    public void setOnScrollPositionListener(OnScrollPositionListener onScrollPositionListener) {
        this.onScrollPositionListener = onScrollPositionListener;
    }
    
    /**
     * removes children, must be after caching children
     *
     * @param coverFrame cover frame
     */
    private void recycleCoverFrame(CoverFrame coverFrame) {
        coverFrame.recycle();
        WeakReference <CoverFrame> ref = new WeakReference <>(coverFrame);
        recycledCoverFrames.addLast(ref);
    }
    
    protected CoverFrame getRecycledCoverFrame() {
        if (!recycledCoverFrames.isEmpty()) {
            CoverFrame v;
            do
            {
                v = recycledCoverFrames.removeFirst().get();
            }
            while (v == null && !recycledCoverFrames.isEmpty());
            return v;
        }
        return null;
    }
    
    /**
     * Removes links to all pictures which are hold by coverflow to speed up rendering
     * Sets environment to state from which it can be refilled on next onLayout
     * Good place to release resources is in activity's onStop.
     */
    public void releaseAllMemoryResources() {
        lastItemPosition = firstItemPosition;
        lastItemPosition--;
    
        final int w = (int) (coverWidth * spacing);
        int sp = getScrollX() % w;
        if (sp < 0) {
            sp = sp + w;
        }
        scrollTo(sp, 0);
    
        removeAllViewsInLayout();
        clearCache();
    }
    
    @Override
    public boolean onPreDraw() { //when child view is about to be drawn we invalidate whole container
    
        if (!invalidated) { //this is hack, no idea now is possible that this works, but fixes problem where not all area was redrawn
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
    
    private class MyCache extends LruCache <Integer, CoverFrame> {
        
        public MyCache(int maxSize) {
            super(maxSize);
        }
    
        @Override
        protected void entryRemoved(boolean evicted, @NonNull Integer key, @NonNull CoverFrame oldValue, CoverFrame newValue) {
            if (evicted) {
                if (oldValue.getChildCount() == 1) {
                    cachedItemViews.addLast(new WeakReference <View>(oldValue.getChildAt(0)));
                    recycleCoverFrame(oldValue); // removes children, must be after caching children
                }
            }
        }
        
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
            
            //register observer to catch cover redraws
            cover.getViewTreeObserver().addOnPreDrawListener(FeatureCoverFlow.this);
            
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
