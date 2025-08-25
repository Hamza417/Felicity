package app.simple.felicity.adapters.preference

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.databinding.AdapterPreferenceHeaderBinding
import app.simple.felicity.databinding.AdapterTypefaceItemBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.typeface.TypeFace
import app.simple.felicity.decorations.typeface.TypefaceStyle
import app.simple.felicity.decorations.utils.RecyclerViewUtils
import app.simple.felicity.preferences.AppearancePreferences

class AdapterTypefaceItem : RecyclerView.Adapter<VerticalListViewHolder>() {

    private val typefaces = TypeFace.list

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        when (viewType) {
            RecyclerViewUtils.TYPE_HEADER -> {
                val binding = AdapterPreferenceHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return Header(binding)
            }
            RecyclerViewUtils.TYPE_ITEM -> {
                val binding = AdapterTypefaceItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return Holder(binding)
            }
            else -> throw IllegalStateException("unknown view type")
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        if (holder is Holder) {
            val typeface = typefaces[position - 1]
            holder.binding.name.text = typeface.typefaceName
            holder.binding.type.text = typeface.type
            holder.binding.license.text = typeface.license
            holder.binding.description.text = typeface.description

            if (position == 1) {
                holder.binding.license.visibility = ViewGroup.GONE
                holder.binding.description.visibility = ViewGroup.GONE
            } else {
                holder.binding.license.visibility = ViewGroup.VISIBLE
                holder.binding.description.visibility = ViewGroup.VISIBLE
            }

            if (typeface.name == AppearancePreferences.getAppFont()) {
                holder.binding.name.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_ring_12dp, 0)
            } else {
                holder.binding.name.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            }

            holder.binding.name.typeface = TypeFace.getTypeFace(typeface.name, TypefaceStyle.BOLD.style, holder.context)
            holder.binding.license.typeface = TypeFace.getTypeFace(typeface.name, TypefaceStyle.REGULAR.style, holder.context)
            holder.binding.description.typeface = TypeFace.getTypeFace(typeface.name, TypefaceStyle.REGULAR.style, holder.context)
            holder.binding.type.typeface = TypeFace.getTypeFace(typeface.name, TypefaceStyle.LIGHT.style, holder.context)
            holder.binding.extraLight.typeface = TypeFace.getTypeFace(typeface.name, TypefaceStyle.EXTRA_LIGHT.style, holder.context)
            holder.binding.light.typeface = TypeFace.getTypeFace(typeface.name, TypefaceStyle.LIGHT.style, holder.context)
            holder.binding.regular.typeface = TypeFace.getTypeFace(typeface.name, TypefaceStyle.REGULAR.style, holder.context)
            holder.binding.medium.typeface = TypeFace.getTypeFace(typeface.name, TypefaceStyle.MEDIUM.style, holder.context)
            holder.binding.bold.typeface = TypeFace.getTypeFace(typeface.name, TypefaceStyle.BOLD.style, holder.context)
            holder.binding.black.typeface = TypeFace.getTypeFace(typeface.name, TypefaceStyle.BLACK.style, holder.context)

            holder.binding.container.setOnClickListener {
                AppearancePreferences.setAppFont(typeface.name)
                notifyDataSetChanged()
            }
        } else if (holder is Header) {
            holder.binding.title.text = holder.context.getString(R.string.typeface)
            holder.binding.summary.text = holder.context.getString(R.string.typeface_summary)
            holder.binding.title.typeface = TypeFace.getTypeFace(AppearancePreferences.getAppFont(), TypefaceStyle.BOLD.style, holder.context)
            holder.binding.summary.typeface = TypeFace.getTypeFace(AppearancePreferences.getAppFont(), TypefaceStyle.REGULAR.style, holder.context)
        }
    }

    override fun getItemCount(): Int {
        return typefaces.size
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) {
            RecyclerViewUtils.TYPE_HEADER
        } else {
            RecyclerViewUtils.TYPE_ITEM
        }
    }

    inner class Holder(val binding: AdapterTypefaceItemBinding) : VerticalListViewHolder(binding.root)

    inner class Header(val binding: AdapterPreferenceHeaderBinding) : VerticalListViewHolder(binding.root)
}