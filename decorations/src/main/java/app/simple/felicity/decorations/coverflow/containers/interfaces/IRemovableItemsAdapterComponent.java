package app.simple.felicity.decorations.coverflow.containers.interfaces;

import android.view.View;

public interface IRemovableItemsAdapterComponent {
    /**
     * Called when item is removed from component by user clicking on remove button
     *
     * @return true, if you removed item from adapter manually in this step
     */
    boolean onItemRemove(int position, View view, Object item);
}
