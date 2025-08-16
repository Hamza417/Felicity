package app.simple.felicity.adapters.artflow

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterCarouselFlowBinding
import app.simple.felicity.decorations.overscroll.HorizontalListViewHolder
import app.simple.felicity.glide.pathcover.Utils.loadFromPathForCarousel
import app.simple.felicity.glide.pathcover.Utils.loadReflection
import app.simple.felicity.repository.models.Song
import com.bumptech.glide.Glide

class ArtFlowRvAdapter(private val data: List<Song>) : RecyclerView.Adapter<ArtFlowRvAdapter.Holder>() {

    private var callback: AdapterCarouselFlowCallback? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(AdapterCarouselFlowBinding
                          .inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.binding.art.loadFromPathForCarousel(data[position].path)
        holder.binding.reflection.loadReflection(data[position].path)
    }

    override fun onViewRecycled(holder: Holder) {
        super.onViewRecycled(holder)
        Glide.with(holder.itemView.context).clear(holder.binding.art)
    }

    inner class Holder(val binding: AdapterCarouselFlowBinding) : HorizontalListViewHolder(binding.root) {
        init {
            binding.art.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    callback?.onSongClicked(data[position], position, binding.art)
                }
            }
        }
    }

    fun setOnCarouselFlowCallbackListener(callback: AdapterCarouselFlowCallback) {
        this.callback = callback
    }

    companion object {
        interface AdapterCarouselFlowCallback {
            fun onSongClicked(song: Song, position: Int, view: View)
        }
    }
}
