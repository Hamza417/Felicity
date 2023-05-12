package app.simple.felicity.decorations.coverflow.containers.contentbands;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Scroller;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import app.simple.felicity.R;
import app.simple.felicity.decorations.coverflow.general.ToolBox;
import app.simple.felicity.decorations.coverflow.general.Validate;

/**
 * @author Martin Appl
 * <p>
 * Horizontally scrollable container with boundaries on the ends, which places Views on coordinates specified
 * by tile objects. Data binding is specified by adapter interface. Use abstract adapter which has already implemented
 * algorithms for searching views in requested ranges. You only need to implement getViewForTile method where you map
 * Tile objects from dataset to corresponding View objects, which get displayed. Position on screen is described by LayoutParams object.
 * Method getLayoutParamsForTile helps generate layout params from data objects. If you don't set Layout params in getViewForTile, this
 * methods is called automatically afterwards.
 * <p>
 * DSP = device specific pixel
 */
public class BasicContentBand extends ViewGroup {
    /**
     * In this mode we have pixel size of DSP specified, if dspHeight is bigger than window, content band can be scrolled vertically.
     */
    public static final int GRID_MODE_FIXED_SIZE = 0;
    /**
     * In this mode is pixel size of DSP calculated dynamically, based on widget height in pixels and value of dspHeight which is fixed
     * and taken from adapters getBottom method
     */
    public static final int GRID_MODE_DYNAMIC_SIZE = 1;
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
    //to which direction on X axis are window coordinates sliding
    protected static final int DIRECTION_RIGHT = 0;
    protected static final int DIRECTION_LEFT = 1;
    //CONSTANTS
    //	private static final String LOG_TAG = "Basic_ContentBand_Component";
    private static final int NO_VALUE = -11;
    private static final int DSP_DEFAULT = 10;
    protected final Scroller mScroller;
    private final ArrayList <View> mTempViewArray = new ArrayList <View>();
    private final Point mDown = new Point();
    //constant values
    private final int mTouchSlop;
    private final int mMinimumVelocity;
    private final int mMaximumVelocity;
    //VARIABLES
    protected Adapter mAdapter;
    protected int mDspHeightModulo;
    //refilling
    protected int mCurrentlyLayoutedViewsLeftEdgeDsp;
    protected int mCurrentlyLayoutedViewsRightEdgeDsp;
    //touch, scrolling
    protected int mTouchState = TOUCH_STATE_RESTING;
    protected int mScrollDirection = NO_VALUE;
    private int mGridMode = GRID_MODE_DYNAMIC_SIZE;
    /**
     * How many normal pixels corresponds to one DSP pixel
     */
    private int mDspPixelRatio = DSP_DEFAULT;
    private int mDspHeight = NO_VALUE;
    private float mLastMotionX;
    private float mLastMotionY;
    private VelocityTracker mVelocityTracker;
    private boolean mHandleSelectionOnActionUp = false;
    //    private final Rect mTempRect = new Rect();
    private boolean mIsZOrderEnabled;
    private int[] mDrawingOrderArray;
    
    //listeners
    private OnItemClickListener mItemClickListener;
    
    public BasicContentBand(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        
        final ViewConfiguration configuration = ViewConfiguration.get(context);
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mScroller = new Scroller(context);
        
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BasicContentBand, defStyle, 0);
            
            mDspPixelRatio = a.getInteger(R.styleable.BasicContentBand_deviceSpecificPixelSize, mDspPixelRatio);
            mGridMode = a.getInteger(R.styleable.BasicContentBand_gridMode, mGridMode);
            
            a.recycle();
        }
        
    }
    
    public BasicContentBand(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public BasicContentBand(Context context) {
        this(context, null);
    }
    
    protected int dspToPx(int dsp) {
        return dsp * mDspPixelRatio;
    }
    
    protected int pxToDsp(int px) {
        return px / mDspPixelRatio;
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec);
        
        if (mAdapter != null) {
            mDspHeight = mAdapter.getBottom();
            Validate.isTrue(mDspHeight > 0, "Adapter getBottom must return value greater than zero");
        } else {
            setMeasuredDimension(widthSpecSize, heightSpecSize);
            return;
        }
        
        int measuredWidth, measuredHeight;
        if (mGridMode == GRID_MODE_FIXED_SIZE) {
            /*HEIGHT*/
            measuredHeight = mDspPixelRatio * mDspHeight;
            
            if (heightSpecMode == MeasureSpec.AT_MOST) {
                if (measuredHeight > heightSpecSize) {
                    measuredHeight = heightSpecSize;
                }
            } else if (heightSpecMode == MeasureSpec.EXACTLY) {
                measuredHeight = heightSpecSize;
            }
            
            /*WIDTH*/
            measuredWidth = widthSpecSize;
            if (mAdapter != null) {
                measuredWidth = mAdapter.getEnd() * mDspPixelRatio;
            }
            
            if (widthSpecMode == MeasureSpec.AT_MOST) {
                if (measuredWidth > widthSpecSize) {
                    measuredWidth = widthSpecSize;
                }
            } else if (widthSpecMode == MeasureSpec.EXACTLY) {
                measuredWidth = widthSpecSize;
            }
        } else {
            if (heightSpecMode == MeasureSpec.UNSPECIFIED) {
                throw new RuntimeException("Can not have unspecified hight dimension in dynamic grid mode");
            }
            /*HEIGHT*/
            measuredHeight = heightSpecSize;
            
            mDspPixelRatio = measuredHeight / mDspHeight;
            mDspHeightModulo = measuredHeight % mDspHeight;
            
            measuredHeight = mDspPixelRatio * mDspHeight;
            
            if (heightSpecMode == MeasureSpec.AT_MOST) {
                if (measuredHeight > heightSpecSize) {
                    measuredHeight = heightSpecSize;
                } else {
                    mDspHeightModulo = 0;
                }
            } else if (heightSpecMode == MeasureSpec.EXACTLY) {
                measuredHeight = heightSpecSize;
            }
            
            /*WIDTH*/
            measuredWidth = widthSpecSize;
            if (mAdapter != null) {
                measuredWidth = mAdapter.getEnd() * mDspPixelRatio;
            }
            
            if (widthSpecMode == MeasureSpec.AT_MOST) {
                if (measuredWidth > widthSpecSize) {
                    measuredWidth = widthSpecSize;
                }
            } else if (widthSpecMode == MeasureSpec.EXACTLY) {
                measuredWidth = widthSpecSize;
            }
            
        }
        
        setMeasuredDimension(measuredWidth, measuredHeight);
    }
    
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int c = getChildCount();
        
        if (c == 0) {
            fillEmptyContainer();
            c = getChildCount();
        }
        
        for (int i = 0; i < c; i++) {
            layoutTileView(getChildAt(i));
        }
    }
    
    protected void layoutNewChildren(View[] list) {
        for (int i = 0; i < list.length; i++) {
            layoutTileView(list[i]);
            list[i].setDrawingCacheEnabled(true);
        }
    }
    
    private void layoutTileView(View v) {
        final LayoutParams lp = (LayoutParams) v.getLayoutParams();
        final int l = dspToPx(lp.dspLeft);
        final int t = dspToPx(lp.dspTop);
        final int r = l + dspToPx(lp.dspWidth);
        final int b = t + dspToPx(lp.dspHeight);
        
        v.layout(l, t, r, b);
    }
    
    //	protected View[] filterAlreadyPresentViews(final View[] arr){
    //		if(getChildCount() == 0) return arr;
    //
    //		LayoutParams lp;
    //		int nullCounter = 0;
    //		for(int i=0; i<arr.length; i++){
    //			lp = (LayoutParams) arr[i].getLayoutParams();
    //			for(int j=getChildCount()-1; j >= 0; j--){ //start at the end, because mostly we are searching for view which was added to end in previous iterations
    //				if(((LayoutParams)getChildAt(j).getLayoutParams()).tileNumber == lp.tileNumber) {
    //					arr[i] = null;
    //					nullCounter++;
    //					break;
    //				}
    //			}
    //		}
    //
    //		final View[] res = new View[arr.length - nullCounter];
    //		for(int i=0,j=0; i<arr.length; i++){
    //			if(arr[i] != null){
    //				res[j] = arr[i];
    //				j++;
    //			}
    //		}
    //
    //		return res;
    //	}
    
    protected void rearrangeViewsAccordingZOrder() {
        View[] tempArr = new View[getChildCount()];
        for (int i = 0; i < getChildCount(); i++) {
            tempArr[i] = getChildAt(i);
            ((LayoutParams) tempArr[i].getLayoutParams()).viewgroupIndex = i;
        }
        
        final Comparator <View> comparator = new Comparator <View>() {
            @Override
            public int compare(View lhs, View rhs) {
                final LayoutParams l = (LayoutParams) lhs.getLayoutParams();
                final LayoutParams r = (LayoutParams) rhs.getLayoutParams();
                
                if (l.z == r.z) {
                    return 0;
                } else if (l.z < r.z) {
                    return -1;
                } else {
                    return 1;
                }
            }
        };
        
        Arrays.sort(tempArr, comparator);
        mDrawingOrderArray = new int[tempArr.length];
        for (int i = 0; i < tempArr.length; i++) {
            mDrawingOrderArray[i] = ((LayoutParams) tempArr[i].getLayoutParams()).viewgroupIndex;
        }
    }
    
    @Override
    protected int getChildDrawingOrder(int count, int i) {
        return mDrawingOrderArray[i];
    }
    
    protected void resetChildren() {
        removeAllViewsInLayout();
        fillEmptyContainer();
        mDrawingOrderArray = null;
    }
    
    private void fillEmptyContainer() {
        if (mAdapter == null) {
            return;
        }
        
        final int leftScreenEdge = getScrollX();
        final int rightScreenEdge = leftScreenEdge + getWidth();
        final int dspLeftScreenEdge = pxToDsp(leftScreenEdge);
        final int dspRightScreenEdge = pxToDsp(rightScreenEdge) + 1;
        
        View[] list = mAdapter.getViewsVisibleInRange(dspLeftScreenEdge, dspRightScreenEdge);
        
        int dspMostRight = 0;
        int dspMostLeft = dspRightScreenEdge;
        LayoutParams lp;
        for (int i = 0; i < list.length; i++) {
            lp = (LayoutParams) list[i].getLayoutParams();
            if (lp.getDspRight() > dspMostRight) {
                dspMostRight = lp.getDspRight();
            }
            if (lp.dspLeft < dspMostLeft) {
                dspMostLeft = lp.dspLeft;
            }
            addViewInLayout(list[i], -1, list[i].getLayoutParams(), true);
        }
        
        if (mIsZOrderEnabled) {
            rearrangeViewsAccordingZOrder();
        }
        
        mCurrentlyLayoutedViewsLeftEdgeDsp = dspMostLeft;
        mCurrentlyLayoutedViewsRightEdgeDsp = dspMostRight;
    }
    
    /**
     * Checks and refills empty area on the left edge of screen
     */
    protected void refillLeftSide() {
        if (mAdapter == null) {
            return;
        }
        
        final int leftScreenEdge = getScrollX();
        final int dspLeftScreenEdge = pxToDsp(leftScreenEdge);
        final int dspNextViewsRight = mCurrentlyLayoutedViewsLeftEdgeDsp;
        
        if (dspLeftScreenEdge >= dspNextViewsRight) {
            return;
        }
        //		Logger.d(LOG_TAG, "from " + dspLeftScreenEdge + ", to " + dspNextViewsRight);
        
        View[] list = mAdapter.getViewsByRightSideRange(dspLeftScreenEdge, dspNextViewsRight);
        //		list = filterAlreadyPresentViews(list);
        
        int dspMostLeft = dspNextViewsRight;
        LayoutParams lp;
        for (int i = 0; i < list.length; i++) {
            lp = (LayoutParams) list[i].getLayoutParams();
            if (lp.dspLeft < dspMostLeft) {
                dspMostLeft = lp.dspLeft;
            }
            addViewInLayout(list[i], -1, list[i].getLayoutParams(), true);
        }
        
        if (list.length > 0) {
            layoutNewChildren(list);
        }
        
        mCurrentlyLayoutedViewsLeftEdgeDsp = dspMostLeft;
    }
    
    /**
     * Checks and refills empty area on the right
     */
    protected void refillRightSide() {
        if (mAdapter == null) {
            return;
        }
        
        final int rightScreenEdge = getScrollX() + getWidth();
        final int dspNextAddedViewsLeft = mCurrentlyLayoutedViewsRightEdgeDsp;
        
        int dspRightScreenEdge = pxToDsp(rightScreenEdge) + 1;
        if (dspRightScreenEdge > mAdapter.getEnd()) {
            dspRightScreenEdge = mAdapter.getEnd();
        }
        
        if (dspNextAddedViewsLeft >= dspRightScreenEdge) {
            return;
        }
        
        View[] list = mAdapter.getViewsByLeftSideRange(dspNextAddedViewsLeft, dspRightScreenEdge);
        //		list = filterAlreadyPresentViews(list);
        
        int dspMostRight = 0;
        LayoutParams lp;
        for (int i = 0; i < list.length; i++) {
            lp = (LayoutParams) list[i].getLayoutParams();
            if (lp.getDspRight() > dspMostRight) {
                dspMostRight = lp.getDspRight();
            }
            addViewInLayout(list[i], -1, list[i].getLayoutParams(), true);
        }
        
        if (list.length > 0) {
            layoutNewChildren(list);
        }
        
        mCurrentlyLayoutedViewsRightEdgeDsp = dspMostRight;
    }
    
    /**
     * Remove non visible views laid out of the screen
     */
    private void removeNonVisibleViews() {
        if (getChildCount() == 0) {
            return;
        }
        
        final int leftScreenEdge = getScrollX();
        final int rightScreenEdge = leftScreenEdge + getWidth();
        
        int dspRightScreenEdge = pxToDsp(rightScreenEdge);
        if (dspRightScreenEdge >= 0) {
            dspRightScreenEdge++; //to avoid problem with rounding of values
        }
        
        int dspLeftScreenEdge = pxToDsp(leftScreenEdge);
        if (dspLeftScreenEdge <= 0) {
            dspLeftScreenEdge--; //when values are <0 they get floored to value which is larger
        }
        
        mTempViewArray.clear();
        View v;
        for (int i = 0; i < getChildCount(); i++) {
            v = getChildAt(i);
            if (!isOnScreen((LayoutParams) v.getLayoutParams(), dspLeftScreenEdge, dspRightScreenEdge)) {
                mTempViewArray.add(v);
            }
        }
        
        for (int i = 0; i < mTempViewArray.size(); i++) {
            v = mTempViewArray.get(i);
            removeViewInLayout(v);
            mAdapter.offerViewForRecycling(v);
        }
        mTempViewArray.clear();
        
        int dspMostRight = dspLeftScreenEdge;
        int dspMostLeft = dspRightScreenEdge;
        LayoutParams lp;
        for (int i = 0; i < getChildCount(); i++) {
            lp = (LayoutParams) getChildAt(i).getLayoutParams();
            if (lp.getDspRight() > dspMostRight) {
                dspMostRight = lp.getDspRight();
            }
            if (lp.dspLeft < dspMostLeft) {
                dspMostLeft = lp.dspLeft;
            }
        }
        
        mCurrentlyLayoutedViewsLeftEdgeDsp = dspMostLeft;
        mCurrentlyLayoutedViewsRightEdgeDsp = dspMostRight;
        
    }
    
    //check if View with specified LayoutParams is currently on screen
    private boolean isOnScreen(LayoutParams lp, int dspLeftScreenEdge, int dspRightScreenEdge) {
        final int left = lp.dspLeft;
        final int right = left + lp.dspWidth;
        
        if (right > dspLeftScreenEdge && left < dspRightScreenEdge) {
            return true;
        } else {
            return false;
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
                final int yDiff = (int) Math.abs(y - mLastMotionY);
                
                final int touchSlop = mTouchSlop;
                final boolean xMoved = xDiff > touchSlop;
                final boolean yMoved = yDiff > touchSlop;
                
                if (xMoved || yMoved) {
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
                mLastMotionY = y;
                
                break;
            case MotionEvent.ACTION_MOVE:
                
                if (mTouchState == TOUCH_STATE_SCROLLING) {
                    // Scroll to follow the motion event
                    final int deltaX = (int) (mLastMotionX - x);
                    final int deltaY = (int) (mLastMotionY - y);
                    mLastMotionX = x;
                    mLastMotionY = y;
                    
                    scrollByDelta(deltaX, deltaY);
                } else {
                    final int xDiff = (int) Math.abs(x - mLastMotionX);
                    final int yDiff = (int) Math.abs(y - mLastMotionY);
                    
                    final int touchSlop = mTouchSlop;
                    final boolean xMoved = xDiff > touchSlop;
                    final boolean yMoved = yDiff > touchSlop;
                    
                    if (xMoved || yMoved) {
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
        if (mScroller.computeScrollOffset()) {
            if (mScroller.getFinalX() == mScroller.getCurrX()) {
                mScroller.abortAnimation();
                mTouchState = TOUCH_STATE_RESTING;
                mScrollDirection = NO_VALUE;
                clearChildrenCache();
            } else {
                final int x = mScroller.getCurrX();
                final int y = mScroller.getCurrY();
                scrollTo(x, y);
                
                postInvalidate();
            }
        } else if (mTouchState == TOUCH_STATE_FLING) {
            mTouchState = TOUCH_STATE_RESTING;
            mScrollDirection = NO_VALUE;
            clearChildrenCache();
        }
        
        removeNonVisibleViews();
        if (mScrollDirection == DIRECTION_LEFT) {
            refillLeftSide();
        }
        if (mScrollDirection == DIRECTION_RIGHT) {
            refillRightSide();
        }
        
        if (mIsZOrderEnabled) {
            rearrangeViewsAccordingZOrder();
        }
    }
    
    public void fling(int velocityX, int velocityY) {
        mTouchState = TOUCH_STATE_FLING;
        final int x = getScrollX();
        final int y = getScrollY();
        final int rightInPixels = dspToPx(mAdapter.getEnd());
        final int bottomInPixels = dspToPx(mAdapter.getBottom()) + mDspHeightModulo;
        
        mScroller.fling(x, y, velocityX, velocityY, 0, rightInPixels - getWidth(), 0, bottomInPixels - getHeight());
        
        if (velocityX < 0) {
            mScrollDirection = DIRECTION_LEFT;
        } else if (velocityX > 0) {
            mScrollDirection = DIRECTION_RIGHT;
        }
        
        invalidate();
    }
    
    protected void scrollByDelta(int deltaX, int deltaY) {
        final int rightInPixels = dspToPx(mAdapter.getEnd());
        final int bottomInPixels = dspToPx(mAdapter.getBottom()) + mDspHeightModulo;
        final int x = getScrollX() + deltaX;
        final int y = getScrollY() + deltaY;
        
        if (x < 0) {
            deltaX -= x;
        } else if (x > rightInPixels - getWidth()) {
            deltaX -= x - (rightInPixels - getWidth());
        }
        
        if (y < 0) {
            deltaY -= y;
        } else if (y > bottomInPixels - getHeight()) {
            deltaY -= y - (bottomInPixels - getHeight());
        }
        
        if (deltaX < 0) {
            mScrollDirection = DIRECTION_LEFT;
        } else {
            mScrollDirection = DIRECTION_RIGHT;
        }
        
        scrollBy(deltaX, deltaY);
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
    
    /**
     * Returns current Adapter with backing data
     */
    public Adapter getAdapter() {
        return mAdapter;
    }
    
    /**
     * Set Adapter with backing data
     */
    public void setAdapter(Adapter adapter) {
        this.mAdapter = adapter;
        requestLayout();
    }
    
    /**
     * Set listener which will fire if item in container is clicked
     */
    public void setOnItemClickListener(OnItemClickListener itemClickListener) {
        this.mItemClickListener = itemClickListener;
    }
    
    private void enableChildrenCache() {
        setChildrenDrawingCacheEnabled(true);
        setChildrenDrawnWithCacheEnabled(true);
    }
    
    private void clearChildrenCache() {
        setChildrenDrawnWithCacheEnabled(false);
    }
    
    /**
     * In GRID_MODE_FIXED_SIZE mode has one dsp dimension set by setDspSize(), If band height is after transformation to normal pixels bigger than
     * available space, content becomes scrollable also vertically.
     * <p>
     * In GRID_MODE_DYNAMIC_SIZE is dsp dimension computed from measured height and band height to always
     */
    public void setGridMode(int mode) {
        mGridMode = mode;
    }
    
    /**
     * Specifies how many normal pixels is in length of one device specific pixel
     * This method is significant only in GRID_MODE_FIXED_SIZE mode (use setGridMode)
     */
    public void setDspSize(int pixels) {
        mDspPixelRatio = pixels;
    }
    
    /**
     * Set to true if you want component to work with tile z parameter;
     * If you don't have any overlapping view, leave it on default false, because computing
     * with z order makes rendering slower.
     */
    public void setZOrderEnabled(boolean enable) {
        mIsZOrderEnabled = enable;
        setChildrenDrawingOrderEnabled(enable);
    }
    
    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams();
    }
    
    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams();
    }
    
    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }
    
    //----------------CONTENT BAND END--------------------------------------------------------------------
    
    public interface OnItemClickListener {
        void onItemClick(View v);
    }
    
    public interface Adapter {
        
        /**
         * Return Views which have left edge in device specific coordinates in range from-to,
         *
         * @param from inclusive
         * @param to   exclusive
         */
        public abstract View[] getViewsByLeftSideRange(int from, int to);
        
        /**
         * Return Views which have right edge in device specific coordinates in range from-to,
         *
         * @param from exclusive
         * @param to   inclusive
         */
        public abstract View[] getViewsByRightSideRange(int from, int to);
        
        /**
         * @return Right Coordinate of last tile in DSP
         */
        int getEnd();
        
        /**
         * @return Bottom Coordinate of tiles on bottom edge in DSP, Must be > 0
         */
        int getBottom();
        
        /**
         * @return total number of tiles
         */
        public int getCount();
        
        /**
         * Makes union between View returned by left side and right side ranges
         * Needed for initialization of component
         */
        public abstract View[] getViewsVisibleInRange(int from, int to);
        
        /**
         * Puts View, which is not needed anymore back to Adapter. View will be used later instead of creating or inflating same view.
         */
        public void offerViewForRecycling(View view);
    }
    
    public static class LayoutParams extends ViewGroup.LayoutParams {
        public int tileId;
        public int dspLeft;
        public int dspTop;
        public int dspWidth;
        public int dspHeight;
        public int z;
        
        private int viewgroupIndex;
        
        public LayoutParams() {
            super(NO_VALUE, NO_VALUE);
        }
        
        public int getDspRight() {
            return dspLeft + dspWidth;
        }
    }
    
    public static abstract class AbstractAdapter <V extends View, Tile extends TileBase> implements Adapter {
        private final ViewCache <V> mViewCache = new ViewCache <V>();
        private final Comparator <Tile> beginingComparator = new Comparator <Tile>() {
            @Override
            public int compare(Tile o1, Tile o2) {
                if (o1.getX() == o2.getX()) {
                    return 0;
                } else if (o1.getX() < o2.getX()) {
                    return -1;
                } else {
                    return 1;
                }
            }
        };
        private final Comparator <Tile> endComparator = new Comparator <Tile>() {
            @Override
            public int compare(Tile o1, Tile o2) {
                if (o1.getXRight() == o2.getXRight()) {
                    return 0;
                } else if (o1.getXRight() < o2.getXRight()) {
                    return -1;
                } else {
                    return 1;
                }
            }
        };
        protected ArrayList <Tile> mTilesByBegining;
        protected ArrayList <Tile> mTilesByEnd;
        //		protected SparseArray<Tile> mTilesByNumber;
        protected IDataListener mChangeListener;
        
        public AbstractAdapter() {
        }
        
        public AbstractAdapter(ArrayList <Tile> tiles) {
            initWithNewData(tiles);
        }
        
        @SuppressWarnings ("unchecked")
        @Override
        public void offerViewForRecycling(View view) {
            mViewCache.cacheView((V) view);
        }
        
        /**
         * Use getLayoutParamsForTile to get correct layout params for Tile data and set them with setLayoutParams before returning View
         *
         * @param t        Tile data from datamodel
         * @param recycled View no more used and returned for recycling. Use together with ViewHolder pattern to avoid performance loss
         *                 in inflating and searching by ids in more complex xml layouts.
         * @return View which will be displayed in component using layout data from Tile
         *
         * <pre>
         * 	public ImageView getViewForTile(Tile t, ImageView recycled) {
         * 		ImageView iw;
         * 		if(recycled != null) iw = recycled;
         * 		else iw = new ImageView(MainActivity.this);
         *
         * 		iw.setLayoutParams(getLayoutParamsForTile(t));
         * 		return iw;
         *    }
         * </pre>
         */
        public abstract V getViewForTile(Tile t, V recycled);
        
        /**
         * @return total number of tiles
         */
        public int getCount() {
            return mTilesByBegining.size();
        }
        
        public int getEnd() {
            if (mTilesByEnd.size() > 0) {
                return mTilesByEnd.get(mTilesByEnd.size() - 1).getXRight();
            } else {
                return 0;
            }
        }
        
        private void checkAndFixLayoutParams(View v, Tile t) {
            if (!(v.getLayoutParams() instanceof LayoutParams)) {
                v.setLayoutParams(getLayoutParamsForTile(t));
            }
        }
        
        @Override
        public View[] getViewsByLeftSideRange(int from, int to) {
            if (from == to) {
                return new View[0];
            }
            final List <Tile> list = getTilesWithLeftRange(from, to);
            
            final View[] arr = new View[list.size()];
            for (int i = 0; i < arr.length; i++) {
                Tile t = list.get(i);
                arr[i] = getViewForTile(t, mViewCache.getCachedView());
                checkAndFixLayoutParams(arr[i], t);
            }
            
            return arr;
        }
        
        @Override
        public View[] getViewsByRightSideRange(int from, int to) {
            if (from == to) {
                return new View[0];
            }
            final List <Tile> list = getTilesWithRightRange(from, to);
            
            final View[] arr = new View[list.size()];
            for (int i = 0; i < arr.length; i++) {
                Tile t = list.get(i);
                arr[i] = getViewForTile(t, mViewCache.getCachedView());
                checkAndFixLayoutParams(arr[i], t);
            }
            
            return arr;
        }
        
        public View[] getViewsVisibleInRange(int from, int to) {
            final List <Tile> listLeft = getTilesWithLeftRange(from, to);
            final List <Tile> listRight = getTilesWithRightRange(from, to);
            
            ArrayList <Tile> union = ToolBox.union(listLeft, listRight);
            
            final View[] arr = new View[union.size()];
            for (int i = 0; i < arr.length; i++) {
                Tile t = union.get(i);
                arr[i] = getViewForTile(t, mViewCache.getCachedView());
                checkAndFixLayoutParams(arr[i], t);
            }
            
            return arr;
        }
        
        public void setTiles(ArrayList <Tile> tiles) {
            initWithNewData(tiles);
            if (mChangeListener != null) {
                mChangeListener.onDataSetChanged();
            }
        }
        
        public void setDataChangeListener(IDataListener listener) {
            mChangeListener = listener;
        }
        
        @SuppressWarnings ("unchecked")
        protected void initWithNewData(ArrayList <Tile> tiles) {
            mTilesByBegining = (ArrayList <Tile>) tiles.clone();
            
            Collections.sort(mTilesByBegining, beginingComparator);
            
            mTilesByEnd = (ArrayList <Tile>) mTilesByBegining.clone();
            Collections.sort(mTilesByEnd, endComparator);
        }
        
        /**
         * @param from inclusive
         * @param to   exclusive
         */
        public List <Tile> getTilesWithLeftRange(int from, int to) {
            if (mTilesByBegining.size() == 0) {
                return Collections.emptyList();
            }
            final int fromIndex = binarySearchLeftEdges(from);
            if (mTilesByBegining.get(fromIndex).getX() > to) {
                return Collections.emptyList();
            }
            
            int i = fromIndex;
            Tile t = mTilesByBegining.get(i);
            while (t.getX() < to) {
                i++;
                if (i < mTilesByBegining.size()) {
                    t = mTilesByBegining.get(i);
                } else {
                    break;
                }
            }
            
            return mTilesByBegining.subList(fromIndex, i);
        }
        
        /**
         * @param from exclusive
         * @param to   inclusive
         */
        public List <Tile> getTilesWithRightRange(int from, int to) {
            if (mTilesByEnd.size() == 0) {
                return Collections.emptyList();
            }
            
            final int fromIndex = binarySearchRightEdges(from + 1); //from is exclusive
            final int fromRight = mTilesByEnd.get(fromIndex).getXRight();
            
            if (fromRight > to) {
                return Collections.emptyList();
            }
            
            int i = fromIndex;
            Tile t = mTilesByEnd.get(i);
            while (t.getXRight() <= to) {
                i++;
                if (i < mTilesByEnd.size()) {
                    t = mTilesByEnd.get(i);
                } else {
                    break;
                }
            }
            
            return mTilesByEnd.subList(fromIndex, i);
        }
        
        /**
         * Continues to split same values until it rests on first of them
         * returns first tile with left equal than value or greater
         */
        private int binarySearchLeftEdges(int value) {
            int lo = 0;
            int hi = mTilesByBegining.size() - 1;
            int mid = 0;
            Tile t = null;
            while (lo <= hi) {
                // Key is in a[lo..hi] or not present.
                mid = lo + (hi - lo) / 2;
                t = mTilesByBegining.get(mid);
                
                if (value > t.getX()) {
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
                
            }
            
            while (t != null && t.getX() < value && mid < mTilesByBegining.size() - 1) {
                mid++;
                t = mTilesByBegining.get(mid);
            }
            
            return mid;
        }
        
        /**
         * Continues to split same values until it rests on first of them
         * returns first tile with right equal than value or greater
         */
        private int binarySearchRightEdges(int value) {
            int lo = 0;
            int hi = mTilesByEnd.size() - 1;
            int mid = 0;
            Tile t = null;
            while (lo <= hi) {
                // Key is in a[lo..hi] or not present.
                mid = lo + (hi - lo) / 2;
                t = mTilesByEnd.get(mid);
                
                final int r = t.getXRight();
                if (value > r) {
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            
            while (t != null && t.getXRight() < value && mid < mTilesByEnd.size() - 1) {
                mid++;
                t = mTilesByEnd.get(mid);
            }
            
            return mid;
        }
        
        /**
         * Use this in getViewForTile implementation to provide correctly initialized layout params for component
         *
         * @param t Tile data from datamodel
         * @return ContendBand layout params
         */
        public LayoutParams getLayoutParamsForTile(Tile t) {
            LayoutParams lp = new LayoutParams();
            lp.tileId = t.getId();
            lp.dspLeft = t.getX();
            lp.dspTop = t.getY();
            lp.dspWidth = t.getWidth();
            lp.dspHeight = t.getHeight();
            lp.z = t.getZ();
            return lp;
        }
        
        interface IDataListener {
            void onDataSetChanged();
        }
        
    }
    
    private static class ViewCache <T extends View> {
        final LinkedList <WeakReference <T>> mCachedItemViews = new LinkedList <WeakReference <T>>();
        
        /**
         * Check if list of weak references has any view still in memory to offer for recycling
         *
         * @return cached view
         */
        T getCachedView() {
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
        
        void cacheView(T v) {
            WeakReference <T> ref = new WeakReference <T>(v);
            mCachedItemViews.addLast(ref);
        }
    }
    
}
