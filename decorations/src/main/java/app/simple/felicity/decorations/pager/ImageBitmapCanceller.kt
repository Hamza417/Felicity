package app.simple.felicity.decorations.pager

import android.widget.ImageView

/**
 * Called by [ImagePageAdapter] when a page leaves the visible window and its
 * in-flight image load should be cancelled to avoid unnecessary work and
 * memory pressure.
 *
 * The implementation (typically in the `music` module using Glide) should call
 * `Glide.with(context).clear(target)` or the equivalent for whichever loading
 * library is in use.  After cancellation [target]'s drawable is left as-is;
 * [ImagePageAdapter] will clear it separately before the view is recycled.
 *
 * @see ImageBitmapProvider
 * @see ImagePageAdapter
 */
fun interface ImageBitmapCanceller {
    /**
     * Cancel any pending or in-progress image load that was previously started
     * for [target] via [ImageBitmapProvider.provide].
     *
     * @param target  The [ImageView] whose load should be cancelled.
     */
    fun cancel(target: ImageView)
}

