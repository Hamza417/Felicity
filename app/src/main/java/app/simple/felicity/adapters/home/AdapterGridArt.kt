package app.simple.felicity.adapters.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterGridImageBinding
import app.simple.felicity.glide.utils.AudioCoverUtil.loadFromUriWithAnimation
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
        return (data.size / 3).coerceAtMost(9)
    }

    override fun getItemId(position: Int): Long {
        return data[position].id
    }

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var adapterGridImageBinding: AdapterGridImageBinding? = null
        fun bind(adapterGridImageBinding: AdapterGridImageBinding) {
            this.adapterGridImageBinding = adapterGridImageBinding
            adapterGridImageBinding.art.loadFromUriWithAnimation(data[bindingAdapterPosition].artUri.toUri())
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

    /**
     * Replace any one item from the top 9 items with a random item
     * from the remaining items in the list.
     */
    fun randomizeAnyOne() {
        val randomIndex = (0 until itemCount).random()
        val randomItem = data.subList(itemCount, data.size).random()
        val replacedItem = data[randomIndex]
        val replacedIndex = data.indexOf(randomItem)
        data[randomIndex] = randomItem
        data[replacedIndex] = replacedItem
        notifyItemChanged(randomIndex)
    }

    /**
     * Replace any two items from the top 9 items with random items
     * from the remaining items in the list.
     */
    fun randomizeAnyTwo() {
        val randomIndexOne = (0 until itemCount).random()
        val randomIndexTwo = (0 until itemCount).random()
        val randomItemOne = data.subList(itemCount, data.size).random()
        val randomItemTwo = data.subList(itemCount, data.size).random()
        val replacedItemOne = data[randomIndexOne]
        val replacedItemTwo = data[randomIndexTwo]
        val replacedIndexOne = data.indexOf(randomItemOne)
        val replacedIndexTwo = data.indexOf(randomItemTwo)
        data[randomIndexOne] = randomItemOne
        data[randomIndexTwo] = randomItemTwo
        data[replacedIndexOne] = replacedItemOne
        data[replacedIndexTwo] = replacedItemTwo
        notifyItemChanged(randomIndexOne)
        notifyItemChanged(randomIndexTwo)
    }
}