package app.simple.felicity.adapters.loader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterLoaderBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import java.io.File

class AdapterLoader : RecyclerView.Adapter<AdapterLoader.Holder>() {

    private val data = mutableListOf<File>()
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(AdapterLoaderBinding.inflate(LayoutInflater.from(parent.context), parent, false).root)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(holder.binding)

    }

    override fun getItemCount(): Int {
        return data.size
    }

    inner class Holder(itemView: View) : VerticalListViewHolder(itemView) {
        val binding = AdapterLoaderBinding.bind(itemView)

        fun bind(adapterLoaderBinding: AdapterLoaderBinding) {
            val file = data[bindingAdapterPosition]
            adapterLoaderBinding.data.text = file.name
        }
    }

    fun updateFile(file: File) {
        data.add(file)
        notifyItemInserted((data.size - 1).coerceAtLeast(0))
    }
}
