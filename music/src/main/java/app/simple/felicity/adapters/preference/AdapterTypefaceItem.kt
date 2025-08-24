package app.simple.felicity.adapters.preference

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.databinding.AdapterTypefaceItemBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.typeface.TypeFace
import app.simple.felicity.decorations.typeface.TypefaceStyle
import app.simple.felicity.preferences.AppearancePreferences

class AdapterTypefaceItem : RecyclerView.Adapter<AdapterTypefaceItem.Holder>() {

    private val typefaces = TypeFace.list

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = AdapterTypefaceItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val typeface = typefaces[position]
        holder.binding.name.text = typeface.typefaceName
        holder.binding.type.text = typeface.type
        holder.binding.license.text = typeface.license
        holder.binding.description.text = typeface.description

        if (position == 0) {
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
    }

    override fun getItemCount(): Int {
        return typefaces.size
    }

    inner class Holder(val binding: AdapterTypefaceItemBinding) : VerticalListViewHolder(binding.root)
}