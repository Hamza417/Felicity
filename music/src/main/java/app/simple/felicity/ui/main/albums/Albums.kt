package app.simple.felicity.ui.main.albums

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.albums.AdapterAlbums
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.FragmentAlbumsBinding
import app.simple.felicity.databinding.HeaderAlbumsBinding
import app.simple.felicity.decorations.fastscroll.SectionedFastScroller
import app.simple.felicity.decorations.fastscroll.SlideFastScroller
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.decorations.views.SharedScrollViewPopup
import app.simple.felicity.dialogs.albums.AlbumsSort.Companion.showAlbumsSort
import app.simple.felicity.extensions.fragments.PanelFragment
import app.simple.felicity.preferences.AlbumPreferences
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.sort.AlbumSort.setCurrentSortOrder
import app.simple.felicity.repository.sort.AlbumSort.setCurrentSortStyle
import app.simple.felicity.viewmodels.main.albums.AlbumsViewModel

class Albums : PanelFragment() {

    private lateinit var binding: FragmentAlbumsBinding
    private lateinit var headerBinding: HeaderAlbumsBinding

    private var adapterAlbums: AdapterAlbums? = null
    private var gridLayoutManager: GridLayoutManager? = null

    private val albumsViewModel: AlbumsViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentAlbumsBinding.inflate(inflater, container, false)
        headerBinding = HeaderAlbumsBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.header.setContentView(headerBinding.root)
        binding.header.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        SlideFastScroller.attach(binding.recyclerView)
        binding.recyclerView.requireAttachedMiniPlayer()

        albumsViewModel.getAlbums().observe(viewLifecycleOwner) { it ->
            adapterAlbums = AdapterAlbums(it)
            gridLayoutManager = GridLayoutManager(requireContext(), AlbumPreferences.getGridSize(isLandscape))
            binding.recyclerView.layoutManager = gridLayoutManager
            binding.recyclerView.setGridType(AlbumPreferences.getGridType(), AlbumPreferences.getGridSize(isLandscape))
            adapterAlbums?.setHasStableIds(true)
            headerBinding.count.text = getString(R.string.x_albums, it.size)
            binding.recyclerView.requireAttachedSectionScroller(
                    sections = provideScrollPositionDataBasedOnSortStyle(albums = it),
                    header = binding.header,
                    view = headerBinding.scroll)

            adapterAlbums?.setGeneralAdapterCallbacks(object : GeneralAdapterCallbacks {
                override fun onAlbumClicked(albums: List<Album>, position: Int, view: View?) {
                    openFragment(AlbumPage.newInstance(albums[position]), AlbumPage.TAG)
                }
            })

            headerBinding.sortStyle.setOnClickListener {
                childFragmentManager.showAlbumsSort()
            }

            headerBinding.sortOrder.setOnClickListener {
                childFragmentManager.showAlbumsSort()
            }

            headerBinding.sortStyle.setCurrentSortStyle()
            headerBinding.sortOrder.setCurrentSortOrder()
            headerBinding.scroll.hideOnUnfavorableSort(
                    sorts = listOf(
                            CommonPreferencesConstants.BY_ALBUM_NAME,
                            CommonPreferencesConstants.BY_ARTIST,
                            CommonPreferencesConstants.BY_FIRST_YEAR,
                            CommonPreferencesConstants.BY_LAST_YEAR
                    ),
                    preference = AlbumPreferences.getAlbumSort()
            )

            headerBinding.gridSize.setGridSizeValue(AlbumPreferences.getGridSize(isLandscape))
            headerBinding.gridType.setGridTypeValue(AlbumPreferences.getGridType())

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
                                R.string.one -> AlbumPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_ONE, isLandscape)
                                R.string.two -> AlbumPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_TWO, isLandscape)
                                R.string.three -> AlbumPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_THREE, isLandscape)
                                R.string.four -> AlbumPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_FOUR, isLandscape)
                                R.string.five -> AlbumPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_FIVE, isLandscape)
                                R.string.six -> AlbumPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_SIX, isLandscape)
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
                                R.string.list -> AlbumPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_LIST)
                                R.string.grid -> AlbumPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_GRID)
                                R.string.peristyle -> AlbumPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_PERISTYLE)
                            }
                        },
                        onDismiss = {

                        }
                ).show()
            }

            binding.recyclerView.adapter = adapterAlbums
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            AlbumPreferences.GRID_SIZE_PORTRAIT, AlbumPreferences.GRID_SIZE_LANDSCAPE -> {
                headerBinding.gridSize.setGridSizeValue(AlbumPreferences.getGridSize(isLandscape))
                binding.recyclerView.beginDelayedTransition()
                gridLayoutManager?.spanCount = AlbumPreferences.getGridSize(isLandscape)
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
            AlbumPreferences.GRID_TYPE -> {
                binding.recyclerView.setGridType(AlbumPreferences.getGridType(), AlbumPreferences.getGridSize(isLandscape))
                headerBinding.gridType.setGridTypeValue(AlbumPreferences.getGridType())
                binding.recyclerView.beginDelayedTransition()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
        }
    }

    private fun provideScrollPositionDataBasedOnSortStyle(albums: List<Album>): List<SectionedFastScroller.Position> {
        when (AlbumPreferences.getAlbumSort()) {
            CommonPreferencesConstants.BY_ALBUM_NAME -> {
                val firstAlphabetToIndex = linkedMapOf<String, Int>()
                albums.forEachIndexed { index, album ->
                    val firstChar = album.name?.firstOrNull()?.uppercaseChar()
                    val key = if (firstChar != null && firstChar.isLetter()) {
                        firstChar.toString()
                    } else {
                        "#"
                    }
                    if (!firstAlphabetToIndex.containsKey(key)) {
                        firstAlphabetToIndex[key] = index
                    }
                }
                return firstAlphabetToIndex.map { (char, index) ->
                    SectionedFastScroller.Position(char, index)
                }
            }
            CommonPreferencesConstants.BY_ARTIST -> {
                val firstAlphabetToIndex = linkedMapOf<Char, Int>()
                albums.forEachIndexed { index, album ->
                    album.artist?.firstOrNull()?.uppercaseChar()?.let { firstChar ->
                        if (firstChar.isLetter() && !firstAlphabetToIndex.containsKey(firstChar)) {
                            firstAlphabetToIndex[firstChar] = index
                        }
                    }
                }
                return firstAlphabetToIndex.map { (char, index) ->
                    SectionedFastScroller.Position(char.toString(), index)
                }
            }
            CommonPreferencesConstants.BY_FIRST_YEAR, CommonPreferencesConstants.BY_LAST_YEAR -> {
                val yearToIndex = linkedMapOf<String, Int>()
                albums.forEachIndexed { index, album ->
                    val year = if (AlbumPreferences.getAlbumSort() == CommonPreferencesConstants.BY_FIRST_YEAR) {
                        album.firstYear
                    } else {
                        album.lastYear
                    }.toString()

                    if (!yearToIndex.containsKey(year)) {
                        yearToIndex[year] = index
                    }
                }
                return yearToIndex.map { (year, index) ->
                    SectionedFastScroller.Position(year, index)
                }
            }
        }

        return emptyList()
    }

    companion object {
        fun newInstance(): Albums {
            val args = Bundle()
            val fragment = Albums()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "DefaultAlbums"
    }
}