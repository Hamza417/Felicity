package app.simple.felicity.decorations.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.GridLayoutAnimationController;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * RecyclerView subclass that applies a diagonal (anti-diagonal) staggered layout animation
 * for items in a {@link GridLayoutManager}. The effect is maintained consistently even
 * when the list is scrolled and new children are attached in the middle of the adapter.
 * <p>
 * Core idea:
 * <ul>
 *     <li>Android's layout animation for grids relies on {@link GridLayoutAnimationController.AnimationParameters}.</li>
 *     <li>We map each visible child to a synthetic "diagonal index" derived from its local
 *     row (relative to the first visible row) plus its column index.</li>
 *     <li>Normalizing the absolute adapter row to a local viewport row prevents large row
 *     values (after scrolling) from collapsing many items onto the same animation delay.</li>
 *     <li>All diagonals inside the viewport form a range: 0 .. (visibleRows + spanCount - 2).</li>
 * </ul>
 * Algorithm steps performed in {@link #attachLayoutAnimationParameters(View, ViewGroup.LayoutParams, int, int)}:
 * <ol>
 *     <li>Obtain spanCount, adapter position, span index.</li>
 *     <li>Compute absolute row via {@link GridLayoutManager.SpanSizeLookup#getSpanGroupIndex(int, int)}.</li>
 *     <li>Determine first/last visible adapter positions and their absolute rows.</li>
 *     <li>Derive baseRow = firstVisibleAbsoluteRow and visible row count.</li>
 *     <li>localRow = absoluteRow - baseRow (clamped &gt;= 0).</li>
 *     <li>Diagonal index diag = localRow + column.</li>
 *     <li>Clamp diag into the valid diagonal range (0 .. rowsVisible + spanCount - 2).</li>
 *     <li>Populate animation parameters so the controller delays by diag ordering.</li>
 * </ol>
 * Fallback heuristics are used during pre-layout / predictive layout when visible positions
 * may not yet be stable. Those heuristics approximate row counts from the total child count.
 *
 * @author Hamza417
 */
public class GridRecyclerView extends SpacingRecyclerView {
    
    public GridRecyclerView(Context context) {
        super(context);
        init();
    }
    
    public GridRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public GridRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }
    
    private void init() {
        // TODO - fix collision wth layout animation
        // setItemAnimator(new ElasticAnimator());
        
        // RecyclerViewUtils.INSTANCE.withDelayedAnimator(this, new ElasticAnimator());
    }
    
    /**
     * Ensures only a {@link GridLayoutManager} is accepted, because the diagonal animation
     * parameter computation depends on grid span information.
     */
    @Override
    public void setLayoutManager(LayoutManager layout) {
        if (!(layout instanceof GridLayoutManager)) {
            return;
        }
        super.setLayoutManager(layout);
    }
    
    /**
     * Assigns {@link GridLayoutAnimationController.AnimationParameters} so that each child
     * occupies a diagonal slot (anti-diagonal sweeping effect). The parameter 'row' is used
     * as the stagger index; 'columnsCount' is collapsed to 1 so only the computed row delay matters.
     *
     * @param child  The child view being processed.
     * @param params Child layout params (receives animation parameters instance).
     * @param index  Index within the current layout pass (unused beyond bookkeeping).
     * @param count  Total number of children in this layout pass.
     */
    @Override
    protected void attachLayoutAnimationParameters(@NonNull View child, @NonNull ViewGroup.LayoutParams params, int index, int count) {
        if (getAdapter() != null && getLayoutManager() instanceof GridLayoutManager layoutManager) {
            GridLayoutAnimationController.AnimationParameters animationParams =
                    (GridLayoutAnimationController.AnimationParameters) params.layoutAnimationParameters;
            if (animationParams == null) {
                animationParams = new GridLayoutAnimationController.AnimationParameters();
                params.layoutAnimationParameters = animationParams;
            }
            GridLayoutManager.LayoutParams lp = (GridLayoutManager.LayoutParams) child.getLayoutParams();
            int spanCount = layoutManager.getSpanCount();
            boolean isRtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            int adapterPosition = lp.getViewLayoutPosition();
            int absoluteRow = layoutManager.getSpanSizeLookup().getSpanGroupIndex(adapterPosition, spanCount);
            int column = lp.getSpanIndex();
            if (isRtl) {
                column = spanCount - 1 - column;
            }
            if (absoluteRow < 0) {
                absoluteRow = 0;
            }
            if (column < 0) {
                column = 0;
            }
            if (column >= spanCount) {
                column = spanCount - 1;
            }
            int firstVisible = layoutManager.findFirstVisibleItemPosition();
            int lastVisible = layoutManager.findLastVisibleItemPosition();
            int baseRow = 0;
            int rowsVisible;
            if (firstVisible != RecyclerView.NO_POSITION && lastVisible != RecyclerView.NO_POSITION) {
                try {
                    int firstRowAbs = layoutManager.getSpanSizeLookup().getSpanGroupIndex(firstVisible, spanCount);
                    int lastRowAbs = layoutManager.getSpanSizeLookup().getSpanGroupIndex(lastVisible, spanCount);
                    baseRow = firstRowAbs;
                    rowsVisible = Math.max(1, (lastRowAbs - firstRowAbs) + 1);
                } catch (Exception ignored) {
                    rowsVisible = (int) Math.ceil(count / (double) spanCount);
                    if (rowsVisible <= 0) {
                        rowsVisible = 1;
                    }
                }
            } else {
                rowsVisible = (int) Math.ceil(count / (double) spanCount);
                if (rowsVisible <= 0) {
                    rowsVisible = 1;
                }
            }
            int localRow = absoluteRow - baseRow;
            if (localRow < 0) {
                localRow = 0;
            }
            int diag = localRow + column;
            int maxDiag = rowsVisible + spanCount - 2;
            if (diag < 0) {
                diag = 0;
            }
            if (diag > maxDiag) {
                diag = maxDiag;
            }
            int totalDiagonals = maxDiag + 1;
            animationParams.count = count;
            animationParams.index = index;
            animationParams.columnsCount = 1;
            animationParams.rowsCount = totalDiagonals;
            animationParams.row = diag;
            animationParams.column = 0;
        } else {
            super.attachLayoutAnimationParameters(child, params, index, count);
        }
    }
}