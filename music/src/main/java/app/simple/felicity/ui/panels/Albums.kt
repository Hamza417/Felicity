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
import app.simple.felicity.adapters.ui.lists.AdapterAlbums
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.FragmentAlbumsBinding
import app.simple.felicity.databinding.HeaderAlbumsBinding
import app.simple.felicity.decorations.fastscroll.SectionedFastScroller
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.dialogs.albums.AlbumsMenu.Companion.showAlbumsMenu
import app.simple.felicity.dialogs.albums.AlbumsSort.Companion.showAlbumsSort
import app.simple.felicity.extensions.fragments.PanelFragment
import app.simple.felicity.preferences.AlbumPreferences
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.sort.AlbumSort.setCurrentSortOrder
import app.simple.felicity.repository.sort.AlbumSort.setCurrentSortStyle
import app.simple.felicity.ui.pages.AlbumPage
import app.simple.felicity.viewmodels.panels.AlbumsViewModel
import kotlinx.coroutines.launch

/**
 * Panel fragment displaying the user's albums with sort, grid layout, and search support.
 *
 * @author Hamza417
 */
class Albums : PanelFragment() {

    private lateinit var binding: FragmentAlbumsBinding
    private lateinit var headerBinding: HeaderAlbumsBinding

    private var adapterAlbums: AdapterAlbums? = null
    private var gridLayoutManager: GridLayoutManager? = null

    private val albumsViewModel: AlbumsViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentAlbumsBinding.inflate(inflater, container, false)
        headerBinding = HeaderAlbumsBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.header.setContentView(headerBinding.root)
        binding.header.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        binding.recyclerView.attachSlideFastScroller()
        binding.recyclerView.requireAttachedMiniPlayer()

        val mode = AlbumPreferences.getGridSize()
        gridLayoutManager = GridLayoutManager(requireContext(), mode.spanCount)
        binding.recyclerView.layoutManager = gridLayoutManager

        setupClickListeners()

        adapterAlbums?.let { binding.recyclerView.adapter = it }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                albumsViewModel.albums
                    .collect { albums ->
                        if (albums.isNotEmpty()) {
                            updateAlbumsList(albums)
                        } else if (adapterAlbums != null) {
                            updateAlbumsList(albums)
                        }
                    }
            }
        }
    }

    override fun onDestroyView() {
        adapterAlbums = null
        gridLayoutManager = null
        super.onDestroyView()
    }

    private fun setupClickListeners() {
        headerBinding.sortStyle.setOnClickListener {
            childFragmentManager.showAlbumsSort()
        }

        headerBinding.sortOrder.setOnClickListener {
            childFragmentManager.showAlbumsSort()
        }

        headerBinding.search.setOnClickListener {
            openSearch()
        }

        headerBinding.menu.setOnClickListener {
            childFragmentManager.showAlbumsMenu()
        }
    }

    private fun updateAlbumsList(albums: List<Album>) {
        if (adapterAlbums == null) {
            adapterAlbums = AdapterAlbums(albums)
            adapterAlbums?.setHasStableIds(true)
            adapterAlbums?.setGeneralAdapterCallbacks(object : GeneralAdapterCallbacks {
                override fun onAlbumClicked(albums: List<Album>, position: Int, view: View?) {
                    openFragment(AlbumPage.newInstance(albums[position]), AlbumPage.TAG)
                }
            })
            binding.recyclerView.adapter = adapterAlbums
        } else {
            adapterAlbums?.updateList(albums)
            if (binding.recyclerView.adapter == null) {
                binding.recyclerView.adapter = adapterAlbums
            }
        }

        headerBinding.count.text = getString(R.string.x_albums, albums.size)
        binding.recyclerView.requireAttachedSectionScroller(
                sections = provideScrollPositionDataBasedOnSortStyle(albums = albums),
                header = binding.header,
                view = headerBinding.scroll)

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
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            AlbumPreferences.GRID_SIZE_PORTRAIT, AlbumPreferences.GRID_SIZE_LANDSCAPE -> {
                val newMode = AlbumPreferences.getGridSize()
                gridLayoutManager?.spanCount = newMode.spanCount
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
                    val key = if (firstChar != null && firstChar.isLetter()) firstChar.toString() else "#"
                    if (!firstAlphabetToIndex.containsKey(key)) firstAlphabetToIndex[key] = index
                }
                return firstAlphabetToIndex.map { (char, index) -> SectionedFastScroller.Position(char, index) }
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
                return firstAlphabetToIndex.map { (char, index) -> SectionedFastScroller.Position(char.toString(), index) }
            }
            CommonPreferencesConstants.BY_FIRST_YEAR, CommonPreferencesConstants.BY_LAST_YEAR -> {
                val yearToIndex = linkedMapOf<String, Int>()
                albums.forEachIndexed { index, album ->
                    val year = if (AlbumPreferences.getAlbumSort() == CommonPreferencesConstants.BY_FIRST_YEAR) {
                        album.firstYear
                    } else {
                        album.lastYear
                    }.toString()
                    if (!yearToIndex.containsKey(year)) yearToIndex[year] = index
                }
                return yearToIndex.map { (year, index) -> SectionedFastScroller.Position(year, index) }
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