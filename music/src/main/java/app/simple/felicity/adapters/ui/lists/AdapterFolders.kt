package app.simple.felicity.adapters.ui.lists

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import app.simple.felicity.R
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.AdapterFoldersListBinding
import app.simple.felicity.databinding.AdapterStyleGridBinding
import app.simple.felicity.decorations.fastscroll.FastScrollAdapter
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.preferences.FoldersPreferences
import app.simple.felicity.repository.models.Folder
import app.simple.felicity.shared.utils.ViewUtils.gone

class AdapterFolders(initial: List<Folder>) : FastScrollAdapter<VerticalListViewHolder>() {

    private var callbacks: GeneralAdapterCallbacks? = null

    private val listUpdateCallback = object : ListUpdateCallback {
        override fun onInserted(position: Int, count: Int) {
            if (count > 100) {
                notifyDataSetChanged()
            } else {
                notifyItemRangeInserted(position, count)
            }
        }

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

    private val diffCallback = object : DiffUtil.ItemCallback<Folder>() {
        override fun areItemsTheSame(oldItem: Folder, newItem: Folder) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Folder, newItem: Folder) = oldItem == newItem
    }

    private val differ = AsyncListDiffer(
            listUpdateCallback,
            AsyncDifferConfig.Builder(diffCallback).build()
    )

    private val list: List<Folder> get() = differ.currentList

    init {
        setHasStableIds(true)
        differ.submitList(initial.toList())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            CommonPreferencesConstants.GRID_TYPE_LIST -> {
                ListHolder(AdapterFoldersListBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            CommonPreferencesConstants.GRID_TYPE_GRID -> {
                GridHolder(AdapterStyleGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            else -> {
                throw IllegalArgumentException("Invalid view type: $viewType")
            }
        }
    }

    override fun onBind(holder: VerticalListViewHolder, position: Int, isLightBind: Boolean) {
        val folder = list[position]
        when (holder) {
            is GridHolder -> holder.bind(folder, isLightBind)
            is ListHolder -> holder.bind(folder, isLightBind)
        }
    }

    override fun getItemId(position: Int): Long = list[position].id
    override fun getItemCount(): Int = list.size
    override fun getItemViewType(position: Int): Int = FoldersPreferences.getGridType()

    inner class GridHolder(private val binding: AdapterStyleGridBinding) : VerticalListViewHolder(binding.root) {
        fun bind(folder: Folder, isLightBind: Boolean) {
            binding.title.text = folder.name
            binding.secondaryDetail.text = context.resources.getQuantityString(R.plurals.number_of_songs, folder.songCount, folder.songCount)
            binding.tertiaryDetail.gone(false)
            if (isLightBind) return
            binding.albumArt.loadArtCoverWithPayload(folder)
            binding.container.setOnClickListener { callbacks?.onFolderClicked(folder, it) }
        }
    }

    inner class ListHolder(private val binding: AdapterFoldersListBinding) : VerticalListViewHolder(binding.root) {
        fun bind(folder: Folder, isLightBind: Boolean) {
            binding.name.text = folder.name
            binding.songCount.text = context.resources.getQuantityString(R.plurals.number_of_songs, folder.songCount, folder.songCount)
            if (isLightBind) return
            binding.cover.loadArtCoverWithPayload(folder)
            binding.container.setOnClickListener { callbacks?.onFolderClicked(folder, it) }
        }
    }

    fun setCallbackListener(listener: GeneralAdapterCallbacks) {
        this.callbacks = listener
    }

    fun updateList(newFolders: List<Folder>) {
        differ.submitList(newFolders.toList())
    }
}
