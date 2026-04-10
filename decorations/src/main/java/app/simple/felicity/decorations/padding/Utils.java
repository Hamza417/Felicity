package app.simple.felicity.decorations.padding;

import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class Utils {
    /**
     * Applies system bar padding to the given [viewGroup] once, using the layout-defined padding
     * values as the stable baseline. The insets listener captures the original padding before it
     * is ever modified so that each subsequent dispatch (e.g. when the IME opens or closes) always
     * computes the final padding as {@code baseline + currentInset} rather than accumulating on
     * top of a value that already contains a previously applied inset.
     *
     * @param viewGroup                 The view to which system-bar padding will be applied.
     * @param statusPaddingRequired     Whether the status-bar (top) inset should be consumed.
     * @param navigationPaddingRequired Whether the navigation-bar (bottom) inset should be consumed.
     */
    public static void applySystemBarPadding(ViewGroup viewGroup, boolean statusPaddingRequired, boolean navigationPaddingRequired) {
        // Snapshot the padding declared in XML before any inset is ever added.
        // Every future dispatch uses these values as the immutable baseline so the
        // padding never accumulates across multiple inset callbacks.
        final int baseLeft = viewGroup.getPaddingLeft();
        final int baseTop = viewGroup.getPaddingTop();
        final int baseRight = viewGroup.getPaddingRight();
        final int baseBottom = viewGroup.getPaddingBottom();
        
        ViewCompat.setOnApplyWindowInsetsListener(viewGroup, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            
            if (statusPaddingRequired && navigationPaddingRequired) {
                viewGroup.setPadding(
                        baseLeft,
                        baseTop + insets.top,
                        baseRight,
                        baseBottom + insets.bottom);
            } else if (statusPaddingRequired) {
                viewGroup.setPadding(
                        baseLeft,
                        baseTop + insets.top,
                        baseRight,
                        baseBottom);
            } else if (navigationPaddingRequired) {
                viewGroup.setPadding(
                        baseLeft,
                        baseTop,
                        baseRight,
                        baseBottom + insets.bottom);
            }
            
            // Return CONSUMED if you don't want the window insets to keep being
            // passed down to descendant views.
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
