package app.simple.felicity.decorations.pinch;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.OnScaleGestureListener;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.simple.felicity.decorations.overscroll.CustomVerticalRecyclerView;

public class ZoomingRecyclerView extends CustomVerticalRecyclerView {
    
    private final ScaleGestureDetector scaleGestureDetector;
    private OnScaleGestureListener onScaleGestureListener;
    
    public ZoomingRecyclerView(Context context) {
        this(context, null, 0);
    }
    
    public ZoomingRecyclerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public ZoomingRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        scaleGestureDetector = new ScaleGestureDetector(context, new OnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                if (isScaleListenerSet()) {
                    return onScaleGestureListener.onScale(detector);
                }
                return false;
            }
            
            @Override
            public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
                if (isScaleListenerSet()) {
                    return onScaleGestureListener.onScaleBegin(detector);
                }
                return false;
            }
            
            @Override
            public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
                if (isScaleListenerSet()) {
                    onScaleGestureListener.onScaleEnd(detector);
                }
            }
            
            private boolean isScaleListenerSet() {
                return onScaleGestureListener != null;
            }
        });
        
        scaleGestureDetector.setQuickScaleEnabled(true);
    }
    
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        ItemAnimator ia = getItemAnimator();
        boolean handled = scaleGestureDetector.onTouchEvent(ev);
        if (ia == null || !ia.isRunning()) {
            handled |= super.dispatchTouchEvent(ev);
        }
        return handled;
        
    }
    
    @Override
    public void setItemAnimator(ItemAnimator animator) {
        super.setItemAnimator(animator);
    }
    
    public void setOnScaleGestureListener(OnScaleGestureListener onScaleGestureListener) {
        this.onScaleGestureListener = onScaleGestureListener;
    }
}
