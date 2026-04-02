package app.simple.felicity.ui.panels

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
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
import app.simple.felicity.databinding.FragmentSongsBinding
import app.simple.felicity.databinding.HeaderSongsBinding
import app.simple.felicity.decorations.fastscroll.SectionedFastScroller
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.dialogs.songs.ShuffleAlgorithmDialog.Companion.showShuffleAlgorithmDialog
import app.simple.felicity.dialogs.songs.SongsMenu.Companion.showSongsMenu
import app.simple.felicity.dialogs.songs.SongsSort.Companion.showSongsSort
import app.simple.felicity.extensions.fragments.PanelFragment
import app.simple.felicity.preferences.SongsPreferences
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.sort.SongSort.setSongOrder
import app.simple.felicity.repository.sort.SongSort.setSongSort
import app.simple.felicity.shared.utils.TimeUtils.toHighlightedTimeString
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.viewmodels.panels.SongsViewModel
import kotlinx.coroutines.launch

class Songs : PanelFragment() {

    private lateinit var binding: FragmentSongsBinding
    private lateinit var headerBinding: HeaderSongsBinding

    private var adapterSongs: AdapterSongs? = null
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
        binding.recyclerView.attachSlideFastScroller()

        // Initialize layout manager once
        gridLayoutManager = GridLayoutManager(requireContext(), SongsPreferences.getGridSize().spanCount)
        binding.recyclerView.layoutManager = gridLayoutManager

        setupClickListeners()

        // Observe StateFlow with proper lifecycle handling for immediate updates
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                songsViewModel.songs.collect { audios ->
                    Log.d(TAG, "Received songs update: ${audios.size} songs")
                    // Skip empty initial state, but allow empty updates after adapter exists
                    if (audios.isNotEmpty()) {
                        updateSongsList(audios)
                    } else if (adapterSongs != null) {
                        // Allow empty list update if adapter already exists (e.g., after deletion)
                        updateSongsList(audios)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        // Clear adapter reference when view is destroyed
        adapterSongs = null
        gridLayoutManager = null
        super.onDestroyView()
    }

    private fun setupClickListeners() {
        // Set up header UI once
        headerBinding.sortStyle.setSongSort()
        headerBinding.sortOrder.setSongOrder()

        headerBinding.menu.setOnClickListener {
            childFragmentManager.showSongsMenu()
        }

        headerBinding.shuffle.setOnClickListener {
            val songs = songsViewModel.songs.value
            if (songs.isNotEmpty()) shuffleMediaItems(songs)
        }

        headerBinding.shuffle.setOnLongClickListener {
            childFragmentManager.showShuffleAlgorithmDialog()
            true
        }

        headerBinding.sortStyle.setOnClickListener {
            childFragmentManager.showSongsSort()
        }

        headerBinding.sortOrder.setOnClickListener {
            childFragmentManager.showSongsSort()
        }

        headerBinding.search.setOnClickListener {
            openSearch()
        }
    }

    /**
     * Update songs list and header information
     * This method is called whenever the Flow emits new data
     */
    private fun updateSongsList(songs: List<Audio>) {
        // Initialize adapter on first data arrival to preserve layout animations
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
            // Update existing adapter data using a background coroutine for the diff calculation
            adapterSongs?.updateSongs(songs)

            // Re-attach adapter if RecyclerView lost its reference (e.g., after navigation)
            if (binding.recyclerView.adapter == null) {
                binding.recyclerView.adapter = adapterSongs
            }
        }

        // Update section scroller
        binding.recyclerView.requireAttachedSectionScroller(
                sections = provideScrollPositionDataBasedOnSortStyle(songs),
                header = binding.appHeader,
                view = headerBinding.scroll
        )

        // Update header counts
        headerBinding.count.text = getString(R.string.x_songs, songs.size)
        headerBinding.hours.text = songs.sumOf { it.duration }.toHighlightedTimeString(ThemeManager.theme.textViewTheme.tertiaryTextColor)
        headerBinding.sortStyle.setSongSort()
        headerBinding.sortOrder.setSongOrder()
    }

    private fun provideScrollPositionDataBasedOnSortStyle(songs: List<Audio>): List<SectionedFastScroller.Position> {
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
                    val key = song.year?.takeIf { it.all { ch -> ch.isDigit() } } ?: "#"
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
                val newMode = SongsPreferences.getGridSize()
                gridLayoutManager?.spanCount = newMode.spanCount
                adapterSongs?.layoutMode = newMode
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