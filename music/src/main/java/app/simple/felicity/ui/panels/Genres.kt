package app.simple.felicity.ui.panels

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.AdapterGenres
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.FragmentGenresBinding
import app.simple.felicity.databinding.HeaderGenresBinding
import app.simple.felicity.decorations.fastscroll.SectionedFastScroller
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.decorations.views.SharedScrollViewPopup
import app.simple.felicity.dialogs.genres.DialogGenreMenu.Companion.showGenreMenu
import app.simple.felicity.dialogs.genres.DialogGenreSort.Companion.showGenresSortDialog
import app.simple.felicity.extensions.fragments.PanelFragment
import app.simple.felicity.preferences.GenresPreferences
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.sort.GenreSort.setCurrentSortOrder
import app.simple.felicity.repository.sort.GenreSort.setCurrentSortStyle
import app.simple.felicity.ui.pages.GenrePage
import app.simple.felicity.viewmodels.panels.GenresViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class Genres : PanelFragment() {

    private val genresViewModel: GenresViewModel by viewModels({ requireActivity() })

    private lateinit var binding: FragmentGenresBinding
    private lateinit var headerBinding: HeaderGenresBinding

    private var gridLayoutManager: GridLayoutManager? = null
    private var adapterGenres: AdapterGenres? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentGenresBinding.inflate(inflater, container, false)
        headerBinding = HeaderGenresBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(TAG, "onViewCreated: adapterGenres=${adapterGenres != null}")

        binding.header.setContentView(headerBinding.root)
        binding.header.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        binding.recyclerView.attachSlideFastScroller()
        binding.recyclerView.requireAttachedMiniPlayer()

        // Initialize layout manager once
        gridLayoutManager = GridLayoutManager(requireContext(), GenresPreferences.getGridSize())
        binding.recyclerView.layoutManager = gridLayoutManager
        binding.recyclerView.setGridType(GenresPreferences.getGridType(), GenresPreferences.getGridSize())

        setupClickListeners()

        // Observe StateFlow with proper lifecycle handling for immediate updates
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                genresViewModel.genres
                    .collect { genres ->
                        // Skip empty initial state, but allow empty updates after adapter exists
                        if (genres.isNotEmpty()) {
                            updateGenresList(genres)
                        } else if (adapterGenres != null) {
                            // Allow empty list update if adapter already exists (e.g., after deletion)
                            updateGenresList(genres)
                        }
                    }
            }
        }
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView: Clearing adapter reference")
        // Clear adapter reference when view is destroyed
        adapterGenres = null
        gridLayoutManager = null
        super.onDestroyView()
    }

    private fun setupClickListeners() {
        headerBinding.menu.setOnClickListener {
            childFragmentManager.showGenreMenu()
        }

        headerBinding.sortOrder.setOnClickListener {
            childFragmentManager.showGenresSortDialog()
        }

        headerBinding.sortStyle.setOnClickListener {
            childFragmentManager.showGenresSortDialog()
        }

        headerBinding.search.setOnClickListener {
            openSearch()
        }

        headerBinding.gridSize.setOnClickListener { button ->
            SharedScrollViewPopup(
                    container = requireContainerView(),
                    anchorView = button,
                    menuItems = listOf(R.string.one,
                                       R.string.two,
                                       R.string.three,
                                       R.string.four,
                                       R.string.five,
                                       R.string.six),
                    menuIcons = listOf(R.drawable.ic_one_16,
                                       R.drawable.ic_two_16dp,
                                       R.drawable.ic_three_16dp,
                                       R.drawable.ic_four_16dp,
                                       R.drawable.ic_five_16dp,
                                       R.drawable.ic_six_16dp),
                    onMenuItemClick = {
                        when (it) {
                            R.string.one -> GenresPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_ONE)
                            R.string.two -> GenresPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_TWO)
                            R.string.three -> GenresPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_THREE)
                            R.string.four -> GenresPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_FOUR)
                            R.string.five -> GenresPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_FIVE)
                            R.string.six -> GenresPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_SIX)
                        }
                    },
                    onDismiss = {

                    }
            ).show()
        }

        headerBinding.gridType.setOnClickListener { button ->
            SharedScrollViewPopup(
                    container = requireContainerView(),
                    anchorView = button,
                    menuItems = listOf(
                            R.string.list,
                            R.string.grid
                    ),
                    menuIcons = listOf(
                            R.drawable.ic_list_16dp,
                            R.drawable.ic_grid_16dp
                    ),
                    onMenuItemClick = {
                        when (it) {
                            R.string.list -> GenresPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_LIST)
                            R.string.grid -> GenresPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_GRID)
                        }
                    },
                    onDismiss = {

                    }
            ).show()
        }
    }

    private fun updateGenresList(genres: List<Genre>) {
        Log.d(TAG, "updateGenresList: genres.size=${genres.size}, adapterGenres=${adapterGenres != null}, recyclerView.adapter=${binding.recyclerView.adapter != null}")

        // Initialize adapter on first data arrival to preserve layout animations
        if (adapterGenres == null) {
            Log.d(TAG, "updateGenresList: Creating new adapter with ${genres.size} genres")
            adapterGenres = AdapterGenres(genres.toMutableList())
            adapterGenres?.setHasStableIds(true)
            adapterGenres?.setCallbackListener(object : GeneralAdapterCallbacks {
                override fun onMenuClicked(view: View) {
                    childFragmentManager.showGenreMenu()
                }

                override fun onGenreClicked(genre: Genre, view: View) {
                    Log.d(TAG, "onGenreClicked: Genre: ${genre.name}")
                    openFragment(GenrePage.newInstance(genre), GenrePage.TAG)
                }
            })
            binding.recyclerView.adapter = adapterGenres
            Log.d(TAG, "updateGenresList: Adapter attached to RecyclerView")
        } else {
            // Update existing adapter data
            Log.d(TAG, "updateGenresList: Updating existing adapter with ${genres.size} genres")
            adapterGenres?.updateList(genres)

            // Re-attach adapter if RecyclerView lost its reference (e.g., after navigation)
            if (binding.recyclerView.adapter == null) {
                Log.d(TAG, "updateGenresList: Re-attaching adapter to RecyclerView")
                binding.recyclerView.adapter = adapterGenres
            }
        }

        headerBinding.count.text = getString(R.string.x_genres, genres.size)
        binding.recyclerView.requireAttachedSectionScroller(
                sections = provideScrollPositionDataBasedOnSortStyle(genres),
                header = binding.header,
                view = headerBinding.scroll)

        headerBinding.sortStyle.setCurrentSortStyle()
        headerBinding.sortOrder.setCurrentSortOrder()
        headerBinding.gridSize.setGridSizeValue(GenresPreferences.getGridSize())
        headerBinding.gridType.setGridTypeValue(GenresPreferences.getGridType())
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            GenresPreferences.GRID_SIZE_PORTRAIT, GenresPreferences.GRID_SIZE_LANDSCAPE -> {
                headerBinding.gridSize.setGridSizeValue(GenresPreferences.getGridSize())
                binding.recyclerView.beginDelayedTransition()
                gridLayoutManager?.spanCount = GenresPreferences.getGridSize()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
            GenresPreferences.GRID_TYPE_PORTRAIT, GenresPreferences.GRID_TYPE_LANDSCAPE -> {
                binding.recyclerView.setGridType(GenresPreferences.getGridType(), GenresPreferences.getGridSize())
                headerBinding.gridType.setGridTypeValue(GenresPreferences.getGridType())
                binding.recyclerView.beginDelayedTransition()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
            GenresPreferences.SHOW_GENRE_COVERS -> {
                adapterGenres?.notifyDataSetChanged()
            }
        }
    }

    private fun provideScrollPositionDataBasedOnSortStyle(genres: List<Genre>): List<SectionedFastScroller.Position> {
        return when (GenresPreferences.getSortStyle()) {
            CommonPreferencesConstants.BY_NAME -> {
                val firstAlphabetToIndex = linkedMapOf<String, Int>()
                genres.forEachIndexed { index, genre ->
                    val firstChar = genre.name?.firstOrNull()?.uppercaseChar()
                    val key = if (firstChar != null && firstChar.isLetter()) {
                        firstChar.toString()
                    } else {
                        "#"
                    }
                    if (!firstAlphabetToIndex.containsKey(key)) {
                        firstAlphabetToIndex[key] = index
                    }
                }
                firstAlphabetToIndex.map { (char, index) ->
                    SectionedFastScroller.Position(char, index)
                }
            }
            else -> {
                listOf()
            }
        }
    }

    companion object {
        const val TAG = "GenresFragment"

        fun newInstance(): Genres {
            val args = Bundle()
            val fragment = Genres()
            fragment.arguments = args
            return fragment
        }
    }
}