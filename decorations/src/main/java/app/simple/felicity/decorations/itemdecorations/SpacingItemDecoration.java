package app.simple.felicity.decorations.itemdecorations;

import android.graphics.Rect;
import android.view.View;

import org.jetbrains.annotations.NotNull;

import androidx.recyclerview.widget.RecyclerView;

public class SpacingItemDecoration extends RecyclerView.ItemDecoration {
    private final int spacing;
    private final boolean includeEdge;
    
    public SpacingItemDecoration(int spacing, boolean includeEdge) {
        this.spacing = spacing;
        this.includeEdge = includeEdge;
    }

    @Override
    public void getItemOffsets(@NotNull Rect outRect, @NotNull View view, RecyclerView parent, @NotNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        
        if (includeEdge) {
            outRect.left = spacing;
            outRect.right = spacing;
            outRect.top = position == 0 ? spacing : 0;
            outRect.bottom = spacing;
        } else {
            outRect.left = 0;
            outRect.right = 0;
            outRect.top = position == 0 ? 0 : spacing;
            outRect.bottom = 0;
        }
    }
}