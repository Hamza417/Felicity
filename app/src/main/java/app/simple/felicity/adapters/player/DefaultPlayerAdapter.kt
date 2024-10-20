package app.simple.felicity.adapters.player

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterPlayerSliderBinding
import app.simple.felicity.repository.models.normal.Audio

class DefaultPlayerAdapter(private val data: ArrayList<Audio>) : RecyclerView.Adapter<DefaultPlayerAdapter.Holder>() {

    inner class Holder(val binding: AdapterPlayerSliderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(audio: Audio) {
            binding.apply {
                //                art.transitionName = audio.fileUri
                //                art.loadFromFileDescriptorFullScreen(audio.fileUri.toUri())
            }
        }
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = AdapterPlayerSliderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(data[position])
    }
}
