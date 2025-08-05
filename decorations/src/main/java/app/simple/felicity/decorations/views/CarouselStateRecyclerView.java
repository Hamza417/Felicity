package app.simple.felicity.decorations.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.google.android.material.carousel.CarouselLayoutManager;

import androidx.recyclerview.widget.RecyclerView;
import app.simple.felicity.decorations.singletons.CarouselScrollStateStore;

public class CarouselStateRecyclerView extends RecyclerView {
    
    public static final String TAG = "CarouselStateRecyclerView";
    private String uniqueKey;
    private boolean hasRestored = false;
    
    public CarouselStateRecyclerView(Context context) {
        super(context);
    }
    
    public CarouselStateRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    public CarouselStateRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (!hasRestored) {
            restoreScrollPosition();
            addOnScrollListener(new OnScrollListener() {
                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    saveScrollPosition();
                }
            });
            hasRestored = true;
        }
    }
    
    public void setUniqueKey(String key) {
        this.uniqueKey = key;
    }
    
    private int getFirstVisibleItemPosition(CarouselLayoutManager layoutManager) {
        int childCount = layoutManager.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = layoutManager.getChildAt(i);
            if (child != null) {
                int pos = layoutManager.getPosition(child);
                if (pos != RecyclerView.NO_POSITION) {
                    return pos;
                }
            }
        }
        
        return RecyclerView.NO_POSITION;
    }
    
    private void saveScrollPosition() {
        LayoutManager lm = getLayoutManager();
        if (lm instanceof CarouselLayoutManager && uniqueKey != null) {
            int pos = getFirstVisibleItemPosition((CarouselLayoutManager) lm);
            CarouselScrollStateStore.INSTANCE.savePosition(uniqueKey, pos);
            Log.d(TAG, "Saved position: " + pos + " for key: " + uniqueKey);
        }
    }
    
    private void restoreScrollPosition() {
        LayoutManager lm = getLayoutManager();
        if (lm instanceof CarouselLayoutManager && uniqueKey != null) {
            int pos = CarouselScrollStateStore.INSTANCE.getPosition(uniqueKey);
            if (pos != RecyclerView.NO_POSITION) {
                Log.d(TAG, "Restored position: " + pos + " for key: " + uniqueKey);
                lm.scrollToPosition(pos);
            }
        }
    }
}