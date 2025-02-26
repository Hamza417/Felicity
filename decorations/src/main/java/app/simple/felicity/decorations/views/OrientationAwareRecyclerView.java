/*
 * Copyright 2018 Rúben Sousa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.simple.felicity.decorations.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A RecyclerView that only handles scroll events with the same orientation of its LayoutManager.
 * Avoids situations where nested recyclerviews don't receive touch events properly:
 */
public class OrientationAwareRecyclerView extends RecyclerView {
    
    private float lastX = 0.0f;
    private float lastY = 0.0f;
    private boolean scrolling = false;
    
    public OrientationAwareRecyclerView(@NonNull Context context) {
        this(context, null);
    }
    
    public OrientationAwareRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public OrientationAwareRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        addOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                scrolling = newState != RecyclerView.SCROLL_STATE_IDLE;
            }
        });
    }
    
    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        final LayoutManager lm = getLayoutManager();
        if (lm == null) {
            return super.onInterceptTouchEvent(e);
        }
        
        boolean allowScroll = true;
        
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                lastX = e.getX();
                lastY = e.getY();
                // If we were scrolling, stop now by faking a touch release
                if (scrolling) {
                    MotionEvent newEvent = MotionEvent.obtain(e);
                    newEvent.setAction(MotionEvent.ACTION_UP);
                    return super.onInterceptTouchEvent(newEvent);
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                // We're moving, so check if we're trying
                // to scroll vertically or horizontally so we don't intercept the wrong event.
                float currentX = e.getX();
                float currentY = e.getY();
                float dx = Math.abs(currentX - lastX);
                float dy = Math.abs(currentY - lastY);
                allowScroll = dy > dx ? lm.canScrollVertically() : lm.canScrollHorizontally();
                break;
            }
        }
        
        if (!allowScroll) {
            return false;
        }
        
        return super.onInterceptTouchEvent(e);
    }
    
    @Override
    public void smoothScrollBy(int dx, int dy, @Nullable Interpolator interpolator) {
        super.smoothScrollBy(dx, dy, new DecelerateInterpolator(1.5F));
    }
}
