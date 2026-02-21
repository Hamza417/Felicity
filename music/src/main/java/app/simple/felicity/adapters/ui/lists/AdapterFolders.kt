package app.simple.felicity.adapters.ui.lists

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
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

class AdapterFolders(private val list: MutableList<Folder>) : FastScrollAdapter<VerticalListViewHolder>() {

    private var callbacks: GeneralAdapterCallbacks? = null

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            CommonPreferencesConstants.GRID_TYPE_LIST -> {
                ListHolder(AdapterFoldersListBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            CommonPreferencesConstants.GRID_TYPE_GRID -> {
                GridHolder(AdapterStyleGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
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
            binding.secondaryDetail.text = context.resources.getQuantityString(
                    R.plurals.number_of_songs, folder.songCount, folder.songCount)
            binding.tertiaryDetail.gone(false)

            if (isLightBind) return

            binding.albumArt.loadArtCoverWithPayload(folder)

            binding.container.setOnClickListener {
                callbacks?.onFolderClicked(folder, it)
            }
        }
    }

    inner class ListHolder(private val binding: AdapterFoldersListBinding) : VerticalListViewHolder(binding.root) {
        fun bind(folder: Folder, isLightBind: Boolean) {
            binding.name.text = folder.name
            binding.songCount.text = context.resources.getQuantityString(
                    R.plurals.number_of_songs, folder.songCount, folder.songCount)

            if (isLightBind) return

            binding.cover.loadArtCoverWithPayload(folder)

            binding.container.setOnClickListener {
                callbacks?.onFolderClicked(folder, it)
            }
        }
    }

    fun setCallbackListener(listener: GeneralAdapterCallbacks) {
        this.callbacks = listener
    }

    /**
     * Update the list of folders with DiffUtil for efficient updates
     */
    fun updateList(newFolders: List<Folder>) {
        Log.d(TAG, "updateList: Old size=${list.size}, New size=${newFolders.size}")

        val diffCallback = FoldersDiffCallback(list, newFolders)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        list.clear()
        list.addAll(newFolders)

        Log.d(TAG, "After update: list.size=${list.size}")
        diffResult.dispatchUpdatesTo(this)
    }

    companion object {
        private const val TAG = "AdapterFolders"
    }

    private class FoldersDiffCallback(
            private val oldList: List<Folder>,
            private val newList: List<Folder>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}



