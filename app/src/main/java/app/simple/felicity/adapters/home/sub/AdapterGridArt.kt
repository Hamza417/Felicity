package app.simple.felicity.adapters.home.sub

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterGridImageBinding
import app.simple.felicity.models.home.Home

class AdapterGridArt(private val data: ArrayList<Home>) :
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
        return (data.size / 3).coerceAtMost(9)
    }

    override fun getItemId(position: Int): Long {
        return data[position].title.toLong()
    }

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var adapterGridImageBinding: AdapterGridImageBinding? = null
        fun bind(adapterGridImageBinding: AdapterGridImageBinding) {
            this.adapterGridImageBinding = adapterGridImageBinding

        }
    }

    fun randomize() {
        val copy = data.toList()
        data.shuffle()
        for (i in 0 until itemCount) {
            // Notify position change
            notifyItemChanged(i)
        }
    }
}
