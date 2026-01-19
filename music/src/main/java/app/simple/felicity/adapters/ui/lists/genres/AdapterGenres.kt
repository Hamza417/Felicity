package app.simple.felicity.adapters.ui.lists.genres

import android.view.LayoutInflater
import android.view.ViewGroup
import app.simple.felicity.R
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.AdapterGenresListBinding
import app.simple.felicity.databinding.AdapterStyleGridBinding
import app.simple.felicity.databinding.AdapterStylePeristyleBinding
import app.simple.felicity.decorations.fastscroll.FastScrollAdapter
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.ViewUtils.clearSkeletonBackground
import app.simple.felicity.decorations.utils.ViewUtils.setSkeletonBackground
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.glide.util.AudioCoverUtils.loadPeristyleArtCover
import app.simple.felicity.preferences.GenresPreferences
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.shared.utils.ViewUtils.gone

class AdapterGenres(private val list: List<Genre>) : FastScrollAdapter<VerticalListViewHolder>() {

    private var callbacks: GeneralAdapterCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            CommonPreferencesConstants.GRID_TYPE_LIST -> {
                ListHolder(AdapterGenresListBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            CommonPreferencesConstants.GRID_TYPE_GRID -> {
                GridHolder(AdapterStyleGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            CommonPreferencesConstants.GRID_TYPE_PERISTYLE -> {
                PeristyleHolder(AdapterStylePeristyleBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBind(holder: VerticalListViewHolder, position: Int, isLightBind: Boolean) {
        val genre = list[position]
        when (holder) {
            is GridHolder -> holder.bind(genre, isLightBind)
            is ListHolder -> holder.bind(genre, isLightBind)
            is PeristyleHolder -> holder.bind(genre, isLightBind)
        }
    }

    override fun getItemId(position: Int): Long = list[position].id

    override fun getItemCount(): Int = list.size

    override fun getItemViewType(position: Int): Int = GenresPreferences.getGridType()

    inner class GridHolder(private val binding: AdapterStyleGridBinding) : VerticalListViewHolder(binding.root) {
        fun bind(genre: Genre, isLightBind: Boolean) {
            if (isLightBind) {
                binding.container.setSkeletonBackground(enable = true)
                return
            }
            binding.container.clearSkeletonBackground()
            binding.title.text = genre.name ?: context.getString(R.string.unknown)
            binding.albumArt.loadArtCoverWithPayload(genre)
            binding.tertiaryDetail.gone(false)
            binding.secondaryDetail.gone(false)
            binding.container.setOnClickListener {
                callbacks?.onGenreClicked(genre, it)
            }
        }
    }

    inner class ListHolder(private val binding: AdapterGenresListBinding) : VerticalListViewHolder(binding.root) {
        fun bind(genre: Genre, isLightBind: Boolean) {
            if (isLightBind) {
                binding.container.setSkeletonBackground(enable = true)
                return
            }
            binding.container.clearSkeletonBackground()
            binding.name.text = genre.name ?: context.getString(R.string.unknown)
            binding.cover.loadArtCoverWithPayload(genre)
            binding.container.setOnClickListener {
                callbacks?.onGenreClicked(genre, it)
            }
        }
    }

    inner class PeristyleHolder(private val binding: AdapterStylePeristyleBinding) : VerticalListViewHolder(binding.root) {
        fun bind(genre: Genre, isLightBind: Boolean) {
            if (isLightBind) {
                binding.container.setSkeletonBackground(enable = true)
                return
            }
            binding.container.clearSkeletonBackground()
            binding.title.text = genre.name ?: context.getString(R.string.unknown)
            binding.albumArt.loadPeristyleArtCover(genre)
            binding.container.setOnClickListener {
                callbacks?.onGenreClicked(genre, it)
            }
        }
    }

    fun setCallbackListener(listener: GeneralAdapterCallbacks) {
        this.callbacks = listener
    }
}