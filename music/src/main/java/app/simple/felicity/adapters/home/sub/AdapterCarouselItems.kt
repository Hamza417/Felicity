package app.simple.felicity.adapters.home.sub

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.core.maths.Lerp.lerp
import app.simple.felicity.databinding.AdapterCarouselBinding
import app.simple.felicity.decorations.overscroll.HorizontalListViewHolder
import app.simple.felicity.glide.genres.GenreCoverModel
import app.simple.felicity.glide.uricover.UriCoverModel
import app.simple.felicity.models.ArtFlowData
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.Song
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel

class AdapterCarouselItems(private val data: ArtFlowData<Any>) : RecyclerView.Adapter<AdapterCarouselItems.Holder>() {

    private var adapterCarouselCallbacks: AdapterCarouselCallbacks? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = AdapterCarouselBinding.inflate(
                LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount(): Int {
        return data.items.size
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        if (data.items.isNotEmpty()) {
            val item = data.items[position]

            when (item) {
                is Song -> {
                    Glide.with(holder.binding.art)
                        .asBitmap()
                        .load(UriCoverModel(
                                holder.getContext(),
                                item.artworkUri!!
                        ))
                        .dontTransform()
                        .dontAnimate()
                        .into(holder.binding.art)

                    holder.binding.title.text = item.title ?: holder.getContext().getString(R.string.unknown)
                    holder.binding.artist.text = item.artist ?: holder.getContext().getString(R.string.unknown)
                }
                is Album -> {
                    Glide.with(holder.binding.art)
                        .asBitmap()
                        .load(UriCoverModel(
                                holder.getContext(),
                                (data.items[position] as Album).artworkUri!!
                        ))
                        .dontTransform()
                        .dontAnimate()
                        .into(holder.binding.art)

                    holder.binding.title.text = item.name ?: holder.getContext().getString(R.string.unknown)
                    holder.binding.artist.text = item.artist ?: holder.getContext().getString(R.string.unknown)
                }
                is Artist -> {
                    Glide.with(holder.binding.art)
                        .asBitmap()
                        .load(UriCoverModel(
                                holder.getContext(),
                                item.artworkUri ?: Uri.EMPTY)
                        )
                        .dontTransform()
                        .dontAnimate()
                        .into(holder.binding.art)

                    holder.binding.title.text = item.name ?: holder.getContext().getString(R.string.unknown)
                }
                is Genre -> {
                    Glide.with(holder.binding.art)
                        .asBitmap()
                        .load(GenreCoverModel(
                                holder.getContext(),
                                (data.items[position] as Genre).id,
                                (data.items[position] as Genre).name
                                    ?: holder.getContext().getString(R.string.unknown)
                        ))
                        .transform(CenterCrop())
                        .dontAnimate()
                        .into(holder.binding.art)

                    holder.binding.title.text = item.name ?: holder.getContext().getString(R.string.unknown)
                    holder.binding.container.transitionName = item.toString()
                }
            }

            holder.binding.container.setOnClickListener {
                adapterCarouselCallbacks?.onClicked(holder.binding.container, position)
            }
        }
    }

    fun setAdapterCarouselCallbacks(callbacks: AdapterCarouselCallbacks) {
        this.adapterCarouselCallbacks = callbacks
    }

    inner class Holder(val binding: AdapterCarouselBinding) : HorizontalListViewHolder(binding.root) {
        init {
            binding.container.setOnMaskChangedListener { maskRect ->
                binding.title.translationX = maskRect.left
                binding.title.setAlpha(lerp(1F, 0F, 0F, 80F, maskRect.left))
                binding.artist.translationX = maskRect.left
                binding.artist.setAlpha(lerp(1F, 0F, 0F, 80F, maskRect.left))
            }

            binding.container.shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setAllCorners(CornerFamily.ROUNDED, AppearancePreferences.getCornerRadius())
                .build()
        }
    }

    companion object {
        interface AdapterCarouselCallbacks {
            fun onClicked(view: View, position: Int)
        }
    }
}