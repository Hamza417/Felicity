package app.simple.felicity.adapters.home.sub

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.core.R
import app.simple.felicity.databinding.AdapterGridImageBinding
import app.simple.felicity.databinding.AdapterGridPanelButtonBinding
import app.simple.felicity.decorations.ripple.RippleUtils
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCover
import app.simple.felicity.models.ArtFlowData
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.Song

class AdapterGridArt(private val data: ArtFlowData<Any>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private lateinit var callbacks: AdapterGridArtCallbacks

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_BUTTON -> Button(AdapterGridPanelButtonBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            TYPE_IMAGE -> Holder(AdapterGridImageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is Holder -> {
                if (data.items.isNotEmpty()) {
                    val shuffledList = data.items.shuffled()
                    val item = shuffledList[position]

                    holder.binding.art.loadArtCover(
                            item = item,
                            shadow = false,
                            roundedCorners = false,
                            skipCache = true,
                            darken = true)

                    holder.binding.container.setOnClickListener {
                        if (data.items.isNotEmpty()) {
                            callbacks.onItemClicked(shuffledList, position)
                        }
                    }

                    when (item) {
                        is Song -> {
                            holder.binding.title.text = item.title
                        }
                        is Album -> {
                            holder.binding.title.text = item.artist
                        }
                        is Artist -> {
                            holder.binding.title.text = item.name
                        }
                        is Genre -> {
                            holder.binding.title.text = item.name
                        }
                    }
                }
            }
            is Button -> {
                if (data.items.isNotEmpty()) {
                    val item = data.items[position]

                    when (item) {
                        is Song -> {
                            holder.binding.title.text = holder.binding.root.context.getString(R.string.songs)
                        }
                        is Album -> {
                            holder.binding.title.text = holder.binding.root.context.getString(R.string.albums)
                        }
                        is Artist -> {
                            holder.binding.title.text = holder.binding.root.context.getString(R.string.artists)
                        }
                        is Genre -> {
                            holder.binding.title.text = holder.binding.root.context.getString(R.string.genres)
                        }
                    }

                    holder.binding.container.setOnClickListener {
                        callbacks.onButtonClicked(data.title)
                    }
                }
            }
            else -> throw IllegalArgumentException("Invalid view holder type")
        }
    }

    override fun getItemCount(): Int {
        return data.items.size.coerceAtMost(9)
    }

    override fun getItemId(position: Int): Long {
        return data.title.toLong()
    }

    override fun getItemViewType(position: Int): Int {
        return if (itemCount == data.items.size.coerceAtMost(9) && position == itemCount - 1) {
            TYPE_BUTTON
        } else {
            TYPE_IMAGE
        }
    }

    inner class Holder(val binding: AdapterGridImageBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.title.setTextColor(Color.LTGRAY)
        }
    }

    inner class Button(val binding: AdapterGridPanelButtonBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            RippleUtils.setForegroundDrawable(binding.container)

            binding.container.setOnClickListener {

            }
        }
    }

    fun randomize() {
        for (i in 0 until itemCount) {
            // Notify position change
            notifyItemChanged(i)
        }
    }

    fun updateItem(position: Int) {
        notifyItemChanged(position)
    }

    fun setCallbacks(callbacks: AdapterGridArtCallbacks) {
        this.callbacks = callbacks
    }

    companion object {
        private const val TYPE_BUTTON = 0
        private const val TYPE_IMAGE = 1

        interface AdapterGridArtCallbacks {
            fun onItemClicked(items: List<Any>, position: Int)
            fun onItemLongClicked(item: Any)
            fun onButtonClicked(title: Int)
        }
    }
}
