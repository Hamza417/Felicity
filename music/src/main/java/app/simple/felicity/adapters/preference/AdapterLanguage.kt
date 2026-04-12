package app.simple.felicity.adapters.preference

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.databinding.AdapterLanguageBinding
import app.simple.felicity.databinding.AdapterPreferenceHeaderBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.decorations.utils.RecyclerViewUtils
import app.simple.felicity.preferences.ConfigurationPreferences
import app.simple.felicity.shared.models.Lang
import app.simple.felicity.shared.utils.LocaleUtils
import app.simple.felicity.utils.AdapterUtils.setSelectedIndicator

class AdapterLanguage : RecyclerView.Adapter<VerticalListViewHolder>() {

    private var langs: MutableList<Lang> = LocaleUtils.langLists.also { it ->
        it.subList(1, it.size).sortBy {
            it.language
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        when (viewType) {
            RecyclerViewUtils.TYPE_HEADER -> {
                val binding = AdapterPreferenceHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return Header(binding)
            }
            RecyclerViewUtils.TYPE_ITEM -> {
                return Holder(AdapterLanguageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
            else -> throw IllegalStateException("unknown view type")
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        if (holder is Holder) {
            val lang = langs[position - 1]
            holder.binding.language.text = if (position == 1) holder.itemView.context.getString(R.string.auto) else lang.language
            holder.binding.language.setSelectedIndicator(ConfigurationPreferences.getAppLanguage() == lang.localeCode)

            holder.binding.language.setOnClickListener {
                ConfigurationPreferences.setAppLanguage(lang.localeCode)
            }
        } else if (holder is Header) {
            holder.binding.title.text = holder.context.getString(R.string.language)
            holder.binding.summary.text = holder.context.getString(R.string.language_summary)
        }
    }

    override fun getItemCount(): Int {
        return langs.size.plus(1) // +1 for header
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) {
            RecyclerViewUtils.TYPE_HEADER
        } else {
            RecyclerViewUtils.TYPE_ITEM
        }
    }

    inner class Holder(val binding: AdapterLanguageBinding) : VerticalListViewHolder(binding.root)

    inner class Header(val binding: AdapterPreferenceHeaderBinding) : VerticalListViewHolder(binding.root)
}