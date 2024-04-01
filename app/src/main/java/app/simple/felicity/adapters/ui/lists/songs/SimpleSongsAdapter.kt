package app.simple.felicity.adapters.ui.lists.songs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.databinding.AdapterSimpleSongsBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.glide.utils.AudioCoverUtil.loadFromUri
import app.simple.felicity.models.normal.Audio

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

    inner class Holder(val binding: AdapterSimpleSongsBinding) : VerticalListViewHolder(binding.root) {
        fun bind(audio: Audio) {
            binding.apply {
                // albumArt.transitionName = audio.fileUri
                title.text = audio.title ?: getString(R.string.unknown)
                artist.text = audio.artist ?: getString(R.string.unknown)
                details.text = audio.album ?: getString(R.string.unknown)

                albumArt.loadFromUri(audio.artUri)
            }

            binding.root.setOnClickListener {
                onItemClickListener?.invoke(audio, bindingAdapterPosition, binding.albumArt)
            }
        }
    }
}
