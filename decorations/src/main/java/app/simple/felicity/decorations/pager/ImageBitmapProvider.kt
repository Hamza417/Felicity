package app.simple.felicity.decorations.pager

import android.graphics.Bitmap
import android.widget.ImageView

/**
 * Called by [ImagePageAdapter] when a page becomes visible and needs its image loaded.
 *
 * The implementation (typically in the `music` module using Glide) must:
 *  1. Start the async image load for [position].
 *  2. Deliver the result by calling [ImageView.setImageBitmap] (or any other
 *     Glide/Picasso target mechanism) on [target] when ready.
 *
 * The provider is responsible for caching, down-sampling, placeholder display, etc.
 * [ImagePageAdapter] never calls [Bitmap] methods directly — it only passes the
 * [ImageView] target to the provider and trusts it to fill or clear the image.
 *
 * @see ImageBitmapCanceller
 * @see ImagePageAdapter
 */
fun interface ImageBitmapProvider {
    /**
     * Load the image for [position] into [target].
     *
     * @param position  Adapter position whose image should be loaded.
     * @param target    The [ImageView] that should receive the loaded image.
     */
    fun provide(position: Int, target: ImageView)
}

