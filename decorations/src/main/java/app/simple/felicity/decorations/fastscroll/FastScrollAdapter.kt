package app.simple.felicity.decorations.fastscroll

import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder

/**
 * Base adapter class that handles light bind mode for fast scrolling.
 * Extend this instead of RecyclerView.Adapter to get automatic fast scroll optimization.
 *
 * Subclasses should override [onBind] instead of [onBindViewHolder] to handle binding
 * with automatic light bind mode support.
 */
abstract class FastScrollAdapter<VH : VerticalListViewHolder> :
        RecyclerView.Adapter<VH>(), SlideFastScroller.FastScrollBindingController {

    /**
     * Current light bind mode state. When true, adapters should skip heavy operations
     * like image loading and only bind minimal UI (e.g., skeleton backgrounds).
     */
    var isLightBindMode: Boolean = false
        private set

    final override fun setLightBindMode(enabled: Boolean) {
        isLightBindMode = enabled
    }

    final override fun onBindViewHolder(holder: VH, position: Int) {
        onBind(holder, position, isLightBindMode)
    }

    /**
     * Override this to handle payloads. By default, delegates to [onBind].
     * Return true if payload was handled, false to fall through to [onBind].
     */
    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty() || !onBindPayload(holder, position, payloads)) {
            onBind(holder, position, isLightBindMode)
        }
    }

    final override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, isLightBind: Boolean) {
        isLightBindMode = isLightBind
        @Suppress("UNCHECKED_CAST")
        onBind(holder as VH, position, isLightBind)
    }

    final override fun shouldHandleCustomBinding(): Boolean = true

    /**
     * Bind data to the ViewHolder.
     *
     * @param holder The ViewHolder to bind
     * @param position The adapter position
     * @param isLightBind If true, skip heavy operations (image loading, complex layouts).
     *                    Use [setSkeletonBackground] for light binds and [clearSkeletonBackground] + full bind for normal binds.
     */
    abstract fun onBind(holder: VH, position: Int, isLightBind: Boolean)

    /**
     * Handle payload-based partial updates.
     * Override this to handle specific payloads for incremental updates.
     *
     * @param holder The ViewHolder to update
     * @param position The adapter position
     * @param payloads The list of payloads
     * @return true if payloads were handled, false to fall through to full [onBind]
     */
    open fun onBindPayload(holder: VH, position: Int, payloads: MutableList<Any>): Boolean = false
}
