package app.simple.felicity.extensions.fragments

import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.core.utils.BarHeight
import app.simple.felicity.core.utils.TextViewUtils.setStartDrawable
import app.simple.felicity.core.utils.ViewUtils.gone
import app.simple.felicity.core.utils.ViewUtils.visible
import app.simple.felicity.decorations.fastscroll.SectionedFastScroller
import app.simple.felicity.decorations.fastscroll.SlideFastScroller
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.decorations.views.SpacingRecyclerView

open class PanelFragment : MediaFragment() {

    protected val isLandscape: Boolean by lazy {
        BarHeight.isLandscape(requireContext())
    }

    protected fun AppCompatTextView.setGridSizeValue(gridSize: Int) {
        when (gridSize) {
            CommonPreferencesConstants.GRID_SIZE_ONE -> {
                text = getString(R.string.one)
            }
            CommonPreferencesConstants.GRID_SIZE_TWO -> {
                text = getString(R.string.two)
            }
            CommonPreferencesConstants.GRID_SIZE_THREE -> {
                text = getString(R.string.three)
            }
            CommonPreferencesConstants.GRID_SIZE_FOUR -> {
                text = getString(R.string.four)
            }
            CommonPreferencesConstants.GRID_SIZE_FIVE -> {
                text = getString(R.string.five)
            }
            CommonPreferencesConstants.GRID_SIZE_SIX -> {
                text = getString(R.string.six)
            }
        }
    }

    protected fun RecyclerView.requireAttachedSectionScroller(
            sections: List<SectionedFastScroller.Position>,
            header: AppHeader,
            view: View) {
        val sectionedFastScroller = SectionedFastScroller.attach(this)
        sectionedFastScroller.setPositions(sections)
        sectionedFastScroller.setOnPositionSelectedListener { position ->
            val layoutManager = this.layoutManager as? GridLayoutManager ?: return@setOnPositionSelectedListener
            val recyclerViewHeight = this.height
            val itemView = layoutManager.findViewByPosition(position.index)
            val itemHeight = itemView?.height ?: 0
            val offset = (recyclerViewHeight / 2) - (paddingTop + itemHeight / 2)
            layoutManager.scrollToPositionWithOffset(position.index, paddingTop)

            if (position.index > 10) {
                header.hideHeader()
            } else {
                header.showHeader()
            }

            header.resumeAutoBehavior()

            post {
                val currentSectionIndex = sections.indexOf(position)
                val nextIndex = if (currentSectionIndex + 1 < sections.size) {
                    sections[currentSectionIndex + 1].index
                } else {
                    sections.last().index
                }

                for (i in position.index until nextIndex) {
                    try {
                        val itemView = layoutManager.findViewByPosition(i)
                        itemView?.let { view ->
                            val blink = AlphaAnimation(1f, 0f).apply {
                                duration = 300
                                repeatCount = 3 // 2 blinks
                                repeatMode = Animation.REVERSE
                            }
                            view.startAnimation(blink)
                        }
                    } catch (_: IndexOutOfBoundsException) {
                        // Ignore
                    }
                }
            }
        }

        sectionedFastScroller.setVisibilityListener(object : SectionedFastScroller.VisibilityListener {
            override fun onShowStart() {
                super.onShowStart()
                hideMiniPlayer()
            }

            override fun onHideStart() {
                super.onHideStart()
                showMiniPlayer()
            }
        })

        view.setOnClickListener {
            sectionedFastScroller.show(animated = true)
        }
    }

    fun AppCompatTextView.setGridTypeValue(gridType: Int) {
        when (gridType) {
            CommonPreferencesConstants.GRID_TYPE_LIST -> {
                text = getString(R.string.list)
                setStartDrawable(R.drawable.ic_list_16dp)
            }
            CommonPreferencesConstants.GRID_TYPE_GRID -> {
                text = getString(R.string.grid)
                setStartDrawable(R.drawable.ic_grid_16dp)
            }
            CommonPreferencesConstants.GRID_TYPE_PERISTYLE -> {
                text = getString(R.string.peristyle)
                setStartDrawable(R.drawable.ic_peristyle_16dp)
            }
            else -> {
                text = getString(R.string.list) // Default to list
                setStartDrawable(R.drawable.ic_list_16dp)
            }
        }
    }

    fun SpacingRecyclerView.setGridType(gridType: Int, size: Int) {
        val gridLayoutManager = this.layoutManager as? GridLayoutManager
        when (gridType) {
            CommonPreferencesConstants.GRID_TYPE_LIST -> {
                gridLayoutManager?.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int = 1
                }

                applySpacing()
            }
            CommonPreferencesConstants.GRID_TYPE_GRID -> {
                gridLayoutManager?.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int = 1
                }

                applySpacing()
            }
            CommonPreferencesConstants.GRID_TYPE_PERISTYLE -> {
                gridLayoutManager?.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        val spanCount = maxOf(1, size)
                        val cycle = spanCount * 2 + 1 // 1 giant + 2 rows of grid
                        return if (position % cycle == 0) spanCount else 1
                    }
                }

                removeSpacing()
            }
        }
    }

    protected fun View.hideOnUnfavorableSort(sorts: List<Int>, preference: Int) {
        if (sorts.contains(preference)) {
            visible(animate = true)
        } else {
            gone(animate = true)
        }
    }

    /**
     * Attaches a SlideFastScroller to the RecyclerView.
     */
    protected fun RecyclerView.attachSlideFastScroller() {
        SlideFastScroller.attach(this).apply {
            setFadeToIdleMode(enabled = true)
            setEnabledWhileEmpty(enable = false)
            setIdleAlpha(0.25F)
        }
    }
}