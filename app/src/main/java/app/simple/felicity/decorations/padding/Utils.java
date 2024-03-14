package app.simple.felicity.decorations.padding;

import android.util.Log;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class Utils {
    public static void applySystemBarPadding(ViewGroup viewGroup, boolean statusPaddingRequired, boolean navigationPaddingRequired) {
        ViewCompat.setOnApplyWindowInsetsListener(viewGroup, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Apply the insets as a margin to the view. Here the system is setting
            // only the bottom, left, and right dimensions, but apply whichever insets are
            // appropriate to your layout. You can also update the view padding
            // if that's more appropriate.
            if (statusPaddingRequired && navigationPaddingRequired) {
                viewGroup.setPadding(viewGroup.getPaddingLeft(),
                        viewGroup.getPaddingTop() + insets.top,
                        viewGroup.getPaddingRight(),
                        viewGroup.getPaddingBottom() + insets.bottom);
            } else if (statusPaddingRequired) {
                viewGroup.setPadding(viewGroup.getPaddingLeft(),
                        viewGroup.getPaddingTop() + insets.top,
                        viewGroup.getPaddingRight(),
                        viewGroup.getPaddingBottom());
            } else if (navigationPaddingRequired) {
                viewGroup.setPadding(viewGroup.getPaddingLeft(),
                        viewGroup.getPaddingTop(),
                        viewGroup.getPaddingRight(),
                        viewGroup.getPaddingBottom() + insets.bottom);
            }
            
            Log.d("Padding", "Padding: " + insets);
            
            // Return CONSUMED if you don't want want the window insets to keep being
            // passed down to descendant views.
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
