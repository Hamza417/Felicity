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
import app.simple.felicity.databinding.AdapterStyleLabelsBinding
import app.simple.felicity.databinding.AdapterStyleListBinding
import app.simple.felicity.decorations.fastscroll.FastScrollAdapter
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.preferences.ComposerPreferences
import app.simple.felicity.repository.models.Artist

/**
 * Adapter for the Composers panel. Works just like [AdapterArtists] but reads
 * [ComposerPreferences] for layout mode so the panel has its own independent
 * display settings. Composers are represented using the [Artist] model.
 *
 * @author Hamza417
 */
class AdapterComposers(initial: List<Artist>) : FastScrollAdapter<VerticalListViewHolder>() {

    private var generalAdapterCallbacks: GeneralAdapterCallbacks? = null

    private val listUpdateCallback = object : ListUpdateCallback {
        override fun onInserted(position: Int, count: Int) {
            if (count > 100) notifyDataSetChanged() else notifyItemRangeInserted(position, count)
        }

        override fun onRemoved(position: Int, count: Int) {
            if (count > 100) notifyDataSetChanged() else notifyItemRangeRemoved(position, count)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            notifyItemMoved(fromPosition, toPosition)
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            notifyItemRangeChanged(position, count, payload)
        }
    }

    private val diffCallback = object : DiffUtil.ItemCallback<Artist>() {
        override fun areItemsTheSame(oldItem: Artist, newItem: Artist) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Artist, newItem: Artist) = oldItem == newItem
    }

    private val differ = AsyncListDiffer(
            listUpdateCallback,
            AsyncDifferConfig.Builder(diffCallback).build()
    )

    private val composers: List<Artist> get() = differ.currentList

    init {
        setHasStableIds(true)
        differ.submitList(initial.toList())
    }

    override fun getItemId(position: Int): Long = composers[position].id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            CommonPreferencesConstants.GRID_TYPE_GRID -> {
                GridHolder(AdapterStyleGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            CommonPreferencesConstants.GRID_TYPE_LABEL -> {
                LabelHolder(AdapterStyleLabelsBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            else -> {
                ListHolder(AdapterStyleListBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
        }
    }

    override fun onBind(holder: VerticalListViewHolder, position: Int, isLightBind: Boolean) {
        val composer = composers[position]
        when (holder) {
            is ListHolder -> holder.bind(composer, isLightBind)
            is GridHolder -> holder.bind(composer, isLightBind)
            is LabelHolder -> holder.bind(composer, isLightBind)
        }
    }

    override fun getItemCount(): Int = composers.size

    override fun getItemViewType(position: Int): Int {
        val mode = ComposerPreferences.getGridSize()
        return when {
            mode.isLabel -> CommonPreferencesConstants.GRID_TYPE_LABEL
            mode.isGrid -> CommonPreferencesConstants.GRID_TYPE_GRID
            else -> CommonPreferencesConstants.GRID_TYPE_LIST
        }
    }

    override fun onViewRecycled(holder: VerticalListViewHolder) {
        holder.itemView.clearAnimation()
        super.onViewRecycled(holder)
    }

    fun setGeneralAdapterCallbacks(callbacks: GeneralAdapterCallbacks) {
        this.generalAdapterCallbacks = callbacks
    }

    fun updateList(newComposers: List<Artist>) {
        differ.submitList(newComposers.toList())
    }

    inner class ListHolder(val binding: AdapterStyleListBinding) : VerticalListViewHolder(binding.root) {
        fun bind(composer: Artist, isLightBind: Boolean) {
            binding.title.setTextOrUnknown(composer.name)
            binding.secondaryDetail.setTextOrUnknown(
                    context.resources.getQuantityString(R.plurals.number_of_songs, composer.trackCount, composer.trackCount)
            )
            binding.tertiaryDetail.setTextOrUnknown(
                    context.resources.getQuantityString(R.plurals.number_of_albums, composer.albumCount, composer.albumCount)
            )
            if (isLightBind) return
            binding.cover.loadArtCoverWithPayload(item = composer)
            binding.container.setOnLongClickListener {
                generalAdapterCallbacks?.onComposerLongClicked(composers, bindingAdapterPosition, binding.cover)
                true
            }
            binding.container.setOnClickListener {
                generalAdapterCallbacks?.onComposerClicked(composers, bindingAdapterPosition, it)
            }
        }
    }

    inner class GridHolder(val binding: AdapterStyleGridBinding) : VerticalListViewHolder(binding.root) {
        fun bind(composer: Artist, isLightBind: Boolean) {
            binding.title.setTextOrUnknown(composer.name)
            binding.secondaryDetail.setTextOrUnknown(
                    context.resources.getQuantityString(R.plurals.number_of_songs, composer.trackCount, composer.trackCount)
            )
            binding.tertiaryDetail.setTextOrUnknown(composer.name)
            if (isLightBind) return
            binding.albumArt.loadArtCoverWithPayload(item = composer)
            binding.container.setOnLongClickListener {
                generalAdapterCallbacks?.onComposerLongClicked(composers, bindingAdapterPosition, binding.albumArt)
                true
            }
            binding.container.setOnClickListener {
                generalAdapterCallbacks?.onComposerClicked(composers, bindingAdapterPosition, it)
            }
        }
    }

    inner class LabelHolder(val binding: AdapterStyleLabelsBinding) : VerticalListViewHolder(binding.root) {
        fun bind(composer: Artist, isLightBind: Boolean) {
            binding.title.setTextOrUnknown(composer.name)
            binding.secondaryDetail.setTextOrUnknown(
                    context.resources.getQuantityString(R.plurals.number_of_songs, composer.trackCount, composer.trackCount)
            )
            binding.tertiaryDetail.setTextOrUnknown(
                    context.resources.getQuantityString(R.plurals.number_of_albums, composer.albumCount, composer.albumCount)
            )
            if (isLightBind) return
            binding.container.setOnLongClickListener {
                generalAdapterCallbacks?.onComposerLongClicked(composers, bindingAdapterPosition, null)
                true
            }
            binding.container.setOnClickListener {
                generalAdapterCallbacks?.onComposerClicked(composers, bindingAdapterPosition, it)
            }
        }
    }
}

