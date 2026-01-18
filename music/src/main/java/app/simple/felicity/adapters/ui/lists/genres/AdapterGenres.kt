package app.simple.felicity.adapters.ui.lists.genres

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.AdapterGenresListBinding
import app.simple.felicity.databinding.AdapterStyleGridBinding
import app.simple.felicity.databinding.AdapterStylePeristyleBinding
import app.simple.felicity.decorations.fastscroll.SlideFastScroller
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.ViewUtils.setSkeletonBackground
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.glide.util.AudioCoverUtils.loadPeristyleArtCover
import app.simple.felicity.preferences.GenresPreferences
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.shared.utils.ViewUtils.gone

class AdapterGenres(private val list: List<Genre>) :
        RecyclerView.Adapter<VerticalListViewHolder>(), SlideFastScroller.FastScrollBindingController {

    private var callbacks: GeneralAdapterCallbacks? = null
    private var isLightBindMode: Boolean = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            CommonPreferencesConstants.GRID_TYPE_LIST -> {
                ListHolder(
                        AdapterGenresListBinding.inflate(
                                LayoutInflater.from(parent.context),
                                parent,
                                false
                        )
                )
            }
            CommonPreferencesConstants.GRID_TYPE_GRID -> {
                GridHolder(
                        AdapterStyleGridBinding.inflate(
                                LayoutInflater.from(parent.context),
                                parent,
                                false
                        )
                )
            }
            CommonPreferencesConstants.GRID_TYPE_PERISTYLE -> {
                GridHolder(
                        AdapterStyleGridBinding.inflate(
                                LayoutInflater.from(parent.context),
                                parent,
                                false
                        )
                )
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        if (isLightBindMode.not()) {
            when (holder) {
                is GridHolder -> holder.bind(list[position])
                is ListHolder -> holder.bind(list[position])
                is PeristyleHolder -> holder.bind(list[position])
            }
        }
    }

    override fun getItemId(position: Int): Long {
        return list[position].id
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun getItemViewType(position: Int): Int {
        return GenresPreferences.getGridType()
    }

    inner class GridHolder(private val binding: AdapterStyleGridBinding) : VerticalListViewHolder(binding.root) {
        fun bind(genre: Genre) {
            binding.title.text = genre.name ?: context.getString(R.string.unknown)
            binding.albumArt.loadArtCoverWithPayload(genre)
            binding.tertiaryDetail.gone(false)
            binding.secondaryDetail.gone(false)

            binding.container.setOnClickListener {
                callbacks?.onGenreClicked(genre, it)
            }
        }

        init {
            binding.container.setSkeletonBackground(isLightBindMode)
        }
    }

    inner class ListHolder(private val binding: AdapterGenresListBinding) : VerticalListViewHolder(binding.root) {
        fun bind(genre: Genre) {
            binding.name.text = genre.name ?: context.getString(R.string.unknown)
            binding.cover.loadArtCoverWithPayload(genre)

            binding.container.setOnClickListener {
                callbacks?.onGenreClicked(genre, it)
            }
        }

        init {
            binding.container.setSkeletonBackground(isLightBindMode)
        }
    }

    inner class PeristyleHolder(private val binding: AdapterStylePeristyleBinding) : VerticalListViewHolder(binding.root) {
        fun bind(genre: Genre) {
            binding.title.text = genre.name ?: context.getString(R.string.unknown)
            binding.albumArt.loadPeristyleArtCover(genre)

            binding.container.setOnClickListener {
                callbacks?.onGenreClicked(genre, it)
            }
        }

        init {
            binding.container.setSkeletonBackground(isLightBindMode)
        }
    }

    fun setCallbackListener(listener: GeneralAdapterCallbacks) {
        this.callbacks = listener
    }

    override fun setLightBindMode(enabled: Boolean) {
        isLightBindMode = enabled
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, isLightBind: Boolean) {
        if (isLightBindMode) {
            return
        }

        onBindViewHolder(holder as VerticalListViewHolder, position)
    }

    override fun shouldHandleCustomBinding(): Boolean {
        return true
    }
}