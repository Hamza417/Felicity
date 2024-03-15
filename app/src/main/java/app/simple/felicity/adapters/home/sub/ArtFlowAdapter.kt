package app.simple.felicity.adapters.home.sub

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.R
import app.simple.felicity.decorations.typeface.TypeFaceTextView
import app.simple.felicity.decorations.views.SquareImageView
import app.simple.felicity.models.home.Home
import app.simple.felicity.models.home.HomeUtils
import com.smarteist.autoimageslider.SliderViewAdapter

class ArtFlowAdapter(private val data: Home) : SliderViewAdapter<ArtFlowAdapter.ArtFlowViewHolder>() {

    inner class ArtFlowViewHolder(itemView: View) : ViewHolder(itemView) {
        val art: SquareImageView = itemView.findViewById(R.id.art)
        val title: TypeFaceTextView = itemView.findViewById(R.id.title)
        val artist: TypeFaceTextView = itemView.findViewById(R.id.artist)
    }

    override fun getCount(): Int {
        return HomeUtils.getHomeDataSize(data).coerceAtMost(25)
    }

    override fun onCreateViewHolder(parent: ViewGroup?): ArtFlowViewHolder {
        return ArtFlowViewHolder(LayoutInflater.from(parent?.context).inflate(R.layout.adapter_art_flow, parent, false))
    }

    override fun onBindViewHolder(viewHolder: ArtFlowViewHolder, position: Int) {
        HomeUtils.loadHomeAlbumArt(data, viewHolder.art, position)
    }
}
