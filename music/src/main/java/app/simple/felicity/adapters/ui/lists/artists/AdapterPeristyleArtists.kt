package app.simple.felicity.adapters.ui.lists.artists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterArtistsPeristyleBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCover
import app.simple.felicity.repository.models.Artist

class AdapterPeristyleArtists(private val artists: List<Artist>) : RecyclerView.Adapter<AdapterPeristyleArtists.Holder>() {

    private var listener: AdapterPeristyleArtistsListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = AdapterArtistsPeristyleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding.root)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(AdapterArtistsPeristyleBinding.bind(holder.itemView))
    }

    override fun getItemCount(): Int {
        return artists.size
    }

    inner class Holder(itemView: View) : VerticalListViewHolder(itemView) {
        var binding: AdapterArtistsPeristyleBinding? = null

        fun bind(binding: AdapterArtistsPeristyleBinding) {
            this.binding = binding
            binding.title.text = artists[bindingAdapterPosition].name
            binding.artistArt.loadArtCover(artists[bindingAdapterPosition], crop = true, roundedCorners = false, skipCache = true)

            binding.container.setOnClickListener {
                listener?.onArtistClick(artists[bindingAdapterPosition], it)
            }
        }
    }

    fun setAdapterPeristyleArtistsListener(listener: AdapterPeristyleArtistsListener) {
        this.listener = listener
    }

    companion object {
        const val TAG = "AdapterPeristyleArtists"

        interface AdapterPeristyleArtistsListener {
            fun onArtistClick(artist: Artist, view: View)
        }
    }
}
