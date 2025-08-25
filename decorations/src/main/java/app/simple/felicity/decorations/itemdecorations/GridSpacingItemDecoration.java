package app.simple.felicity.decorations.itemdecorations;

import android.graphics.Rect;
import android.view.View;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

import androidx.core.text.TextUtilsCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

public class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
    private final int spanCount;
    private final int horizontalSpacing;
    private final int verticalSpacing;
    private final boolean includeEdge;
    private final int headerNum;
    private final boolean isRtl = TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault()) == ViewCompat.LAYOUT_DIRECTION_RTL;
    
    public GridSpacingItemDecoration(int spanCount, int horizontalSpacing, int verticalSpacing, boolean includeEdge, int headerNum) {
        this.spanCount = spanCount;
        this.horizontalSpacing = horizontalSpacing;
        this.verticalSpacing = verticalSpacing;
        this.includeEdge = includeEdge;
        this.headerNum = headerNum;
    }

    @Override
    public void getItemOffsets(@NotNull Rect outRect, @NotNull View view, RecyclerView parent, @NotNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view) - headerNum;
        if (position >= 0) {
            int column = position % spanCount;
            if (isRtl) {
                column = spanCount - 1 - column;
            }
            if (includeEdge) {
                outRect.left = horizontalSpacing - column * horizontalSpacing / spanCount;
                outRect.right = (column + 1) * horizontalSpacing / spanCount;
                
                if (position < spanCount) {
                    outRect.top = verticalSpacing;
                }
                outRect.bottom = verticalSpacing;
            } else {
                outRect.left = column * horizontalSpacing / spanCount;
                outRect.right = horizontalSpacing - (column + 1) * horizontalSpacing / spanCount;
                if (position >= spanCount) {
                    outRect.top = verticalSpacing;
                }
            }
        } else {
            outRect.left = 0;
            outRect.right = 0;
            outRect.top = 0;
            outRect.bottom = 0;
        }
    }
}
