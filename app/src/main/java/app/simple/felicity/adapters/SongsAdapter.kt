package app.simple.felicity.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.ripple.DynamicRippleConstraintLayout
import app.simple.felicity.decorations.typeface.TypeFaceTextView
import app.simple.felicity.glide.utils.AudioCoverUtil.loadFromUri
import app.simple.felicity.models.Audio

class SongsAdapter(private val audio: ArrayList<Audio>) : RecyclerView.Adapter<SongsAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(LayoutInflater.from(parent.context).inflate(R.layout.adapter_songs, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.art.loadFromUri(audio[position].artUri.toUri())
        holder.title.text = audio[position].title
        holder.artist.text = audio[position].artist
        holder.details.text = audio[position].album
    }

    override fun getItemCount(): Int {
        return audio.size
    }

    class Holder(itemView: View) : VerticalListViewHolder(itemView) {
        val art: ImageView = itemView.findViewById(R.id.album_art)
        val title: TypeFaceTextView = itemView.findViewById(R.id.title)
        val artist: TypeFaceTextView = itemView.findViewById(R.id.artist)
        val details: TypeFaceTextView = itemView.findViewById(R.id.details)
        val container: DynamicRippleConstraintLayout = itemView.findViewById(R.id.container)
    }
}