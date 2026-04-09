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
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.dialogs.app.TotalTime.Companion.showTotalTime
import app.simple.felicity.dialogs.search.SearchFilter.Companion.showSearchFilter
import app.simple.felicity.dialogs.search.SearchMenu.Companion.showSearchMenu
import app.simple.felicity.dialogs.search.SearchSort.Companion.showSearchSort
import app.simple.felicity.extensions.fragments.PanelFragment
import app.simple.felicity.models.SearchResults
import app.simple.felicity.preferences.SearchPreferences
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.sort.SearchSort.setSearchOrder
import app.simple.felicity.repository.sort.SearchSort.setSearchSort
import app.simple.felicity.shared.utils.TimeUtils.toDynamicTimeString
import app.simple.felicity.ui.pages.AlbumPage
import app.simple.felicity.ui.pages.ArtistPage
import app.simple.felicity.ui.pages.GenrePage
import app.simple.felicity.viewmodels.panels.SearchViewModel
import kotlinx.coroutines.launch

/**
 * Search panel that performs a full-library search across songs, albums, artists,
 * and genres using a single [AdapterSearch] with multiple view types. Results are
 * separated by labeled section headers that are part of the same flat item list.
 * A filter button allows toggling which categories are searched.
 * Search queries are debounced by 300 ms in [SearchViewModel] to avoid excessive
 * database queries while the user is typing.
 *
 * @author Hamza417
 */
class Search : PanelFragment() {

    private lateinit var binding: FragmentSearchBinding
    private lateinit var headerBinding: HeaderSearchBinding

    private var adapterSearch: AdapterSearch? = null
    private var gridLayoutManager: GridLayoutManager? = null

    /**
     * This will keep the query and search results until the activity is alive.
     */
    private val searchViewModel: SearchViewModel by viewModels({ requireActivity() })

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?): View {
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

        gridLayoutManager = GridLayoutManager(
                requireContext(), SearchPreferences.getGridSize().spanCount
        )
        binding.recyclerView.layoutManager = gridLayoutManager

        setupAdapters()
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
                searchViewModel.searchResults.collect { results ->
                    updateSearchResults(results)
                }
            }
        }
    }

    override fun onDestroyView() {
        adapterSearch = null
        gridLayoutManager = null
        super.onDestroyView()
    }

    private fun setupAdapters() {
        adapterSearch = AdapterSearch().also { adapter ->
            adapter.setGeneralAdapterCallbacks(object : GeneralAdapterCallbacks {
                override fun onSongClicked(songs: MutableList<Audio>, position: Int, view: View) {
                    setMediaItems(songs, position)
                }

                override fun onSongLongClicked(
                        audios: MutableList<Audio>,
                        position: Int,
                        imageView: ImageView?) {
                    openSongsMenu(audios, position, imageView)
                }

                override fun onAlbumClicked(albums: MutableList<Album>, position: Int, view: View) {
                    openFragment(AlbumPage.newInstance(albums[position]), AlbumPage.TAG)
                }

                override fun onArtistClicked(
                        artist: MutableList<Artist>,
                        position: Int,
                        view: View) {
                    openFragment(ArtistPage.newInstance(artist[position]), ArtistPage.TAG)
                }

                override fun onGenreClicked(genre: Genre, view: View) {
                    openFragment(GenrePage.newInstance(genre), GenrePage.TAG)
                }
            })
        }

        binding.recyclerView.adapter = adapterSearch

        gridLayoutManager?.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val spanCount = gridLayoutManager?.spanCount ?: 1
                val adapter = adapterSearch ?: return 1
                if (position < 0 || position >= adapter.itemCount) return 1
                return when (adapter.getItemViewType(position)) {
                    AdapterSearch.VIEW_TYPE_SONG_LIST,
                    AdapterSearch.VIEW_TYPE_SONG_GRID,
                    AdapterSearch.VIEW_TYPE_SONG_LABEL -> 1
                    else -> spanCount
                }
            }
        }
    }

    private fun setupClickListeners() {
        headerBinding.sortStyle.setSearchSort()
        headerBinding.sortOrder.setSearchOrder()

        headerBinding.editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) =
                Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) =
                Unit

            override fun afterTextChanged(s: Editable?) {
                searchViewModel.setSearchQuery(s?.toString() ?: "")
            }
        })

        headerBinding.filter.setOnClickListener {
            childFragmentManager.showSearchFilter()
        }

        headerBinding.menu.setOnClickListener {
            childFragmentManager.showSearchMenu()
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

        headerBinding.shuffle.setOnClickListener {
            val songs = searchViewModel.searchResults.value.songs
            if (songs.isNotEmpty()) shuffleMediaItems(songs)
        }
    }

    private fun updateSearchResults(results: SearchResults) {
        adapterSearch?.submitResults(
                results = results,
                songsHeader = getString(R.string.songs),
                albumsHeader = getString(R.string.albums),
                artistsHeader = getString(R.string.artists),
                genresHeader = getString(R.string.genres)
        )

        headerBinding.count.text = getString(R.string.x_songs, results.songs.size)
        headerBinding.hours.text = results.songs.sumOf { it.duration }.toDynamicTimeString()
        headerBinding.sortStyle.setSearchSort()
        headerBinding.sortOrder.setSearchOrder()

        headerBinding.hours.setOnClickListener {
            childFragmentManager.showTotalTime(
                    totalTime = results.songs.sumOf { it.duration },
                    count = results.songs.size
            )
        }
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
                val newMode = SearchPreferences.getGridSize()
                gridLayoutManager?.spanCount = newMode.spanCount
                adapterSearch?.layoutMode = newMode
                binding.recyclerView.beginDelayedTransition()
                // notifyDataSetChanged forces RecyclerView to re-check view types for all positions,
                // which ensures song items are re-inflated with the correct list/grid/label layout.
                adapterSearch?.notifyDataSetChanged()
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