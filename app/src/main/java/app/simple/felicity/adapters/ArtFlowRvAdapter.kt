package app.simple.felicity.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.decorations.overscroll.HorizontalListViewHolder
import app.simple.felicity.glide.utils.AudioCoverUtil.loadFromFileDescriptor
import app.simple.felicity.models.Audio

class ArtFlowRvAdapter(private val data: ArrayList<Audio>) : RecyclerView.Adapter<ArtFlowRvAdapter.Holder>() {

    inner class Holder(itemView: View) : HorizontalListViewHolder(itemView) {
        val art: ImageView = itemView.findViewById(R.id.art)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(LayoutInflater.from(parent.context).inflate(R.layout.adpater_art_flow_rv, parent, false))
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.art.loadFromFileDescriptor(data[position].fileUri.toUri())
    }
}