package app.simple.felicity.decorations.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.GridLayoutAnimationController;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;

public class GridRecyclerView extends SpacingRecyclerView {
    
    public GridRecyclerView(Context context) {
        super(context);
    }
    
    public GridRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    public GridRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    
    @Override
    public void setLayoutManager(LayoutManager layout) {
        if (!(layout instanceof GridLayoutManager)) {
            // Ignore non-grid managers to keep animation math valid.
            return;
        }
        super.setLayoutManager(layout);
    }
    
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
            
            // Compute the visual row and column for this child robustly, accounting for span indices and RTL.
            int adapterPosition = lp.getViewLayoutPosition();
            int row = layoutManager.getSpanSizeLookup().getSpanGroupIndex(adapterPosition, spanCount);
            int column = lp.getSpanIndex();
            if (isRtl) {
                column = spanCount - 1 - column;
            }
            if (row < 0) {
                row = 0;
            }
            if (column < 0) {
                column = 0;
            }
            if (column >= spanCount) {
                column = spanCount - 1;
            }
            
            // Current visible rows in this layout pass
            int rowsVisible = (int) Math.ceil(count / (double) spanCount);
            if (rowsVisible <= 0) {
                rowsVisible = 1;
            }
            
            // Diagonal index: items with same (row+column) belong to the same anti-diagonal
            int diag = row + column;
            int maxDiag = rowsVisible + spanCount - 2; // last diagonal index
            if (diag < 0) {
                diag = 0;
            }
            if (diag > maxDiag) {
                diag = maxDiag;
            }
            
            // Make every item in the same diagonal share the same start offset by setting column=0.
            // Use rowsCount as the total number of diagonals and columnsCount=1 so only the row controls delay.
            int totalDiagonals = maxDiag + 1; // rowsVisible + spanCount - 1
            
            animationParams.count = count;
            animationParams.index = index; // not used by GridLayoutAnimationController for grid, but set anyway
            animationParams.columnsCount = 1;
            animationParams.rowsCount = totalDiagonals;
            animationParams.row = diag;
            animationParams.column = 0;
        } else {
            super.attachLayoutAnimationParameters(child, params, index, count);
        }
    }
}