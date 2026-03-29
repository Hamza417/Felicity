package app.simple.felicity.adapters.ui.lists

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterMilkdropPagerPageBinding
import app.simple.felicity.milkdrop.models.MilkdropPreset

/**
 * [RecyclerView.Adapter] that backs the ViewPager2 preset pager on the Milkdrop screen.
 *
 * Each page displays only the human-readable preset name centered in white text so
 * it remains legible against the semi-transparent gradient overlay.  Tapping any page
 * invokes [onPageClicked] so the host fragment can refresh the overlay fade timer.
 *
 * Call [submitList] whenever the preset list changes.
 *
 * @param onPageClicked Callback invoked on the main thread when the user taps a page.
 *
 * @author Hamza417
 */
class AdapterMilkdropPager(
        private val onPageClicked: () -> Unit,
) : RecyclerView.Adapter<AdapterMilkdropPager.PageViewHolder>() {

    private var presets: List<MilkdropPreset> = emptyList()

    /**
     * Replaces the displayed preset list and triggers a full redraw.
     *
     * @param list The updated sorted preset list.
     */
    fun submitList(list: List<MilkdropPreset>) {
        presets = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = AdapterMilkdropPagerPageBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
        )
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(presets[position])
    }

    override fun getItemCount(): Int = presets.size

    /**
     * ViewHolder for a single preset pager page.
     *
     * @param binding View binding for the page layout.
     */
    inner class PageViewHolder(
            private val binding: AdapterMilkdropPagerPageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        /** Binds [preset] data to the page and attaches the click callback. */
        fun bind(preset: MilkdropPreset) {
            binding.presetName.text = preset.name
            binding.root.setOnClickListener { onPageClicked() }
        }
    }
}

