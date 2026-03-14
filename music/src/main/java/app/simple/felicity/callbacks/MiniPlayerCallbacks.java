package app.simple.felicity.callbacks;

import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

public interface MiniPlayerCallbacks {
    void onHideMiniPlayer();
    
    void onShowMiniPlayer();
    
    void onAttachMiniPlayer(RecyclerView recyclerView);
    
    void onDetachMiniPlayer(RecyclerView recyclerView);
    
    /**
     * Called when a fragment wants the mini player to track a {@link NestedScrollView}
     * for auto-hide-on-scroll behavior.
     *
     * @param scrollView The scroll view to attach to.
     */
    default void onAttachMiniPlayerScrollView(NestedScrollView scrollView) {
    }
    
    /**
     * Called when a fragment wants the mini player to stop tracking a previously
     * attached {@link NestedScrollView}.
     *
     * @param scrollView The scroll view to detach from.
     */
    default void onDetachMiniPlayerScrollView(NestedScrollView scrollView) {
    }

    default void onMakeTransparentMiniPlayer() {
    }
    
    default void onMakeOpaqueMiniPlayer() {
    }
}
