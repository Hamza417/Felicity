package app.simple.felicity.adapters.ui.lists

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import app.simple.felicity.R
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.AdapterGenresListBinding
import app.simple.felicity.databinding.AdapterStyleGridBinding
import app.simple.felicity.decorations.fastscroll.FastScrollAdapter
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.ViewUtils.clearSkeletonBackground
import app.simple.felicity.decorations.utils.ViewUtils.setSkeletonBackground
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.preferences.GenresPreferences
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.shared.utils.ViewUtils.gone

class AdapterGenres(private val list: MutableList<Genre>) : FastScrollAdapter<VerticalListViewHolder>() {

    private var callbacks: GeneralAdapterCallbacks? = null

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            CommonPreferencesConstants.GRID_TYPE_LIST -> {
                ListHolder(AdapterGenresListBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            CommonPreferencesConstants.GRID_TYPE_GRID -> {
                GridHolder(AdapterStyleGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBind(holder: VerticalListViewHolder, position: Int, isLightBind: Boolean) {
        val genre = list[position]
        when (holder) {
            is GridHolder -> holder.bind(genre, isLightBind)
            is ListHolder -> holder.bind(genre, isLightBind)
        }
    }

    override fun getItemId(position: Int): Long = list[position].id

    override fun getItemCount(): Int = list.size

    override fun getItemViewType(position: Int): Int = GenresPreferences.getGridType()

    inner class GridHolder(private val binding: AdapterStyleGridBinding) : VerticalListViewHolder(binding.root) {
        fun bind(genre: Genre, isLightBind: Boolean) {
            // Always update text content so users see correct data during fast scroll
            binding.title.text = genre.name ?: context.getString(R.string.unknown)
            binding.tertiaryDetail.gone(false)
            binding.secondaryDetail.gone(false)

            if (isLightBind) {
                // Skip heavy operations: image loading
                binding.container.setSkeletonBackground(enable = true)
                return
            }

            // Full binding: clear skeleton and load images
            binding.container.clearSkeletonBackground()
            binding.albumArt.loadArtCoverWithPayload(genre)

            binding.container.setOnClickListener {
                callbacks?.onGenreClicked(genre, it)
            }
        }
    }

    inner class ListHolder(private val binding: AdapterGenresListBinding) : VerticalListViewHolder(binding.root) {
        fun bind(genre: Genre, isLightBind: Boolean) {
            // Always update text content so users see correct data during fast scroll
            binding.name.text = genre.name ?: context.getString(R.string.unknown)

            if (isLightBind) {
                // Skip heavy operations: image loading
                binding.container.setSkeletonBackground(enable = true)
                return
            }

            // Full binding: clear skeleton and load images
            binding.container.clearSkeletonBackground()
            binding.cover.loadArtCoverWithPayload(genre)

            binding.container.setOnClickListener {
                callbacks?.onGenreClicked(genre, it)
            }
        }
    }

    fun setCallbackListener(listener: GeneralAdapterCallbacks) {
        this.callbacks = listener
    }

    /**
     * Update the list of genres with DiffUtil for efficient updates
     * This is called when the Flow emits new data from the database
     */
    fun updateList(newGenres: List<Genre>) {
        Log.d(TAG, "updateList: Old size=${list.size}, New size=${newGenres.size}")
        if (newGenres.isNotEmpty()) {
            Log.d(TAG, "First new genre: id=${newGenres.first().id}, name=${newGenres.first().name}, songCount=${newGenres.first().songCount}")
        }

        val diffCallback = GenresDiffCallback(list, newGenres)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        list.clear()
        list.addAll(newGenres)

        Log.d(TAG, "After update: list.size=${list.size}")
        diffResult.dispatchUpdatesTo(this)
        Log.d(TAG, "DiffUtil updates dispatched")
    }

    companion object {
        private const val TAG = "AdapterGenres"
    }

    /**
     * DiffUtil callback for efficient list updates
     */
    private class GenresDiffCallback(
            private val oldList: List<Genre>,
            private val newList: List<Genre>
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