package app.simple.felicity.decorations.pager

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView

/**
 * A concrete [FelicityPager.PageAdapter] that displays one full-screen image per page.
 *
 * Image loading and cancellation are fully delegated to the caller via two
 * dependency-injection interfaces so that this class (and the `decorations` module)
 * carries **zero** image-library dependencies:
 *
 *  • [ImageBitmapProvider]  — starts loading for a given position into an [ImageView].
 *  • [ImageBitmapCanceller] — cancels an in-flight or completed load on an [ImageView].
 *
 * In the `music` module these are typically thin lambdas backed by Glide:
 * ```kotlin
 * val adapter = ImagePageAdapter(
 *     count     = songs.size,
 *     provider  = { pos, iv -> Glide.with(iv).load(songs[pos].artworkUri).centerCrop().into(iv) },
 *     canceller = { iv -> Glide.with(iv).clear(iv) }
 * )
 * pager.setAdapter(adapter)
 * ```
 *
 * Override [getItemId] if your data source has stable IDs other than position.
 *
 * @param count      Number of pages; update via [updateCount] + [FelicityPager.notifyDataSetChanged].
 * @param provider   [ImageBitmapProvider] responsible for loading images into page views.
 * @param canceller  [ImageBitmapCanceller] responsible for cancelling loads on recycled views.
 * @param scaleType  [ImageView.ScaleType] applied to each page view. Defaults to CENTER_CROP.
 *
 * @see FelicityPager
 * @see ImageBitmapProvider
 * @see ImageBitmapCanceller
 */
open class ImagePageAdapter(
        private var count: Int,
        private val provider: ImageBitmapProvider,
        private val canceller: ImageBitmapCanceller,
        private val scaleType: ImageView.ScaleType = ImageView.ScaleType.CENTER_CROP
) : FelicityPager.PageAdapter {

    /** Update the total page count before calling [FelicityPager.notifyDataSetChanged]. */
    fun updateCount(newCount: Int) {
        count = newCount
    }

    override fun getCount(): Int = count

    // getItemId defaults to position — override in a subclass for stable IDs.

    override fun onCreateView(position: Int, parent: ViewGroup): View =
        ImageView(parent.context).apply {
            scaleType = this@ImagePageAdapter.scaleType
            // Fill the pager cell; the pager's onMeasure will enforce exact dimensions.
            layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

    override fun onBindView(position: Int, view: View) {
        provider.provide(position, view as ImageView)
    }

    override fun onRecycleView(position: Int, view: View) {
        val iv = view as ImageView
        canceller.cancel(iv)
        iv.setImageDrawable(null)   // clear stale image so recycled views don't flash old content
    }
}

