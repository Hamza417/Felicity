package app.simple.felicity.ui.panels

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.AdapterYear
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.FragmentYearBinding
import app.simple.felicity.databinding.HeaderYearBinding
import app.simple.felicity.decorations.fastscroll.SectionedFastScroller
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.decorations.views.SharedScrollViewPopup
import app.simple.felicity.dialogs.year.DialogYearSort.Companion.showYearSortDialog
import app.simple.felicity.extensions.fragments.PanelFragment
import app.simple.felicity.preferences.YearPreferences
import app.simple.felicity.repository.models.YearGroup
import app.simple.felicity.repository.sort.YearSort.setCurrentSortOrder
import app.simple.felicity.repository.sort.YearSort.setCurrentSortStyle
import app.simple.felicity.ui.pages.YearPage
import app.simple.felicity.viewmodels.panels.YearViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class Year : PanelFragment() {

    private val yearViewModel: YearViewModel by viewModels({ requireActivity() })

    private lateinit var binding: FragmentYearBinding
    private lateinit var headerBinding: HeaderYearBinding

    private var gridLayoutManager: GridLayoutManager? = null
    private var adapterYear: AdapterYear? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentYearBinding.inflate(inflater, container, false)
        headerBinding = HeaderYearBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.header.setContentView(headerBinding.root)
        binding.header.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        binding.recyclerView.attachSlideFastScroller()
        binding.recyclerView.requireAttachedMiniPlayer()

        gridLayoutManager = GridLayoutManager(requireContext(), YearPreferences.getGridSize())
        binding.recyclerView.layoutManager = gridLayoutManager
        binding.recyclerView.setGridType(YearPreferences.getGridType(), YearPreferences.getGridSize())

        setupClickListeners()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                yearViewModel.years.collect { years ->
                    if (years.isNotEmpty()) {
                        updateYearList(years)
                    } else if (adapterYear != null) {
                        updateYearList(years)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        adapterYear = null
        gridLayoutManager = null
        super.onDestroyView()
    }

    private fun setupClickListeners() {
        headerBinding.menu.setOnClickListener {
            childFragmentManager.showYearSortDialog()
        }

        headerBinding.sortOrder.setOnClickListener {
            childFragmentManager.showYearSortDialog()
        }

        headerBinding.sortStyle.setOnClickListener {
            childFragmentManager.showYearSortDialog()
        }

        headerBinding.search.setOnClickListener {
            openSearch()
        }

        headerBinding.gridSize.setOnClickListener { button ->
            SharedScrollViewPopup(
                    container = requireContainerView(),
                    anchorView = button,
                    menuItems = listOf(R.string.one, R.string.two, R.string.three,
                                       R.string.four, R.string.five, R.string.six),
                    menuIcons = listOf(R.drawable.ic_one_16, R.drawable.ic_two_16dp,
                                       R.drawable.ic_three_16dp, R.drawable.ic_four_16dp,
                                       R.drawable.ic_five_16dp, R.drawable.ic_six_16dp),
                    onMenuItemClick = {
                        when (it) {
                            R.string.one -> YearPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_ONE)
                            R.string.two -> YearPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_TWO)
                            R.string.three -> YearPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_THREE)
                            R.string.four -> YearPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_FOUR)
                            R.string.five -> YearPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_FIVE)
                            R.string.six -> YearPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_SIX)
                        }
                    },
                    onDismiss = {}
            ).show()
        }

        headerBinding.gridType.setOnClickListener { button ->
            SharedScrollViewPopup(
                    container = requireContainerView(),
                    anchorView = button,
                    menuItems = listOf(R.string.list, R.string.grid),
                    menuIcons = listOf(R.drawable.ic_list_16dp, R.drawable.ic_grid_16dp),
                    onMenuItemClick = {
                        when (it) {
                            R.string.list -> YearPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_LIST)
                            R.string.grid -> YearPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_GRID)
                        }
                    },
                    onDismiss = {}
            ).show()
        }
    }

    private fun updateYearList(years: List<YearGroup>) {
        if (adapterYear == null) {
            adapterYear = AdapterYear(years)
            adapterYear?.setHasStableIds(true)
            adapterYear?.setCallbackListener(object : GeneralAdapterCallbacks {
                override fun onYearGroupClicked(yearGroup: YearGroup, view: View) {
                    openFragment(YearPage.newInstance(yearGroup), YearPage.TAG)
                }
            })
            binding.recyclerView.adapter = adapterYear
        } else {
            adapterYear?.updateList(years)
            if (binding.recyclerView.adapter == null) {
                binding.recyclerView.adapter = adapterYear
            }
        }

        headerBinding.count.text = getString(R.string.x_years, years.size)
        binding.recyclerView.requireAttachedSectionScroller(
                sections = provideScrollPositions(years),
                header = binding.header,
                view = headerBinding.scroll
        )

        headerBinding.sortStyle.setCurrentSortStyle()
        headerBinding.sortOrder.setCurrentSortOrder()
        headerBinding.scroll.hideOnUnfavorableSort(
                sorts = listOf(CommonPreferencesConstants.BY_YEAR),
                preference = YearPreferences.getSortStyle()
        )

        headerBinding.gridSize.setGridSizeValue(YearPreferences.getGridSize())
        headerBinding.gridType.setGridTypeValue(YearPreferences.getGridType())
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            YearPreferences.GRID_SIZE_PORTRAIT, YearPreferences.GRID_SIZE_LANDSCAPE -> {
                headerBinding.gridSize.setGridSizeValue(YearPreferences.getGridSize())
                binding.recyclerView.beginDelayedTransition()
                gridLayoutManager?.spanCount = YearPreferences.getGridSize()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
            YearPreferences.GRID_TYPE_PORTRAIT, YearPreferences.GRID_TYPE_LANDSCAPE -> {
                binding.recyclerView.setGridType(YearPreferences.getGridType(), YearPreferences.getGridSize())
                headerBinding.gridType.setGridTypeValue(YearPreferences.getGridType())
                binding.recyclerView.beginDelayedTransition()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
        }
    }

    private fun provideScrollPositions(years: List<YearGroup>): List<SectionedFastScroller.Position> {
        val yearToIndex = linkedMapOf<String, Int>()
        years.forEachIndexed { index, yearGroup ->
            val key = yearGroup.year.takeIf { it.all { ch -> ch.isDigit() } } ?: "#"
            if (!yearToIndex.containsKey(key)) {
                yearToIndex[key] = index
            }
        }
        return yearToIndex.map { (year, index) ->
            SectionedFastScroller.Position(year, index)
        }
    }

    companion object {
        fun newInstance(): Year {
            val args = Bundle()
            val fragment = Year()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "Year"
    }
}