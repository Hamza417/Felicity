package app.simple.felicity.adapters.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterSongsBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.glide.utils.AudioCoverUtil.loadFromUri
import app.simple.felicity.models.Audio

class SongsAdapter(private val audio: ArrayList<Audio>) : RecyclerView.Adapter<SongsAdapter.Holder>() {

    var onItemClickListener: ((Audio, Int) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = AdapterSongsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(audio[position])
    }

    override fun getItemCount(): Int {
        return audio.size
    }

    inner class Holder(private val binding: AdapterSongsBinding) : VerticalListViewHolder(binding.root) {
        fun bind(audio: Audio) {
            binding.apply {
                title.text = audio.title
                artist.text = audio.artist
                details.text = audio.album
                albumArt.loadFromUri(audio.artUri.toUri())
            }

            binding.root.setOnClickListener {
                onItemClickListener?.invoke(audio, bindingAdapterPosition)
            }
        }
    }
}