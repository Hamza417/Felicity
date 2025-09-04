package app.simple.felicity.adapters.ui.lists.songs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.AdapterStyleGridBinding
import app.simple.felicity.databinding.AdapterStyleListBinding
import app.simple.felicity.decorations.fastscroll.SlideFastScroller
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.decorations.utils.ViewUtils.clearSkeletonBackground
import app.simple.felicity.decorations.utils.ViewUtils.setSkeletonBackground
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.preferences.SongsPreferences
import app.simple.felicity.repository.models.Song
import com.bumptech.glide.Glide

class SongsAdapter(initial: List<Song>) :
        RecyclerView.Adapter<VerticalListViewHolder>(), SlideFastScroller.FastScrollBindingController {

    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null
    private var previousIndex = -1

    private var songs = mutableListOf<Song>().apply { addAll(initial) }
    private var lightBindMode = false

    var currentlyPlayingSong: Song? = null
        set(value) {
            val oldIndex = previousIndex
            field = value
            val newIndex = value?.let { songs.indexOf(it) } ?: -1
            if (newIndex != -1) {
                notifyItemChanged(newIndex, PAYLOAD_PLAYBACK_STATE)
            }
            if (oldIndex != -1 && oldIndex != newIndex) {
                notifyItemChanged(oldIndex, PAYLOAD_PLAYBACK_STATE)
            }
            previousIndex = newIndex
        }

    override fun getItemId(position: Int): Long {
        return songs[position].id
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            CommonPreferencesConstants.GRID_TYPE_LIST -> {
                ListHolder(AdapterStyleListBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            CommonPreferencesConstants.GRID_TYPE_GRID -> {
                GridHolder(AdapterStyleGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            else -> {
                ListHolder(AdapterStyleListBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        val song = songs[position]

        when (holder) {
            is ListHolder -> {
                if (lightBindMode.not()) {
                    holder.bind(song)
                }

                holder.binding.container.setOnClickListener {
                    generalAdapterCallbacks?.onSongClicked(songs, holder.bindingAdapterPosition, it)
                }
            }
            is GridHolder -> {
                if (lightBindMode.not()) {
                    holder.bind(song)
                }

                holder.binding.container.setOnClickListener {
                    generalAdapterCallbacks?.onSongClicked(songs, holder.bindingAdapterPosition, it)
                }
            }
        }
    }

    override fun getItemCount(): Int = songs.size

    override fun getItemViewType(position: Int): Int {
        return SongsPreferences.getGridType()
    }

    override fun onViewRecycled(holder: VerticalListViewHolder) {
        holder.itemView.clearAnimation()
        super.onViewRecycled(holder)
        when (holder) {
            is ListHolder -> {
                Glide.with(holder.binding.cover).clear(holder.binding.cover)
            }
            is GridHolder -> {
                Glide.with(holder.binding.albumArt).clear(holder.binding.albumArt)
            }
        }
    }

    fun setGeneralAdapterCallbacks(callbacks: GeneralAdapterCallbacks) {
        this.generalAdapterCallbacks = callbacks
    }

    override fun setLightBindMode(enabled: Boolean) {
        lightBindMode = enabled
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, isLightBind: Boolean) {
        lightBindMode = isLightBind

        when (holder) {
            is ListHolder -> {
                if (isLightBind.not()) {
                    holder.bind(songs[position])
                }
            }
            is GridHolder -> {
                if (isLightBind.not()) {
                    holder.bind(songs[position])
                }
            }
        }
    }

    override fun shouldHandleCustomBinding(): Boolean {
        return true
    }

    inner class ListHolder(val binding: AdapterStyleListBinding) : VerticalListViewHolder(binding.root) {
        fun bind(song: Song) {
            binding.apply {
                title.setTextOrUnknown(song.title)
                secondaryDetail.setTextOrUnknown(song.artist)
                tertiaryDetail.setTextOrUnknown(song.album)
                cover.loadArtCoverWithPayload(song)

                container.setOnLongClickListener {
                    generalAdapterCallbacks?.onSongLongClicked(songs, bindingAdapterPosition, it)
                    true
                }

                binding.container.setDefaultBackground(currentlyPlayingSong == song)
            }
        }

        init {
            binding.container.setSkeletonBackground(enable = lightBindMode)
        }
    }

    inner class GridHolder(val binding: AdapterStyleGridBinding) : VerticalListViewHolder(binding.root) {
        fun bind(song: Song) {
            binding.apply {
                binding.title.setTextOrUnknown(song.title)
                binding.secondaryDetail.setTextOrUnknown(song.artist)
                binding.tertiaryDetail.setTextOrUnknown(song.album)

                binding.albumArt.loadArtCoverWithPayload(song)

                binding.container.setOnLongClickListener {
                    generalAdapterCallbacks?.onSongLongClicked(songs, bindingAdapterPosition, it)
                    true
                }

                binding.container.setOnClickListener {
                    generalAdapterCallbacks?.onSongClicked(songs, bindingAdapterPosition, it)
                }

                binding.container.clearSkeletonBackground()
            }
        }

        init {
            binding.container.setSkeletonBackground(enable = lightBindMode)
        }
    }

    companion object {
        private const val PAYLOAD_PLAYBACK_STATE = "payload_playing_state"
    }
}