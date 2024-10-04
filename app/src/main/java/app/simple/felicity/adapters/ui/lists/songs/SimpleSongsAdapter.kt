package app.simple.felicity.adapters.ui.lists.songs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterSimpleSongsBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.TextViewUtils.setTextOrUnknown
import app.simple.felicity.glide.pathcover.Utils.loadFromPath
import app.simple.felicity.models.normal.Audio
import com.bumptech.glide.Glide

class SimpleSongsAdapter(private val audio: ArrayList<Audio>) : RecyclerView.Adapter<SimpleSongsAdapter.Holder>() {

    var onItemClickListener: ((Audio, Int, View) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(AdapterSimpleSongsBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(audio[position])
    }

    override fun getItemCount(): Int {
        return audio.size
    }

    override fun onViewRecycled(holder: Holder) {
        super.onViewRecycled(holder)
        Glide.with(holder.binding.albumArt).clear(holder.binding.albumArt)
    }

    inner class Holder(val binding: AdapterSimpleSongsBinding) : VerticalListViewHolder(binding.root) {
        fun bind(audio: Audio) {
            binding.apply {
                // albumArt.transitionName = audio.fileUri
                title.setTextOrUnknown(audio.title)
                artist.setTextOrUnknown(audio.artist)
                details.setTextOrUnknown(audio.album)

                albumArt.loadFromPath(audio.path)
                albumArt.transitionName = audio.path

                binding.container.radius = 0F
            }

            binding.root.setOnClickListener {
                onItemClickListener?.invoke(audio, bindingAdapterPosition, binding.albumArt)
            }
        }
    }
}
