package app.simple.felicity.ui.main.songs

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.songs.SongsAdapter
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.core.utils.TimeUtils.toHighlightedTimeString
import app.simple.felicity.databinding.FragmentSongsBinding
import app.simple.felicity.databinding.HeaderSongsBinding
import app.simple.felicity.decorations.fastscroll.SectionedFastScroller
import app.simple.felicity.decorations.fastscroll.SlideFastScroller
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.decorations.views.SharedScrollViewPopup
import app.simple.felicity.dialogs.songs.SongMenu.Companion.showSongMenu
import app.simple.felicity.dialogs.songs.SongsMenu.Companion.showSongsMenu
import app.simple.felicity.dialogs.songs.SongsSort.Companion.showSongsSort
import app.simple.felicity.extensions.fragments.PanelFragment
import app.simple.felicity.preferences.SongsPreferences
import app.simple.felicity.repository.models.Song
import app.simple.felicity.repository.sort.SongSort.setSongOrder
import app.simple.felicity.repository.sort.SongSort.setSongSort
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.viewmodels.main.songs.SongsViewModel

class Songs : PanelFragment() {

    private lateinit var binding: FragmentSongsBinding
    private lateinit var headerBinding: HeaderSongsBinding

    private var songsAdapter: SongsAdapter? = null
    private var gridLayoutManager: GridLayoutManager? = null

    private val songsViewModel: SongsViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSongsBinding.inflate(inflater, container, false)
        headerBinding = HeaderSongsBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.requireAttachedMiniPlayer()
        binding.appHeader.setContentView(headerBinding.root)
        binding.appHeader.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        SlideFastScroller.attach(binding.recyclerView)

        songsViewModel.getSongs().observe(viewLifecycleOwner) { songs ->
            binding.recyclerView.requireAttachedSectionScroller(
                    sections = provideScrollPositionDataBasedOnSortStyle(songs),
                    header = binding.appHeader,
                    view = headerBinding.scroll
            )

            if (gridLayoutManager == null) {
                gridLayoutManager = GridLayoutManager(requireContext(), SongsPreferences.getGridSize(requireContext()))
                binding.recyclerView.layoutManager = gridLayoutManager
            }

            binding.recyclerView.setGridType(SongsPreferences.getGridType())
            songsAdapter = SongsAdapter(songs)

            songsAdapter?.setGeneralAdapterCallbacks(object : GeneralAdapterCallbacks {
                override fun onSongClicked(songs: List<Song>, position: Int, view: View?) {
                    setMediaItems(songs, position)
                }

                override fun onSongLongClicked(songs: List<Song>, position: Int, view: View?) {
                    childFragmentManager.showSongMenu(songs[position])
                }
            })

            headerBinding.sortStyle.setSongSort()
            headerBinding.sortOrder.setSongOrder()
            headerBinding.count.text = getString(R.string.x_songs, songs.size)
            headerBinding.hours.text = songs.sumOf { it.duration }.toHighlightedTimeString(ThemeManager.theme.textViewTheme.tertiaryTextColor)
            headerBinding.gridSize.setGridSizeValue(SongsPreferences.getGridSize(requireContext()))
            headerBinding.gridType.setGridTypeValue(SongsPreferences.getGridType())

            headerBinding.menu.setOnClickListener {
                childFragmentManager.showSongsMenu()
            }

            headerBinding.sortStyle.setOnClickListener {
                childFragmentManager.showSongsSort()
            }

            headerBinding.sortOrder.setOnClickListener {
                childFragmentManager.showSongsSort()
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
                                R.string.one -> SongsPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_ONE, requireContext())
                                R.string.two -> SongsPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_TWO, requireContext())
                                R.string.three -> SongsPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_THREE, requireContext())
                                R.string.four -> SongsPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_FOUR, requireContext())
                                R.string.five -> SongsPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_FIVE, requireContext())
                                R.string.six -> SongsPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_SIX, requireContext())
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
                                R.string.list -> SongsPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_LIST)
                                R.string.grid -> SongsPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_GRID)
                                R.string.peristyle -> SongsPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_PERISTYLE)
                            }
                        },
                        onDismiss = {

                        }
                ).show()
            }

            binding.recyclerView.adapter = songsAdapter
        }
    }

    override fun onSong(song: Song) {
        super.onSong(song)
        songsAdapter?.currentlyPlayingSong = song
    }

    private fun provideScrollPositionDataBasedOnSortStyle(songs: List<Song>): List<SectionedFastScroller.Position> {
        return when (SongsPreferences.getSongSort()) {
            CommonPreferencesConstants.BY_TITLE -> {
                val firstAlphabetToIndex = linkedMapOf<String, Int>()
                songs.forEachIndexed { index, song ->
                    val firstChar = song.title?.firstOrNull()?.uppercaseChar()
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
            CommonPreferencesConstants.BY_ARTIST -> {
                val firstAlphabetToIndex = linkedMapOf<Char, Int>()
                songs.forEachIndexed { index, song ->
                    song.artist?.firstOrNull()?.uppercaseChar()?.let { firstChar ->
                        if (firstChar.isLetter() && !firstAlphabetToIndex.containsKey(firstChar)) {
                            firstAlphabetToIndex[firstChar] = index
                        }
                    }
                }
                firstAlphabetToIndex.map { (char, index) ->
                    SectionedFastScroller.Position(char.toString(), index)
                }
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
                firstAlphabetToIndex.map { (char, index) ->
                    SectionedFastScroller.Position(char.toString(), index)
                }
            }
            CommonPreferencesConstants.BY_YEAR -> {
                val firstAlphabetToIndex = linkedMapOf<String, Int>()
                songs.forEachIndexed { index, song ->
                    val key = song.year?.toString()?.takeIf { it.all { ch -> ch.isDigit() } } ?: "#"
                    if (!firstAlphabetToIndex.containsKey(key)) {
                        firstAlphabetToIndex[key] = index
                    }
                }
                firstAlphabetToIndex.map { (year, index) ->
                    SectionedFastScroller.Position(year, index)
                }
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
                firstAlphabetToIndex.map { (char, index) ->
                    SectionedFastScroller.Position(char.toString(), index)
                }
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            SongsPreferences.GRID_SIZE_PORTRAIT, SongsPreferences.GRID_SIZE_LANDSCAPE -> {
                headerBinding.gridSize.setGridSizeValue(SongsPreferences.getGridSize(requireContext()))
                binding.recyclerView.beginDelayedTransition()
                gridLayoutManager?.spanCount = SongsPreferences.getGridSize(requireContext())
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
            SongsPreferences.GRID_TYPE -> {
                binding.recyclerView.setGridType(SongsPreferences.getGridType())
                binding.recyclerView.beginDelayedTransition()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
        }
    }

    companion object {
        const val TAG = "Songs"

        fun newInstance(): Songs {
            val args = Bundle()
            val fragment = Songs()
            fragment.arguments = args
            return fragment
        }
    }
}
