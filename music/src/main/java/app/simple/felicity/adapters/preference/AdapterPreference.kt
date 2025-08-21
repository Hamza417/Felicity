package app.simple.felicity.adapters.preference

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.databinding.AdapterPreferenceBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.viewmodels.main.preferences.PreferencesViewModel.Companion.Preference

class AdapterPreference(private val data: List<Preference>) : RecyclerView.Adapter<AdapterPreference.Holder>() {

    private var callbacks: AdapterPreferenceCallbacks? = null
    private var titlesVisible = true

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(AdapterPreferenceBinding.inflate(LayoutInflater.from(parent.context),
                                                       parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.binding.title.text = holder.context.getString(data[position].title)
        holder.binding.description.text = holder.context.getString(data[position].description)
        holder.binding.icon.setImageResource(data[position].icon)

        holder.binding.title.visibility = if (titlesVisible) View.VISIBLE else View.GONE
        holder.binding.description.visibility = if (titlesVisible) View.VISIBLE else View.GONE

        holder.binding.container.setOnClickListener {
            callbacks?.onPreferenceClicked(data[position], position, holder.binding.container)
        }
    }

    override fun getItemCount(): Int {
        return data.size
    }

    inner class Holder(val binding: AdapterPreferenceBinding) : VerticalListViewHolder(binding.root)

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    fun setAdapterPreferenceCallbacks(callbacks: AdapterPreferenceCallbacks) {
        this.callbacks = callbacks
    }

    fun setTitlesVisible(visible: Boolean) {
        if (titlesVisible != visible) {
            titlesVisible = visible
            notifyDataSetChanged()
        }
    }

    companion object {
        interface AdapterPreferenceCallbacks {
            fun onPreferenceClicked(preference: Preference, position: Int, view: View)
        }
    }
}