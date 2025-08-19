package app.simple.felicity.decorations.flowsidemenu

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.core.content.withStyledAttributes
import androidx.core.view.setPadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.decoration.R
import app.simple.felicity.decorations.circular.CircularImageButton
import kotlin.math.abs

class FelicitySideBar @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val recyclerView: RecyclerView

    private var side: Int = SIDE_LEFT
    private var autoHideOnScroll = true // still available for external control
    private var animationDuration: Long = 250L
    private var showToggle: Boolean = true // retained for backward compatibility but unused now

    private var isHidden = false

    private val adapter = SidebarAdapter(mutableListOf())

    // Optional vertical centering for sidebar items
    private var centerItemsVertically = true
    private var recyclerBaseTopPadding = 0
    private var recyclerBaseBottomPadding = 0

    // Style configuration for item buttons
    private var itemBackgroundColor: Int = 0xFF222222.toInt()
    private var itemSpacingDp: Int = 20

    // Global item click listener (index, item)
    private var onItemClick: ((Int, View) -> Unit)? = null

    init {
        clipToPadding = false
        clipChildren = false
        clipToOutline = false

        recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            overScrollMode = OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            adapter = this@FelicitySideBar.adapter
            clipToPadding = false
            clipChildren = false
            clipToOutline = false
        }
        addView(recyclerView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))

        obtain(attrs)
        applySideGravity()

        val pad = 16.dp
        setPadding(
                if (side == SIDE_LEFT) 0 else pad,
                pad,
                if (side == SIDE_LEFT) pad else 0,
                pad
        )

        if (centerItemsVertically) {
            setupVerticalCentering()
        }
    }

    private fun obtain(attrs: AttributeSet?) {
        if (attrs == null) return
        context.withStyledAttributes(attrs, R.styleable.FelicitySideBar) {
            side = getInt(R.styleable.FelicitySideBar_sidebarSide, SIDE_LEFT)
            autoHideOnScroll = getBoolean(R.styleable.FelicitySideBar_sidebarAutoHideOnScroll, true)
            animationDuration = getInt(R.styleable.FelicitySideBar_sidebarAnimationDuration, 250).toLong()
            showToggle = getBoolean(R.styleable.FelicitySideBar_sidebarShowToggle, true) // ignored now
        }
    }

    private fun applySideGravity() {
        (recyclerView.layoutParams as? LayoutParams)?.let { lp ->
            lp.gravity = if (side == SIDE_LEFT) Gravity.START else Gravity.END
            recyclerView.layoutParams = lp
        }
    }

    private fun setupVerticalCentering() {
        recyclerBaseTopPadding = recyclerView.paddingTop
        recyclerBaseBottomPadding = recyclerView.paddingBottom
        recyclerView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> adjustRecyclerPaddingForCenter() }
        recyclerView.viewTreeObserver.addOnGlobalLayoutListener { adjustRecyclerPaddingForCenter() }
    }

    private fun adjustRecyclerPaddingForCenter() {
        if (!centerItemsVertically) return
        // Total scroll range approximates content height
        val contentHeight = recyclerView.computeVerticalScrollRange()
        val rvHeight = recyclerView.height
        if (rvHeight == 0) return
        if (contentHeight > 0 && contentHeight < rvHeight) {
            val extraSpace = rvHeight - contentHeight
            val top = recyclerBaseTopPadding + extraSpace / 2
            val bottom = recyclerBaseBottomPadding + extraSpace / 2
            if (recyclerView.paddingTop != top || recyclerView.paddingBottom != bottom) {
                recyclerView.setPadding(
                        recyclerView.paddingLeft,
                        top,
                        recyclerView.paddingRight,
                        bottom
                )
                recyclerView.invalidate()
            }
        } else {
            // Restore base paddings
            if (recyclerView.paddingTop != recyclerBaseTopPadding || recyclerView.paddingBottom != recyclerBaseBottomPadding) {
                recyclerView.setPadding(
                        recyclerView.paddingLeft,
                        recyclerBaseTopPadding,
                        recyclerView.paddingRight,
                        recyclerBaseBottomPadding
                )
            }
        }
    }

    fun setCenterItemsVertically(enabled: Boolean) {
        centerItemsVertically = enabled
        if (enabled) {
            setupVerticalCentering()
        } else {
            // restore base
            recyclerView.setPadding(
                    recyclerView.paddingLeft,
                    recyclerBaseTopPadding,
                    recyclerView.paddingRight,
                    recyclerBaseBottomPadding
            )
        }
    }

    fun setItems(items: List<SidebarItem>) {
        adapter.setItems(items)
        post { adjustRecyclerPaddingForCenter() }
    }

    fun setItemStyle(backgroundColor: Int = itemBackgroundColor, spacingDp: Int = itemSpacingDp) {
        itemBackgroundColor = backgroundColor
        itemSpacingDp = spacingDp
        recyclerView.post { adapter.notifyDataSetChanged() }
    }

    fun toggle(forceShow: Boolean? = null) {
        val targetShow = forceShow ?: isHidden
        if (targetShow) show() else hide()
    }

    fun show() {
        if (!isHidden) return
        animate().translationX(0f).setDuration(animationDuration).start()
        isHidden = false
    }

    fun hide() {
        if (isHidden) return
        post {
            val distance = width.toFloat() + 24f
            val translation = if (side == SIDE_LEFT) -distance else distance
            animate().translationX(translation).setDuration(animationDuration).start()
            isHidden = true
        }
    }

    fun attachToRecyclerView(rv: RecyclerView) {
        if (!autoHideOnScroll) return
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (abs(dy) < 6) return
                if (dy > 0) {
                    hide()
                } else if (dy < 0) {
                    show()
                }
            }
        })
    }

    fun setOnItemClickListener(listener: ((Int, View) -> Unit)?) {
        onItemClick = listener
    }

    fun addItem(@DrawableRes icon: Int, onClick: (() -> Unit)? = null) {
        adapter.addItem(SidebarItem(icon, onClick))
        post { adjustRecyclerPaddingForCenter() }
    }

    fun clearItems() {
        adapter.clear()
        post { adjustRecyclerPaddingForCenter() }
    }

    fun getItems(): List<SidebarItem> = adapter.getItems()

    data class SidebarItem(@DrawableRes val icon: Int, val onClick: (() -> Unit)? = null)

    private inner class SidebarAdapter(private val data: MutableList<SidebarItem>) : RecyclerView.Adapter<SidebarVH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SidebarVH {
            val size = resources.getDimensionPixelOffset(R.dimen.button_size)
            val btn = CircularImageButton(parent.context).apply {
                setPadding(resources.getDimensionPixelOffset(R.dimen.padding_12))
                layoutParams = RecyclerView.LayoutParams(size, size).also { lp ->
                    val space = itemSpacingDp.dp
                    lp.setMargins(0, space / 2, 0, space / 2)
                }
            }
            return SidebarVH(btn)
        }

        override fun getItemCount(): Int = data.size
        override fun onBindViewHolder(holder: SidebarVH, position: Int) = holder.bind(data[position])

        @SuppressLint("NotifyDataSetChanged")
        fun setItems(items: List<SidebarItem>) {
            data.clear()
            data.addAll(items)
            notifyDataSetChanged()
        }

        fun addItem(item: SidebarItem) {
            data.add(item)
            notifyItemInserted(data.lastIndex)
        }

        fun clear() {
            val size = data.size
            if (size == 0) return
            data.clear()
            notifyItemRangeRemoved(0, size)
        }

        fun getItems(): List<SidebarItem> = data.toList()
    }

    private inner class SidebarVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val button: CircularImageButton = itemView as CircularImageButton
        fun bind(item: SidebarItem) {
            button.setImageResource(item.icon)
            button.setOnClickListener {
                item.onClick?.invoke()
                onItemClick?.invoke(getItems()[bindingAdapterPosition].icon, it)
            }
            styleButton(button)
        }
    }

    private fun styleButton(button: CircularImageButton) {
        button.overrideRadius(250F)
        button.setCircleColor(itemBackgroundColor)
    }

    companion object {
        const val SIDE_LEFT = 0
        const val SIDE_RIGHT = 1
    }
}

// Extension helpers
private val Int.dp: Int get() = (this * (android.content.res.Resources.getSystem().displayMetrics.density)).toInt()
private val Float.dpF: Float get() = (this * android.content.res.Resources.getSystem().displayMetrics.density)
