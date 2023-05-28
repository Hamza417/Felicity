package app.simple.felicity.decorations.carousel;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Adapter;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Scroller;

import java.lang.ref.WeakReference;
import java.util.LinkedList;

import app.simple.felicity.decorations.coverflow.general.ToolBox;

import static android.widget.AdapterView.INVALID_POSITION;

public class Carousel extends ViewGroup {
    /**
     * Children added with this layout mode will be added after the last child
     */
    protected static final int LAYOUT_MODE_AFTER = 0;
    /**
     * Children added with this layout mode will be added before the first child
     */
    protected static final int LAYOUT_MODE_TO_BEFORE = 1;
    /**
     * User is not touching the list
     */
    protected static final int TOUCH_STATE_RESTING = 0;
    /**
     * User is scrolling the list
     */
    protected static final int TOUCH_STATE_SCROLLING = 1;
    /**
     * Fling gesture in progress
     */
    protected static final int TOUCH_STATE_FLING = 2;
    
    protected static final int TOUCH_STATE_DISTANCE_SCROLL = 5;
    /**
     * Aligning in progress
     */
    protected static final int TOUCH_STATE_ALIGN = 3;
    private static final String TAG = "Carousel";
    protected final int NO_VALUE = Integer.MIN_VALUE + 1777;
    protected final ViewCache <View> viewCache = new ViewCache <>();
    protected static final int SCROLLING_DURATION = 500;
    final Scroller scroller = new Scroller(getContext(), new DecelerateInterpolator(1.5F));
    private final int minimumVelocity;
    private final int maximumVelocity;
    private final int touchSlop;
    protected int touchState = TOUCH_STATE_RESTING;
    private final Point down = new Point();
    /**
     * The adaptor position of the first visible item
     */
    protected int firstItemPosition;
    /**
     * The adaptor position of the last visible item
     */
    protected int lastItemPosition;
    /**
     * Relative spacing value of Views in container. If <1 Views will overlap, if >1 Views will have spaces between them
     */
    protected float spacing = 0.7f;
    protected int childWidth = 350;
    /**
     * The adaptor position of selected item
     */
    protected int selectedPosition = INVALID_POSITION;
    protected OnItemClickListener onItemClickListener;
    protected OnItemSelectedListener onItemSelectedListener;
    private boolean handleSelectionOnActionUp = false;
    private boolean interceptTouchEvents;
    private int centerItemOffset;
    protected int childHeight = 350;
    protected Adapter adapter;
    protected int rightEdge = NO_VALUE;
    protected int leftEdge = NO_VALUE;
    /**
     * How long will alignment animation take
     */
    private int alignTime = 1000;
    private boolean allowLongPress;
    /**
     * Index of view in center of screen, which is most in foreground
     */
    private int reverseOrderIndex = -1;
    private VelocityTracker velocityTracker;
    private int selection;
    private float lastMotionX;
    /**
     * Movement speed will be divided by this coefficient;
     */
    private int slowDownCoefficient = 1;
    private int firstVisibleChild;
    private int lastVisibleChild;
    
    private final DataSetObserver dataSetObserver = new DataSetObserver() {
        
        @Override
        public void onChanged() {
            reset();
        }
        
        @Override
        public void onInvalidated() {
            removeAllViews();
            invalidate();
        }
        
    };
    
    public Carousel(Context context) {
        this(context, null);
    }
    
    public Carousel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public Carousel(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        
        setChildrenDrawingOrderEnabled(true);
        final ViewConfiguration configuration = ViewConfiguration.get(context);
        touchSlop = configuration.getScaledTouchSlop();
        minimumVelocity = configuration.getScaledMinimumFlingVelocity();
        maximumVelocity = configuration.getScaledMaximumFlingVelocity();
    }
    
    public Adapter getAdapter() {
        return adapter;
    }
    
    public void setAdapter(Adapter adapter) {
        if (this.adapter != null) {
            this.adapter.unregisterDataSetObserver(dataSetObserver);
        }
    
        this.adapter = adapter;
        this.adapter.registerDataSetObserver(dataSetObserver);
        reset();
    }
    
    public View getSelectedView() {
        return getChildAt(reverseOrderIndex);
    }
    
    public int getSelection() {
        return selection;
    }
    
    public void setSelection(int position) {
        if (adapter == null) {
            throw new IllegalStateException("You are trying to set selection on widget without adapter");
        }
        if (position < 0 || position > adapter.getCount() - 1) {
            throw new IllegalArgumentException("Position index must be in range of adapter values (0 - getCount()-1)");
        }
    
        selection = position;
        
        reset();
    }
    
    @Override
    public void computeScroll() {
        final int centerItemLeft = getWidth() / 2 - childWidth / 2;
        final int centerItemRight = getWidth() / 2 + childWidth / 2;
    
        if (rightEdge != NO_VALUE && scroller.getFinalX() > rightEdge - centerItemRight) {
            scroller.setFinalX(rightEdge - centerItemRight);
        }
        if (leftEdge != NO_VALUE && scroller.getFinalX() < leftEdge - centerItemLeft) {
            scroller.setFinalX(leftEdge - centerItemLeft);
        }
    
        if (scroller.computeScrollOffset()) {
            if (scroller.getFinalX() == scroller.getCurrX()) {
                scroller.abortAnimation();
                touchState = TOUCH_STATE_RESTING;
                Log.d("Carousel", "computeScroll: clearChildrenCache");
                if (!checkScrollPosition()) {
                    clearChildrenCache();
                }
                return;
            }
        
            int x = scroller.getCurrX();
            scrollTo(x, 0);
        
            postInvalidate();
        } else if (touchState == TOUCH_STATE_FLING || touchState == TOUCH_STATE_DISTANCE_SCROLL) {
            touchState = TOUCH_STATE_RESTING;
            if (!checkScrollPosition()) {
                clearChildrenCache();
            }
        }
    
        refill();
        updateReverseOrderIndex();
    
        super.computeScroll();
    }
    
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (adapter == null || adapter.getCount() == 0) {
            return;
        }
        View v = null;
        if (getChildCount() == 0) {
            v = getViewFromAdapter(selection);
            addAndMeasureChild(v, LAYOUT_MODE_AFTER);
    
            final int horizontalCenter = getWidth() / 2;
            final int verticalCenter = getHeight() / 2;
            final int left = horizontalCenter - v.getMeasuredWidth() / 2;
            final int right = left + v.getMeasuredWidth();
            final int top = verticalCenter - v.getMeasuredHeight() / 2;
            final int bottom = top + v.getMeasuredHeight();
            v.layout(left, top, right, bottom);
    
            firstVisibleChild = selection;
            lastVisibleChild = selection;
    
            if (lastVisibleChild == adapter.getCount() - 1) {
                rightEdge = right;
            }
            if (firstVisibleChild == 0) {
                leftEdge = left;
            }
        }
        
        refill();
        
        if (v != null) {
            reverseOrderIndex = indexOfChild(v);
            v.setSelected(true);
        } else {
            updateReverseOrderIndex();
        }
    }
    
    private void updateReverseOrderIndex() {
        int oldReverseIndex = reverseOrderIndex;
        final int screenCenter = getWidth() / 2 + getScrollX();
        final int c = getChildCount();
        
        int minDiff = Integer.MAX_VALUE;
        int minDiffIndex = -1;
        
        int viewCenter, diff;
        for (int i = 0; i < c; i++) {
            viewCenter = getChildCenter(i);
            diff = Math.abs(screenCenter - viewCenter);
            if (diff < minDiff) {
                minDiff = diff;
                minDiffIndex = i;
            }
        }
    
        if (minDiff != Integer.MAX_VALUE) {
            reverseOrderIndex = minDiffIndex;
        }
    
        try {
            if (oldReverseIndex != reverseOrderIndex) {
                View oldSelected = getChildAt(oldReverseIndex);
                View newSelected = getChildAt(reverseOrderIndex);
            
                oldSelected.setSelected(false);
                newSelected.setSelected(true);
            
                selection = firstVisibleChild + reverseOrderIndex;
                if (onItemSelectedListener != null) {
                    onItemSelectedListener.onItemSelected(newSelected, selection);
                }
            }
        } catch (NullPointerException e) {
            Log.e("Carousel", "NullPointerException in updateReverseOrderIndex");
        }
    }
    
    protected boolean checkScrollPosition() {
        if (centerItemOffset != 0) {
            scroller.startScroll(getScrollX(), 0, centerItemOffset, 0, alignTime);
            touchState = TOUCH_STATE_ALIGN;
            invalidate();
            return true;
        }
        return false;
    }
    
    /**
     * Layout children from right to left
     */
    protected int layoutChildToBefore(View v, int right) {
        final int verticalCenter = getHeight() / 2;
        
        int l, t, r, b;
        l = right - v.getMeasuredWidth();
        t = verticalCenter - v.getMeasuredHeight() / 2;
        ;
        r = right;
        b = t + v.getMeasuredHeight();
        
        v.layout(l, t, r, b);
        return r - (int) (v.getMeasuredWidth() * spacing);
    }
    
    /**
     * @param left X coordinate where should we start layout
     */
    protected int layoutChild(View v, int left) {
        final int verticalCenter = getHeight() / 2;
        
        int l, t, r, b;
        l = left;
        t = verticalCenter - v.getMeasuredHeight() / 2;
        ;
        r = l + v.getMeasuredWidth();
        b = t + v.getMeasuredHeight();
        
        v.layout(l, t, r, b);
        return l + (int) (v.getMeasuredWidth() * spacing);
    }
    
    /**
     * Adds a view as a child view and takes care of measuring it
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
    
    /**
     * Remove all data, reset to initial state and attempt to refill
     */
    private void reset() {
        if (adapter == null || adapter.getCount() == 0) {
            return;
        }
    
        if (getChildCount() == 0) {
            requestLayout();
            return;
        }
    
        View selectedView = getChildAt(reverseOrderIndex);
        int selectedLeft = selectedView.getLeft();
        int selectedTop = selectedView.getTop();
    
        removeAllViewsInLayout();
        rightEdge = NO_VALUE;
        leftEdge = NO_VALUE;
    
        View v = adapter.getView(selection, null, this);
        addAndMeasureChild(v, LAYOUT_MODE_AFTER);
        reverseOrderIndex = 0;
    
        final int right = selectedLeft + v.getMeasuredWidth();
        final int bottom = selectedTop + v.getMeasuredHeight();
        v.layout(selectedLeft, selectedTop, right, bottom);
    
        firstVisibleChild = selection;
        lastVisibleChild = selection;
    
        if (lastVisibleChild == adapter.getCount() - 1) {
            rightEdge = right;
        }
        if (firstVisibleChild == 0) {
            leftEdge = selectedLeft;
        }
    
        refill();
    
        reverseOrderIndex = indexOfChild(v);
        v.setSelected(true);
    }
    
    protected void refill() {
        if (adapter == null || getChildCount() == 0) {
            return;
        }
        
        final int leftScreenEdge = getScrollX();
        int rightScreenEdge = leftScreenEdge + getWidth();
        
        removeNonVisibleViewsLeftToRight(leftScreenEdge);
        removeNonVisibleViewsRightToLeft(rightScreenEdge);
        
        refillLeftToRight(leftScreenEdge, rightScreenEdge);
        refillRightToLeft(leftScreenEdge);
    }
    
    protected int getPartOfViewCoveredBySibling() {
        return (int) (childWidth * (1.0f - spacing));
    }
    
    protected View getViewFromAdapter(int position) {
        return adapter.getView(position, viewCache.getCachedView(), this);
    }
    
    /**
     * Checks and refills empty area on the left
     *
     * @return firstItemPosition
     */
    protected void refillRightToLeft(final int leftScreenEdge) {
        if (getChildCount() == 0) {
            return;
        }
    
        View child = getChildAt(0);
        int childRight = child.getRight();
        int newRight = childRight - (int) (childWidth * spacing);
    
        while (newRight - getPartOfViewCoveredBySibling() > leftScreenEdge && firstVisibleChild > 0) {
            firstVisibleChild--;
        
            child = getViewFromAdapter(firstVisibleChild);
            child.setSelected(false);
            reverseOrderIndex++;
        
            addAndMeasureChild(child, LAYOUT_MODE_TO_BEFORE);
            newRight = layoutChildToBefore(child, newRight);
        
            if (firstVisibleChild <= 0) {
                leftEdge = child.getLeft();
            }
        }
        return;
    }
    
    /**
     * Checks and refills empty area on the right
     */
    protected void refillLeftToRight(final int leftScreenEdge, final int rightScreenEdge) {
    
        View child;
        int newLeft;
    
        child = getChildAt(getChildCount() - 1);
        int childLeft = child.getLeft();
        newLeft = childLeft + (int) (childWidth * spacing);
    
        while (newLeft + getPartOfViewCoveredBySibling() < rightScreenEdge && lastVisibleChild < adapter
                .getCount() - 1) {
            lastVisibleChild++;
        
            child = getViewFromAdapter(lastVisibleChild);
            child.setSelected(false);
        
            addAndMeasureChild(child, LAYOUT_MODE_AFTER);
            newLeft = layoutChild(child, newLeft);
        
            if (lastVisibleChild >= adapter.getCount() - 1) {
                rightEdge = child.getRight();
            }
        }
    }
    
    /**
     * Remove non visible views from left edge of screen
     */
    protected void removeNonVisibleViewsLeftToRight(final int leftScreenEdge) {
        if (getChildCount() == 0) {
            return;
        }
        
        // check if we should remove any views in the left
        View firstChild = getChildAt(0);
    
        while (firstChild != null && firstChild.getLeft() + (childWidth * spacing) < leftScreenEdge && getChildCount() > 1) {
        
            // remove view
            removeViewsInLayout(0, 1);
        
            viewCache.cacheView(firstChild);
        
            firstVisibleChild++;
            reverseOrderIndex--;
        
            if (reverseOrderIndex == 0) {
                break;
            }
        
            // Continue to check the next child only if we have more than
            // one child left
            if (getChildCount() > 1) {
                firstChild = getChildAt(0);
            } else {
                firstChild = null;
            }
        }
        
    }
    
    /**
     * Remove non visible views from right edge of screen
     */
    protected void removeNonVisibleViewsRightToLeft(final int rightScreenEdge) {
        if (getChildCount() == 0) {
            return;
        }
        
        // check if we should remove any views in the right
        View lastChild = getChildAt(getChildCount() - 1);
        while (lastChild != null && lastChild.getRight() - (childWidth * spacing) > rightScreenEdge &&
                getChildCount() > 1) {
            // remove the right view
            removeViewsInLayout(getChildCount() - 1, 1);
    
            viewCache.cacheView(lastChild);
    
            lastVisibleChild--;
            if (getChildCount() - 1 == reverseOrderIndex) {
                break;
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
    
    protected int getChildCenter(View v) {
        final int w = v.getRight() - v.getLeft();
        return v.getLeft() + w / 2;
    }
    
    protected int getChildCenter(int i) {
        return getChildCenter(getChildAt(i));
    }
    
    //    @Override
    //    protected void dispatchDraw(Canvas canvas) {
    //        super.dispatchDraw(canvas);
    //    }
    
    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        if (i < reverseOrderIndex) {
            return i;
        } else {
            return childCount - 1 - (i - reverseOrderIndex);
        }
    }
    
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        
        /*
         * This method JUST determines whether we want to intercept the motion.
         * If we return true, onTouchEvent will be called and we do the actual
         * scrolling there.
         */
        
        
        /*
         * Shortcut the most recurring case: the user is in the dragging
         * state and he is moving his finger.  We want to intercept this
         * motion.
         */
        final int action = ev.getAction();
        if ((action == MotionEvent.ACTION_MOVE) && (touchState == TOUCH_STATE_SCROLLING)) {
            return true;
        }
        
        final float x = ev.getX();
        final float y = ev.getY();
        switch (action) {
            case MotionEvent.ACTION_MOVE:
                /*
                 * not dragging, otherwise the shortcut would have caught it. Check
                 * whether the user has moved far enough from his original down touch.
                 */
    
                /*
                 * Locally do absolute value. lastMotionX is set to the x value
                 * of the down event.
                 */
                final int xDiff = (int) Math.abs(x - lastMotionX);
                
                final int touchSlop = this.touchSlop;
                final boolean xMoved = xDiff > touchSlop;
                
                if (xMoved) {
                    // Scroll if the user moved far enough along the axis
                    touchState = TOUCH_STATE_SCROLLING;
                    handleSelectionOnActionUp = false;
                    enableChildrenCache();
                    cancelLongPress();
                }
                
                break;
    
            case MotionEvent.ACTION_DOWN:
                // Remember location of down touch
                lastMotionX = x;
    
                /*
                 * If being flinged and user touches the screen, initiate drag;
                 * otherwise don't.  scroller.isFinished should be false when
                 * being flinged.
                 */
                touchState = scroller.isFinished() ? TOUCH_STATE_RESTING : TOUCH_STATE_SCROLLING;
                //if he had normal click in rested state, remember for action up check
                if (touchState == TOUCH_STATE_RESTING) {
                    handleSelectionOnActionUp = true;
                }
                break;
    
            case MotionEvent.ACTION_UP:
                //if we had normal down click and we haven't moved enough to initiate drag, take action as a click on down coordinates
                if (handleSelectionOnActionUp && touchState == TOUCH_STATE_RESTING) {
                    final double d = ToolBox.getLineLength(down.x, down.y, x, y);
                    if ((ev.getEventTime() - ev.getDownTime()) < ViewConfiguration.getLongPressTimeout() && d < this.touchSlop) {
                        handleClick(down);
                    }
                }
                // Release the drag
                allowLongPress = false;
                handleSelectionOnActionUp = false;
                down.x = -1;
                down.y = -1;
                if (touchState == TOUCH_STATE_SCROLLING) {
                    if (checkScrollPosition()) {
                        Log.d(TAG, "onInterceptTouchEvent: ACTION_UP: checkScrollPosition returned true, scrolling to nearest item");
                        break;
                    }
                }
                touchState = TOUCH_STATE_RESTING;
                clearChildrenCache();
                break;
        }
    
        return touchState == TOUCH_STATE_SCROLLING;
        
    }
    
    protected void scrollByDelta(int deltaX) {
        deltaX /= slowDownCoefficient;
    
        final int centerItemLeft = getWidth() / 2 - childWidth / 2;
        final int centerItemRight = getWidth() / 2 + childWidth / 2;
    
        final int rightInPixels;
        final int leftInPixels;
        if (rightEdge == NO_VALUE) {
            rightInPixels = Integer.MAX_VALUE;
        } else {
            rightInPixels = rightEdge;
        }
    
        if (leftEdge == NO_VALUE) {
            leftInPixels = Integer.MIN_VALUE + getWidth(); //we cant have min value because of integer overflow
        } else {
            leftInPixels = leftEdge;
        }
        
        final int x = getScrollX() + deltaX;
    
        if (x < (leftInPixels - centerItemLeft)) {
            deltaX -= x - (leftInPixels - centerItemLeft);
        } else if (x > rightInPixels - centerItemRight) {
            deltaX -= x - (rightInPixels - centerItemRight);
        }
    
        scrollBy(deltaX, 0);
    }
    
    /**
     * Scroll widget by given distance in pixels
     *
     * @param dx
     */
    public void scroll(int dx) {
        scroller.startScroll(getScrollX(), 0, dx, 0, SCROLLING_DURATION);
        touchState = TOUCH_STATE_DISTANCE_SCROLL;
        invalidate();
    }
    
    public boolean onTouchEvent(MotionEvent event) {
        if (adapter == null) {
            return false;
        }
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(event);
    
        final int action = event.getAction();
        final float x = event.getX();
        final float y = event.getY();
    
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                super.onTouchEvent(event);
                /*
                 * If being flinged and user touches, stop the fling. isFinished
                 * will be false if being flinged.
                 */
                if (!scroller.isFinished()) {
                    scroller.forceFinished(true);
                }
            
                down.x = (int) x;
                // Remember where the motion event started
                lastMotionX = x;
                // mLastMotionY = y;
            
                break;
            case MotionEvent.ACTION_MOVE:
                // if we have scrolling disabled, we don't do anything
                // if (!shouldRepeat && isSrollingDisabled) return false;
            
                if (touchState == TOUCH_STATE_SCROLLING) {
                    // Scroll to follow the motion event
                    final int deltaX = (int) (lastMotionX - x);
                    lastMotionX = x;
                    // mLastMotionY = y;
                
                    int sx = getScrollX() + deltaX;
                
                    if (down.x > x) {
                        scrollBy(deltaX, 0);
                    } else {
                        final int rightInPixels;
                        if (rightEdge == NO_VALUE) {
                            rightInPixels = Integer.MAX_VALUE;
                        } else {
                            rightInPixels = rightEdge;
                        }
                    
                        if (sx < rightInPixels - getWidth()) {
                            scrollBy(deltaX, 0);
                        }
                    }
                
                    down.x = (int) x;
                
                } else {
                    final int xDiff = (int) Math.abs(x - lastMotionX);
                    
                    final int touchSlop = this.touchSlop;
                    final boolean xMoved = xDiff > touchSlop;
                
                    if (xMoved) {
                    
                        // Scroll if the user moved far enough along the X axis
                        touchState = TOUCH_STATE_SCROLLING;
                        enableChildrenCache();
                    
                        // Either way, cancel any pending longpress
                        if (allowLongPress) {
                            allowLongPress = false;
                            // Try canceling the long press. It could also have been scheduled
                            // by a distant descendant, so use the allowLongPress flag to block
                            // everything
                            cancelLongPress();
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            
                //this must be here, in case no child view returns true,
                //events will propagate back here and on intercept touch event wont be called again
                //in case of no parent it propagates here, in case of parent it usualy propagates to on cancel
                if (handleSelectionOnActionUp && touchState == TOUCH_STATE_RESTING) {
                    final float d = (float) ToolBox.getLineLength(down.x, down.y, x, y);
                    if ((event.getEventTime() - event.getDownTime()) < ViewConfiguration.getLongPressTimeout() && d < touchSlop) {
                        handleClick(down);
                    }
                    handleSelectionOnActionUp = false;
                }
            
                //if we had normal down click and we haven't moved enough to initiate drag, take action as a click on down coordinates
                if (touchState == TOUCH_STATE_SCROLLING) {
                
                    velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
                    int initialXVelocity = (int) velocityTracker.getXVelocity();
                    int initialYVelocity = (int) velocityTracker.getYVelocity();
                
                    if (Math.abs(initialXVelocity) + Math.abs(initialYVelocity) > minimumVelocity) {
                        fling(-initialXVelocity, -initialYVelocity);
                    } else {
                        // Release the drag
                        clearChildrenCache();
                        touchState = TOUCH_STATE_RESTING;
                        checkScrollPosition();
                        allowLongPress = false;
                    
                        down.x = -1;
                        down.y = -1;
                    }
                
                    if (velocityTracker != null) {
                        velocityTracker.recycle();
                        velocityTracker = null;
                    }
                
                    break;
                }
            
                // Release the drag
                clearChildrenCache();
                touchState = TOUCH_STATE_RESTING;
                allowLongPress = false;
            
                down.x = -1;
                down.y = -1;
            
                break;
            case MotionEvent.ACTION_CANCEL:
            
                allowLongPress = false;
            
                down.x = -1;
                down.y = -1;
            
                if (touchState == TOUCH_STATE_SCROLLING) {
                    if (checkScrollPosition()) {
                        break;
                    }
                }
            
                touchState = TOUCH_STATE_RESTING;
        }
        
        return true;
    }
    
    public void fling(int velocityX, int velocityY) {
        velocityX /= slowDownCoefficient;
    
        touchState = TOUCH_STATE_FLING;
        final int x = getScrollX();
        final int y = getScrollY();
    
        final int centerItemLeft = getWidth() / 2 - childWidth / 2;
        final int centerItemRight = getWidth() / 2 + childWidth / 2;
        final int rightInPixels;
        final int leftInPixels;
        if (rightEdge == NO_VALUE) {
            rightInPixels = Integer.MAX_VALUE;
        } else {
            rightInPixels = rightEdge;
        }
        if (leftEdge == NO_VALUE) {
            leftInPixels = Integer.MIN_VALUE + getWidth();
        } else {
            leftInPixels = leftEdge;
        }
    
        scroller.fling(x, y, velocityX, velocityY, leftInPixels - centerItemLeft,
                rightInPixels - centerItemRight + 1, 0, 0);
        
        invalidate();
    }
    
    private void enableChildrenCache() {
        setChildrenDrawnWithCacheEnabled(true);
        setChildrenDrawingCacheEnabled(true);
    }
    
    void clearChildrenCache() {
        setChildrenDrawnWithCacheEnabled(false);
    }
    
    /**
     * Set widget spacing (float means fraction of widget size, 1 = widget size)
     *
     * @param spacing the spacing to set
     */
    public void setSpacing(float spacing) {
        this.spacing = spacing;
    }
    
    public void setChildWidth(int width) {
        childWidth = width;
    }
    
    public void setChildHeight(int height) {
        childHeight = height;
    }
    
    public void setSlowDownCoefficient(int c) {
        if (c < 1) {
            throw new IllegalArgumentException("Slowdown coeficient must be greater than 0");
        }
        slowDownCoefficient = c;
    }
    
    protected void handleClick(Point p) {
        final int c = getChildCount();
        View v;
        final Rect r = new Rect();
        for (int i = 0; i < c; i++) {
            v = getChildAt(i);
            v.getHitRect(r);
            if (r.contains(getScrollX() + p.x, getScrollY() + p.y)) {
                final View old = getSelectedView();
                if (old != null) {
                    old.setSelected(false);
                }
                
                int position = firstItemPosition + i;
                if (position >= adapter.getCount()) {
                    position = position - adapter.getCount();
                }
                
                selectedPosition = position;
                v.setSelected(true);
                
                if (onItemClickListener != null) {
                    // onItemClickListener.onItemClick(this, v, position, getItemIdAtPosition(position));
                }
                if (onItemSelectedListener != null) {
                    // onItemSelectedListener.onItemSelected(this, v, position, getItemIdAtPosition(position));
                }
                
                break;
            }
        }
    }
    
    public long getItemIdAtPosition(int position) {
        return adapter.getItemId(position);
    }
    
    public void setOnItemSelectedListener(OnItemSelectedListener onItemSelectedListener) {
        this.onItemSelectedListener = onItemSelectedListener;
    }
    
    public int getCurrentItemPosition() {
        return selectedPosition;
    }
    
    public interface OnItemSelectedListener {
        void onItemSelected(View child, int position);
    }
    
    protected static class ViewCache <T extends View> {
        private final LinkedList <WeakReference <T>> mCachedItemViews = new LinkedList <WeakReference <T>>();
        
        /**
         * Check if list of weak references has any view still in memory to offer for recycling
         *
         * @return cached view
         */
        public T getCachedView() {
            if (mCachedItemViews.size() != 0) {
                T v;
                do
                {
                    v = mCachedItemViews.removeFirst().get();
                }
                while (v == null && mCachedItemViews.size() != 0);
                return v;
            }
            return null;
        }
        
        public void cacheView(T v) {
            WeakReference <T> ref = new WeakReference <T>(v);
            mCachedItemViews.addLast(ref);
        }
    }
}