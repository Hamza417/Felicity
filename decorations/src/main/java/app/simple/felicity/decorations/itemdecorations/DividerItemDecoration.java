package app.simple.felicity.decorations.itemdecorations;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * DividerItemDecoration is a {@link RecyclerView.ItemDecoration} that can be used as a divider
 * between items of a {@link LinearLayoutManager}. It supports both {@link #HORIZONTAL} and
 * {@link #VERTICAL} orientations.
 *
 * <pre>
 *     mDividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(),
 *             mLayoutManager.getOrientation());
 *     recyclerView.addItemDecoration(mDividerItemDecoration);
 * </pre>
 */
public class DividerItemDecoration extends RecyclerView.ItemDecoration {
    public static final int HORIZONTAL = LinearLayout.HORIZONTAL;
    public static final int VERTICAL = LinearLayout.VERTICAL;
    
    private static final String TAG = "DividerItem";
    private static final int[] ATTRS = new int[] {android.R.attr.listDivider};
    private final Rect bounds = new Rect();
    private Drawable dividerDrawable;
    /**
     * Current orientation. Either {@link #HORIZONTAL} or {@link #VERTICAL}.
     */
    private int orientation;
    
    /**
     * Creates a divider {@link RecyclerView.ItemDecoration} that can be used with a
     * {@link LinearLayoutManager}.
     *
     * @param context     Current context, it will be used to access resources.
     * @param orientation Divider orientation. Should be {@link #HORIZONTAL} or {@link #VERTICAL}.
     */
    public DividerItemDecoration(Context context, int orientation) {
        final TypedArray a = context.obtainStyledAttributes(ATTRS);
        dividerDrawable = a.getDrawable(0);
        if (dividerDrawable == null) {
            Log.w(TAG, "@android:attr/listDivider was not set in the theme used for this "
                    + "DividerItemDecoration. Please set that attribute all call setDrawable()");
        }
        a.recycle();
        setOrientation(orientation);
    }
    
    /**
     * Sets the orientation for this divider. This should be called if
     * {@link RecyclerView.LayoutManager} changes orientation.
     *
     * @param orientation {@link #HORIZONTAL} or {@link #VERTICAL}
     */
    public void setOrientation(int orientation) {
        if (orientation != HORIZONTAL && orientation != VERTICAL) {
            throw new IllegalArgumentException(
                    "Invalid orientation. It should be either HORIZONTAL or VERTICAL");
        }
        this.orientation = orientation;
    }
    
    /**
     * @return the {@link Drawable} for this divider.
     */
    @Nullable
    public Drawable getDrawable() {
        return dividerDrawable;
    }
    
    /**
     * Sets the {@link Drawable} for this divider.
     *
     * @param drawable Drawable that should be used as a divider.
     */
    public void setDrawable(@NonNull Drawable drawable) {
        dividerDrawable = drawable;
    }
    
    @Override
    public void onDraw(@NonNull Canvas c, RecyclerView parent, @NonNull RecyclerView.State state) {
        if (parent.getLayoutManager() == null || dividerDrawable == null) {
            return;
        }
        if (orientation == VERTICAL) {
            drawVertical(c, parent);
        } else {
            drawHorizontal(c, parent);
        }
    }
    
    private void drawVertical(Canvas canvas, RecyclerView parent) {
        canvas.save();
        final int left;
        final int right;
        //noinspection AndroidLintNewApi - NewApi lint fails to handle overrides.
        if (parent.getClipToPadding()) {
            left = parent.getPaddingLeft();
            right = parent.getWidth() - parent.getPaddingRight();
            canvas.clipRect(left, parent.getPaddingTop(), right,
                    parent.getHeight() - parent.getPaddingBottom());
        } else {
            left = 0;
            right = parent.getWidth();
        }
        
        final int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = parent.getChildAt(i);
            parent.getDecoratedBoundsWithMargins(child, bounds);
            
            /*
             * Instead of gluing the divider to the very bottom of the decorated bounds
             * (which makes it collide with the top of the next item when spacing decorations
             * are also present), we figure out the gap between where the item's content
             * actually ends and where the decorated area ends, then place the divider
             * right in the center of that gap — like a nice little peace treaty between items.
             */
            final int translationY = Math.round(child.getTranslationY());
            final int itemContentBottom = child.getBottom() + translationY;
            final int decoratedBottom = bounds.bottom + translationY;
            final int gap = decoratedBottom - itemContentBottom;
            final int dividerCenter = itemContentBottom + gap / 2;
            final int dividerHalfHeight = dividerDrawable.getIntrinsicHeight() / 2;
            final int top = dividerCenter - dividerHalfHeight;
            final int bottom = top + dividerDrawable.getIntrinsicHeight();

            dividerDrawable.setBounds(left, top, right, bottom);
            dividerDrawable.draw(canvas);
        }
        canvas.restore();
    }
    
    private void drawHorizontal(Canvas canvas, RecyclerView parent) {
        canvas.save();
        final int top;
        final int bottom;
        //noinspection AndroidLintNewApi - NewApi lint fails to handle overrides.
        if (parent.getClipToPadding()) {
            top = parent.getPaddingTop();
            bottom = parent.getHeight() - parent.getPaddingBottom();
            canvas.clipRect(parent.getPaddingLeft(), top,
                    parent.getWidth() - parent.getPaddingRight(), bottom);
        } else {
            top = 0;
            bottom = parent.getHeight();
        }
        
        final int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = parent.getChildAt(i);
            assert parent.getLayoutManager() != null;
            parent.getLayoutManager().getDecoratedBoundsWithMargins(child, bounds);
            final int right = bounds.right + Math.round(child.getTranslationX());
            final int left = right - dividerDrawable.getIntrinsicWidth();
            dividerDrawable.setBounds(left, top, right, bottom);
            dividerDrawable.draw(canvas);
        }
        canvas.restore();
    }
    
    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (dividerDrawable == null) {
            outRect.set(0, 0, 0, 0);
            return;
        }
        if (orientation == VERTICAL) {
            outRect.set(0, 0, 0, dividerDrawable.getIntrinsicHeight());
        } else {
            outRect.set(0, 0, dividerDrawable.getIntrinsicWidth(), 0);
        }
    }
}
