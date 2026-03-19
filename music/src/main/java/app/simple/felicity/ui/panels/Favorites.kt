package app.simple.felicity.ui.panels

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.AdapterSongs
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.FragmentFavoritesBinding
import app.simple.felicity.databinding.HeaderFavoritesBinding
import app.simple.felicity.decorations.fastscroll.SectionedFastScroller
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.decorations.views.SharedScrollViewPopup
import app.simple.felicity.dialogs.favorites.FavoritesMenu.Companion.showFavoritesMenu
import app.simple.felicity.dialogs.favorites.FavoritesSort.Companion.showFavoritesSort
import app.simple.felicity.dialogs.songs.ShuffleAlgorithmDialog.Companion.showShuffleAlgorithmDialog
import app.simple.felicity.extensions.fragments.PanelFragment
import app.simple.felicity.preferences.FavoritesPreferences
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.sort.FavoritesSort.setFavoritesOrder
import app.simple.felicity.repository.sort.FavoritesSort.setFavoritesSort
import app.simple.felicity.shared.utils.TimeUtils.toHighlightedTimeString
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.viewmodels.panels.FavoritesViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Panel fragment that displays the user's favorite songs with full sort, grid, and shuffle support.
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class Favorites : PanelFragment() {

    private lateinit var binding: FragmentFavoritesBinding
    private lateinit var headerBinding: HeaderFavoritesBinding

    private var adapterSongs: AdapterSongs? = null
    private var gridLayoutManager: GridLayoutManager? = null

    private val favoritesViewModel: FavoritesViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        headerBinding = HeaderFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.requireAttachedMiniPlayer()
        binding.appHeader.setContentView(headerBinding.root)
        binding.appHeader.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        binding.recyclerView.attachSlideFastScroller()

        gridLayoutManager = GridLayoutManager(requireContext(), FavoritesPreferences.getGridSize())
        binding.recyclerView.layoutManager = gridLayoutManager
        binding.recyclerView.setGridType(FavoritesPreferences.getGridType(), FavoritesPreferences.getGridSize())

        setupClickListeners()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                favoritesViewModel.favorites.collect { songs ->
                    if (songs.isNotEmpty()) {
                        updateFavoritesList(songs)
                    } else if (adapterSongs != null) {
                        updateFavoritesList(songs)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        adapterSongs = null
        gridLayoutManager = null
        super.onDestroyView()
    }

    private fun setupClickListeners() {
        headerBinding.sortStyle.setFavoritesSort()
        headerBinding.sortOrder.setFavoritesOrder()
        headerBinding.gridSize.setGridSizeValue(FavoritesPreferences.getGridSize())
        headerBinding.gridType.setGridTypeValue(FavoritesPreferences.getGridType())

        headerBinding.menu.setOnClickListener {
            childFragmentManager.showFavoritesMenu()
        }

        headerBinding.shuffle.setOnClickListener {
            val songs = favoritesViewModel.favorites.value
            if (songs.isNotEmpty()) shuffleMediaItems(songs)
        }

        headerBinding.shuffle.setOnLongClickListener {
            childFragmentManager.showShuffleAlgorithmDialog()
            true
        }

        headerBinding.sortStyle.setOnClickListener {
            childFragmentManager.showFavoritesSort()
        }

        headerBinding.sortOrder.setOnClickListener {
            childFragmentManager.showFavoritesSort()
        }

        headerBinding.search.setOnClickListener {
            openSearch()
        }

        headerBinding.gridSize.setOnClickListener { button ->
            SharedScrollViewPopup(
                    container = requireContainerView(),
                    anchorView = button,
                    menuItems = listOf(
                            R.string.one,
                            R.string.two,
                            R.string.three,
                            R.string.four,
                            R.string.five,
                            R.string.six),
                    menuIcons = listOf(
                            R.drawable.ic_one_16,
                            R.drawable.ic_two_16dp,
                            R.drawable.ic_three_16dp,
                            R.drawable.ic_four_16dp,
                            R.drawable.ic_five_16dp,
                            R.drawable.ic_six_16dp),
                    onMenuItemClick = {
                        when (it) {
                            R.string.one -> FavoritesPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_ONE)
                            R.string.two -> FavoritesPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_TWO)
                            R.string.three -> FavoritesPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_THREE)
                            R.string.four -> FavoritesPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_FOUR)
                            R.string.five -> FavoritesPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_FIVE)
                            R.string.six -> FavoritesPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_SIX)
                        }
                    },
                    onDismiss = {}
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
                            R.string.list -> FavoritesPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_LIST)
                            R.string.grid -> FavoritesPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_GRID)
                        }
                    },
                    onDismiss = {}
            ).show()
        }
    }

    /**
     * Updates the favorites list and refreshes all header chips.
     * Initialises the adapter on first call and diffs subsequent updates.
     *
     * @param songs the latest sorted list of favorite [Audio] entries
     */
    private fun updateFavoritesList(songs: List<Audio>) {
        if (adapterSongs == null) {
            adapterSongs = AdapterSongs(songs)
            adapterSongs?.setHasStableIds(true)
            adapterSongs?.setGeneralAdapterCallbacks(object : GeneralAdapterCallbacks {
                override fun onSongClicked(songs: MutableList<Audio>, position: Int, view: View) {
                    setMediaItems(songs, position)
                }

                override fun onSongLongClicked(audios: MutableList<Audio>, position: Int, view: View) {
                    openSongsMenu(audios, position, view as ImageView)
                }
            })
            binding.recyclerView.adapter = adapterSongs
        } else {
            adapterSongs?.updateSongs(songs)
            if (binding.recyclerView.adapter == null) {
                binding.recyclerView.adapter = adapterSongs
            }
        }

        binding.recyclerView.requireAttachedSectionScroller(
                sections = provideScrollPositionDataBasedOnSortStyle(songs),
                header = binding.appHeader,
                view = headerBinding.scroll
        )

        headerBinding.count.text = getString(R.string.x_songs, songs.size)
        headerBinding.hours.text = songs.sumOf { it.duration }.toHighlightedTimeString(ThemeManager.theme.textViewTheme.tertiaryTextColor)
        headerBinding.sortStyle.setFavoritesSort()
        headerBinding.sortOrder.setFavoritesOrder()
    }

    private fun provideScrollPositionDataBasedOnSortStyle(songs: List<Audio>): List<SectionedFastScroller.Position> {
        return when (FavoritesPreferences.getSongSort()) {
            CommonPreferencesConstants.BY_TITLE -> {
                val firstAlphabetToIndex = linkedMapOf<String, Int>()
                songs.forEachIndexed { index, song ->
                    val firstChar = song.title?.firstOrNull()?.uppercaseChar()
                    val key = if (firstChar != null && firstChar.isLetter()) firstChar.toString() else "#"
                    if (!firstAlphabetToIndex.containsKey(key)) firstAlphabetToIndex[key] = index
                }
                firstAlphabetToIndex.map { (char, index) -> SectionedFastScroller.Position(char, index) }
            }
            CommonPreferencesConstants.BY_ARTIST -> {
                val firstAlphabetToIndex = linkedMapOf<Char, Int>()
                songs.forEachIndexed { index, song ->
                    song.artist?.firstOrNull()?.uppercaseChar()?.let { firstChar ->
                        if (firstChar.isLetter() && !firstAlphabetToIndex.containsKey(firstChar)) {
                            firstAlphabetToIndex[firstChar] = index
                        }
                    }
                }
                firstAlphabetToIndex.map { (char, index) -> SectionedFastScroller.Position(char.toString(), index) }
            }
            CommonPreferencesConstants.BY_ALBUM -> {
                val firstAlphabetToIndex = linkedMapOf<Char, Int>()
                songs.forEachIndexed { index, song ->
                    song.album?.firstOrNull()?.uppercaseChar()?.let { firstChar ->
                        if (firstChar.isLetter() && !firstAlphabetToIndex.containsKey(firstChar)) {
                            firstAlphabetToIndex[firstChar] = index
                        }
                    }
                }
                firstAlphabetToIndex.map { (char, index) -> SectionedFastScroller.Position(char.toString(), index) }
            }
            CommonPreferencesConstants.BY_YEAR -> {
                val firstAlphabetToIndex = linkedMapOf<String, Int>()
                songs.forEachIndexed { index, song ->
                    val key = song.year?.takeIf { it.all { ch -> ch.isDigit() } } ?: "#"
                    if (!firstAlphabetToIndex.containsKey(key)) firstAlphabetToIndex[key] = index
                }
                firstAlphabetToIndex.map { (year, index) -> SectionedFastScroller.Position(year, index) }
            }
            else -> {
                val firstAlphabetToIndex = linkedMapOf<Char, Int>()
                songs.forEachIndexed { index, song ->
                    song.title?.firstOrNull()?.uppercaseChar()?.let { firstChar ->
                        if (firstChar.isLetter() && !firstAlphabetToIndex.containsKey(firstChar)) {
                            firstAlphabetToIndex[firstChar] = index
                        }
                    }
                }
                firstAlphabetToIndex.map { (char, index) -> SectionedFastScroller.Position(char.toString(), index) }
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            FavoritesPreferences.GRID_SIZE_PORTRAIT, FavoritesPreferences.GRID_SIZE_LANDSCAPE -> {
                headerBinding.gridSize.setGridSizeValue(FavoritesPreferences.getGridSize())
                binding.recyclerView.beginDelayedTransition()
                gridLayoutManager?.spanCount = FavoritesPreferences.getGridSize()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
            FavoritesPreferences.GRID_TYPE_PORTRAIT, FavoritesPreferences.GRID_TYPE_LANDSCAPE -> {
                binding.recyclerView.setGridType(FavoritesPreferences.getGridType(), FavoritesPreferences.getGridSize())
                headerBinding.gridType.setGridTypeValue(FavoritesPreferences.getGridType())
                binding.recyclerView.beginDelayedTransition()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
        }
    }

    companion object {
        const val TAG = "Favorites"

        fun newInstance(): Favorites {
            val args = Bundle()

            val fragment = Favorites()
            fragment.arguments = args
            return fragment
        }
    }
}