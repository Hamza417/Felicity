package app.simple.felicity.decorations.coverflow.containers;

import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewDebug.CapturedViewProperty;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.Scroller;

import java.lang.ref.WeakReference;
import java.util.LinkedList;

import app.simple.felicity.R;
import app.simple.felicity.decorations.coverflow.containers.interfaces.IViewObserver;
import app.simple.felicity.decorations.coverflow.general.ToolBox;
import app.simple.felicity.decorations.coverflow.general.Validate;

/**
 * @author Martin Appl
 * <p>
 * Endless loop with items filling from adapter. Currently only horizontal orientation is implemented
 * View recycling in adapter is supported. You are encouraged to recycle view in adapter if possible
 */
public class EndlessLoopAdapterContainer extends AdapterView <Adapter> {
    /**
     * Children added with this layout mode will be added after the last child
     */
    protected static final int LAYOUT_MODE_AFTER = 0;
    
    /**
     * Children added with this layout mode will be added before the first child
     */
    protected static final int LAYOUT_MODE_TO_BEFORE = 1;
    
    protected static final int SCROLLING_DURATION = 500;
    /**
     * User is not touching the list
     */
    protected static final int TOUCH_STATE_RESTING = 1;
    /**
     * User is scrolling the list
     */
    protected static final int TOUCH_STATE_SCROLLING = 2;
    /**
     * Fling gesture in progress
     */
    protected static final int TOUCH_STATE_FLING = 3;
    /**
     * Aligning in progress
     */
    protected static final int TOUCH_STATE_ALIGN = 4;
    protected static final int TOUCH_STATE_DISTANCE_SCROLL = 5;
    /**
     * A list of cached (re-usable) item views
     */
    protected final LinkedList <WeakReference <View>> mCachedItemViews = new LinkedList <WeakReference <View>>();
    protected final Scroller mScroller = new Scroller(getContext());
    private final Point mDown = new Point();
    /**
     * The adapter providing data for container
     */
    protected Adapter adapter;
    /**
     * The adaptor position of the first visible item
     */
    protected int mFirstItemPosition;
    /**
     * The adaptor position of the last visible item
     */
    protected int mLastItemPosition;
    /**
     * The adaptor position of selected item
     */
    protected int mSelectedPosition = INVALID_POSITION;
    /**
     * Left of current most left child
     */
    protected int mLeftChildEdge;
    /**
     * If there is not enough items to fill adapter, this value is set to true and scrolling is disabled. Since all items from adapter are on screen
     */
    protected boolean isSrollingDisabled = false;
    /**
     * Whether content should be repeated when there is not enough items to fill container
     */
    protected boolean shouldRepeat = true;
    /**
     * Position to scroll adapter only if is in endless mode. This is done after layout if we find out we are endless, we must relayout
     */
    protected int mScrollPositionIfEndless = -1;
    protected int touchState = TOUCH_STATE_RESTING;
    protected OnItemClickListener mOnItemClickListener;
    protected OnItemSelectedListener mOnItemSelectedListener;
    private IViewObserver mViewObserver;
    private VelocityTracker mVelocityTracker;
    private boolean mDataChanged;
    private final DataSetObserver fDataObserver = new DataSetObserver() {
        
        @Override
        public void onChanged() {
            synchronized (this) {
                mDataChanged = true;
            }
            invalidate();
        }
        
        @Override
        public void onInvalidated() {
            adapter = null;
        }
    };
    private int mTouchSlop;
    private int mMinimumVelocity;
    //	private long mDownTime;
    private int mMaximumVelocity;
    private boolean mAllowLongPress;
    private float mLastMotionX;
    //	private boolean mCancelInIntercept;
    private float mLastMotionY;
    private boolean mHandleSelectionOnActionUp = false;
    private boolean mInterceptTouchEvents;
    
    public EndlessLoopAdapterContainer(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
        
        final ViewConfiguration configuration = ViewConfiguration.get(context);
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        
        //init params from xml
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.EndlessLoopAdapterContainer, defStyle, 0);
            
            shouldRepeat = a.getBoolean(R.styleable.EndlessLoopAdapterContainer_shouldRepeat, true);
            
            a.recycle();
        }
    }
    
    public EndlessLoopAdapterContainer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        
    }
    
    public EndlessLoopAdapterContainer(Context context) {
        this(context, null);
    }
    
    protected LoopLayoutParams createLayoutParams(int w, int h) {
        return new LoopLayoutParams(w, h);
    }
    
    protected LoopLayoutParams createLayoutParams(int w, int h, int pos) {
        return new LoopLayoutParams(w, h, pos);
    }
    
    protected LoopLayoutParams createLayoutParams(LayoutParams lp) {
        return new LoopLayoutParams(lp);
    }
    
    public boolean isRepeatable() {
        return shouldRepeat;
    }
    
    public boolean isEndlessRightNow() {
        return !isSrollingDisabled;
    }
    
    public void setShouldRepeat(boolean shouldRepeat) {
        this.shouldRepeat = shouldRepeat;
    }
    
    /**
     * Sets position in adapter of first shown item in container
     *
     * @param position
     */
    public void scrollToPosition(int position) {
        if (position < 0 || position >= adapter.getCount()) {
            throw new IndexOutOfBoundsException("Position must be in bounds of adapter values count");
        }
        
        reset();
        refillInternal(position - 1, position);
        invalidate();
    }
    
    public void scrollToPositionIfEndless(int position) {
        if (position < 0 || position >= adapter.getCount()) {
            throw new IndexOutOfBoundsException("Position must be in bounds of adapter values count");
        }
        
        if (isEndlessRightNow() && getChildCount() != 0) {
            scrollToPosition(position);
        } else {
            mScrollPositionIfEndless = position;
        }
    }
    
    /**
     * Returns position to which will container scroll on next relayout
     *
     * @return scroll position on next layout or -1 if it will scroll nowhere
     */
    public int getScrollPositionIfEndless() {
        return mScrollPositionIfEndless;
    }
    
    /**
     * Get index of currently first item in adapter
     *
     * @return
     */
    public int getScrollPosition() {
        return mFirstItemPosition;
    }
    
    /**
     * Return offset by which is edge off first item moved off screen.
     * You can persist it and insert to setFirstItemOffset() to restore exact scroll position
     *
     * @return offset of first item, or 0 if there is not enough items to fill container and scrolling is disabled
     */
    public int getFirstItemOffset() {
        if (isSrollingDisabled) {
            return 0;
        } else {
            return getScrollX() - mLeftChildEdge;
        }
    }
    
    /**
     * Negative number. Offset by which is left edge of first item moved off screen.
     *
     * @param offset
     */
    public void setFirstItemOffset(int offset) {
        scrollTo(offset, 0);
    }
    
    @Override
    public Adapter getAdapter() {
        return adapter;
    }
    
    @Override
    public void setAdapter(Adapter adapter) {
        if (this.adapter != null) {
            this.adapter.unregisterDataSetObserver(fDataObserver);
        }
        this.adapter = adapter;
        this.adapter.registerDataSetObserver(fDataObserver);
        
        if (adapter instanceof IViewObserver) {
            setViewObserver((IViewObserver) adapter);
        }
        
        reset();
        refill();
        invalidate();
    }
    
    @Override
    public View getSelectedView() {
        if (mSelectedPosition == INVALID_POSITION) {
            return null;
        }
        
        final int index;
        if (mFirstItemPosition > mSelectedPosition) {
            index = mSelectedPosition + adapter.getCount() - mFirstItemPosition;
        } else {
            index = mSelectedPosition - mFirstItemPosition;
        }
        if (index < 0 || index >= getChildCount()) {
            return null;
        }
        
        return getChildAt(index);
    }
    
    /**
     * Position index must be in range of adapter values (0 - getCount()-1) or -1 to unselect
     */
    @Override
    public void setSelection(int position) {
        if (adapter == null) {
            throw new IllegalStateException("You are trying to set selection on widget without adapter");
        }
        if (adapter.getCount() == 0 && position == 0) {
            position = -1;
        }
        if (position < -1 || position > adapter.getCount() - 1) {
            throw new IllegalArgumentException("Position index must be in range of adapter values (0 - getCount()-1) or -1 to unselect");
        }
        
        View v = getSelectedView();
        if (v != null) {
            v.setSelected(false);
        }
        
        final int oldPos = mSelectedPosition;
        mSelectedPosition = position;
        
        if (position == -1) {
            if (mOnItemSelectedListener != null) {
                mOnItemSelectedListener.onNothingSelected(this);
            }
            return;
        }
        
        v = getSelectedView();
        if (v != null) {
            v.setSelected(true);
        }
        
        if (oldPos != mSelectedPosition && mOnItemSelectedListener != null) {
            mOnItemSelectedListener.onItemSelected(this, v, mSelectedPosition, getSelectedItemId());
        }
    }
    
    private void reset() {
        scrollTo(0, 0);
        removeAllViewsInLayout();
        mFirstItemPosition = 0;
        mLastItemPosition = -1;
        mLeftChildEdge = 0;
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
        
        if (mScroller.computeScrollOffset()) {
            if (mScroller.getFinalX() == mScroller.getCurrX()) {
                mScroller.abortAnimation();
                touchState = TOUCH_STATE_RESTING;
                if (!checkScrollPosition()) {
                    clearChildrenCache();
                }
                return;
            }
            
            int x = mScroller.getCurrX();
            scrollTo(x, 0);
            
            postInvalidate();
        } else if (touchState == TOUCH_STATE_FLING || touchState == TOUCH_STATE_DISTANCE_SCROLL) {
            touchState = TOUCH_STATE_RESTING;
            if (!checkScrollPosition()) {
                clearChildrenCache();
            }
        }
        
        if (mDataChanged) {
            removeAllViewsInLayout();
            refillOnChange(mFirstItemPosition);
            return;
        }
        
        relayout();
        removeNonVisibleViews();
        refillRight();
        refillLeft();
        
    }
    
    /**
     * @param velocityY The initial velocity in the Y direction. Positive
     *                  numbers mean that the finger/cursor is moving down the screen,
     *                  which means we want to scroll towards the top.
     * @param velocityX The initial velocity in the X direction. Positive
     *                  numbers mean that the finger/cursor is moving right the screen,
     *                  which means we want to scroll towards the top.
     */
    public void fling(int velocityX, int velocityY) {
        touchState = TOUCH_STATE_FLING;
        final int x = getScrollX();
        final int y = getScrollY();
        
        mScroller.fling(x, y, velocityX, velocityY, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
        
        invalidate();
    }
    
    /**
     * Scroll widget by given distance in pixels
     *
     * @param dx
     */
    public void scroll(int dx) {
        mScroller.startScroll(getScrollX(), 0, dx, 0, SCROLLING_DURATION);
        touchState = TOUCH_STATE_DISTANCE_SCROLL;
        invalidate();
    }
    
    @Override
    protected void onLayout(boolean changed, int left, int top, int right,
            int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        
        // if we don't have an adapter, we don't need to do anything
        if (adapter == null) {
            return;
        }
        
        refillInternal(mLastItemPosition, mFirstItemPosition);
    }
    
    /**
     * Method for actualizing content after data change in adapter. It is expected container was emptied before
     *
     * @param firstItemPosition
     */
    protected void refillOnChange(int firstItemPosition) {
        refillInternal(firstItemPosition - 1, firstItemPosition);
    }
    
    protected void refillInternal(final int lastItemPos, final int firstItemPos) {
        // if we don't have an adapter, we don't need to do anything
        if (adapter == null) {
            return;
        }
        if (adapter.getCount() == 0) {
            return;
        }
        
        if (getChildCount() == 0) {
            fillFirstTime(lastItemPos, firstItemPos);
        } else {
            relayout();
            removeNonVisibleViews();
            refillRight();
            refillLeft();
        }
    }
    
    /**
     * Check if container visible area is filled and refill empty areas
     */
    private void refill() {
        scrollTo(0, 0);
        refillInternal(-1, 0);
    }
    
    protected void measureChild(View child) {
        final int pwms = MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY);
        final int phms = MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY);
        measureChild(child, pwms, phms);
    }
    
    //	protected void measureChild(View child, LoopLayoutParams params){
    //		//prepare spec for measurement
    //        final int specW, specH;
    //
    //        specW = getChildMeasureSpec(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.UNSPECIFIED), 0, params.width);
    //        specH = getChildMeasureSpec(MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.UNSPECIFIED), 0, params.height);
    //
    //////        final boolean useMeasuredW, useMeasuredH;
    ////        if(params.height >= 0){
    ////        	specH = MeasureSpec.EXACTLY | params.height;
    //////        	useMeasuredH = false;
    ////        }
    ////        else{
    ////        	if(params.height == LayoutParams.MATCH_PARENT){
    ////        		specH = MeasureSpec.EXACTLY | getHeight();
    ////        		params.height = getHeight();
    //////            	useMeasuredH = false;
    ////        	}else{
    ////        		specH = MeasureSpec.AT_MOST | getHeight();
    //////            	useMeasuredH = true;
    ////        	}
    ////        }
    ////
    ////        if(params.width >= 0){
    ////        	specW = MeasureSpec.EXACTLY | params.width;
    //////        	useMeasuredW = false;
    ////        }
    ////        else{
    ////        	if(params.width == LayoutParams.MATCH_PARENT){
    ////        		specW = MeasureSpec.EXACTLY | getWidth();
    ////        		params.width = getWidth();
    //////            	useMeasuredW = false;
    ////        	}else{
    ////        		specW = MeasureSpec.UNSPECIFIED;
    //////            	useMeasuredW = true;
    ////        	}
    ////        }
    //
    //        //measure
    //        child.measure(specW, specH);
    //        //put measured values into layout params from where they will be used in layout.
    //        //Use measured values only if exact values was not specified in layout params.
    ////        if(useMeasuredH) params.actualHeight = child.getMeasuredHeight();
    ////        else params.actualHeight = params.height;
    ////
    ////        if(useMeasuredW) params.actualWidth = child.getMeasuredWidth();
    ////        else params.actualWidth = params.width;
    //	}
    
    private void relayout() {
        final int c = getChildCount();
        int left = mLeftChildEdge;
        
        View child;
        LoopLayoutParams lp;
        for (int i = 0; i < c; i++) {
            child = getChildAt(i);
            lp = (LoopLayoutParams) child.getLayoutParams();
            measureChild(child);
            
            left = layoutChildHorizontal(child, left, lp);
        }
        
    }
    
    protected void fillFirstTime(final int lastItemPos, final int firstItemPos) {
        final int leftScreenEdge = 0;
        final int rightScreenEdge = leftScreenEdge + getWidth();
        
        int right;
        int left;
        View child;
        
        boolean isRepeatingNow = false;
        
        //scrolling is enabled until we find out we don't have enough items
        isSrollingDisabled = false;
        
        mLastItemPosition = lastItemPos;
        mFirstItemPosition = firstItemPos;
        mLeftChildEdge = 0;
        right = mLeftChildEdge;
        left = mLeftChildEdge;
        
        while (right < rightScreenEdge) {
            mLastItemPosition++;
            
            if (isRepeatingNow && mLastItemPosition >= firstItemPos) {
                return;
            }
            
            if (mLastItemPosition >= adapter.getCount()) {
                if (firstItemPos == 0 && shouldRepeat) {
                    mLastItemPosition = 0;
                } else {
                    if (firstItemPos > 0) {
                        mLastItemPosition = 0;
                        isRepeatingNow = true;
                    } else if (!shouldRepeat) {
                        mLastItemPosition--;
                        isSrollingDisabled = true;
                        final int w = right - mLeftChildEdge;
                        final int dx = (getWidth() - w) / 2;
                        scrollTo(-dx, 0);
                        return;
                    }
                    
                }
            }
            
            if (mLastItemPosition >= adapter.getCount()) {
                Log.wtf("EndlessLoop", "mLastItemPosition > mAdapter.getCount()");
                return;
            }
            
            child = adapter.getView(mLastItemPosition, getCachedView(), this);
            Validate.notNull(child, "Your adapter has returned null from getView.");
            child = addAndMeasureChildHorizontal(child, LAYOUT_MODE_AFTER);
            left = layoutChildHorizontal(child, left, (LoopLayoutParams) child.getLayoutParams());
            right = child.getRight();
            
            //if selected view is going to screen, set selected state on him
            if (mLastItemPosition == mSelectedPosition) {
                child.setSelected(true);
            }
            
        }
        
        if (mScrollPositionIfEndless > 0) {
            final int p = mScrollPositionIfEndless;
            mScrollPositionIfEndless = -1;
            removeAllViewsInLayout();
            refillOnChange(p);
        }
    }
    
    /**
     * Checks and refills empty area on the right
     */
    protected void refillRight() {
        if (!shouldRepeat && isSrollingDisabled) {
            return; //prevent next layout calls to override override first init to scrolling disabled by falling to this branch
        }
        if (getChildCount() == 0) {
            return;
        }
        
        final int leftScreenEdge = getScrollX();
        final int rightScreenEdge = leftScreenEdge + getWidth();
        
        View child = getChildAt(getChildCount() - 1);
        int right = child.getRight();
        int currLayoutLeft = right + ((LoopLayoutParams) child.getLayoutParams()).rightMargin;
        while (right < rightScreenEdge) {
            mLastItemPosition++;
            if (mLastItemPosition >= adapter.getCount()) {
                mLastItemPosition = 0;
            }
            
            child = adapter.getView(mLastItemPosition, getCachedView(), this);
            Validate.notNull(child, "Your adapter has returned null from getView.");
            child = addAndMeasureChildHorizontal(child, LAYOUT_MODE_AFTER);
            currLayoutLeft = layoutChildHorizontal(child, currLayoutLeft, (LoopLayoutParams) child.getLayoutParams());
            right = child.getRight();
            
            //if selected view is going to screen, set selected state on him
            if (mLastItemPosition == mSelectedPosition) {
                child.setSelected(true);
            }
        }
    }
    
    /**
     * Checks and refills empty area on the left
     */
    protected void refillLeft() {
        if (!shouldRepeat && isSrollingDisabled) {
            return; //prevent next layout calls to override first init to scrolling disabled by falling to this branch
        }
        if (getChildCount() == 0) {
            return;
        }
        
        final int leftScreenEdge = getScrollX();
        
        View child = getChildAt(0);
        int childLeft = child.getLeft();
        int currLayoutRight = childLeft - ((LoopLayoutParams) child.getLayoutParams()).leftMargin;
        while (currLayoutRight > leftScreenEdge) {
            mFirstItemPosition--;
            if (mFirstItemPosition < 0) {
                mFirstItemPosition = adapter.getCount() - 1;
            }
            
            child = adapter.getView(mFirstItemPosition, getCachedView(), this);
            Validate.notNull(child, "Your adapter has returned null from getView.");
            child = addAndMeasureChildHorizontal(child, LAYOUT_MODE_TO_BEFORE);
            currLayoutRight = layoutChildHorizontalToBefore(child, currLayoutRight, (LoopLayoutParams) child.getLayoutParams());
            childLeft = child.getLeft() - ((LoopLayoutParams) child.getLayoutParams()).leftMargin;
            //update left edge of children in container
            mLeftChildEdge = childLeft;
            
            //if selected view is going to screen, set selected state on him
            if (mFirstItemPosition == mSelectedPosition) {
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
        final int leftedge = firstChild.getLeft() - ((LoopLayoutParams) firstChild.getLayoutParams()).leftMargin;
        if (leftedge != mLeftChildEdge) {
            throw new IllegalStateException("firstChild.getLeft() != mLeftChildEdge");
        }
        while (firstChild != null && firstChild.getRight() + ((LoopLayoutParams) firstChild.getLayoutParams()).rightMargin < leftScreenEdge) {
            //if selected view is going off screen, remove selected state
            firstChild.setSelected(false);
            
            // remove view
            removeViewInLayout(firstChild);
            
            if (mViewObserver != null) {
                mViewObserver.onViewRemovedFromParent(firstChild, mFirstItemPosition);
            }
            WeakReference <View> ref = new WeakReference <View>(firstChild);
            mCachedItemViews.addLast(ref);
            
            mFirstItemPosition++;
            if (mFirstItemPosition >= adapter.getCount()) {
                mFirstItemPosition = 0;
            }
            
            // update left item position
            mLeftChildEdge = getChildAt(0).getLeft() - ((LoopLayoutParams) getChildAt(0).getLayoutParams()).leftMargin;
            
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
        while (lastChild != null && firstChild != null && lastChild.getLeft() - ((LoopLayoutParams) firstChild.getLayoutParams()).leftMargin > rightScreenEdge) {
            //if selected view is going off screen, remove selected state
            lastChild.setSelected(false);
            
            // remove the right view
            removeViewInLayout(lastChild);
            
            if (mViewObserver != null) {
                mViewObserver.onViewRemovedFromParent(lastChild, mLastItemPosition);
            }
            WeakReference <View> ref = new WeakReference <View>(lastChild);
            mCachedItemViews.addLast(ref);
            
            mLastItemPosition--;
            if (mLastItemPosition < 0) {
                mLastItemPosition = adapter.getCount() - 1;
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
    
    //	/**
    //	 * Checks and refills empty area on the left
    //	 */
    //	protected void refillLeft(){
    //		if(!shouldRepeat && isSrollingDisabled) return; //prevent next layout calls to override override first init to scrolling disabled by falling to this branch
    //		final int leftScreenEdge = getScrollX();
    //
    //		View child = getChildAt(0);
    //		int currLayoutRight = child.getRight();
    //		while(currLayoutRight > leftScreenEdge){
    //			mFirstItemPosition--;
    //			if(mFirstItemPosition < 0) mFirstItemPosition = mAdapter.getCount()-1;
    //
    //			child = mAdapter.getView(mFirstItemPosition, getCachedView(mFirstItemPosition), this);
    //			child = addAndMeasureChildHorizontal(child, LAYOUT_MODE_TO_BEFORE);
    //			currLayoutRight = layoutChildHorizontalToBefore(child, currLayoutRight, (LoopLayoutParams) child.getLayoutParams());
    //
    //			//update left edge of children in container
    //			mLeftChildEdge = child.getLeft();
    //
    //			//if selected view is going to screen, set selected state on him
    //			if(mFirstItemPosition == mSelectedPosition){
    //				child.setSelected(true);
    //			}
    //		}
    //	}
    
    /**
     * Adds a view as a child view and takes care of measuring it
     *
     * @param child      The view to add
     * @param layoutMode Either LAYOUT_MODE_LEFT or LAYOUT_MODE_RIGHT
     * @return child which was actually added to container, subclasses can override to introduce frame views
     */
    protected View addAndMeasureChildHorizontal(final View child, final int layoutMode) {
        LayoutParams lp = child.getLayoutParams();
        LoopLayoutParams params;
        if (lp == null) {
            params = createLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        } else {
            if (lp != null && lp instanceof LoopLayoutParams) {
                params = (LoopLayoutParams) lp;
            } else {
                params = createLayoutParams(lp);
            }
        }
        final int index = layoutMode == LAYOUT_MODE_TO_BEFORE ? 0 : -1;
        addViewInLayout(child, index, params, true);
        
        measureChild(child);
        child.setDrawingCacheEnabled(true);
        
        return child;
    }
    
    /**
     * Layouts children from left to right
     *
     * @param left positon for left edge in parent container
     * @param lp   layout params
     * @return new left
     */
    protected int layoutChildHorizontal(View v, int left, LoopLayoutParams lp) {
        int l, t, r, b;
        
        switch (lp.position) {
            case LoopLayoutParams.TOP:
                l = left + lp.leftMargin;
                t = lp.topMargin;
                r = l + v.getMeasuredWidth();
                b = t + v.getMeasuredHeight();
                break;
            case LoopLayoutParams.BOTTOM:
                b = getHeight() - lp.bottomMargin;
                t = b - v.getMeasuredHeight();
                l = left + lp.leftMargin;
                r = l + v.getMeasuredWidth();
                break;
            case LoopLayoutParams.CENTER:
                l = left + lp.leftMargin;
                r = l + v.getMeasuredWidth();
                final int x = (getHeight() - v.getMeasuredHeight()) / 2;
                t = x;
                b = t + v.getMeasuredHeight();
                break;
            default:
                throw new RuntimeException("Only TOP,BOTTOM,CENTER are alowed in horizontal orientation");
        }
        
        v.layout(l, t, r, b);
        return r + lp.rightMargin;
    }
    
    /**
     * Layout children from right to left
     */
    protected int layoutChildHorizontalToBefore(View v, int right, LoopLayoutParams lp) {
        final int left = right - v.getMeasuredWidth() - lp.leftMargin - lp.rightMargin;
        layoutChildHorizontal(v, left, lp);
        return left;
    }
    
    /**
     * Allows to make scroll alignments
     *
     * @return true if invalidate() was issued, and container is going to scroll
     */
    protected boolean checkScrollPosition() {
        return false;
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
                //if we have scrolling disabled, we don't do anything
                if (!shouldRepeat && isSrollingDisabled) {
                    return false;
                }
                
                /*
                 * not dragging, otherwise the shortcut would have caught it. Check
                 * whether the user has moved far enough from his original down touch.
                 */
                
                /*
                 * Locally do absolute value. mLastMotionX is set to the x value
                 * of the down event.
                 */
                final int xDiff = (int) Math.abs(x - mLastMotionX);
                final int yDiff = (int) Math.abs(y - mLastMotionY);
                
                final int touchSlop = mTouchSlop;
                final boolean xMoved = xDiff > touchSlop;
                final boolean yMoved = yDiff > touchSlop;
                
                if (xMoved) {
                    
                    // Scroll if the user moved far enough along the X axis
                    touchState = TOUCH_STATE_SCROLLING;
                    mHandleSelectionOnActionUp = false;
                    enableChildrenCache();
                    
                    // Either way, cancel any pending longpress
                    if (mAllowLongPress) {
                        mAllowLongPress = false;
                        // Try canceling the long press. It could also have been scheduled
                        // by a distant descendant, so use the mAllowLongPress flag to block
                        // everything
                        cancelLongPress();
                    }
                }
                if (yMoved) {
                    mHandleSelectionOnActionUp = false;
                    if (mAllowLongPress) {
                        mAllowLongPress = false;
                        cancelLongPress();
                    }
                }
                break;
            
            case MotionEvent.ACTION_DOWN:
                // Remember location of down touch
                mLastMotionX = x;
                mLastMotionY = y;
                mAllowLongPress = true;
                //                mCancelInIntercept = false;
                
                mDown.x = (int) x;
                mDown.y = (int) y;
                
                /*
                 * If being flinged and user touches the screen, initiate drag;
                 * otherwise don't.  mScroller.isFinished should be false when
                 * being flinged.
                 */
                touchState = mScroller.isFinished() ? TOUCH_STATE_RESTING : TOUCH_STATE_SCROLLING;
                //if he had normal click in rested state, remember for action up check
                if (touchState == TOUCH_STATE_RESTING) {
                    mHandleSelectionOnActionUp = true;
                }
                break;
            
            case MotionEvent.ACTION_CANCEL:
                mDown.x = -1;
                mDown.y = -1;
                //            	mCancelInIntercept = true;
                break;
            case MotionEvent.ACTION_UP:
                //if we had normal down click and we haven't moved enough to initiate drag, take action as a click on down coordinates
                if (mHandleSelectionOnActionUp && touchState == TOUCH_STATE_RESTING) {
                    final double d = ToolBox.getLineLength(mDown.x, mDown.y, x, y);
                    if ((ev.getEventTime() - ev.getDownTime()) < ViewConfiguration.getLongPressTimeout() && d < mTouchSlop) {
                        handleClick(mDown);
                    }
                }
                // Release the drag
                mAllowLongPress = false;
                mHandleSelectionOnActionUp = false;
                mDown.x = -1;
                mDown.y = -1;
                if (touchState == TOUCH_STATE_SCROLLING) {
                    if (checkScrollPosition()) {
                        break;
                    }
                }
                touchState = TOUCH_STATE_RESTING;
                clearChildrenCache();
                break;
        }
        
        mInterceptTouchEvents = touchState == TOUCH_STATE_SCROLLING;
        return mInterceptTouchEvents;
        
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
                
                int position = mFirstItemPosition + i;
                if (position >= adapter.getCount()) {
                    position = position - adapter.getCount();
                }
                
                mSelectedPosition = position;
                v.setSelected(true);
                
                if (mOnItemClickListener != null) {
                    mOnItemClickListener.onItemClick(this, v, position, getItemIdAtPosition(position));
                }
                if (mOnItemSelectedListener != null) {
                    mOnItemSelectedListener.onItemSelected(this, v, position, getItemIdAtPosition(position));
                }
                
                break;
            }
        }
    }
    
    //	/**
    //	 * Allow subclasses to override this to always intercept events
    //	 * @return
    //	 */
    //	protected boolean interceptEvents(){
    //		/*
    //         * The only time we want to intercept motion events is if we are in the
    //         * drag mode.
    //         */
    //        return mTouchState == TOUCH_STATE_SCROLLING;
    //	}
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // if we don't have an adapter, we don't need to do anything
        if (adapter == null) {
            return false;
        }
        
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
        
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
                if (!mScroller.isFinished()) {
                    mScroller.forceFinished(true);
                }
                
                // Remember where the motion event started
                mLastMotionX = x;
                mLastMotionY = y;
                
                break;
            case MotionEvent.ACTION_MOVE:
                //if we have scrolling disabled, we don't do anything
                if (!shouldRepeat && isSrollingDisabled) {
                    return false;
                }
                
                if (touchState == TOUCH_STATE_SCROLLING) {
                    // Scroll to follow the motion event
                    final int deltaX = (int) (mLastMotionX - x);
                    mLastMotionX = x;
                    mLastMotionY = y;
                    
                    int sx = getScrollX() + deltaX;
                    
                    scrollTo(sx, 0);
                    
                } else {
                    final int xDiff = (int) Math.abs(x - mLastMotionX);
                    
                    final int touchSlop = mTouchSlop;
                    final boolean xMoved = xDiff > touchSlop;
                    
                    if (xMoved) {
                        
                        // Scroll if the user moved far enough along the X axis
                        touchState = TOUCH_STATE_SCROLLING;
                        enableChildrenCache();
                        
                        // Either way, cancel any pending longpress
                        if (mAllowLongPress) {
                            mAllowLongPress = false;
                            // Try canceling the long press. It could also have been scheduled
                            // by a distant descendant, so use the mAllowLongPress flag to block
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
                if (mHandleSelectionOnActionUp && touchState == TOUCH_STATE_RESTING) {
                    final double d = ToolBox.getLineLength(mDown.x, mDown.y, x, y);
                    if ((event.getEventTime() - event.getDownTime()) < ViewConfiguration.getLongPressTimeout() && d < mTouchSlop) {
                        handleClick(mDown);
                    }
                    mHandleSelectionOnActionUp = false;
                }
                
                //if we had normal down click and we haven't moved enough to initiate drag, take action as a click on down coordinates
                if (touchState == TOUCH_STATE_SCROLLING) {
                    
                    mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    int initialXVelocity = (int) mVelocityTracker.getXVelocity();
                    int initialYVelocity = (int) mVelocityTracker.getYVelocity();
                    
                    if (Math.abs(initialXVelocity) + Math.abs(initialYVelocity) > mMinimumVelocity) {
                        fling(-initialXVelocity, -initialYVelocity);
                    } else {
                        // Release the drag
                        clearChildrenCache();
                        touchState = TOUCH_STATE_RESTING;
                        checkScrollPosition();
                        mAllowLongPress = false;
                        
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
                touchState = TOUCH_STATE_RESTING;
                mAllowLongPress = false;
                
                mDown.x = -1;
                mDown.y = -1;
                
                break;
            case MotionEvent.ACTION_CANCEL:
                
                //this must be here, in case no child view returns true,
                //events will propagate back here and on intercept touch event wont be called again
                //instead we get cancel here, since we stated we shouldn't intercept events and propagate them to children
                //but events propagated back here, because no child was interested
                //        	if(!mInterceptTouchEvents && mHandleSelectionOnActionUp && mTouchState == TOUCH_STATE_RESTING){
                //        		handleClick(mDown);
                //        		mHandleSelectionOnActionUp = false;
                //        	}
                
                mAllowLongPress = false;
                
                mDown.x = -1;
                mDown.y = -1;
                
                if (touchState == TOUCH_STATE_SCROLLING) {
                    if (checkScrollPosition()) {
                        break;
                    }
                }
                
                touchState = TOUCH_STATE_RESTING;
        }
        
        return true;
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                checkScrollFocusLeft();
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                checkScrollFocusRight();
                break;
            default:
                break;
        }
        
        return super.onKeyDown(keyCode, event);
    }
    
    /**
     * Moves with scroll window if focus hits one view before end of screen
     */
    private void checkScrollFocusLeft() {
        final View focused = getFocusedChild();
        if (getChildCount() >= 2) {
            View second = getChildAt(1);
            View first = getChildAt(0);
            
            if (focused == second) {
                scroll(-first.getWidth());
            }
        }
    }
    
    private void checkScrollFocusRight() {
        final View focused = getFocusedChild();
        if (getChildCount() >= 2) {
            View last = getChildAt(getChildCount() - 1);
            View lastButOne = getChildAt(getChildCount() - 2);
            
            if (focused == lastButOne) {
                scroll(last.getWidth());
            }
        }
    }
    
    /**
     * Check if list of weak references has any view still in memory to offer for recyclation
     *
     * @return cached view
     */
    protected View getCachedView() {
        if (mCachedItemViews.size() != 0) {
            View v;
            do
            {
                v = mCachedItemViews.removeFirst().get();
            }
            while (v == null && mCachedItemViews.size() != 0);
            return v;
        }
        return null;
    }
    
    protected void enableChildrenCache() {
        setChildrenDrawnWithCacheEnabled(true);
        setChildrenDrawingCacheEnabled(true);
    }
    
    protected void clearChildrenCache() {
        setChildrenDrawnWithCacheEnabled(false);
    }
    
    @Override
    public void setOnItemClickListener(
            OnItemClickListener listener) {
        mOnItemClickListener = listener;
    }
    
    @Override
    public void setOnItemSelectedListener(
            OnItemSelectedListener listener) {
        mOnItemSelectedListener = listener;
    }
    
    @Override
    @CapturedViewProperty
    public int getSelectedItemPosition() {
        return mSelectedPosition;
    }
    
    /**
     * Only set value for selection position field, no gui updates are done
     * for setting selection with gui updates and callback calls use setSelection
     *
     * @param position
     */
    public void setSeletedItemPosition(int position) {
        if (adapter.getCount() == 0 && position == 0) {
            position = -1;
        }
        if (position < -1 || position > adapter.getCount() - 1) {
            throw new IllegalArgumentException("Position index must be in range of adapter values (0 - getCount()-1) or -1 to unselect");
        }
        
        mSelectedPosition = position;
    }
    
    @Override
    @CapturedViewProperty
    public long getSelectedItemId() {
        return adapter.getItemId(mSelectedPosition);
    }
    
    @Override
    public Object getSelectedItem() {
        return getSelectedView();
    }
    
    @Override
    @CapturedViewProperty
    public int getCount() {
        if (adapter != null) {
            return adapter.getCount();
        } else {
            return 0;
        }
    }
    
    @Override
    public int getPositionForView(View view) {
        final int c = getChildCount();
        View v;
        for (int i = 0; i < c; i++) {
            v = getChildAt(i);
            if (v == view) {
                return mFirstItemPosition + i;
            }
        }
        return INVALID_POSITION;
    }
    
    @Override
    public int getFirstVisiblePosition() {
        return mFirstItemPosition;
    }
    
    @Override
    public int getLastVisiblePosition() {
        return mLastItemPosition;
    }
    
    @Override
    public Object getItemAtPosition(int position) {
        final int index;
        if (mFirstItemPosition > position) {
            index = position + adapter.getCount() - mFirstItemPosition;
        } else {
            index = position - mFirstItemPosition;
        }
        if (index < 0 || index >= getChildCount()) {
            return null;
        }
        
        return getChildAt(index);
    }
    
    @Override
    public long getItemIdAtPosition(int position) {
        return adapter.getItemId(position);
    }
    
    @Override
    public boolean performItemClick(View view, int position, long id) {
        throw new UnsupportedOperationException();
    }
    
    public void setViewObserver(IViewObserver viewObserver) {
        this.mViewObserver = viewObserver;
    }
    
    /**
     * Params describing position of child view in container
     * in HORIZONTAL mode TOP,CENTER,BOTTOM are active in VERTICAL mode LEFT,CENTER,RIGHT are active
     *
     * @author Martin Appl
     */
    public static class LoopLayoutParams extends MarginLayoutParams {
        public static final int TOP = 0;
        public static final int CENTER = 1;
        public static final int BOTTOM = 2;
        public static final int LEFT = 3;
        public static final int RIGHT = 4;
        
        public int position;
        //		public int actualWidth;
        //		public int actualHeight;
        
        public LoopLayoutParams(int w, int h) {
            super(w, h);
            position = CENTER;
        }
        
        public LoopLayoutParams(int w, int h, int pos) {
            super(w, h);
            position = pos;
        }
        
        public LoopLayoutParams(LayoutParams lp) {
            super(lp);
            
            if (lp != null && lp instanceof MarginLayoutParams) {
                MarginLayoutParams mp = (MarginLayoutParams) lp;
                leftMargin = mp.leftMargin;
                rightMargin = mp.rightMargin;
                topMargin = mp.topMargin;
                bottomMargin = mp.bottomMargin;
            }
            
            position = CENTER;
        }
        
    }
    
}


