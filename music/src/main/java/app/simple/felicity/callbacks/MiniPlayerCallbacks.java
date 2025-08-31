package app.simple.felicity.callbacks;

import androidx.recyclerview.widget.RecyclerView;

public interface MiniPlayerCallbacks {
    void onHideMiniPlayer();
    
    void onShowMiniPlayer();
    
    void onAttachMiniPlayer(RecyclerView recyclerView);
    
    void onDetachMiniPlayer(RecyclerView recyclerView);
}
