package app.simple.felicity.decorations.inset;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

public class InsetRecyclerView extends RecyclerView {
    public InsetRecyclerView(@NonNull Context context) {
        super(context);
    }
    
    public InsetRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }
    
    public InsetRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
    
    protected void applySystemBarPadding(boolean statusPaddingRequired, boolean navigationPaddingRequired) {
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            
            if (statusPaddingRequired && navigationPaddingRequired) {
                setPadding(getPaddingLeft(),
                        getPaddingTop() + insets.top,
                        getPaddingRight(),
                        getPaddingBottom() + insets.bottom);
            } else if (statusPaddingRequired) {
                setPadding(getPaddingLeft(),
                        getPaddingTop() + insets.top,
                        getPaddingRight(),
                        getPaddingBottom());
            } else if (navigationPaddingRequired) {
                setPadding(getPaddingLeft(),
                        getPaddingTop(),
                        getPaddingRight(),
                        getPaddingBottom() + insets.bottom);
            }
            
            Log.d("Padding", "Padding: " + insets);
            
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
