package app.simple.felicity.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import app.simple.felicity.R
import app.simple.felicity.decorations.typeface.TypeFaceTextView
import app.simple.felicity.decorations.views.SquareImageView
import app.simple.felicity.glide.utils.AudioCoverUtil.loadFromUri
import app.simple.felicity.models.Audio
import com.smarteist.autoimageslider.SliderViewAdapter

class ArtFlowAdapter(private val data: ArrayList<Audio>) : SliderViewAdapter<ArtFlowAdapter.ArtFlowViewHolder>() {

    inner class ArtFlowViewHolder(itemView: View) : ViewHolder(itemView) {
        val art: SquareImageView = itemView.findViewById(R.id.art)
        val title: TypeFaceTextView = itemView.findViewById(R.id.title)
        val artist: TypeFaceTextView = itemView.findViewById(R.id.artist)
    }

    override fun getCount(): Int {
        return data.size.coerceAtMost(maximumValue = 25)
    }

    override fun onCreateViewHolder(parent: ViewGroup?): ArtFlowViewHolder {
        return ArtFlowViewHolder(LayoutInflater.from(parent?.context).inflate(R.layout.adapter_art_flow, parent, false))
    }

    override fun onBindViewHolder(viewHolder: ArtFlowViewHolder, position: Int) {
        viewHolder.title.text = data[position].title
        viewHolder.artist.text = data[position].artist
        viewHolder.art.loadFromUri(data[position].artUri.toUri())
    }
}