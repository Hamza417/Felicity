package app.simple.felicity.ui.panels

import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import app.simple.felicity.adapters.ui.lists.AdapterSearch
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.FragmentSearchBinding
import app.simple.felicity.databinding.HeaderSearchBinding
import app.simple.felicity.decorations.fastscroll.SectionedFastScroller
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.dialogs.search.SearchSort.Companion.showSearchSort
import app.simple.felicity.dialogs.songs.SongsMenu.Companion.showSongsMenu
import app.simple.felicity.extensions.fragments.PanelFragment
import app.simple.felicity.preferences.SearchPreferences
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.sort.SearchSort.setSearchOrder
import app.simple.felicity.repository.sort.SearchSort.setSearchSort
import app.simple.felicity.shared.utils.TimeUtils.toHighlightedTimeString
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.viewmodels.panels.SearchViewModel
import kotlinx.coroutines.launch

class Search : PanelFragment() {

    private lateinit var binding: FragmentSearchBinding
    private lateinit var headerBinding: HeaderSearchBinding

    private var adapterSearch: AdapterSearch? = null
    private var gridLayoutManager: GridLayoutManager? = null

    private val searchViewModel: SearchViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSearchBinding.inflate(inflater, container, false)
        headerBinding = HeaderSearchBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.requireAttachedMiniPlayer()
        binding.appHeader.setContentView(headerBinding.root)
        binding.appHeader.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        binding.recyclerView.attachSlideFastScroller()

        gridLayoutManager = GridLayoutManager(requireContext(), SearchPreferences.getGridSize())
        binding.recyclerView.layoutManager = gridLayoutManager
        binding.recyclerView.setGridType(SearchPreferences.getGridType(), SearchPreferences.getGridSize())

        setupClickListeners()

        // Restore previous query to the EditText
        viewLifecycleOwner.lifecycleScope.launch {
            searchViewModel.searchQuery.collect { query ->
                if (headerBinding.editText.text.toString() != query) {
                    headerBinding.editText.setText(query)
                    headerBinding.editText.setSelection(query.length)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                searchViewModel.songs.collect { audios ->
                    updateSongsList(audios)
                }
            }
        }
    }

    override fun onDestroyView() {
        adapterSearch = null
        gridLayoutManager = null
        super.onDestroyView()
    }

    private fun setupClickListeners() {
        headerBinding.sortStyle.setSearchSort()
        headerBinding.sortOrder.setSearchOrder()

        headerBinding.editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                searchViewModel.setSearchQuery(s?.toString() ?: "")
            }
        })

        headerBinding.menu.setOnClickListener {
            childFragmentManager.showSongsMenu()
        }

        headerBinding.sortStyle.setOnClickListener {
            childFragmentManager.showSearchSort()
        }

        headerBinding.sortOrder.setOnClickListener {
            childFragmentManager.showSearchSort()
        }

        headerBinding.scroll.setOnClickListener {
            binding.recyclerView.smoothScrollToPosition(0)
        }
    }

    private fun updateSongsList(songs: List<Audio>) {
        if (adapterSearch == null) {
            adapterSearch = AdapterSearch(songs)
            adapterSearch?.setHasStableIds(true)
            adapterSearch?.setGeneralAdapterCallbacks(object : GeneralAdapterCallbacks {
                override fun onSongClicked(songs: MutableList<Audio>, position: Int, view: View) {
                    setMediaItems(songs, position)
                }

                override fun onSongLongClicked(audios: MutableList<Audio>, position: Int, view: View) {
                    openSongsMenu(audios, position, view as ImageView)
                }
            })
            binding.recyclerView.adapter = adapterSearch
        } else {
            adapterSearch?.updateSongs(songs)
            if (binding.recyclerView.adapter == null) {
                binding.recyclerView.adapter = adapterSearch
            }
        }

        binding.recyclerView.requireAttachedSectionScroller(
                sections = provideScrollPositions(songs),
                header = binding.appHeader,
                view = headerBinding.scroll
        )

        headerBinding.count.text = getString(R.string.x_songs, songs.size)
        headerBinding.hours.text = songs.sumOf { it.duration }
            .toHighlightedTimeString(ThemeManager.theme.textViewTheme.tertiaryTextColor)
        headerBinding.sortStyle.setSearchSort()
        headerBinding.sortOrder.setSearchOrder()
    }

    private fun provideScrollPositions(songs: List<Audio>): List<SectionedFastScroller.Position> {
        val firstAlphabetToIndex = linkedMapOf<String, Int>()
        songs.forEachIndexed { index, song ->
            val firstChar = song.title?.firstOrNull()?.uppercaseChar()
            val key = if (firstChar != null && firstChar.isLetter()) firstChar.toString() else "#"
            if (!firstAlphabetToIndex.containsKey(key)) {
                firstAlphabetToIndex[key] = index
            }
        }
        return firstAlphabetToIndex.map { (char, index) -> SectionedFastScroller.Position(char, index) }
    }

    override fun onAudio(audio: Audio) {
        super.onAudio(audio)
        adapterSearch?.currentlyPlayingSong = audio
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            SearchPreferences.SONG_SORT -> {
                headerBinding.sortStyle.setSearchSort()
            }
            SearchPreferences.SORTING_STYLE -> {
                headerBinding.sortOrder.setSearchOrder()
            }
            SearchPreferences.GRID_SIZE_PORTRAIT, SearchPreferences.GRID_SIZE_LANDSCAPE -> {
                binding.recyclerView.beginDelayedTransition()
                gridLayoutManager?.spanCount = SearchPreferences.getGridSize()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
            SearchPreferences.GRID_TYPE_PORTRAIT, SearchPreferences.GRID_TYPE_LANDSCAPE -> {
                binding.recyclerView.setGridType(SearchPreferences.getGridType(), SearchPreferences.getGridSize())
                binding.recyclerView.beginDelayedTransition()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
        }
    }

    companion object {
        const val TAG = "Search"

        fun newInstance(): Search {
            val args = Bundle()
            val fragment = Search()
            fragment.arguments = args
            return fragment
        }
    }
}