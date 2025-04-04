package app.simple.felicity.adapters.home.sub

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.R
import app.simple.felicity.decorations.typeface.TypeFaceTextView
import app.simple.felicity.decorations.views.SquareImageView
import app.simple.felicity.glide.pathcover.PathCoverModel
import app.simple.felicity.glide.utils.HomeUtils
import app.simple.felicity.repository.models.home.Home
import app.simple.felicity.repository.models.home.HomeAudio
import com.bumptech.glide.Glide
import com.smarteist.autoimageslider.SliderViewAdapter

class ArtFlowAdapter(private val data: Home) : SliderViewAdapter<ArtFlowAdapter.ArtFlowViewHolder>() {

    inner class ArtFlowViewHolder(itemView: View) : ViewHolder(itemView) {
        val art: SquareImageView = itemView.findViewById(R.id.art)
        val title: TypeFaceTextView = itemView.findViewById(R.id.title)
        val artist: TypeFaceTextView = itemView.findViewById(R.id.artist)

        fun getContext(): Context {
            return itemView.context
        }
    }

    override fun getCount(): Int {
        return HomeUtils.getHomeDataSize(data).coerceAtMost(25)
    }

    override fun onCreateViewHolder(parent: ViewGroup?): ArtFlowViewHolder {
        return ArtFlowViewHolder(LayoutInflater.from(parent?.context).inflate(R.layout.adapter_art_flow, parent, false))
    }

    override fun onBindViewHolder(viewHolder: ArtFlowViewHolder, position: Int) {
        Glide.with(viewHolder.art)
            .asBitmap()
            .load(PathCoverModel(viewHolder.getContext(), (data as HomeAudio).audios[position].path))
            .dontTransform()
            .dontAnimate()
            .into(viewHolder.art)
    }
}
