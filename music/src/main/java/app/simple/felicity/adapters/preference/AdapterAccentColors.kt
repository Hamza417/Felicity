package app.simple.felicity.adapters.preference

import android.annotation.SuppressLint
import android.view.LayoutInflater
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.databinding.AdapterAccentColorsBinding
import app.simple.felicity.databinding.AdapterPreferenceHeaderBinding
import app.simple.felicity.decorations.overscroll.RecyclerViewUtils
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.glide.util.AudioCoverUtils.loadPlainArtCover
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.ViewUtils
import app.simple.felicity.shared.utils.ViewUtils.gone
import app.simple.felicity.shared.utils.ViewUtils.visible
import app.simple.felicity.theme.accents.AlbumArt
import app.simple.felicity.theme.managers.ThemeManager

class AdapterAccentColors : RecyclerView.Adapter<VerticalListViewHolder>() {

    private val colors = ThemeManager.getAllAccents()

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            RecyclerViewUtils.TYPE_HEADER -> {
                Header(AdapterPreferenceHeaderBinding
                           .inflate(LayoutInflater.from(parent.context), parent, false))
            }
            RecyclerViewUtils.TYPE_ITEM -> {
                Holder(AdapterAccentColorsBinding
                           .inflate(LayoutInflater.from(parent.context), parent, false))
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        when (holder) {
            is Header -> {
                holder.binding.title.setText(R.string.accent_color)
                holder.binding.summary.setText(R.string.accent_color_summary)
            }
            is Holder -> {
                val accent = colors[position - 1]

                if (accent.identifier == AlbumArt.IDENTIFIER) {
                    holder.binding.secondaryColor.gone()
                    holder.binding.primaryColor.loadPlainArtCover(MediaManager.getCurrentSong() ?: Audio())
                } else {
                    holder.binding.primaryColor.setBackgroundColor(accent.primaryAccentColor)
                    holder.binding.secondaryColor.setBackgroundColor(accent.secondaryAccentColor)
                    holder.binding.primaryColor.visible(false)
                }

                holder.binding.name.text = accent.identifier
                holder.binding.palette.text = accent.hexes
                ViewUtils.addShadow(holder.binding.container, accent.primaryAccentColor)

                holder.itemView.setOnClickListener {
                    AppearancePreferences.setAccentColorName(accent.identifier)
                    notifyDataSetChanged()
                }

                if (accent.identifier == AppearancePreferences.getAccentColorName()) {
                    holder.binding.name.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_ring_12dp, 0)
                } else {
                    holder.binding.name.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return colors.size + 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) {
            RecyclerViewUtils.TYPE_HEADER
        } else {
            RecyclerViewUtils.TYPE_ITEM
        }
    }

    fun reloadAlbumArt() {
        val albumArtAccentIndex = colors.indexOfFirst { it.identifier == AlbumArt.IDENTIFIER }
        if (albumArtAccentIndex != -1) {
            notifyItemChanged(albumArtAccentIndex + 1) // +1 for header
        }
    }

    inner class Holder(val binding: AdapterAccentColorsBinding) : VerticalListViewHolder(binding.root)

    inner class Header(val binding: AdapterPreferenceHeaderBinding) : VerticalListViewHolder(binding.root)
}