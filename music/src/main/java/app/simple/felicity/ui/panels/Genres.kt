package app.simple.felicity.ui.panels

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.genres.AdapterGenres
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.FragmentGenresBinding
import app.simple.felicity.databinding.HeaderGenresBinding
import app.simple.felicity.decorations.fastscroll.SectionedFastScroller
import app.simple.felicity.decorations.fastscroll.SlideFastScroller
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
import app.simple.felicity.viewmodels.main.genres.GenresViewModel

class Genres : PanelFragment() {

    private val genresViewModel: GenresViewModel by viewModels({ requireActivity() })

    private lateinit var binding: FragmentGenresBinding
    private lateinit var headerBinding: HeaderGenresBinding

    private lateinit var gridLayoutManager: GridLayoutManager
    private lateinit var adapterGenres: AdapterGenres

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        binding = FragmentGenresBinding.inflate(inflater, container, false)
        headerBinding = HeaderGenresBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.setContentView(headerBinding.root)
        binding.header.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        SlideFastScroller.Companion.attach(binding.recyclerView)
        binding.recyclerView.requireAttachedMiniPlayer()
        postponeEnterTransition()

        genresViewModel.getGenresData().observe(viewLifecycleOwner) { genres ->
            Log.d(TAG, "onViewCreated: Genres: ${genres.size}")
            adapterGenres = AdapterGenres(genres)
            adapterGenres.setHasStableIds(true)

            binding.recyclerView.requireAttachedSectionScroller(
                    sections = provideScrollPositionDataBasedOnSortStyle(genres),
                    header = binding.header,
                    view = headerBinding.scroll
            )

            gridLayoutManager = GridLayoutManager(requireContext(), GenresPreferences.getGridSize())
            binding.recyclerView.layoutManager = gridLayoutManager
            binding.recyclerView.setGridType(GenresPreferences.getGridType(), GenresPreferences.getGridSize())
            binding.recyclerView.setHasFixedSize(true)
            binding.recyclerView.adapter = adapterGenres

            adapterGenres.setCallbackListener(object : GeneralAdapterCallbacks {
                override fun onMenuClicked(view: View) {
                    childFragmentManager.showGenreMenu()
                }

                override fun onGenreClicked(genre: Genre, view: View) {
                    Log.d(TAG, "onGenreClicked: Genre: ${genre.name}")
                    openFragment(GenrePage.newInstance(genre), GenrePage.TAG)
                }
            })

            headerBinding.count.text = getString(R.string.x_genres, genres.size)
            headerBinding.sortStyle.setCurrentSortStyle()
            headerBinding.sortOrder.setCurrentSortOrder()
            headerBinding.gridSize.setGridSizeValue(GenresPreferences.getGridSize())
            headerBinding.gridType.setGridTypeValue(GenresPreferences.getGridType())

            headerBinding.menu.setOnClickListener {
                childFragmentManager.showGenreMenu()
            }

            headerBinding.sortOrder.setOnClickListener {
                childFragmentManager.showGenresSortDialog()
            }

            headerBinding.sortStyle.setOnClickListener {
                childFragmentManager.showGenresSortDialog()
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
                                R.string.grid,
                                R.string.peristyle,
                        ),
                        menuIcons = listOf(
                                R.drawable.ic_list_16dp,
                                R.drawable.ic_grid_16dp,
                                R.drawable.ic_peristyle_16dp,
                        ),
                        onMenuItemClick = {
                            when (it) {
                                R.string.list -> GenresPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_LIST)
                                R.string.grid -> GenresPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_GRID)
                                R.string.peristyle -> GenresPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_PERISTYLE)
                            }
                        },
                        onDismiss = {

                        }
                ).show()
            }

            view.startTransitionOnPreDraw()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            GenresPreferences.GRID_SIZE_PORTRAIT, GenresPreferences.GRID_SIZE_LANDSCAPE -> {
                headerBinding.gridSize.setGridSizeValue(GenresPreferences.getGridSize())
                binding.recyclerView.beginDelayedTransition()
                gridLayoutManager.spanCount = GenresPreferences.getGridSize()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
            GenresPreferences.GRID_TYPE_PORTRAIT, GenresPreferences.GRID_TYPE_LANDSCAPE -> {
                binding.recyclerView.setGridType(GenresPreferences.getGridType(), GenresPreferences.getGridSize())
                headerBinding.gridType.setGridTypeValue(GenresPreferences.getGridType())
                binding.recyclerView.beginDelayedTransition()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
            GenresPreferences.SHOW_GENRE_COVERS -> {
                adapterGenres.notifyDataSetChanged()
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