package app.simple.felicity.decorations.coverflow.containers;

import android.annotation.SuppressLint;
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
    protected final LinkedList <WeakReference <View>> cachedItemViews = new LinkedList <WeakReference <View>>();
    protected final Scroller scroller = new Scroller(getContext());
    private final Point down = new Point();
    /**
     * The adapter providing data for container
     */
    protected Adapter adapter;
    private final int touchSlop;
    private final int minimumVelocity;
    //	private long mDownTime;
    private final int maximumVelocity;
    /**
     * The adaptor position of the first visible item
     */
    protected int firstItemPosition;
    /**
     * The adaptor position of the last visible item
     */
    protected int lastItemPosition;
    /**
     * The adaptor position of selected item
     */
    protected int selectedPosition = INVALID_POSITION;
    /**
     * Left of current most left child
     */
    protected int leftChildEdge;
    protected int touchState = TOUCH_STATE_RESTING;
    /**
     * If there is not enough items to fill adapter, this value is set to true and scrolling is disabled. Since all items from adapter are on screen
     */
    protected boolean isScrollingDisabled = false;
    /**
     * Whether content should be repeated when there is not enough items to fill container
     */
    protected boolean shouldRepeat = false;
    /**
     * Position to scroll adapter only if is in endless mode. This is done after layout if we find out we are endless, we must relayout
     */
    protected int scrollPositionIfEndless = -1;
    protected OnItemClickListener onItemClickListener;
    protected OnItemSelectedListener onItemSelectedListener;
    private IViewObserver viewObserver;
    private VelocityTracker velocityTracker;
    private boolean dataChanged;
    private final DataSetObserver dataSetObserver = new DataSetObserver() {
        
        @Override
        public void onChanged() {
            synchronized (this) {
                dataChanged = true;
            }
            invalidate();
        }
        
        @Override
        public void onInvalidated() {
            adapter = null;
        }
    };
    private boolean allowLongPress;
    private float lastMotionX;
    //	private boolean mCancelInIntercept;
    private float mLastMotionY;
    private boolean handleSelectionOnActionUp = false;
    private boolean interceptTouchEvents;
    
    public EndlessLoopAdapterContainer(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
        
        final ViewConfiguration configuration = ViewConfiguration.get(context);
        touchSlop = configuration.getScaledTouchSlop();
        minimumVelocity = configuration.getScaledMinimumFlingVelocity();
        maximumVelocity = configuration.getScaledMaximumFlingVelocity();
        
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
        return !isScrollingDisabled;
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
            scrollPositionIfEndless = position;
        }
    }
    
    /**
     * Returns position to which will container scroll on next relayout
     *
     * @return scroll position on next layout or -1 if it will scroll nowhere
     */
    public int getScrollPositionIfEndless() {
        return scrollPositionIfEndless;
    }
    
    /**
     * Get index of currently first item in adapter
     *
     * @return
     */
    public int getScrollPosition() {
        return firstItemPosition;
    }
    
    /**
     * Return offset by which is edge off first item moved off screen.
     * You can persist it and insert to setFirstItemOffset() to restore exact scroll position
     *
     * @return offset of first item, or 0 if there is not enough items to fill container and scrolling is disabled
     */
    public int getFirstItemOffset() {
        if (isScrollingDisabled) {
            return 0;
        } else {
            return getScrollX() - leftChildEdge;
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
            this.adapter.unregisterDataSetObserver(dataSetObserver);
        }
        this.adapter = adapter;
        this.adapter.registerDataSetObserver(dataSetObserver);
        
        if (adapter instanceof IViewObserver) {
            setViewObserver((IViewObserver) adapter);
        }
        
        reset();
        refill();
        invalidate();
    }
    
    @Override
    public View getSelectedView() {
        if (selectedPosition == INVALID_POSITION) {
            return null;
        }
    
        final int index;
        if (firstItemPosition > selectedPosition) {
            index = selectedPosition + adapter.getCount() - firstItemPosition;
        } else {
            index = selectedPosition - firstItemPosition;
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
    
        final int oldPos = selectedPosition;
        selectedPosition = position;
    
        if (position == -1) {
            if (onItemSelectedListener != null) {
                onItemSelectedListener.onNothingSelected(this);
            }
            return;
        }
    
        v = getSelectedView();
        if (v != null) {
            v.setSelected(true);
        }
    
        if (oldPos != selectedPosition && onItemSelectedListener != null) {
            onItemSelectedListener.onItemSelected(this, v, selectedPosition, getSelectedItemId());
        }
    }
    
    private void reset() {
        scrollTo(0, 0);
        removeAllViewsInLayout();
        firstItemPosition = 0;
        lastItemPosition = -1;
        leftChildEdge = 0;
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
    
        if (scroller.computeScrollOffset()) {
            if (scroller.getFinalX() == scroller.getCurrX()) {
                scroller.abortAnimation();
                touchState = TOUCH_STATE_RESTING;
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
    
        if (dataChanged) {
            removeAllViewsInLayout();
            refillOnChange(firstItemPosition);
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
    
        scroller.fling(x, y, velocityX, velocityY, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
        
        invalidate();
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
    
    @Override
    protected void onLayout(boolean changed, int left, int top, int right,
            int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        
        // if we don't have an adapter, we don't need to do anything
        if (adapter == null) {
            return;
        }
    
        refillInternal(lastItemPosition, firstItemPosition);
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
        int left = leftChildEdge;
        
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
        isScrollingDisabled = false;
    
        lastItemPosition = lastItemPos;
        firstItemPosition = firstItemPos;
        leftChildEdge = 0;
        right = leftChildEdge;
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
        int right = child.getRight();
        int currLayoutLeft = right + ((LoopLayoutParams) child.getLayoutParams()).rightMargin;
        while (right < rightScreenEdge) {
            lastItemPosition++;
            if (lastItemPosition >= adapter.getCount()) {
                lastItemPosition = 0;
            }
    
            child = adapter.getView(lastItemPosition, getCachedView(), this);
            Validate.notNull(child, "Your adapter has returned null from getView.");
            child = addAndMeasureChildHorizontal(child, LAYOUT_MODE_AFTER);
            currLayoutLeft = layoutChildHorizontal(child, currLayoutLeft, (LoopLayoutParams) child.getLayoutParams());
            right = child.getRight();
    
            //if selected view is going to screen, set selected state on him
            if (lastItemPosition == selectedPosition) {
                child.setSelected(true);
            }
        }
    }
    
    /**
     * Checks and refills empty area on the left
     */
    protected void refillLeft() {
        if (!shouldRepeat && isScrollingDisabled) {
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
            firstItemPosition--;
            if (firstItemPosition < 0) {
                firstItemPosition = adapter.getCount() - 1;
            }
    
            child = adapter.getView(firstItemPosition, getCachedView(), this);
            Validate.notNull(child, "Your adapter has returned null from getView.");
            child = addAndMeasureChildHorizontal(child, LAYOUT_MODE_TO_BEFORE);
            currLayoutRight = layoutChildHorizontalToBefore(child, currLayoutRight, (LoopLayoutParams) child.getLayoutParams());
            childLeft = child.getLeft() - ((LoopLayoutParams) child.getLayoutParams()).leftMargin;
            //update left edge of children in container
            leftChildEdge = childLeft;
            
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
        final int leftEdge = firstChild.getLeft() - ((LoopLayoutParams) firstChild.getLayoutParams()).leftMargin;
        if (leftEdge != leftChildEdge) {
            throw new IllegalStateException("firstChild.getLeft() != mLeftChildEdge");
        }
        while (firstChild != null && firstChild.getRight() + ((LoopLayoutParams) firstChild.getLayoutParams()).rightMargin < leftScreenEdge) {
            //if selected view is going off screen, remove selected state
            firstChild.setSelected(false);
        
            // remove view
            removeViewInLayout(firstChild);
        
            if (viewObserver != null) {
                viewObserver.onViewRemovedFromParent(firstChild, firstItemPosition);
            }
            WeakReference <View> ref = new WeakReference <View>(firstChild);
            cachedItemViews.addLast(ref);
        
            firstItemPosition++;
            if (firstItemPosition >= adapter.getCount()) {
                firstItemPosition = 0;
            }
        
            // update left item position
            leftChildEdge = getChildAt(0).getLeft() - ((LoopLayoutParams) getChildAt(0).getLayoutParams()).leftMargin;
        
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
    
            if (viewObserver != null) {
                viewObserver.onViewRemovedFromParent(lastChild, lastItemPosition);
            }
            WeakReference <View> ref = new WeakReference <View>(lastChild);
            cachedItemViews.addLast(ref);
    
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
            if (lp instanceof LoopLayoutParams) {
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
     * @param left position for left edge in parent container
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
                if (!shouldRepeat && isScrollingDisabled) {
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
                final int xDiff = (int) Math.abs(x - lastMotionX);
                final int yDiff = (int) Math.abs(y - mLastMotionY);
    
                final int touchSlop = this.touchSlop;
                final boolean xMoved = xDiff > touchSlop;
                final boolean yMoved = yDiff > touchSlop;
                
                if (xMoved) {
    
                    // Scroll if the user moved far enough along the X axis
                    touchState = TOUCH_STATE_SCROLLING;
                    handleSelectionOnActionUp = false;
                    enableChildrenCache();
    
                    // Either way, cancel any pending longpress
                    if (allowLongPress) {
                        allowLongPress = false;
                        // Try canceling the long press. It could also have been scheduled
                        // by a distant descendant, so use the mAllowLongPress flag to block
                        // everything
                        cancelLongPress();
                    }
                }
                if (yMoved) {
                    handleSelectionOnActionUp = false;
                    if (allowLongPress) {
                        allowLongPress = false;
                        cancelLongPress();
                    }
                }
                break;
    
            case MotionEvent.ACTION_DOWN:
                // Remember location of down touch
                lastMotionX = x;
                mLastMotionY = y;
                allowLongPress = true;
                //                mCancelInIntercept = false;
        
                down.x = (int) x;
                down.y = (int) y;
        
                /*
                 * If being flinged and user touches the screen, initiate drag;
                 * otherwise don't.  mScroller.isFinished should be false when
                 * being flinged.
                 */
                touchState = scroller.isFinished() ? TOUCH_STATE_RESTING : TOUCH_STATE_SCROLLING;
                //if he had normal click in rested state, remember for action up check
                if (touchState == TOUCH_STATE_RESTING) {
                    handleSelectionOnActionUp = true;
                }
                break;
    
            case MotionEvent.ACTION_CANCEL:
                down.x = -1;
                down.y = -1;
                //            	mCancelInIntercept = true;
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
                        break;
                    }
                }
                touchState = TOUCH_STATE_RESTING;
                clearChildrenCache();
                break;
        }
    
        interceptTouchEvents = touchState == TOUCH_STATE_SCROLLING;
        return interceptTouchEvents;
    
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
                    onItemClickListener.onItemClick(this, v, position, getItemIdAtPosition(position));
                }
                if (onItemSelectedListener != null) {
                    onItemSelectedListener.onItemSelected(this, v, position, getItemIdAtPosition(position));
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
    
    @SuppressLint ("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // if we don't have an adapter, we don't need to do anything
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
                
                // Remember where the motion event started
                lastMotionX = x;
                mLastMotionY = y;
                
                break;
            case MotionEvent.ACTION_MOVE:
                //if we have scrolling disabled, we don't do anything
                if (!shouldRepeat && isScrollingDisabled) {
                    return false;
                }
                
                if (touchState == TOUCH_STATE_SCROLLING) {
                    // Scroll to follow the motion event
                    final int deltaX = (int) (lastMotionX - x);
                    lastMotionX = x;
                    mLastMotionY = y;
                    
                    int sx = getScrollX() + deltaX;
                    
                    scrollTo(sx, 0);
                    
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
                if (handleSelectionOnActionUp && touchState == TOUCH_STATE_RESTING) {
                    final double d = ToolBox.getLineLength(down.x, down.y, x, y);
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
                
                //this must be here, in case no child view returns true,
                //events will propagate back here and on intercept touch event wont be called again
                //instead we get cancel here, since we stated we shouldn't intercept events and propagate them to children
                //but events propagated back here, because no child was interested
                //        	if(!mInterceptTouchEvents && mHandleSelectionOnActionUp && mTouchState == TOUCH_STATE_RESTING){
                //        		handleClick(mDown);
                //        		mHandleSelectionOnActionUp = false;
                //        	}
                
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
        if (cachedItemViews.size() != 0) {
            View v;
            do
            {
                v = cachedItemViews.removeFirst().get();
            }
            while (v == null && cachedItemViews.size() != 0);
            return v;
        }
        return null;
    }
    
    protected void enableChildrenCache() {
        // setChildrenDrawnWithCacheEnabled(true);
        setChildrenDrawingCacheEnabled(true);
    }
    
    protected void clearChildrenCache() {
        // setChildrenDrawnWithCacheEnabled(false);
    }
    
    @Override
    public void setOnItemClickListener(OnItemClickListener listener) {
        onItemClickListener = listener;
    }
    
    @Override
    public void setOnItemSelectedListener(
            OnItemSelectedListener listener) {
        onItemSelectedListener = listener;
    }
    
    @Override
    @CapturedViewProperty
    public int getSelectedItemPosition() {
        return selectedPosition;
    }
    
    /**
     * Only set value for selection position field, no gui updates are done
     * for setting selection with gui updates and callback calls use setSelection
     *
     * @param position position to set
     */
    public void setSelectedItemPosition(int position) {
        if (adapter.getCount() == 0 && position == 0) {
            position = -1;
        }
        if (position < -1 || position > adapter.getCount() - 1) {
            throw new IllegalArgumentException("Position index must be in range of adapter values (0 - getCount()-1) or -1 to unselect");
        }
        
        selectedPosition = position;
    }
    
    @Override
    @CapturedViewProperty
    public long getSelectedItemId() {
        return adapter.getItemId(selectedPosition);
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
                return firstItemPosition + i;
            }
        }
        return INVALID_POSITION;
    }
    
    @Override
    public int getFirstVisiblePosition() {
        return firstItemPosition;
    }
    
    @Override
    public int getLastVisiblePosition() {
        return lastItemPosition;
    }
    
    @Override
    public Object getItemAtPosition(int position) {
        final int index;
        if (firstItemPosition > position) {
            index = position + adapter.getCount() - firstItemPosition;
        } else {
            index = position - firstItemPosition;
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
        this.viewObserver = viewObserver;
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
    
            if (lp instanceof MarginLayoutParams) {
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


