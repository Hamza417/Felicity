package app.simple.felicity.decorations.pinch;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.annotation.SuppressLint;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A custom {@link RecyclerView.ItemAnimator} that handles pinch-to-zoom gestures
 * to change the span count of a {@link GridLayoutManager} in real-time.
 *
 * <p>During the pre-threshold phase all visible items scale visually via
 * {@code setScaleX}/{@code setScaleY} to provide continuous finger-tracking
 * feedback. Once the cumulative scale product crosses {@link #SCALE_THRESHOLD},
 * the span count changes by one step and items interpolate to their new layout
 * positions in real-time as the gesture continues. Only one span change is
 * permitted per gesture to prevent accidental multi-step jumps.</p>
 *
 * @author Hamza417
 */
public class ZoomItemAnimator extends RecyclerView.ItemAnimator
        implements ScaleGestureDetector.OnScaleGestureListener {
    
    /**
     * Required cumulative scale change (30 %) before a span change is triggered.
     * A larger value requires a more deliberate pinch, reducing accidental triggers.
     */
    private static final float SCALE_THRESHOLD = 0.30f;
    
    /**
     * Additional scale range beyond the threshold that is mapped to the
     * item-interpolation progress (0–1). Items reach their final layout
     * positions once the fingers travel this extra distance.
     */
    private static final float PROGRESS_RANGE = 0.25f;
    
    /**
     * Maximum number of spans the layout manager is allowed to reach.
     */
    private static final int MAX_SPAN_COUNT = 8;
    
    private final ArrayList <AnimatedItem> animatedSet = new ArrayList <>();
    
    private GridLayoutManager layoutManager;
    private ZoomingRecyclerView recyclerView;
    private AnimatorSet finishAnimator;
    
    /**
     * Product of all incremental scale factors received since the gesture started.
     */
    private float cumulativeScale = 1.0f;
    
    /**
     * Prevents more than one span change from occurring within a single gesture.
     */
    private boolean spanChangedInGesture = false;
    
    /**
     * Direction of the gesture that triggered the span change.
     * {@code true} means pinch-out (span decreases); {@code false} means pinch-in (span increases).
     */
    private boolean pinchOut = false;
    
    private boolean running = false;
    
    /**
     * Attaches this animator to the given {@link ZoomingRecyclerView}.
     * Must be called after a {@link GridLayoutManager} has already been set on the view.
     *
     * @param recyclerView the target recycler view
     */
    public void setup(ZoomingRecyclerView recyclerView) {
        this.recyclerView = recyclerView;
        this.layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
        recyclerView.setOnScaleGestureListener(this);
        recyclerView.setItemAnimator(this);
    }
    
    @Override
    public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
        cumulativeScale = 1.0f;
        spanChangedInGesture = false;
        animatedSet.clear();
        if (finishAnimator != null && finishAnimator.isRunning()) {
            finishAnimator.cancel();
            finishAnimator = null;
        }
        return true;
    }
    
    @Override
    public boolean onScale(@NonNull ScaleGestureDetector detector) {
        cumulativeScale *= detector.getScaleFactor();
        
        if (!spanChangedInGesture) {
            // Pre-threshold phase: scale items visually to track the fingers in real-time.
            applyVisualScale(cumulativeScale);
            
            if (cumulativeScale > 1.0f + SCALE_THRESHOLD) {
                // Pinch-out: fingers spreading apart, items should grow → fewer columns.
                pinchOut = true;
                if (decrementSpanCount()) {
                    spanChangedInGesture = true;
                    resetVisualScale();
                }
            } else if (cumulativeScale < 1.0f - SCALE_THRESHOLD) {
                // Pinch-in: fingers coming together, items should shrink → more columns.
                pinchOut = false;
                incrementSpanCount();
                spanChangedInGesture = true;
                resetVisualScale();
            }
        } else {
            // Post-threshold phase: map continued finger movement to 0–1 progress and
            // interpolate each item between its pre-change and post-change positions.
            float thresholdScale = pinchOut
                    ? (1.0f + SCALE_THRESHOLD)
                    : (1.0f - SCALE_THRESHOLD);
            float raw = pinchOut
                    ? (cumulativeScale - thresholdScale)
                    : (thresholdScale - cumulativeScale);
            float progress = Math.max(0.0f, Math.min(1.0f, raw / PROGRESS_RANGE));
            interpolateItems(progress);
        }
        
        return true;
    }
    
    @Override
    public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
        running = false;
        resetVisualScale();
        if (!animatedSet.isEmpty()) {
            finishAnimation(new ArrayList <>(animatedSet));
        }
        animatedSet.clear();
        cumulativeScale = 1.0f;
        spanChangedInGesture = false;
    }
    
    @Override
    public boolean animateDisappearance(@NonNull RecyclerView.ViewHolder viewHolder,
            @NonNull ItemHolderInfo preLayoutInfo, @Nullable ItemHolderInfo postLayoutInfo) {
        animatedSet.add(buildItem(viewHolder, preLayoutInfo, postLayoutInfo,
                AnimatedItem.Type.DISAPPEARANCE));
        return false;
    }
    
    @Override
    public boolean animateAppearance(@NonNull RecyclerView.ViewHolder viewHolder,
            @Nullable ItemHolderInfo preLayoutInfo, @NonNull ItemHolderInfo postLayoutInfo) {
        animatedSet.add(buildItem(viewHolder, preLayoutInfo, postLayoutInfo,
                AnimatedItem.Type.APPEARANCE));
        return false;
    }
    
    @Override
    public boolean animatePersistence(@NonNull RecyclerView.ViewHolder viewHolder,
            @NonNull ItemHolderInfo preLayoutInfo, @NonNull ItemHolderInfo postLayoutInfo) {
        animatedSet.add(buildItem(viewHolder, preLayoutInfo, postLayoutInfo,
                AnimatedItem.Type.PERSISTENCE));
        return false;
    }
    
    @Override
    public boolean animateChange(@NonNull RecyclerView.ViewHolder oldHolder,
            @NonNull RecyclerView.ViewHolder newHolder,
            @NonNull ItemHolderInfo preLayoutInfo, @NonNull ItemHolderInfo postLayoutInfo) {
        animatedSet.add(buildItem(newHolder, preLayoutInfo, postLayoutInfo,
                AnimatedItem.Type.CHANGE));
        return false;
    }
    
    @Override
    public void runPendingAnimations() {
    }
    
    @Override
    public void endAnimation(@NonNull RecyclerView.ViewHolder item) {
    }
    
    @Override
    public void endAnimations() {
    }
    
    @Override
    public boolean isRunning() {
        return running || (finishAnimator != null && finishAnimator.isRunning());
    }
    
    /**
     * Applies a uniform visual scale to every child of the recycler view.
     * Used during the pre-threshold phase so items track the fingers in real-time.
     *
     * @param scale the desired scale value, clamped to [0.5, 2.0]
     */
    private void applyVisualScale(float scale) {
        float clamped = Math.max(0.5f, Math.min(2.0f, scale));
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            if (child != null) {
                child.setScaleX(clamped);
                child.setScaleY(clamped);
            }
        }
    }
    
    /**
     * Resets the visual scale of every child of the recycler view back to 1.0.
     * Called when the span count actually changes or when the gesture ends.
     */
    private void resetVisualScale() {
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            if (child != null) {
                child.setScaleX(1.0f);
                child.setScaleY(1.0f);
            }
        }
    }
    
    /**
     * Moves each animated item to the position linearly interpolated between
     * its pre-change bounds and its post-change bounds.
     *
     * @param progress interpolation factor in [0, 1]
     */
    private void interpolateItems(float progress) {
        for (AnimatedItem ai : animatedSet) {
            View view = ai.getViewHolder().itemView;
            view.setTop(lerp(ai.getPreRect().top, ai.getPostRect().top, progress));
            view.setLeft(lerp(ai.getPreRect().left, ai.getPostRect().left, progress));
            view.setBottom(lerp(ai.getPreRect().bottom, ai.getPostRect().bottom, progress));
            view.setRight(lerp(ai.getPreRect().right, ai.getPostRect().right, progress));
        }
    }
    
    /**
     * Integer linear interpolation helper.
     *
     * @param start start value
     * @param end   end value
     * @param t     interpolation factor in [0, 1]
     * @return the interpolated integer value
     */
    private int lerp(int start, int end, float t) {
        return start + (int) (t * (end - start));
    }
    
    /**
     * Animates all items in the provided list to their final post-change bounds
     * and dispatches {@link #dispatchAnimationFinished(RecyclerView.ViewHolder)} for each
     * once the animation completes.
     *
     * @param items snapshot of animated items to bring to their post-change positions
     */
    private void finishAnimation(final ArrayList <AnimatedItem> items) {
        finishAnimator = new AnimatorSet();
        finishAnimator.setDuration(280);
        finishAnimator.setInterpolator(new DecelerateInterpolator());
        finishAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                running = false;
                finishAnimator = null;
                for (AnimatedItem ai : items) {
                    dispatchAnimationFinished(ai.getViewHolder());
                }
            }
        });
        
        List <Animator> animators = new ArrayList <>();
        for (AnimatedItem ai : items) {
            animators.add(ObjectAnimator.ofPropertyValuesHolder(
                    ai.getViewHolder().itemView,
                    PropertyValuesHolder.ofInt("top", ai.getPostRect().top),
                    PropertyValuesHolder.ofInt("left", ai.getPostRect().left),
                    PropertyValuesHolder.ofInt("bottom", ai.getPostRect().bottom),
                    PropertyValuesHolder.ofInt("right", ai.getPostRect().right)));
        }
        
        if (!animators.isEmpty()) {
            finishAnimator.playTogether(animators);
            finishAnimator.start();
        }
    }
    
    /**
     * Decrements the span count by one if the current count is greater than 1.
     *
     * @return {@code true} if the span count was actually changed
     */
    private boolean decrementSpanCount() {
        int span = layoutManager.getSpanCount();
        if (span > 1) {
            layoutManager.setSpanCount(span - 1);
            notifyDataSetChanged();
            running = true;
            return true;
        }
        return false;
    }
    
    /**
     * Increments the span count by one up to {@link #MAX_SPAN_COUNT}.
     */
    private void incrementSpanCount() {
        int span = layoutManager.getSpanCount();
        if (span < MAX_SPAN_COUNT) {
            layoutManager.setSpanCount(span + 1);
            notifyDataSetChanged();
            running = true;
        }
    }
    
    @SuppressLint ("NotifyDataSetChanged")
    private void notifyDataSetChanged() {
        if (recyclerView.getAdapter() != null) {
            recyclerView.getAdapter().notifyDataSetChanged();
        }
    }
    
    /**
     * Convenience method to construct an {@link AnimatedItem} from the data
     * provided by the {@link RecyclerView.ItemAnimator} callbacks.
     */
    private AnimatedItem buildItem(RecyclerView.ViewHolder holder,
            ItemHolderInfo pre, ItemHolderInfo post, AnimatedItem.Type type) {
        return new AnimatedItem.Builder()
                .setViewHolder(holder)
                .setPreRect(pre)
                .setPostRect(post)
                .setType(type)
                .build();
    }
    
}
