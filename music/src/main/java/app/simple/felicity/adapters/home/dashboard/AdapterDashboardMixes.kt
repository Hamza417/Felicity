package app.simple.felicity.adapters.home.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.databinding.AdapterMixCardBinding
import app.simple.felicity.decorations.overscroll.HorizontalListViewHolder
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCover
import app.simple.felicity.preferences.AlbumArtPreferences
import app.simple.felicity.viewmodels.panels.MixesViewModel.Mix

/**
 * Horizontal carousel adapter for the auto-generated [Mix] playlists (Discover, Random Picks
 * and Queue Mix) shown in the dashboard "Mixed" row. Each card draws its cover as a stack
 * so it reads as a collection of songs.
 *
 * @param mixes The initial list of mixes to show.
 * @author Hamza417
 */
class AdapterDashboardMixes(
        private var mixes: List<Mix>
) : RecyclerView.Adapter<AdapterDashboardMixes.Holder>() {

    private var callbacks: AdapterDashboardMixesCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(AdapterMixCardBinding.inflate(
                LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int = mixes.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val mix = mixes[position]
        val context = holder.itemView.context
        val songs = mix.songs

        holder.binding.art.loadArtCover(
                item = mix,
                roundedCorners = AlbumArtPreferences.isRoundedCornersEnabled(),
                greyscale = AlbumArtPreferences.isGreyscaleEnabled()
        )

        holder.binding.title.text = context.getString(mix.titleRes)
        holder.binding.artist.text = context.getString(R.string.x_songs, songs.size)

        holder.binding.container.setOnClickListener {
            callbacks?.onMixClicked(mixes[holder.bindingAdapterPosition])
        }
    }

    /**
     * Replaces the current data set with [newMixes] using [DiffUtil].
     *
     * @param newMixes The updated list of mixes to display.
     */
    fun updateData(newMixes: List<Mix>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = mixes.size
            override fun getNewListSize() = newMixes.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                mixes[oldItemPosition].type == newMixes[newItemPosition].type

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                mixes[oldItemPosition] == newMixes[newItemPosition]
        })
        mixes = newMixes
        diff.dispatchUpdatesTo(this)
    }

    /**
     * Attaches the callbacks used to respond to mix card interactions.
     *
     * @param callbacks The implementation to attach.
     */
    fun setCallbacks(callbacks: AdapterDashboardMixesCallbacks) {
        this.callbacks = callbacks
    }

    inner class Holder(val binding: AdapterMixCardBinding) :
            HorizontalListViewHolder(binding.root)

    companion object {
        /**
         * Callback interface for mix card interactions.
         */
        interface AdapterDashboardMixesCallbacks {
            /**
             * Called when the user taps a mix card.
             *
             * @param mix The [Mix] that was tapped.
             */
            fun onMixClicked(mix: Mix)
        }
    }
}
