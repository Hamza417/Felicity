package app.simple.felicity.decorations.views;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class SnappyRecyclerView extends RecyclerView {
    
    // Use it with a horizontal LinearLayoutManager
    // Based on https://stackoverflow.com/a/29171652/4034572
    
    public SnappyRecyclerView(Context context) {
        super(context);
    }
    
    public SnappyRecyclerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }
    
    public SnappyRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    
    @Override
    public boolean fling(int velocityX, int velocityY) {
        
        LinearLayoutManager linearLayoutManager = (LinearLayoutManager) getLayoutManager();
        
        int screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        
        // views on the screen
        assert linearLayoutManager != null;
        int lastVisibleItemPosition = linearLayoutManager.findLastVisibleItemPosition();
        View lastView = linearLayoutManager.findViewByPosition(lastVisibleItemPosition);
        int firstVisibleItemPosition = linearLayoutManager.findFirstVisibleItemPosition();
        View firstView = linearLayoutManager.findViewByPosition(firstVisibleItemPosition);
        
        // distance we need to scroll
        assert lastView != null;
        int leftMargin = (screenWidth - lastView.getWidth()) / 2;
        assert firstView != null;
        int rightMargin = (screenWidth - firstView.getWidth()) / 2 + firstView.getWidth();
        int leftEdge = lastView.getLeft();
        int rightEdge = firstView.getRight();
        int scrollDistanceLeft = leftEdge - leftMargin;
        int scrollDistanceRight = rightMargin - rightEdge;
        
        if (Math.abs(velocityX) < 1000) {
            // The fling is slow -> stay at the current page if we are less than half through,
            // or go to the next page if more than half through
            
            if (leftEdge > screenWidth / 2) {
                // go to next page
                smoothScrollBy(-scrollDistanceRight, 0);
            } else if (rightEdge < screenWidth / 2) {
                // go to next page
                smoothScrollBy(scrollDistanceLeft, 0);
            } else {
                // stay at current page
                if (velocityX > 0) {
                    smoothScrollBy(-scrollDistanceRight, 0);
                } else {
                    smoothScrollBy(scrollDistanceLeft, 0);
                }
            }
            return true;
            
        } else {
            // The fling is fast -> go to next page
            
            if (velocityX > 0) {
                smoothScrollBy(scrollDistanceLeft, 0);
            } else {
                smoothScrollBy(-scrollDistanceRight, 0);
            }
            return true;
            
        }
        
    }
    
    @Override
    public void onScrollStateChanged(int state) {
        super.onScrollStateChanged(state);
        
        // If you tap on the phone while the RecyclerView is scrolling it will stop in the middle.
        // This code fixes this. This code is not strictly necessary but it improves the behaviour.
        
        if (state == SCROLL_STATE_IDLE) {
            LinearLayoutManager linearLayoutManager = (LinearLayoutManager) getLayoutManager();
            
            int screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
            
            // views on the screen
            assert linearLayoutManager != null;
            int lastVisibleItemPosition = linearLayoutManager.findLastVisibleItemPosition();
            View lastView = linearLayoutManager.findViewByPosition(lastVisibleItemPosition);
            int firstVisibleItemPosition = linearLayoutManager.findFirstVisibleItemPosition();
            View firstView = linearLayoutManager.findViewByPosition(firstVisibleItemPosition);
            
            // distance we need to scroll
            assert lastView != null;
            int leftMargin = (screenWidth - lastView.getWidth()) / 2;
            assert firstView != null;
            int rightMargin = (screenWidth - firstView.getWidth()) / 2 + firstView.getWidth();
            int leftEdge = lastView.getLeft();
            int rightEdge = firstView.getRight();
            int scrollDistanceLeft = leftEdge - leftMargin;
            int scrollDistanceRight = rightMargin - rightEdge;
            
            if (leftEdge > screenWidth / 2) {
                smoothScrollBy(-scrollDistanceRight, 0);
            } else if (rightEdge < screenWidth / 2) {
                smoothScrollBy(scrollDistanceLeft, 0);
            }
        }
    }
    
}