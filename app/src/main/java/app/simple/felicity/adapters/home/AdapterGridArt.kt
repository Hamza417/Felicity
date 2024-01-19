package app.simple.felicity.adapters.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterGridImageBinding
import app.simple.felicity.glide.utils.AudioCoverUtil.loadFromUri
import app.simple.felicity.models.Audio

class AdapterGridArt(private val data: ArrayList<Audio>) :
    RecyclerView.Adapter<AdapterGridArt.Holder>() {

    private var adapterGridImageBinding: AdapterGridImageBinding? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        adapterGridImageBinding =
            AdapterGridImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(adapterGridImageBinding!!.root)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(adapterGridImageBinding!!)
    }

    override fun getItemCount(): Int {
        return data.size.coerceAtMost(12) //(data.size % 3).coerceAtMost(10)
    }

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(adapterGridImageBinding: AdapterGridImageBinding) {
            adapterGridImageBinding.art.loadFromUri(data[bindingAdapterPosition].artUri.toUri())
        }
    }
}