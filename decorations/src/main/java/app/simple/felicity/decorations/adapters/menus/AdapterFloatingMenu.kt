package app.simple.felicity.decorations.adapters.menus

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.decoration.R
import app.simple.felicity.decorations.interfaces.BottomMenuCallbacks
import app.simple.felicity.decorations.overscroll.HorizontalListViewHolder
import app.simple.felicity.decorations.ripple.DynamicRippleImageButton
import app.simple.felicity.decorations.ripple.DynamicRippleLinearLayout
import app.simple.felicity.decorations.theme.ThemeIcon
import app.simple.felicity.decorations.typeface.TypeFaceTextView
import app.simple.felicity.decorations.utils.RecyclerViewUtils

class AdapterFloatingMenu(private val bottomMenuItems: ArrayList<Pair<Int, Int>>) : RecyclerView.Adapter<HorizontalListViewHolder>() {

    private var bottomMenuCallbacks: BottomMenuCallbacks? = null
    private val isBottomMenuContext = app.simple.felicity.preferences.AccessibilityPreferences.isAppElementsContext()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HorizontalListViewHolder {
        return when (viewType) {
            RecyclerViewUtils.TYPE_DIVIDER -> {
                Divider(LayoutInflater.from(parent.context).inflate(R.layout.adapter_bottom_menu_divider, parent, false))
            }

            RecyclerViewUtils.TYPE_ITEM -> {
                if (isBottomMenuContext) {
                    HolderContext(LayoutInflater.from(parent.context).inflate(R.layout.adapter_floating_menu_context, parent, false))
                } else {
                    Holder(LayoutInflater.from(parent.context).inflate(R.layout.adapter_floating_menu, parent, false))
                }
            }

            else -> {
                throw java.lang.IllegalArgumentException("Invalid view type")
            }
        }
    }

    override fun onBindViewHolder(holder: HorizontalListViewHolder, position: Int) {
        when (holder) {
            is Holder -> {
                holder.button.setImageResource(bottomMenuItems[position].first)
                holder.button.contentDescription = holder.itemView.context.getString(bottomMenuItems[position].second)

                holder.button.setOnClickListener {
                    bottomMenuCallbacks?.onBottomMenuItemClicked(bottomMenuItems[position].first, it)
                }

                //                when {
                //                    app.simple.felicity.preferences.AppearancePreferences.isAccentColorOnBottomMenu() -> {
                //                        holder.button.imageTintList = ColorStateList.valueOf(Color.WHITE)
                //                    }
                //
                //                    app.simple.felicity.preferences.AccessibilityPreferences.isColorfulIcons() -> {
                //                        holder.button.imageTintList = ColorStateList(arrayOf(intArrayOf(
                //                                android.R.attr.state_enabled
                //                        ), intArrayOf()), intArrayOf(
                //                                app.simple.felicity.shared.constants.Colors.getColors()[position],
                //                                app.simple.felicity.shared.constants.Colors.getColors()[position]
                //                        ))
                //                    }
                //                }
            }

            is HolderContext -> {
                holder.button.setImageResource(bottomMenuItems[position].first)
                holder.button.contentDescription = holder.itemView.context.getString(bottomMenuItems[position].second)
                holder.text.text = holder.itemView.context.getString(bottomMenuItems[position].second)

                holder.container.setOnClickListener {
                    bottomMenuCallbacks?.onBottomMenuItemClicked(bottomMenuItems[position].first, it)
                }

                //                when {
                //                    app.simple.felicity.preferences.AppearancePreferences.isAccentColorOnBottomMenu() -> {
                //                        holder.button.imageTintList = ColorStateList.valueOf(Color.WHITE)
                //                        holder.text.setTextColor(Color.WHITE)
                //                    }
                //
                //                    app.simple.felicity.preferences.AccessibilityPreferences.isColorfulIcons() -> {
                //                        holder.button.imageTintList = ColorStateList(arrayOf(intArrayOf(
                //                                android.R.attr.state_enabled
                //                        ), intArrayOf()), intArrayOf(
                //                                app.simple.felicity.shared.constants.Colors.getColors()[position],
                //                                app.simple.felicity.shared.constants.Colors.getColors()[position]
                //                        ))
                //                    }
                //                }
            }
        }
    }

    override fun getItemCount(): Int {
        return bottomMenuItems.size
    }

    override fun getItemId(position: Int): Long {
        return bottomMenuItems[position].first.toLong()
    }

    override fun getItemViewType(position: Int): Int {
        return if (bottomMenuItems[position].first == -1) { // -1 is the divider
            RecyclerViewUtils.TYPE_DIVIDER
        } else {
            RecyclerViewUtils.TYPE_ITEM
        }
    }

    fun setMenuCallbacks(bottomMenuCallbacks: BottomMenuCallbacks) {
        this.bottomMenuCallbacks = bottomMenuCallbacks
    }

    fun getBottomMenuItems(): ArrayList<Pair<Int, Int>> {
        return bottomMenuItems
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateMenu(bottomMenuItems: ArrayList<Pair<Int, Int>>) {
        this.bottomMenuItems.clear()
        this.bottomMenuItems.addAll(bottomMenuItems)
        notifyDataSetChanged()
    }

    inner class Holder(itemView: View) : HorizontalListViewHolder(itemView) {
        val button: DynamicRippleImageButton = itemView.findViewById(R.id.button)
    }

    inner class HolderContext(itemView: View) : HorizontalListViewHolder(itemView) {
        val button: ThemeIcon = itemView.findViewById(R.id.button)
        val text: TypeFaceTextView = itemView.findViewById(R.id.text)
        val container: DynamicRippleLinearLayout = itemView.findViewById(R.id.container)

        init {
            container.cornerFactor = 2F
        }
    }

    inner class Divider(parent: View) : HorizontalListViewHolder(parent) {
        val divider: View = parent.findViewById(R.id.divider)

        init {
            val layoutParams = divider.layoutParams
            layoutParams.height = app.simple.felicity.preferences.MainPreferences.getFloatingMenuHeight()
            divider.layoutParams = layoutParams
        }
    }
}
