package app.simple.felicity.adapters.ui.lists

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.AdapterStyleListBinding
import app.simple.felicity.decorations.fastscroll.FastScrollAdapter
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.utils.AudioUtils.getArtists
import app.simple.felicity.utils.AdapterUtils.addAudioQualityIcon
import com.bumptech.glide.Glide

/**
 * A dedicated adapter for the Selections panel. Unlike [AdapterSongs], this one always
 * uses the simple list layout regardless of what the user has chosen for the main song list.
 * That way the selection basket always looks clean and consistent.
 *
 * @author Hamza417
 */
class AdapterSelections(initial: List<Audio>) : FastScrollAdapter<AdapterSelections.SelectionHolder>() {

    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null

    private val listUpdateCallback = object : ListUpdateCallback {
        @SuppressLint("NotifyDataSetChanged")
        override fun onInserted(position: Int, count: Int) {
            if (count > 100) notifyDataSetChanged()
            else notifyItemRangeInserted(position, count)
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onRemoved(position: Int, count: Int) {
            if (count > 100) notifyDataSetChanged()
            else notifyItemRangeRemoved(position, count)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            notifyItemMoved(fromPosition, toPosition)
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            notifyItemRangeChanged(position, count, payload)
        }
    }

    private val diffCallback = object : DiffUtil.ItemCallback<Audio>() {
        override fun areItemsTheSame(oldItem: Audio, newItem: Audio) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Audio, newItem: Audio): Boolean {
            return oldItem.title == newItem.title &&
                    oldItem.artist == newItem.artist &&
                    oldItem.album == newItem.album &&
                    oldItem.duration == newItem.duration &&
                    oldItem.uri == newItem.uri
        }
    }

    private val differ = AsyncListDiffer(
            listUpdateCallback,
            AsyncDifferConfig.Builder(diffCallback).build()
    )

    private val songs: List<Audio> get() = differ.currentList

    init {
        setHasStableIds(true)
        differ.submitList(initial.toList())
    }

    override fun getItemId(position: Int): Long = songs[position].id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectionHolder {
        return SelectionHolder(AdapterStyleListBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBind(holder: SelectionHolder, position: Int, isLightBind: Boolean) {
        holder.bind(songs[position], isLightBind)
    }

    override fun getItemCount(): Int = songs.size

    override fun onViewRecycled(holder: SelectionHolder) {
        holder.itemView.clearAnimation()
        super.onViewRecycled(holder)
        Glide.with(holder.binding.cover).clear(holder.binding.cover)
    }

    fun setGeneralAdapterCallbacks(callbacks: GeneralAdapterCallbacks) {
        this.generalAdapterCallbacks = callbacks
    }

    fun updateSongs(newSongs: List<Audio>) {
        differ.submitList(newSongs.toList())
    }

    inner class SelectionHolder(val binding: AdapterStyleListBinding) : VerticalListViewHolder(binding.root) {
        fun bind(audio: Audio, isLightBind: Boolean) {
            binding.title.setTextOrUnknown(audio.title)
            binding.secondaryDetail.setTextOrUnknown(audio.getArtists())
            binding.tertiaryDetail.setTextOrUnknown(audio.album)
            binding.title.addAudioQualityIcon(audio)
            binding.container.setAudioID(audio.id)
            if (isLightBind) return
            binding.cover.loadArtCoverWithPayload(audio)
            binding.container.setOnClickListener {
                generalAdapterCallbacks?.onSongClicked(songs, bindingAdapterPosition, it)
            }
            // Long-press lets the user pull a single track out of the selection basket.
            binding.container.setOnLongClickListener {
                generalAdapterCallbacks?.onSongLongClicked(songs, bindingAdapterPosition, binding.cover)
                true
            }
        }
    }
}

