package app.simple.felicity.adapters.home.main

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.AdapterHomeArtflowBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.pager.FelicityPager
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCover
import app.simple.felicity.models.ArtFlowData
import com.bumptech.glide.Glide

/**
 * Top-level [RecyclerView.Adapter] for the ArtFlow home screen.
 *
 * <p>Each row renders an [ArtFlowData] section as a [app.simple.felicity.decorations.pager.FelicitySlider]
 * backed by the private [SliderAdapter] inner class. The auto-slide is started immediately after
 * the adapter is attached and paused when the view is recycled.</p>
 *
 * @author Hamza417
 */
class AdapterArtFlowHome(private var data: List<ArtFlowData<Any>>) : RecyclerView.Adapter<VerticalListViewHolder>() {

    private var adapterArtFlowHomeCallbacks: AdapterArtFlowHomeCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return Holder(AdapterHomeArtflowBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: VerticalListViewHolder, @SuppressLint("RecyclerView") position: Int) {
        if (holder is Holder) {
            val item = data[position]
            val sliderAdapter = SliderAdapter(item)

            holder.binding.title.text = holder.binding.title.context.getString(item.title)
            holder.binding.felicitySlider.setAdapter(sliderAdapter)
            holder.binding.felicitySlider.start()

            sliderAdapter.setOnItemClickListener { itemPosition, imageView ->
                item.position = itemPosition
                adapterArtFlowHomeCallbacks?.onItemClicked(imageView, position, itemPosition)
            }

            sliderAdapter.setOnItemLongClickListener { itemPosition, imageView ->
                item.position = itemPosition
                adapterArtFlowHomeCallbacks?.onItemLongClicked(imageView, position, itemPosition)
            }

            if (item.position >= 0) {
                holder.binding.felicitySlider.setCurrentItem(item.position, smoothScroll = false)
                holder.binding.container.transitionName = item.items[item.position].toString()
            }

            holder.binding.title.setOnClickListener {
                adapterArtFlowHomeCallbacks?.onPanelItemClicked(item.title, it)
            }

            holder.binding.container.setOnClickListener {
                item.position = holder.binding.felicitySlider.getCurrentItem()
                adapterArtFlowHomeCallbacks?.onClicked(holder.binding.container, position)
            }
        }
    }

    override fun onViewRecycled(holder: VerticalListViewHolder) {
        super.onViewRecycled(holder)
        if (holder is Holder) {
            holder.binding.felicitySlider.stop()
        }
    }

    inner class Holder(val binding: AdapterHomeArtflowBinding) : VerticalListViewHolder(binding.root)

    /**
     * Drives the [app.simple.felicity.decorations.pager.FelicitySlider] for a single
     * [ArtFlowData] section row. Loads artwork via Glide and surfaces per-slide click and
     * long-click events via optional lambdas.
     *
     * @param data The section whose items are displayed as slides.
     */
    private inner class SliderAdapter(private val data: ArtFlowData<Any>) : FelicityPager.PageAdapter {

        private var onItemClick: ((position: Int, imageView: ImageView) -> Unit)? = null
        private var onItemLongClick: ((position: Int, imageView: ImageView) -> Unit)? = null

        override fun getCount(): Int = data.items.size.coerceAtMost(12)

        override fun getItemId(position: Int): Long = position.toLong()

        override fun onCreateView(position: Int, parent: ViewGroup): View {
            return ImageView(parent.context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        }

        override fun onBindView(position: Int, view: View) {
            val iv = view as ImageView
            if (data.items.isNotEmpty()) {
                iv.loadArtCover(
                        item = data.items[position],
                        roundedCorners = false,
                        blur = false,
                        skipCache = false,
                        crop = true
                )
            }
            iv.setOnClickListener { onItemClick?.invoke(position, iv) }
            iv.setOnLongClickListener { onItemLongClick?.invoke(position, iv); true }
        }

        override fun onRecycleView(position: Int, view: View) {
            val iv = view as ImageView
            Glide.with(iv.context).clear(iv)
            iv.setImageDrawable(null)
        }

        fun setOnItemClickListener(listener: (position: Int, imageView: ImageView) -> Unit) {
            onItemClick = listener
        }

        fun setOnItemLongClickListener(listener: (position: Int, imageView: ImageView) -> Unit) {
            onItemLongClick = listener
        }
    }

    /**
     * Replaces the entire data set and dispatches granular change notifications computed by
     * [DiffUtil]. Sections are identified by their [ArtFlowData.title] string resource, so a
     * section that gains or loses items triggers a targeted [notifyItemChanged] rather than a
     * full rebind. This preserves the [RecyclerView] scroll position and keeps running
     * [app.simple.felicity.decorations.pager.FelicitySlider] animations alive on untouched rows.
     *
     * @param newData The updated list of [ArtFlowData] sections.
     */
    fun updateData(newData: List<ArtFlowData<Any>>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = data.size
            override fun getNewListSize() = newData.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                data[oldItemPosition].title == newData[newItemPosition].title
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                data[oldItemPosition].items == newData[newItemPosition].items
        })
        data = newData
        diff.dispatchUpdatesTo(this)
    }

    /**
     * Returns the [ArtFlowData] section at [rowPosition], or `null` if the position is
     * out of bounds. Callbacks should use this instead of closing over a stale list
     * reference so they always operate on the latest data after a [updateData] call.
     *
     * @param rowPosition Zero-based row index.
     * @return The section at that position, or `null`.
     */
    fun getSection(rowPosition: Int): ArtFlowData<Any>? = data.getOrNull(rowPosition)

    /**
     * Registers a callback for slider-row and panel-title interaction events.
     *
     * @param adapterArtFlowHomeCallbacks The callback implementation to attach.
     */
    fun setAdapterArtFlowHomeCallbacks(adapterArtFlowHomeCallbacks: AdapterArtFlowHomeCallbacks) {
        this.adapterArtFlowHomeCallbacks = adapterArtFlowHomeCallbacks
    }

    companion object {
        interface AdapterArtFlowHomeCallbacks : GeneralAdapterCallbacks {
            /**
             * Fired when the user taps a slide image (normal click = play).
             *
             * @param imageView    The [ImageView] that was tapped.
             * @param rowPosition  Zero-based index of the [ArtFlowData] section row.
             * @param itemPosition Zero-based index of the tapped item within that section.
             */
            fun onItemClicked(imageView: ImageView, rowPosition: Int, itemPosition: Int)

            /**
             * Fired when the user long-presses a slide image (long press = open menu).
             *
             * @param imageView    The [ImageView] that was long-pressed; use as the
             *                     shared-element source for {@code openSongsMenu}.
             * @param rowPosition  Zero-based index of the [ArtFlowData] section row.
             * @param itemPosition Zero-based index of the long-pressed item within that section.
             */
            fun onItemLongClicked(imageView: ImageView, rowPosition: Int, itemPosition: Int)

            fun onClicked(view: View, position: Int, itemPosition: Int)
            fun onClicked(view: View, position: Int)
        }
    }
}
