package app.simple.felicity.extensions.fragments

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.decorations.views.SpacingRecyclerView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Intermediate base fragment that sits between [PanelFragment] and every concrete list-based
 * panel. It centralizes boilerplate that every panel otherwise repeats:
 *
 * - [GridLayoutManager] lifecycle management (creation, span-count updates, and cleanup)
 * - Lifecycle-aware [Flow] collection via [collectWhenStarted] / [collectListWhenStarted]
 * - Grid-size preference updates via [applyGridSizeUpdate]
 *
 * Concrete panels should extend this class instead of [PanelFragment] directly.
 *
 * @author Hamza417
 */
abstract class BasePanelFragment : PanelFragment() {

    /**
     * The [GridLayoutManager] used by the concrete panel's [RecyclerView].
     * Initialized via [RecyclerView.setupGridLayoutManager] and automatically
     * nulled in [onDestroyView].
     */
    protected var gridLayoutManager: GridLayoutManager? = null

    override fun onDestroyView() {
        gridLayoutManager = null
        super.onDestroyView()
    }

    /**
     * Creates a [GridLayoutManager] with the given [spanCount], assigns it as this
     * [RecyclerView]'s layout manager, and stores the reference in [gridLayoutManager].
     *
     * @param spanCount The initial number of columns.
     * @return The newly created [GridLayoutManager].
     */
    protected fun RecyclerView.setupGridLayoutManager(spanCount: Int): GridLayoutManager {
        return GridLayoutManager(requireContext(), spanCount).also { manager ->
            layoutManager = manager
            gridLayoutManager = manager
        }
    }

    /**
     * Collects a [Flow] in a lifecycle-aware manner, resuming from [Lifecycle.State.STARTED]
     * and suspending when the lifecycle drops below that state. All emitted values are
     * forwarded to [onEach] unconditionally.
     *
     * @param T The emitted type.
     * @param onEach Called with each emitted value.
     */
    protected fun <T> Flow<T>.collectWhenStarted(onEach: (T) -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                collect { value -> onEach(value) }
            }
        }
    }

    /**
     * Collects a [Flow] of lists in a lifecycle-aware manner, resuming from
     * [Lifecycle.State.STARTED]. [onEach] is only invoked when the emitted list
     * is non-empty, or when [hasActiveAdapter] returns `true` (meaning an adapter
     * is already initialized and may need to handle an empty-state update, such as
     * after a deletion).
     *
     * @param T The list item type.
     * @param hasActiveAdapter Returns `true` if an adapter is already initialized in the panel.
     * @param onEach Called with each qualifying list emission.
     */
    protected fun <T> Flow<List<T>>.collectListWhenStarted(
            hasActiveAdapter: () -> Boolean,
            onEach: (List<T>) -> Unit
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                collect { items ->
                    if (items.isNotEmpty() || hasActiveAdapter()) {
                        onEach(items)
                    }
                }
            }
        }
    }

    /**
     * Updates [gridLayoutManager]'s span count, triggers an animated layout transition on
     * [recyclerView], and notifies the adapter of a full-range item change so every visible
     * item is re-measured and re-drawn at the new column width.
     *
     * @param recyclerView The panel's [SpacingRecyclerView].
     * @param newSpanCount The new number of columns to apply.
     */
    protected fun applyGridSizeUpdate(recyclerView: SpacingRecyclerView, newSpanCount: Int) {
        gridLayoutManager?.spanCount = newSpanCount
        recyclerView.beginDelayedTransition()
        recyclerView.adapter?.notifyItemRangeChanged(0, recyclerView.adapter?.itemCount ?: 0)
    }
}

