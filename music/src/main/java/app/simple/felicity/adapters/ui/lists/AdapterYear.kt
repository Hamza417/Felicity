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
import app.simple.felicity.databinding.AdapterStyleGridBinding
import app.simple.felicity.databinding.AdapterYearListBinding
import app.simple.felicity.decorations.fastscroll.FastScrollAdapter
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.preferences.YearPreferences
import app.simple.felicity.repository.models.YearGroup
import app.simple.felicity.shared.utils.ViewUtils.gone

class AdapterYear(initial: List<YearGroup>) : FastScrollAdapter<VerticalListViewHolder>() {

    private var callbacks: GeneralAdapterCallbacks? = null

    private val listUpdateCallback = object : ListUpdateCallback {
        override fun onInserted(position: Int, count: Int) {
            if (count > 100) notifyDataSetChanged()
            else notifyItemRangeInserted(position, count)
        }

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

    private val diffCallback = object : DiffUtil.ItemCallback<YearGroup>() {
        override fun areItemsTheSame(oldItem: YearGroup, newItem: YearGroup) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: YearGroup, newItem: YearGroup) = oldItem == newItem
    }

    private val differ = AsyncListDiffer(
            listUpdateCallback,
            AsyncDifferConfig.Builder(diffCallback).build()
    )

    private val list: List<YearGroup> get() = differ.currentList

    init {
        setHasStableIds(true)
        differ.submitList(initial.toList())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            CommonPreferencesConstants.GRID_TYPE_LIST -> {
                ListHolder(AdapterYearListBinding.inflate(LayoutInflater.from(parent.context), parent, false))
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
        val yearGroup = list[position]
        when (holder) {
            is GridHolder -> holder.bind(yearGroup, isLightBind)
            is ListHolder -> holder.bind(yearGroup, isLightBind)
        }
    }

    override fun getItemId(position: Int): Long = list[position].id
    override fun getItemCount(): Int = list.size
    override fun getItemViewType(position: Int): Int = YearPreferences.getGridType()

    inner class GridHolder(private val binding: AdapterStyleGridBinding) : VerticalListViewHolder(binding.root) {
        fun bind(yearGroup: YearGroup, isLightBind: Boolean) {
            binding.title.text = yearGroup.year
            binding.secondaryDetail.text = context.resources.getQuantityString(
                    R.plurals.number_of_songs, yearGroup.songCount, yearGroup.songCount)
            binding.tertiaryDetail.gone(false)
            if (isLightBind) return
            binding.albumArt.loadArtCoverWithPayload(yearGroup)
            binding.container.setOnClickListener { callbacks?.onYearGroupClicked(yearGroup, it) }
        }
    }

    inner class ListHolder(private val binding: AdapterYearListBinding) : VerticalListViewHolder(binding.root) {
        fun bind(yearGroup: YearGroup, isLightBind: Boolean) {
            binding.name.text = yearGroup.year
            binding.songCount.text = context.resources.getQuantityString(
                    R.plurals.number_of_songs, yearGroup.songCount, yearGroup.songCount)
            if (isLightBind) return
            binding.cover.loadArtCoverWithPayload(yearGroup)
            binding.container.setOnClickListener { callbacks?.onYearGroupClicked(yearGroup, it) }
        }
    }

    fun setCallbackListener(listener: GeneralAdapterCallbacks) {
        this.callbacks = listener
    }

    fun updateList(newYears: List<YearGroup>) {
        differ.submitList(newYears.toList())
    }
}

