package app.simple.felicity.decorations.coverflow.containers.contentbands;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import app.simple.felicity.decorations.coverflow.general.ToolBox;

/**
 * @author Martin Appl
 * DSP = device specific pixel
 * TODO last poster is disappearing prematurely and reappearing late. Time to time container isn't drawn after Activity initialization.
 */
public class EndlessContentBand extends BasicContentBand {
    
    public EndlessContentBand(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    
    public EndlessContentBand(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    public EndlessContentBand(Context context) {
        super(context);
    }
    
    /**
     * Checks and refills empty area on the left edge of screen
     */
    @Override
    protected void refillLeftSide() {
        final int leftScreenEdge = getScrollX();
        final int dspNextViewsRight = mCurrentlyLayoutedViewsLeftEdgeDsp;
        
        int dspLeftScreenEdge = pxToDsp(leftScreenEdge);
        if (dspLeftScreenEdge <= 0) {
            dspLeftScreenEdge--; //when values are <0 they get floored to value which is larger
        }
        
        int end = mAdapter.getEnd();
        
        if (dspLeftScreenEdge >= dspNextViewsRight || end == 0) {
            return;
        }
        
        int dspModuloLeftScreenEdge = dspLeftScreenEdge % end;
        int dspModuloNextViewsRight = dspNextViewsRight % end;
        int dspOffsetLeftScreenEdge = dspLeftScreenEdge / end;
        int dspOffsetNextViewsRight = dspNextViewsRight / end;
        
        if (dspModuloLeftScreenEdge < 0) {
            dspModuloLeftScreenEdge += end;
            dspOffsetLeftScreenEdge -= 1;
        }
        if (dspModuloNextViewsRight < 0) {
            dspModuloNextViewsRight += end;
            dspOffsetNextViewsRight -= 1;
        }
        
        View[] list;
        if (dspModuloLeftScreenEdge > dspModuloNextViewsRight) {
            View[] list1, list2;
            list1 = mAdapter.getViewsByRightSideRange(dspModuloLeftScreenEdge, end);
            list2 = mAdapter.getViewsByRightSideRange(0, dspModuloNextViewsRight);
            translateLayoutParams(list1, dspOffsetLeftScreenEdge);
            translateLayoutParams(list2, dspOffsetNextViewsRight);
            
            list = ToolBox.concatenateArray(list1, list2);
        } else {
            list = mAdapter.getViewsByRightSideRange(dspModuloLeftScreenEdge, dspModuloNextViewsRight);
            translateLayoutParams(list, dspOffsetLeftScreenEdge);
        }
        
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
    
    private void translateLayoutParams(View[] list, int offset) {
        if (offset == 0 || list.length == 0) {
            return;
        }
        
        final int end = mAdapter.getEnd();
        LayoutParams lp;
        
        for (int i = 0; i < list.length; i++) {
            lp = (LayoutParams) list[i].getLayoutParams();
            lp.dspLeft += offset * end;
        }
    }
    
    /**
     * Checks and refills empty area on the right
     */
    @Override
    protected void refillRightSide() {
        final int rightScreenEdge = getScrollX() + getWidth();
        final int dspNextAddedViewsLeft = mCurrentlyLayoutedViewsRightEdgeDsp;
        final int end = mAdapter.getEnd();
        
        int dspRightScreenEdge = pxToDsp(rightScreenEdge);
        if (dspRightScreenEdge >= 0) {
            dspRightScreenEdge++; //to avoid problem with rounding of values
        }
        
        if (dspNextAddedViewsLeft >= dspRightScreenEdge || end == 0) {
            return;
        }
        
        int dspModuloRightScreenEdge = dspRightScreenEdge % end;
        int dspModuloNextAddedViewsLeft = dspNextAddedViewsLeft % end;
        int dspOffsetRightScreenEdge = dspRightScreenEdge / end;
        int dspOffsetNextAddedViewsLeft = dspNextAddedViewsLeft / end;
        
        if (dspModuloRightScreenEdge < 0) {
            dspModuloRightScreenEdge += end;
            dspOffsetRightScreenEdge -= 1;
        }
        if (dspModuloNextAddedViewsLeft < 0) {
            dspModuloNextAddedViewsLeft += end;
            dspOffsetNextAddedViewsLeft -= 1;
        }
        
        View[] list;
        if (dspModuloNextAddedViewsLeft > dspModuloRightScreenEdge) {
            View[] list1, list2;
            list1 = mAdapter.getViewsByLeftSideRange(dspModuloNextAddedViewsLeft, end);
            list2 = mAdapter.getViewsByLeftSideRange(0, dspModuloRightScreenEdge);
            translateLayoutParams(list1, dspOffsetNextAddedViewsLeft);
            translateLayoutParams(list2, dspOffsetRightScreenEdge);
            
            list = ToolBox.concatenateArray(list1, list2);
        } else {
            list = mAdapter.getViewsByLeftSideRange(dspModuloNextAddedViewsLeft, dspModuloRightScreenEdge);
            translateLayoutParams(list, dspOffsetNextAddedViewsLeft);
        }
        
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
    
    public void fling(int velocityX, int velocityY) {
        mTouchState = TOUCH_STATE_FLING;
        final int x = getScrollX();
        final int y = getScrollY();
        final int bottomInPixels = dspToPx(mAdapter.getBottom()) + mDspHeightModulo;
        
        mScroller.fling(x, y, velocityX, velocityY, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, bottomInPixels - getHeight());
        
        if (velocityX < 0) {
            mScrollDirection = DIRECTION_LEFT;
        } else if (velocityX > 0) {
            mScrollDirection = DIRECTION_RIGHT;
        }
        
        invalidate();
    }
    
    @Override
    protected void scrollByDelta(int deltaX, int deltaY) {
        final int bottomInPixels = dspToPx(mAdapter.getBottom()) + mDspHeightModulo;
        final int y = getScrollY() + deltaY;
        
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
    
}
