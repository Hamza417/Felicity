package app.simple.felicity.adapters.ui.lists

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.AdapterFolderHierarchyFolderBinding
import app.simple.felicity.databinding.AdapterFolderHierarchyFolderGridBinding
import app.simple.felicity.databinding.AdapterStyleGridBinding
import app.simple.felicity.databinding.AdapterStyleListBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.preferences.FolderHierarchyPreferences
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Folder
import app.simple.felicity.shared.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.utils.AdapterUtils.addAudioQualityIcon
import app.simple.felicity.viewmodels.panels.FolderHierarchyViewModel.FolderHierarchyContents
import com.bumptech.glide.Glide

/**
 * Adapter for browsing folder hierarchy.
 * Shows folders first (full-width), then songs below.
 * Both folder rows and song rows respect the current grid type setting.
 */
class AdapterFolderHierarchy(contents: FolderHierarchyContents) : RecyclerView.Adapter<VerticalListViewHolder>() {

    private var callbacks: GeneralAdapterCallbacks? = null

    private var folders: List<Folder> = contents.subFolders.toList()
    private var songs: List<Audio> = contents.songs.toList()

    private val folderCount: Int get() = folders.size
    private val songCount: Int get() = songs.size

    init {
        setHasStableIds(true)
    }

    override fun getItemCount(): Int = folderCount + songCount

    override fun getItemId(position: Int): Long {
        return if (position < folderCount) {
            folders[position].id
        } else {
            songs[position - folderCount].id
        }
    }

    override fun getItemViewType(position: Int): Int {
        val isSong = position >= folderCount
        return when (FolderHierarchyPreferences.getGridType()) {
            CommonPreferencesConstants.GRID_TYPE_GRID -> {
                if (isSong) VIEW_TYPE_SONG_GRID else VIEW_TYPE_FOLDER_GRID
            }
            else -> {
                if (isSong) VIEW_TYPE_SONG_LIST else VIEW_TYPE_FOLDER_LIST
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            VIEW_TYPE_FOLDER_LIST -> FolderListHolder(
                    AdapterFolderHierarchyFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            VIEW_TYPE_FOLDER_GRID -> FolderGridHolder(
                    AdapterFolderHierarchyFolderGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            VIEW_TYPE_SONG_GRID -> SongGridHolder(
                    AdapterStyleGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            else -> SongListHolder(
                    AdapterStyleListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        when (holder) {
            is FolderListHolder -> holder.bind(folders[position])
            is FolderGridHolder -> holder.bind(folders[position])
            is SongListHolder -> holder.bind(songs[position - folderCount])
            is SongGridHolder -> holder.bind(songs[position - folderCount])
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }
        if (payloads.contains(PAYLOAD_PLAYBACK_STATE)) {
            val song = songs.getOrNull(position - folderCount) ?: return
            when (holder) {
                is SongListHolder -> holder.bindSelectionState(song)
                is SongGridHolder -> holder.bindSelectionState(song)
            }
        }
    }

    override fun onViewRecycled(holder: VerticalListViewHolder) {
        super.onViewRecycled(holder)
        when (holder) {
            is SongListHolder -> Glide.with(holder.binding.cover).clear(holder.binding.cover)
            is SongGridHolder -> Glide.with(holder.binding.albumArt).clear(holder.binding.albumArt)
            is FolderListHolder -> Glide.with(holder.binding.cover).clear(holder.binding.cover)
            is FolderGridHolder -> Glide.with(holder.binding.albumArt).clear(holder.binding.albumArt)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateContents(contents: FolderHierarchyContents) {
        folders = contents.subFolders.toList()
        songs = contents.songs.toList()
        notifyDataSetChanged()
    }

    fun setCallbacks(listener: GeneralAdapterCallbacks) {
        this.callbacks = listener
    }

    // --------------------------------------------------------------------------------------------- //

    inner class FolderListHolder(val binding: AdapterFolderHierarchyFolderBinding) :
            VerticalListViewHolder(binding.root) {
        fun bind(folder: Folder) {
            binding.name.text = folder.name
            binding.songCount.text = context.resources.getQuantityString(
                    R.plurals.number_of_songs,
                    folder.songCount,
                    folder.songCount
            )
            binding.cover.loadArtCoverWithPayload(folder)
            binding.container.setOnClickListener {
                callbacks?.onFolderClicked(folder, it)
            }
        }
    }

    inner class FolderGridHolder(val binding: AdapterFolderHierarchyFolderGridBinding) :
            VerticalListViewHolder(binding.root) {
        fun bind(folder: Folder) {
            binding.name.text = folder.name
            binding.songCount.text = context.resources.getQuantityString(
                    R.plurals.number_of_songs,
                    folder.songCount,
                    folder.songCount
            )
            binding.albumArt.loadArtCoverWithPayload(folder)
            binding.container.setOnClickListener {
                callbacks?.onFolderClicked(folder, it)
            }
        }
    }

    inner class SongListHolder(val binding: AdapterStyleListBinding) :
            VerticalListViewHolder(binding.root) {
        fun bindSelectionState(song: Audio) {
            binding.container.isSelected = MediaManager.getCurrentSongId() == song.id
        }

        fun bind(audio: Audio) {
            binding.title.setTextOrUnknown(audio.title)
            binding.secondaryDetail.setTextOrUnknown(audio.artist)
            binding.tertiaryDetail.setTextOrUnknown(audio.album)
            binding.title.addAudioQualityIcon(audio)
            bindSelectionState(audio)
            binding.cover.loadArtCoverWithPayload(audio)
            binding.container.setOnClickListener {
                callbacks?.onSongClicked(songs.toMutableList(), bindingAdapterPosition - folderCount, it)
            }
            binding.container.setOnLongClickListener {
                callbacks?.onSongLongClicked(songs, bindingAdapterPosition - folderCount, binding.cover)
                true
            }
        }
    }

    inner class SongGridHolder(val binding: AdapterStyleGridBinding) :
            VerticalListViewHolder(binding.root) {
        fun bindSelectionState(audio: Audio) {
            binding.container.isSelected = MediaManager.getCurrentSongId() == audio.id
        }

        fun bind(song: Audio) {
            binding.title.setTextOrUnknown(song.title)
            binding.secondaryDetail.setTextOrUnknown(song.artist)
            binding.tertiaryDetail.setTextOrUnknown(song.album)
            bindSelectionState(song)
            binding.albumArt.loadArtCoverWithPayload(song)
            binding.container.setOnClickListener {
                callbacks?.onSongClicked(songs.toMutableList(), bindingAdapterPosition - folderCount, it)
            }
            binding.container.setOnLongClickListener {
                callbacks?.onSongLongClicked(songs, bindingAdapterPosition - folderCount, binding.albumArt)
                true
            }
        }
    }

    companion object {
        const val VIEW_TYPE_FOLDER_LIST = 0
        const val VIEW_TYPE_FOLDER_GRID = 1
        private const val VIEW_TYPE_SONG_LIST = 2
        private const val VIEW_TYPE_SONG_GRID = 3
        private const val PAYLOAD_PLAYBACK_STATE = "payload_playing_state"

        /** Convenience alias used by the Fragment's SpanSizeLookup to identify folder items. */
        val FOLDER_VIEW_TYPES = setOf(VIEW_TYPE_FOLDER_LIST, VIEW_TYPE_FOLDER_GRID)
    }
}
