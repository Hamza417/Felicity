package app.simple.felicity.adapters.home.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterGridImageBinding
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCover
import app.simple.felicity.repository.models.Audio

/**
 * Adapter for the recommended spanned art grid on the dashboard.
 *
 * Renders each [Audio] item as a full-bleed square image with an overlaid title.
 * Supports tap to play and long-press to open the song context menu.
 *
 * @param list The initial list of recommended [Audio] items to display.
 * @author Hamza417
 */
class AdapterRecommended(private var list: List<Audio>) :
        RecyclerView.Adapter<AdapterRecommended.Holder>() {

    private lateinit var callbacks: AdapterRecommendedCallbacks

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(AdapterGridImageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        if (list.isNotEmpty()) {
            val item = list[position]

            holder.binding.art.loadArtCover(
                    item = item,
                    shadow = false,
                    roundedCorners = false,
                    skipCache = true,
                    darken = false)

            holder.binding.container.setOnClickListener {
                if (list.isNotEmpty()) {
                    callbacks.onItemClicked(list, holder.bindingAdapterPosition)
                }
            }

            holder.binding.container.setOnLongClickListener {
                if (list.isNotEmpty()) {
                    callbacks.onItemLongClicked(list, holder.bindingAdapterPosition, holder.binding.art)
                }
                true
            }

            holder.binding.title.text = item.title
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun getItemId(position: Int): Long {
        return list[position].id
    }

    inner class Holder(val binding: AdapterGridImageBinding) : RecyclerView.ViewHolder(binding.root)

    fun randomize() {
        for (i in 0 until itemCount) {
            // Notify position change
            notifyItemChanged(i)
        }
    }

    fun updateItem(position: Int) {
        notifyItemChanged(position)
    }

    /**
     * Replaces the current data set with [newList] and dispatches granular change notifications
     * computed by [DiffUtil]. Items are identified by [Audio.id] so additions, removals, and
     * moves are animated individually without a full rebind.
     *
     * @param newList The updated list of recommended songs.
     */
    fun updateData(newList: List<Audio>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = list.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                list[oldItemPosition].id == newList[newItemPosition].id
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                list[oldItemPosition] == newList[newItemPosition]
        })
        list = newList
        diff.dispatchUpdatesTo(this)
    }

    fun setCallbacks(callbacks: AdapterRecommendedCallbacks) {
        this.callbacks = callbacks
    }

    companion object {

        interface AdapterRecommendedCallbacks {
            /**
             * Called when the user taps a recommended item.
             *
             * @param items    The full recommended list.
             * @param position The index of the tapped item.
             */
            fun onItemClicked(items: List<Audio>, position: Int)

            /**
             * Called when the user long-presses a recommended item.
             *
             * @param items     The full recommended list.
             * @param position  The index of the long-pressed item.
             * @param imageView The album art [ImageView] used as a shared-element source for the menu.
             */
            fun onItemLongClicked(items: List<Audio>, position: Int, imageView: ImageView)
        }
    }
}
