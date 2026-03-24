package app.simple.felicity.adapters.ui.lists

import android.annotation.SuppressLint
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.AdapterStyleGridBinding
import app.simple.felicity.databinding.AdapterStyleListBinding
import app.simple.felicity.decorations.fastscroll.FastScrollAdapter
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.AudioWithStat
import app.simple.felicity.utils.AdapterUtils.addAudioQualityIcon
import com.bumptech.glide.Glide

/**
 * Recycler adapter for song lists that append a per-item stat value to the album line,
 * displayed as {@code "Album Name • stat text"}. Used by the Recently Played panel
 * (relative last-played time) and the Most Played panel (play count).
 *
 * <p>Uses the same {@code adapter_style_list} and {@code adapter_style_grid} layouts that
 * {@link AdapterSongs} uses, so the visual style is identical across all song panels.
 * The caller supplies a [gridTypeProvider] so each panel can use its own preference key,
 * and a [statTextProvider] that returns just the stat portion of the third line (e.g.
 * {@code "2 minutes ago"} or {@code "5 times"}).</p>
 *
 * @author Hamza417
 */
class AdapterSongsWithStat(
        initial: List<AudioWithStat>,
        private val gridTypeProvider: () -> Int,
        private val statTextProvider: (AudioWithStat) -> CharSequence
) : FastScrollAdapter<VerticalListViewHolder>() {

    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null

    private val listUpdateCallback = object : ListUpdateCallback {
        @SuppressLint("NotifyDataSetChanged")
        override fun onInserted(position: Int, count: Int) {
            if (count > 100) {
                notifyDataSetChanged()
            } else {
                notifyItemRangeInserted(position, count)
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onRemoved(position: Int, count: Int) {
            if (count > 100) {
                notifyDataSetChanged()
            } else {
                notifyItemRangeRemoved(position, count)
            }
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            notifyItemMoved(fromPosition, toPosition)
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            notifyItemRangeChanged(position, count, payload)
        }
    }

    private val diffCallback = object : DiffUtil.ItemCallback<AudioWithStat>() {
        override fun areItemsTheSame(oldItem: AudioWithStat, newItem: AudioWithStat) =
            oldItem.audio.id == newItem.audio.id

        override fun areContentsTheSame(oldItem: AudioWithStat, newItem: AudioWithStat): Boolean {
            return oldItem.audio.title == newItem.audio.title &&
                    oldItem.audio.artist == newItem.audio.artist &&
                    oldItem.audio.album == newItem.audio.album &&
                    oldItem.audio.duration == newItem.audio.duration &&
                    oldItem.lastPlayed == newItem.lastPlayed &&
                    oldItem.playCount == newItem.playCount
        }
    }

    private val differ = AsyncListDiffer(
            listUpdateCallback,
            AsyncDifferConfig.Builder(diffCallback).build()
    )

    private val songs: List<AudioWithStat> get() = differ.currentList

    /** Extracted [Audio] list used when passing items to media or callback consumers. */
    private val audioList: List<Audio> get() = songs.map { it.audio }

    init {
        setHasStableIds(true)
        differ.submitList(initial.toList())
    }

    override fun getItemId(position: Int): Long = songs[position].audio.id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            CommonPreferencesConstants.GRID_TYPE_LIST -> {
                ListHolder(AdapterStyleListBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false))
            }
            CommonPreferencesConstants.GRID_TYPE_GRID -> {
                GridHolder(AdapterStyleGridBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false))
            }
            else -> {
                ListHolder(AdapterStyleListBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false))
            }
        }
    }

    override fun onBind(holder: VerticalListViewHolder, position: Int, isLightBind: Boolean) {
        val item = songs[position]
        when (holder) {
            is ListHolder -> holder.bind(item, isLightBind)
            is GridHolder -> holder.bind(item, isLightBind)
        }
    }

    override fun getItemCount(): Int = songs.size

    override fun getItemViewType(position: Int): Int = gridTypeProvider()

    override fun onViewRecycled(holder: VerticalListViewHolder) {
        holder.itemView.clearAnimation()
        super.onViewRecycled(holder)
        when (holder) {
            is ListHolder -> Glide.with(holder.binding.cover).clear(holder.binding.cover)
            is GridHolder -> Glide.with(holder.binding.albumArt).clear(holder.binding.albumArt)
        }
    }

    /** Registers the callback that handles click and long-click events on song items. */
    fun setGeneralAdapterCallbacks(callbacks: GeneralAdapterCallbacks) {
        this.generalAdapterCallbacks = callbacks
    }

    /** Submits an updated list, triggering diff calculation on a background thread. */
    fun updateSongs(newSongs: List<AudioWithStat>) {
        differ.submitList(newSongs.toList())
    }

    /**
     * Builds the combined album + stat string for the tertiary detail line.
     * If the album name is absent the stat text is shown on its own.
     */
    private fun buildTertiaryText(item: AudioWithStat): CharSequence {
        val album = item.audio.album?.takeIf { it.isNotEmpty() }
        val stat = statTextProvider(item)
        return if (album != null) "$album \u2022 $stat" else stat
    }

    inner class ListHolder(val binding: AdapterStyleListBinding) :
            VerticalListViewHolder(binding.root) {

        fun bind(item: AudioWithStat, isLightBind: Boolean) {
            val audio = item.audio
            binding.title.setTextOrUnknown(audio.title)
            binding.secondaryDetail.setTextOrUnknown(audio.artist)
            binding.tertiaryDetail.text = buildTertiaryText(item)
            binding.title.addAudioQualityIcon(audio)
            binding.container.setAudioID(audio.id)
            if (isLightBind) return
            binding.cover.loadArtCoverWithPayload(audio)
            binding.container.setOnLongClickListener {
                generalAdapterCallbacks?.onSongLongClicked(audioList, bindingAdapterPosition, binding.cover)
                true
            }
            binding.container.setOnClickListener {
                generalAdapterCallbacks?.onSongClicked(audioList, bindingAdapterPosition, it)
            }
        }
    }

    inner class GridHolder(val binding: AdapterStyleGridBinding) :
            VerticalListViewHolder(binding.root) {

        fun bind(item: AudioWithStat, isLightBind: Boolean) {
            val audio = item.audio
            binding.title.setTextOrUnknown(audio.title)
            binding.secondaryDetail.setTextOrUnknown(audio.artist)
            binding.tertiaryDetail.text = buildTertiaryText(item)
            binding.container.setAudioID(audio.id)
            if (isLightBind) return
            binding.albumArt.loadArtCoverWithPayload(audio)
            binding.container.setOnLongClickListener {
                generalAdapterCallbacks?.onSongLongClicked(audioList, bindingAdapterPosition, binding.albumArt)
                true
            }
            binding.container.setOnClickListener {
                generalAdapterCallbacks?.onSongClicked(audioList, bindingAdapterPosition, it)
            }
        }
    }

    companion object {
        /**
         * Returns a human-readable relative time string for [timestamp], such as
         * "2 minutes ago", "Yesterday", "3 weeks ago", or "Last month".
         */
        fun formatRelativeTime(timestamp: Long): CharSequence {
            return DateUtils.getRelativeTimeSpanString(
                    timestamp,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
            )
        }
    }
}
