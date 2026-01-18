package app.simple.felicity.adapters.preference

import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.databinding.AdapterDividerBinding
import app.simple.felicity.databinding.AdapterPreferenceHeaderBinding
import app.simple.felicity.databinding.AdapterThemeBinding
import app.simple.felicity.decorations.overscroll.RecyclerViewUtils
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.shared.utils.ConditionUtils.isZero
import app.simple.felicity.shared.utils.ViewUtils.invisible
import app.simple.felicity.shared.utils.ViewUtils.visible
import app.simple.felicity.theme.constants.ThemeConstants

class AdapterTheme : RecyclerView.Adapter<VerticalListViewHolder>() {

    private val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayListOf(
                -1, // Light
                ThemeConstants.LIGHT_THEME,
                ThemeConstants.SOAPSTONE,
                ThemeConstants.MATERIAL_YOU_LIGHT,
                ThemeConstants.HIGH_CONTRAST_LIGHT,
                -1, // Dark
                ThemeConstants.DARK_THEME,
                ThemeConstants.MATERIAL_YOU_DARK,
                ThemeConstants.AMOLED,
                ThemeConstants.SLATE,
                ThemeConstants.OIL,
                ThemeConstants.HIGH_CONTRAST_DARK,
                -1, // Auto
                ThemeConstants.FOLLOW_SYSTEM,
                ThemeConstants.DAY_NIGHT,
        )
    } else {
        arrayListOf(
                -1, // Light
                ThemeConstants.LIGHT_THEME,
                ThemeConstants.SOAPSTONE,
                ThemeConstants.HIGH_CONTRAST_LIGHT,
                -1, // Dark
                ThemeConstants.DARK_THEME,
                ThemeConstants.AMOLED,
                ThemeConstants.SLATE,
                ThemeConstants.OIL,
                ThemeConstants.HIGH_CONTRAST_DARK,
                -1, // Auto
                ThemeConstants.FOLLOW_SYSTEM,
                ThemeConstants.DAY_NIGHT,
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            RecyclerViewUtils.TYPE_HEADER -> {
                Header(AdapterPreferenceHeaderBinding
                           .inflate(LayoutInflater.from(parent.context), parent, false))
            }
            RecyclerViewUtils.TYPE_DIVIDER -> {
                Divider(AdapterDividerBinding
                            .inflate(LayoutInflater.from(parent.context), parent, false))
            }
            RecyclerViewUtils.TYPE_ITEM -> {
                Holder(AdapterThemeBinding
                           .inflate(LayoutInflater.from(parent.context), parent, false))
            }
            else -> throw IllegalStateException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        if (holder is Holder) {
            val position = position - 1
            val theme = list[position]
            holder.binding.name.text = holder.itemView.context.getThemeName(theme)

            if (AppearancePreferences.getLastDarkTheme() == list[position] || AppearancePreferences.getLastLightTheme() == list[position]) {
                holder.binding.name.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_dot_12dp, 0, 0, 0)
            } else {
                holder.binding.name.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            }

            if (AppearancePreferences.getTheme() == list[position]) {
                holder.binding.ring.visible(false)
            } else {
                holder.binding.ring.invisible(false)
            }

            holder.binding.name.setOnClickListener {
                if (AppearancePreferences.setTheme(list[position])) {
                    when (list[position]) {
                        ThemeConstants.LIGHT_THEME,
                        ThemeConstants.SOAPSTONE,
                        ThemeConstants.HIGH_CONTRAST_LIGHT,
                        ThemeConstants.MATERIAL_YOU_LIGHT -> {
                            AppearancePreferences.setLastLightTheme(list[position])
                        }
                        ThemeConstants.HIGH_CONTRAST_DARK,
                        ThemeConstants.DARK_THEME,
                        ThemeConstants.SLATE,
                        ThemeConstants.AMOLED,
                        ThemeConstants.OIL,
                        ThemeConstants.MATERIAL_YOU_DARK -> {
                            AppearancePreferences.setLastDarkTheme(list[position])
                        }
                    }

                    notifyDataSetChanged()
                }
            }
        } else if (holder is Header) {
            holder.binding.title.setText(R.string.theme)
            holder.binding.summary.setText(R.string.theme_summary)
        }
    }

    override fun getItemCount(): Int {
        return list.size.plus(1)
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            position.isZero() -> {
                RecyclerViewUtils.TYPE_HEADER
            }
            list[position - 1] < 0 -> {
                RecyclerViewUtils.TYPE_DIVIDER
            }
            else -> {
                RecyclerViewUtils.TYPE_ITEM
            }
        }
    }

    inner class Holder(val binding: AdapterThemeBinding) : VerticalListViewHolder(binding.root)

    inner class Header(val binding: AdapterPreferenceHeaderBinding) : VerticalListViewHolder(binding.root)

    inner class Divider(val binding: AdapterDividerBinding) : VerticalListViewHolder(binding.root)

    private fun Context.getThemeName(theme: Int): String {
        return when (theme) {
            ThemeConstants.LIGHT_THEME -> getString(R.string.light)
            ThemeConstants.SOAPSTONE -> getString(R.string.soapstone)
            ThemeConstants.HIGH_CONTRAST_LIGHT -> getString(R.string.high_contrast)
            ThemeConstants.DARK_THEME -> getString(R.string.dark)
            ThemeConstants.AMOLED -> getString(R.string.amoled)
            ThemeConstants.SLATE -> getString(R.string.slate)
            ThemeConstants.OIL -> getString(R.string.oil)
            ThemeConstants.HIGH_CONTRAST_DARK -> getString(R.string.high_contrast)
            ThemeConstants.FOLLOW_SYSTEM -> getString(R.string.follow_system)
            ThemeConstants.DAY_NIGHT -> getString(R.string.day_night)
            ThemeConstants.MATERIAL_YOU_LIGHT -> getString(R.string.material_you_light)
            ThemeConstants.MATERIAL_YOU_DARK -> getString(R.string.material_you_dark)
            else -> getString(R.string.unknown)
        }
    }
}