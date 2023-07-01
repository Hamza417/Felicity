package app.simple.felicity.adapters.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.net.toUri
import app.simple.felicity.databinding.AdapterPlayerSliderBinding
import app.simple.felicity.glide.utils.AudioCoverUtil.loadFromFileDescriptor
import app.simple.felicity.models.Audio
import com.smarteist.autoimageslider.SliderViewAdapter

class DefaultPlayerAdapter(private val data: ArrayList<Audio>) : SliderViewAdapter<DefaultPlayerAdapter.Holder>() {

    inner class Holder(private val binding: AdapterPlayerSliderBinding) : ViewHolder(binding.root) {
        fun bind(audio: Audio) {
            binding.apply {
                art.loadFromFileDescriptor(audio.fileUri.toUri())
            }
        }
    }

    override fun getCount(): Int {
        return data.size
    }

    override fun onCreateViewHolder(parent: ViewGroup?): Holder {
        val binding = AdapterPlayerSliderBinding.inflate(LayoutInflater.from(parent?.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(data[position])
    }
}