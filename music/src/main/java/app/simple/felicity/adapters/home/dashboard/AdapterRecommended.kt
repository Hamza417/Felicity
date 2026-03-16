package app.simple.felicity.adapters.home.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterGridImageBinding
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCover
import app.simple.felicity.repository.models.Audio

class AdapterRecommended(private val list: List<Audio>) :
        RecyclerView.Adapter<AdapterRecommended.Holder>() {

    private lateinit var callbacks: AdapterRecommendedCallbacks

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(AdapterGridImageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        if (list.isNotEmpty()) {
            val item = list[position]

            holder.binding.art.loadArtCover(
                    item = item,
                    shadow = false,
                    roundedCorners = false,
                    skipCache = true,
                    darken = false)

            holder.binding.container.setOnClickListener {
                if (list.isNotEmpty()) {
                    callbacks.onItemClicked(list, position)
                }
            }

            holder.binding.title.text = item.title
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun getItemId(position: Int): Long {
        return list[position].id.toLong()
    }

    inner class Holder(val binding: AdapterGridImageBinding) : RecyclerView.ViewHolder(binding.root)

    fun randomize() {
        for (i in 0 until itemCount) {
            // Notify position change
            notifyItemChanged(i)
        }
    }

    fun updateItem(position: Int) {
        notifyItemChanged(position)
    }

    fun setCallbacks(callbacks: AdapterRecommendedCallbacks) {
        this.callbacks = callbacks
    }

    companion object {

        interface AdapterRecommendedCallbacks {
            fun onItemClicked(items: List<Audio>, position: Int)
        }
    }
}
