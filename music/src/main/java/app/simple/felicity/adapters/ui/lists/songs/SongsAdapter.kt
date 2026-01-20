package app.simple.felicity.adapters.ui.lists.songs

import android.view.LayoutInflater
import android.view.ViewGroup
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.AdapterStyleGridBinding
import app.simple.felicity.databinding.AdapterStyleListBinding
import app.simple.felicity.decorations.fastscroll.FastScrollAdapter
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.ViewUtils.clearSkeletonBackground
import app.simple.felicity.decorations.utils.ViewUtils.setSkeletonBackground
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.preferences.SongsPreferences
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Song
import app.simple.felicity.shared.utils.TextViewUtils.setTextOrUnknown
import com.bumptech.glide.Glide

class SongsAdapter(initial: List<Song>) : FastScrollAdapter<VerticalListViewHolder>() {

    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null
    private var previousIndex = -1
    private var songs = mutableListOf<Song>().apply { addAll(initial) }

    init {
        setHasStableIds(true)
    }

    var currentlyPlayingSong: Song? = null
        set(value) {
            val oldIndex = previousIndex
            field = value
            val newIndex = value?.let { v -> songs.indexOfFirst { it.id == v.id } } ?: -1
            if (newIndex != -1) {
                notifyItemChanged(newIndex, PAYLOAD_PLAYBACK_STATE)
            }
            if (oldIndex != -1 && oldIndex != newIndex) {
                notifyItemChanged(oldIndex, PAYLOAD_PLAYBACK_STATE)
            }
            previousIndex = newIndex
        }

    override fun getItemId(position: Int): Long = songs[position].id

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

    override fun onBind(holder: VerticalListViewHolder, position: Int, isLightBind: Boolean) {
        val song = songs[position]
        when (holder) {
            is ListHolder -> holder.bind(song, isLightBind)
            is GridHolder -> holder.bind(song, isLightBind)
        }
    }

    override fun onBindPayload(holder: VerticalListViewHolder, position: Int, payloads: MutableList<Any>): Boolean {
        if (payloads.contains(PAYLOAD_PLAYBACK_STATE)) {
            val song = songs[position]
            when (holder) {
                is ListHolder -> holder.bindSelectionState(song)
                is GridHolder -> holder.bindSelectionState(song)
            }
            return true
        }
        return false
    }

    override fun getItemCount(): Int = songs.size

    override fun getItemViewType(position: Int): Int = SongsPreferences.getGridType()

    override fun onViewRecycled(holder: VerticalListViewHolder) {
        holder.itemView.clearAnimation()
        super.onViewRecycled(holder)
        when (holder) {
            is ListHolder -> Glide.with(holder.binding.cover).clear(holder.binding.cover)
            is GridHolder -> Glide.with(holder.binding.albumArt).clear(holder.binding.albumArt)
        }
    }

    fun setGeneralAdapterCallbacks(callbacks: GeneralAdapterCallbacks) {
        this.generalAdapterCallbacks = callbacks
    }

    inner class ListHolder(val binding: AdapterStyleListBinding) : VerticalListViewHolder(binding.root) {
        fun bindSelectionState(song: Song) {
            binding.container.isSelected = MediaManager.getCurrentSongId() == song.id
        }

        fun bind(song: Song, isLightBind: Boolean) {
            if (isLightBind) {
                binding.container.setSkeletonBackground(enable = true)
                bindSelectionState(song)
                return
            }
            binding.container.clearSkeletonBackground()
            binding.title.setTextOrUnknown(song.title)
            binding.secondaryDetail.setTextOrUnknown(song.artist)
            binding.tertiaryDetail.setTextOrUnknown(song.album)
            binding.cover.loadArtCoverWithPayload(song)
            bindSelectionState(song)
            binding.container.setOnLongClickListener {
                generalAdapterCallbacks?.onSongLongClicked(songs, bindingAdapterPosition, binding.cover)
                true
            }
            binding.container.setOnClickListener {
                generalAdapterCallbacks?.onSongClicked(songs, bindingAdapterPosition, it)
            }
        }
    }

    inner class GridHolder(val binding: AdapterStyleGridBinding) : VerticalListViewHolder(binding.root) {
        fun bindSelectionState(song: Song) {
            binding.container.isSelected = MediaManager.getCurrentSongId() == song.id
        }

        fun bind(song: Song, isLightBind: Boolean) {
            if (isLightBind) {
                binding.container.setSkeletonBackground(enable = true)
                bindSelectionState(song)
                return
            }
            binding.container.clearSkeletonBackground()
            binding.title.setTextOrUnknown(song.title)
            binding.secondaryDetail.setTextOrUnknown(song.artist)
            binding.tertiaryDetail.setTextOrUnknown(song.album)
            binding.albumArt.loadArtCoverWithPayload(song)
            bindSelectionState(song)
            binding.container.setOnLongClickListener {
                generalAdapterCallbacks?.onSongLongClicked(songs, bindingAdapterPosition, it)
                true
            }
            binding.container.setOnClickListener {
                generalAdapterCallbacks?.onSongClicked(songs, bindingAdapterPosition, it)
            }
        }
    }


    companion object {
        private const val PAYLOAD_PLAYBACK_STATE = "payload_playing_state"
    }
}