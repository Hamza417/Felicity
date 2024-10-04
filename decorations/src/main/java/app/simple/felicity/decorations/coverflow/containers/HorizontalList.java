package app.simple.felicity.decorations.coverflow.containers;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.Scroller;

import app.simple.felicity.decorations.coverflow.containers.interfaces.IViewObserver;
import app.simple.felicity.decorations.coverflow.general.ToolBox;

public class HorizontalList extends ViewGroup {
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
    /**
     * Children added with this layout mode will be added after the last child
     */
    protected static final int LAYOUT_MODE_AFTER = 0;
    /**
     * Children added with this layout mode will be added before the first child
     */
    protected static final int LAYOUT_MODE_TO_BEFORE = 1;
    protected final int NO_VALUE = -11;
    protected final ToolBox.ViewCache <View> mCache = new ToolBox.ViewCache <View>();
    private final Scroller mScroller = new Scroller(getContext());
    private final Point mDown = new Point();
    protected int mFirstItemPosition;
    protected int mLastItemPosition;
    protected boolean isScrollingDisabled = false;
    protected Adapter mAdapter;
    protected int mTouchSlop;
    protected int mRightEdge = NO_VALUE;
    protected IViewObserver mViewObserver;
    private int mMinimumVelocity;
    private int mMaximumVelocity;
    private int mTouchState = TOUCH_STATE_RESTING;
    private float mLastMotionX;
    private VelocityTracker mVelocityTracker;
    private boolean mHandleSelectionOnActionUp = false;
    private int mDefaultItemWidth = 200;
    private final DataSetObserver mDataObserver = new DataSetObserver() {
        
        @Override
        public void onChanged() {
            reset();
            invalidate();
        }
        
        @Override
        public void onInvalidated() {
            removeAllViews();
            invalidate();
        }
        
    };
    //listeners
    private OnItemClickListener mItemClickListener;
    
    public HorizontalList(Context context) {
        this(context, null);
    }
    
    public HorizontalList(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public HorizontalList(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        
        final ViewConfiguration configuration = ViewConfiguration.get(context);
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
    }
    
    /**
     * Remove all data, reset to initial state and attempt to refill
     * Position of first item on screen in Adapter data set is maintained
     */
    private void reset() {
        int scroll = getScrollX();
        
        int left = 0;
        if (getChildCount() != 0) {
            left = getChildAt(0).getLeft() - ((MarginLayoutParams) getChildAt(0).getLayoutParams()).leftMargin;
        }
        
        removeAllViewsInLayout();
        mLastItemPosition = mFirstItemPosition;
        mRightEdge = NO_VALUE;
        scrollTo(left, 0);
        
        final int leftScreenEdge = getScrollX();
        int rightScreenEdge = leftScreenEdge + getWidth();
        
        refillLeftToRight(leftScreenEdge, rightScreenEdge);
        refillRightToLeft(leftScreenEdge);
        
        scrollTo(scroll, 0);
    }
    
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        refill();
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
        int childLeft = child.getLeft();
        int lastLeft = childLeft - ((MarginLayoutParams) child.getLayoutParams()).leftMargin;
        
        while (lastLeft > leftScreenEdge && mFirstItemPosition > 0) {
            mFirstItemPosition--;
            
            child = mAdapter.getView(mFirstItemPosition, mCache.getCachedView(), this);
            sanitizeLayoutParams(child);
            
            addAndMeasureChild(child, LAYOUT_MODE_TO_BEFORE);
            final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            lastLeft = layoutChildToBefore(child, lastLeft, lp);
            childLeft = child.getLeft() - ((MarginLayoutParams) child.getLayoutParams()).leftMargin;
            
        }
        return;
    }
    
    /**
     * Checks and refills empty area on the right
     */
    protected void refillLeftToRight(final int leftScreenEdge, final int rightScreenEdge) {
        
        View child;
        int lastRight;
        if (getChildCount() != 0) {
            child = getChildAt(getChildCount() - 1);
            lastRight = child.getRight() + ((MarginLayoutParams) child.getLayoutParams()).rightMargin;
        } else {
            lastRight = leftScreenEdge;
            if (mLastItemPosition == mFirstItemPosition) {
                mLastItemPosition--;
            }
        }
        
        while (lastRight < rightScreenEdge && mLastItemPosition < mAdapter.getCount() - 1) {
            mLastItemPosition++;
            
            child = mAdapter.getView(mLastItemPosition, mCache.getCachedView(), this);
            sanitizeLayoutParams(child);
            
            addAndMeasureChild(child, LAYOUT_MODE_AFTER);
            final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            lastRight = layoutChild(child, lastRight, lp);
            
            if (mLastItemPosition >= mAdapter.getCount() - 1) {
                mRightEdge = lastRight;
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
        
        while (firstChild != null && firstChild.getRight() + ((MarginLayoutParams) firstChild.getLayoutParams()).rightMargin < leftScreenEdge) {
            
            // remove view
            removeViewsInLayout(0, 1);
            
            if (mViewObserver != null) {
                mViewObserver.onViewRemovedFromParent(firstChild, mFirstItemPosition);
            }
            mCache.cacheView(firstChild);
            
            mFirstItemPosition++;
            if (mFirstItemPosition >= mAdapter.getCount()) {
                mFirstItemPosition = 0;
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
        while (lastChild != null && lastChild.getLeft() - ((MarginLayoutParams) lastChild.getLayoutParams()).leftMargin > rightScreenEdge) {
            // remove the right view
            removeViewsInLayout(getChildCount() - 1, 1);
            
            if (mViewObserver != null) {
                mViewObserver.onViewRemovedFromParent(lastChild, mLastItemPosition);
            }
            mCache.cacheView(lastChild);
            
            mLastItemPosition--;
            if (mLastItemPosition < 0) {
                mLastItemPosition = mAdapter.getCount() - 1;
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
    
    protected void refill() {
        if (mAdapter == null) {
            return;
        }
        
        final int leftScreenEdge = getScrollX();
        int rightScreenEdge = leftScreenEdge + getWidth();
        
        removeNonVisibleViewsLeftToRight(leftScreenEdge);
        removeNonVisibleViewsRightToLeft(rightScreenEdge);
        
        refillLeftToRight(leftScreenEdge, rightScreenEdge);
        refillRightToLeft(leftScreenEdge);
    }
    
    protected void sanitizeLayoutParams(View child) {
        MarginLayoutParams lp;
        if (child.getLayoutParams() instanceof MarginLayoutParams) {
            lp = (MarginLayoutParams) child.getLayoutParams();
        } else if (child.getLayoutParams() != null) {
            lp = new MarginLayoutParams(child.getLayoutParams());
        } else {
            lp = new MarginLayoutParams(mDefaultItemWidth, getHeight());
        }
        
        if (lp.height == LayoutParams.MATCH_PARENT) {
            lp.height = getHeight();
        }
        if (lp.width == LayoutParams.MATCH_PARENT) {
            lp.width = getWidth();
        }
        
        if (lp.height == LayoutParams.WRAP_CONTENT) {
            measureUnspecified(child);
            lp.height = child.getMeasuredHeight();
        }
        if (lp.width == LayoutParams.WRAP_CONTENT) {
            measureUnspecified(child);
            lp.width = child.getMeasuredWidth();
        }
        child.setLayoutParams(lp);
    }
    
    private void measureUnspecified(View child) {
        final int pwms = MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.UNSPECIFIED);
        final int phms = MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.UNSPECIFIED);
        measureChild(child, pwms, phms);
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
            child.setLayoutParams(new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }
        
        final int index = layoutMode == LAYOUT_MODE_TO_BEFORE ? 0 : -1;
        addViewInLayout(child, index, child.getLayoutParams(), true);
        
        final int pwms = MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY);
        final int phms = MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY);
        measureChild(child, pwms, phms);
        child.setDrawingCacheEnabled(false);
        
        return child;
    }
    
    /**
     * Layout children from right to left
     */
    protected int layoutChildToBefore(View v, int right, MarginLayoutParams lp) {
        final int left = right - v.getMeasuredWidth() - lp.leftMargin - lp.rightMargin;
        layoutChild(v, left, lp);
        return left;
    }
    
    /**
     * @param topline Y coordinate of topline
     * @param left    X coordinate where should we start layout
     */
    protected int layoutChild(View v, int left, MarginLayoutParams lp) {
        int l, t, r, b;
        l = left + lp.leftMargin;
        t = lp.topMargin;
        r = l + v.getMeasuredWidth();
        b = t + v.getMeasuredHeight();
        
        v.layout(l, t, r, b);
        return r + lp.rightMargin;
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
        if ((action == MotionEvent.ACTION_MOVE) && (mTouchState == TOUCH_STATE_SCROLLING)) {
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
                 * Locally do absolute value. mLastMotionX is set to the x value
                 * of the down event.
                 */
                final int xDiff = (int) Math.abs(x - mLastMotionX);
                
                final int touchSlop = mTouchSlop;
                final boolean xMoved = xDiff > touchSlop;
                
                if (xMoved) {
                    // Scroll if the user moved far enough along the axis
                    mTouchState = TOUCH_STATE_SCROLLING;
                    mHandleSelectionOnActionUp = false;
                    enableChildrenCache();
                    cancelLongPress();
                }
                
                break;
            
            case MotionEvent.ACTION_DOWN:
                // Remember location of down touch
                mLastMotionX = x;
                
                mDown.x = (int) x;
                mDown.y = (int) y;
                
                /*
                 * If being flinged and user touches the screen, initiate drag;
                 * otherwise don't.  mScroller.isFinished should be false when
                 * being flinged.
                 */
                mTouchState = mScroller.isFinished() ? TOUCH_STATE_RESTING : TOUCH_STATE_SCROLLING;
                //if he had normal click in rested state, remember for action up check
                if (mTouchState == TOUCH_STATE_RESTING) {
                    mHandleSelectionOnActionUp = true;
                }
                break;
            
            case MotionEvent.ACTION_CANCEL:
                mDown.x = -1;
                mDown.y = -1;
                break;
            case MotionEvent.ACTION_UP:
                //if we had normal down click and we haven't moved enough to initiate drag, take action as a click on down coordinates
                if (mHandleSelectionOnActionUp && mTouchState == TOUCH_STATE_RESTING) {
                    final double d = ToolBox.getLineLength(mDown.x, mDown.y, x, y);
                    if ((ev.getEventTime() - ev.getDownTime()) < ViewConfiguration.getLongPressTimeout() && d < mTouchSlop) {
                        handleClick(mDown);
                    }
                }
                // Release the drag
                mHandleSelectionOnActionUp = false;
                mDown.x = -1;
                mDown.y = -1;
                
                mTouchState = TOUCH_STATE_RESTING;
                clearChildrenCache();
                break;
        }
        
        return mTouchState == TOUCH_STATE_SCROLLING;
        
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
        
        final int action = event.getAction();
        final float x = event.getX();
        final float y = event.getY();
        
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                /*
                 * If being flinged and user touches, stop the fling. isFinished
                 * will be false if being flinged.
                 */
                if (!mScroller.isFinished()) {
                    mScroller.forceFinished(true);
                }
                
                // Remember where the motion event started
                mLastMotionX = x;
                
                break;
            case MotionEvent.ACTION_MOVE:
                
                if (mTouchState == TOUCH_STATE_SCROLLING) {
                    // Scroll to follow the motion event
                    final int deltaX = (int) (mLastMotionX - x);
                    mLastMotionX = x;
                    
                    scrollByDelta(deltaX);
                } else {
                    final int xDiff = (int) Math.abs(x - mLastMotionX);
                    
                    final int touchSlop = mTouchSlop;
                    final boolean xMoved = xDiff > touchSlop;
                    
                    if (xMoved) {
                        // Scroll if the user moved far enough along the axis
                        mTouchState = TOUCH_STATE_SCROLLING;
                        enableChildrenCache();
                        cancelLongPress();
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                
                //this must be here, in case no child view returns true,
                //events will propagate back here and on intercept touch event wont be called again
                //in case of no parent it propagates here, in case of parent it usually propagates to on cancel
                if (mHandleSelectionOnActionUp && mTouchState == TOUCH_STATE_RESTING) {
                    final double d = ToolBox.getLineLength(mDown.x, mDown.y, x, y);
                    if ((event.getEventTime() - event.getDownTime()) < ViewConfiguration.getLongPressTimeout() && d < mTouchSlop) {
                        handleClick(mDown);
                    }
                    mHandleSelectionOnActionUp = false;
                }
                
                //if we had normal down click and we haven't moved enough to initiate drag, take action as a click on down coordinates
                if (mTouchState == TOUCH_STATE_SCROLLING) {
                    
                    mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    int initialXVelocity = (int) mVelocityTracker.getXVelocity();
                    int initialYVelocity = (int) mVelocityTracker.getYVelocity();
                    
                    if (Math.abs(initialXVelocity) + Math.abs(initialYVelocity) > mMinimumVelocity) {
                        fling(-initialXVelocity, -initialYVelocity);
                    } else {
                        // Release the drag
                        clearChildrenCache();
                        mTouchState = TOUCH_STATE_RESTING;
                        
                        mDown.x = -1;
                        mDown.y = -1;
                    }
                    
                    if (mVelocityTracker != null) {
                        mVelocityTracker.recycle();
                        mVelocityTracker = null;
                    }
                    
                    break;
                }
                
                // Release the drag
                clearChildrenCache();
                mTouchState = TOUCH_STATE_RESTING;
                
                mDown.x = -1;
                mDown.y = -1;
                
                break;
            case MotionEvent.ACTION_CANCEL:
                mTouchState = TOUCH_STATE_RESTING;
        }
        
        return true;
    }
    
    @Override
    public void computeScroll() {
        if (mRightEdge != NO_VALUE && mScroller.getFinalX() > mRightEdge - getWidth() + 1) {
            mScroller.setFinalX(mRightEdge - getWidth() + 1);
        }
        
        if (mRightEdge != NO_VALUE && getScrollX() > mRightEdge - getWidth()) {
            if (mRightEdge - getWidth() > 0) {
                scrollTo(mRightEdge - getWidth(), 0);
            } else {
                scrollTo(0, 0);
            }
            return;
        }
        
        if (mScroller.computeScrollOffset()) {
            if (mScroller.getFinalX() == mScroller.getCurrX()) {
                mScroller.abortAnimation();
                mTouchState = TOUCH_STATE_RESTING;
                clearChildrenCache();
            } else {
                final int x = mScroller.getCurrX();
                scrollTo(x, 0);
                
                postInvalidate();
            }
        } else if (mTouchState == TOUCH_STATE_FLING) {
            mTouchState = TOUCH_STATE_RESTING;
            clearChildrenCache();
        }
        
        refill();
    }
    
    public void fling(int velocityX, int velocityY) {
        if (isScrollingDisabled) {
            return;
        }
        
        mTouchState = TOUCH_STATE_FLING;
        final int x = getScrollX();
        final int y = getScrollY();
        
        final int rightInPixels;
        if (mRightEdge == NO_VALUE) {
            rightInPixels = Integer.MAX_VALUE;
        } else {
            rightInPixels = mRightEdge;
        }
        
        mScroller.fling(x, y, velocityX, velocityY, 0, rightInPixels - getWidth() + 1, 0, 0);
        
        invalidate();
    }
    
    protected void scrollByDelta(int deltaX) {
        if (isScrollingDisabled) {
            return;
        }
        
        final int rightInPixels;
        if (mRightEdge == NO_VALUE) {
            rightInPixels = Integer.MAX_VALUE;
        } else {
            rightInPixels = mRightEdge;
            if (getScrollX() > mRightEdge - getWidth()) {
                if (mRightEdge - getWidth() > 0) {
                    scrollTo(mRightEdge - getWidth(), 0);
                } else {
                    scrollTo(0, 0);
                }
                return;
            }
        }
        
        final int x = getScrollX() + deltaX;
        
        if (x < 0) {
            deltaX -= x;
        } else if (x > rightInPixels - getWidth()) {
            deltaX -= x - (rightInPixels - getWidth());
        }
        
        scrollBy(deltaX, 0);
    }
    
    protected void handleClick(Point p) {
        final int c = getChildCount();
        View v;
        final Rect r = new Rect();
        for (int i = 0; i < c; i++) {
            v = getChildAt(i);
            v.getHitRect(r);
            if (r.contains(getScrollX() + p.x, getScrollY() + p.y)) {
                if (mItemClickListener != null) {
                    mItemClickListener.onItemClick(v);
                }
            }
        }
    }
    
    public void setAdapter(Adapter adapter) {
        if (mAdapter != null) {
            mAdapter.unregisterDataSetObserver(mDataObserver);
        }
        mAdapter = adapter;
        mAdapter.registerDataSetObserver(mDataObserver);
        reset();
    }
    
    private void enableChildrenCache() {
        setChildrenDrawnWithCacheEnabled(true);
        setChildrenDrawingCacheEnabled(true);
    }
    
    private void clearChildrenCache() {
        setChildrenDrawnWithCacheEnabled(false);
    }
    
    @Override
    protected MarginLayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
    }
    
    @Override
    protected MarginLayoutParams generateLayoutParams(LayoutParams p) {
        return new MarginLayoutParams(p);
    }
    
    @Override
    protected boolean checkLayoutParams(LayoutParams p) {
        return p instanceof MarginLayoutParams;
    }
    
    public void setDefaultItemWidth(int width) {
        //MTODO add xml attributes
        mDefaultItemWidth = width;
    }
    
    /**
     * Set listener which will fire if item in container is clicked
     */
    public void setOnItemClickListener(OnItemClickListener itemClickListener) {
        this.mItemClickListener = itemClickListener;
    }
    
    public void setViewObserver(IViewObserver viewObserver) {
        this.mViewObserver = viewObserver;
    }
    
    public interface OnItemClickListener {
        void onItemClick(View v);
    }
    
}
