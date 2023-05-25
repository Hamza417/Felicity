package app.simple.felicity.decorations.carousel;

import android.content.Context;
import android.database.DataSetObserver;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.Scroller;

import java.lang.ref.WeakReference;
import java.util.LinkedList;

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
    /**
     * Aligning in progress
     */
    protected static final int TOUCH_STATE_ALIGN = 3;
    protected final int NO_VALUE = Integer.MIN_VALUE + 1777;
    protected final ViewCache <View> mCache = new ViewCache <>();
    private final Scroller mScroller = new Scroller(getContext());
    private final int minimumVelocity;
    private final int maximumVelocity;
    protected int touchSlop;
    protected int mTouchState = TOUCH_STATE_RESTING;
    /**
     * Relative spacing value of Views in container. If <1 Views will overlap, if >1 Views will have spaces between them
     */
    protected float mSpacing = 0.5f;
    protected int mChildWidth = 240;
    protected int mChildHeight = 360;
    protected Adapter mAdapter;
    protected int mRightEdge = NO_VALUE;
    protected int mLeftEdge = NO_VALUE;
    private VelocityTracker mVelocityTracker;
    private float mLastMotionX;
    /**
     * Index of view in center of screen, which is most in foreground
     */
    private int mReverseOrderIndex = -1;
    /**
     * Movement speed will be divided by this coefficient;
     */
    private int mSlowDownCoefficient = 1;
    private int mSelection;
    private int mFirstVisibleChild;
    private int mLastVisibleChild;
    private final DataSetObserver mDataObserver = new DataSetObserver() {
        
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
    private OnItemSelectedListener mOnItemSelectedListener;
    
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
        return mAdapter;
    }
    
    public void setAdapter(Adapter adapter) {
        if (mAdapter != null) {
            mAdapter.unregisterDataSetObserver(mDataObserver);
        }
        mAdapter = adapter;
        mAdapter.registerDataSetObserver(mDataObserver);
        reset();
    }
    
    public View getSelectedView() {
        return getChildAt(mReverseOrderIndex);
    }
    
    public int getSelection() {
        return mSelection;
    }
    
    public void setSelection(int position) {
        if (mAdapter == null) {
            throw new IllegalStateException("You are trying to set selection on widget without adapter");
        }
        if (position < 0 || position > mAdapter.getCount() - 1) {
            throw new IllegalArgumentException("Position index must be in range of adapter values (0 - getCount()-1)");
        }
        
        mSelection = position;
        
        reset();
    }
    
    @Override
    public void computeScroll() {
        final int centerItemLeft = getWidth() / 2 - mChildWidth / 2;
        final int centerItemRight = getWidth() / 2 + mChildWidth / 2;
        
        if (mRightEdge != NO_VALUE && mScroller.getFinalX() > mRightEdge - centerItemRight) {
            mScroller.setFinalX(mRightEdge - centerItemRight);
        }
        if (mLeftEdge != NO_VALUE && mScroller.getFinalX() < mLeftEdge - centerItemLeft) {
            mScroller.setFinalX(mLeftEdge - centerItemLeft);
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
        updateReverseOrderIndex();
    }
    
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (mAdapter == null || mAdapter.getCount() == 0) {
            return;
        }
        View v = null;
        if (getChildCount() == 0) {
            v = getViewFromAdapter(mSelection);
            addAndMeasureChild(v, LAYOUT_MODE_AFTER);
            
            final int horizontalCenter = getWidth() / 2;
            final int verticalCenter = getHeight() / 2;
            final int left = horizontalCenter - v.getMeasuredWidth() / 2;
            final int right = left + v.getMeasuredWidth();
            final int top = verticalCenter - v.getMeasuredHeight() / 2;
            final int bottom = top + v.getMeasuredHeight();
            v.layout(left, top, right, bottom);
            
            mFirstVisibleChild = mSelection;
            mLastVisibleChild = mSelection;
            
            if (mLastVisibleChild == mAdapter.getCount() - 1) {
                mRightEdge = right;
            }
            if (mFirstVisibleChild == 0) {
                mLeftEdge = left;
            }
        }
        
        refill();
        
        if (v != null) {
            mReverseOrderIndex = indexOfChild(v);
            v.setSelected(true);
        } else {
            updateReverseOrderIndex();
        }
    }
    
    private void updateReverseOrderIndex() {
        int oldReverseIndex = mReverseOrderIndex;
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
            mReverseOrderIndex = minDiffIndex;
        }
        
        if (oldReverseIndex != mReverseOrderIndex) {
            View oldSelected = getChildAt(oldReverseIndex);
            View newSelected = getChildAt(mReverseOrderIndex);
            
            oldSelected.setSelected(false);
            newSelected.setSelected(true);
            
            mSelection = mFirstVisibleChild + mReverseOrderIndex;
            if (mOnItemSelectedListener != null) {
                mOnItemSelectedListener.onItemSelected(newSelected, mSelection);
            }
        }
        
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
        return r - (int) (v.getMeasuredWidth() * mSpacing);
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
        return l + (int) (v.getMeasuredWidth() * mSpacing);
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
            child.setLayoutParams(new LayoutParams(mChildWidth,
                    mChildHeight));
        }
        
        final int index = layoutMode == LAYOUT_MODE_TO_BEFORE ? 0 : -1;
        addViewInLayout(child, index, child.getLayoutParams(), true);
        
        final int pwms = MeasureSpec.makeMeasureSpec(mChildWidth, MeasureSpec.EXACTLY);
        final int phms = MeasureSpec.makeMeasureSpec(mChildHeight, MeasureSpec.EXACTLY);
        measureChild(child, pwms, phms);
        child.setDrawingCacheEnabled(isChildrenDrawnWithCacheEnabled());
        
        return child;
    }
    
    /**
     * Remove all data, reset to initial state and attempt to refill
     */
    private void reset() {
        if (mAdapter == null || mAdapter.getCount() == 0) {
            return;
        }
        
        if (getChildCount() == 0) {
            requestLayout();
            return;
        }
        
        View selectedView = getChildAt(mReverseOrderIndex);
        int selectedLeft = selectedView.getLeft();
        int selectedTop = selectedView.getTop();
        
        removeAllViewsInLayout();
        mRightEdge = NO_VALUE;
        mLeftEdge = NO_VALUE;
        
        View v = mAdapter.getView(mSelection, null, this);
        addAndMeasureChild(v, LAYOUT_MODE_AFTER);
        mReverseOrderIndex = 0;
        
        final int right = selectedLeft + v.getMeasuredWidth();
        final int bottom = selectedTop + v.getMeasuredHeight();
        v.layout(selectedLeft, selectedTop, right, bottom);
        
        mFirstVisibleChild = mSelection;
        mLastVisibleChild = mSelection;
        
        if (mLastVisibleChild == mAdapter.getCount() - 1) {
            mRightEdge = right;
        }
        if (mFirstVisibleChild == 0) {
            mLeftEdge = selectedLeft;
        }
        
        refill();
        
        mReverseOrderIndex = indexOfChild(v);
        v.setSelected(true);
    }
    
    protected void refill() {
        if (mAdapter == null || getChildCount() == 0) {
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
        return (int) (mChildWidth * (1.0f - mSpacing));
    }
    
    protected View getViewFromAdapter(int position) {
        return mAdapter.getView(position, mCache.getCachedView(), this);
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
        int newRight = childRight - (int) (mChildWidth * mSpacing);
        
        while (newRight - getPartOfViewCoveredBySibling() > leftScreenEdge && mFirstVisibleChild > 0) {
            mFirstVisibleChild--;
            
            child = getViewFromAdapter(mFirstVisibleChild);
            child.setSelected(false);
            mReverseOrderIndex++;
            
            addAndMeasureChild(child, LAYOUT_MODE_TO_BEFORE);
            newRight = layoutChildToBefore(child, newRight);
            
            if (mFirstVisibleChild <= 0) {
                mLeftEdge = child.getLeft();
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
        newLeft = childLeft + (int) (mChildWidth * mSpacing);
        
        while (newLeft + getPartOfViewCoveredBySibling() < rightScreenEdge && mLastVisibleChild < mAdapter
                .getCount() - 1) {
            mLastVisibleChild++;
            
            child = getViewFromAdapter(mLastVisibleChild);
            child.setSelected(false);
            
            addAndMeasureChild(child, LAYOUT_MODE_AFTER);
            newLeft = layoutChild(child, newLeft);
            
            if (mLastVisibleChild >= mAdapter.getCount() - 1) {
                mRightEdge = child.getRight();
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
        
        while (firstChild != null && firstChild.getLeft() + (mChildWidth * mSpacing) < leftScreenEdge && getChildCount() > 1) {
            
            // remove view
            removeViewsInLayout(0, 1);
            
            mCache.cacheView(firstChild);
            
            mFirstVisibleChild++;
            mReverseOrderIndex--;
            
            if (mReverseOrderIndex == 0) {
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
        while (lastChild != null && lastChild.getRight() - (mChildWidth * mSpacing) > rightScreenEdge &&
                getChildCount() > 1) {
            // remove the right view
            removeViewsInLayout(getChildCount() - 1, 1);
            
            mCache.cacheView(lastChild);
            
            mLastVisibleChild--;
            if (getChildCount() - 1 == mReverseOrderIndex) {
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
        if (i < mReverseOrderIndex) {
            return i;
        } else {
            return childCount - 1 - (i - mReverseOrderIndex);
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
                
                final int touchSlop = this.touchSlop;
                final boolean xMoved = xDiff > touchSlop;
                
                if (xMoved) {
                    // Scroll if the user moved far enough along the axis
                    mTouchState = TOUCH_STATE_SCROLLING;
                    enableChildrenCache();
                    cancelLongPress();
                }
                
                break;
            
            case MotionEvent.ACTION_DOWN:
                // Remember location of down touch
                mLastMotionX = x;
                
                /*
                 * If being flinged and user touches the screen, initiate drag;
                 * otherwise don't.  mScroller.isFinished should be false when
                 * being flinged.
                 */
                mTouchState = mScroller.isFinished() ? TOUCH_STATE_RESTING : TOUCH_STATE_SCROLLING;
                break;
            
            case MotionEvent.ACTION_UP:
                mTouchState = TOUCH_STATE_RESTING;
                clearChildrenCache();
                break;
        }
        
        return mTouchState == TOUCH_STATE_SCROLLING;
        
    }
    
    protected void scrollByDelta(int deltaX) {
        deltaX /= mSlowDownCoefficient;
        
        final int centerItemLeft = getWidth() / 2 - mChildWidth / 2;
        final int centerItemRight = getWidth() / 2 + mChildWidth / 2;
        
        final int rightInPixels;
        final int leftInPixels;
        if (mRightEdge == NO_VALUE) {
            rightInPixels = Integer.MAX_VALUE;
        } else {
            rightInPixels = mRightEdge;
        }
        
        if (mLeftEdge == NO_VALUE) {
            leftInPixels = Integer.MIN_VALUE + getWidth(); //we cant have min value because of integer overflow
        } else {
            leftInPixels = mLeftEdge;
        }
        
        final int x = getScrollX() + deltaX;
        
        if (x < (leftInPixels - centerItemLeft)) {
            deltaX -= x - (leftInPixels - centerItemLeft);
        } else if (x > rightInPixels - centerItemRight) {
            deltaX -= x - (rightInPixels - centerItemRight);
        }
        
        scrollBy(deltaX, 0);
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
                    
                    final int touchSlop = this.touchSlop;
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
                //if we had normal down click and we haven't moved enough to initiate drag, take action as a click on down coordinates
                if (mTouchState == TOUCH_STATE_SCROLLING) {
                    
                    mVelocityTracker.computeCurrentVelocity(1000, maximumVelocity);
                    int initialXVelocity = (int) mVelocityTracker.getXVelocity();
                    int initialYVelocity = (int) mVelocityTracker.getYVelocity();
                    
                    if (Math.abs(initialXVelocity) + Math.abs(initialYVelocity) > minimumVelocity) {
                        fling(-initialXVelocity, -initialYVelocity);
                    } else {
                        // Release the drag
                        clearChildrenCache();
                        mTouchState = TOUCH_STATE_RESTING;
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
                
                break;
            case MotionEvent.ACTION_CANCEL:
                mTouchState = TOUCH_STATE_RESTING;
        }
        
        return true;
    }
    
    public void fling(int velocityX, int velocityY) {
        velocityX /= mSlowDownCoefficient;
        
        mTouchState = TOUCH_STATE_FLING;
        final int x = getScrollX();
        final int y = getScrollY();
        
        final int centerItemLeft = getWidth() / 2 - mChildWidth / 2;
        final int centerItemRight = getWidth() / 2 + mChildWidth / 2;
        final int rightInPixels;
        final int leftInPixels;
        if (mRightEdge == NO_VALUE) {
            rightInPixels = Integer.MAX_VALUE;
        } else {
            rightInPixels = mRightEdge;
        }
        if (mLeftEdge == NO_VALUE) {
            leftInPixels = Integer.MIN_VALUE + getWidth();
        } else {
            leftInPixels = mLeftEdge;
        }
        
        mScroller.fling(x, y, velocityX, velocityY, leftInPixels - centerItemLeft,
                rightInPixels - centerItemRight + 1, 0, 0);
        
        invalidate();
    }
    
    private void enableChildrenCache() {
        setChildrenDrawnWithCacheEnabled(true);
        setChildrenDrawingCacheEnabled(true);
    }
    
    private void clearChildrenCache() {
        setChildrenDrawnWithCacheEnabled(false);
    }
    
    /**
     * Set widget spacing (float means fraction of widget size, 1 = widget size)
     *
     * @param spacing the spacing to set
     */
    public void setSpacing(float spacing) {
        this.mSpacing = spacing;
    }
    
    public void setChildWidth(int width) {
        mChildWidth = width;
    }
    
    public void setChildHeight(int height) {
        mChildHeight = height;
    }
    
    public void setSlowDownCoefficient(int c) {
        if (c < 1) {
            throw new IllegalArgumentException("Slowdown coeficient must be greater than 0");
        }
        mSlowDownCoefficient = c;
    }
    
    public void setOnItemSelectedListener(OnItemSelectedListener onItemSelectedListener) {
        mOnItemSelectedListener = onItemSelectedListener;
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