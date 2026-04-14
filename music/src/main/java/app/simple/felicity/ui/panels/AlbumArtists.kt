package app.simple.felicity.ui.panels

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.viewModels
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.AdapterAlbumArtists
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.FragmentAlbumArtistsBinding
import app.simple.felicity.databinding.HeaderAlbumArtistsBinding
import app.simple.felicity.decorations.fastscroll.SectionedFastScroller
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.dialogs.albumartists.AlbumArtistsMenu.Companion.showAlbumArtistsMenu
import app.simple.felicity.dialogs.albumartists.AlbumArtistsSort.Companion.showAlbumArtistsSort
import app.simple.felicity.extensions.fragments.BasePanelFragment
import app.simple.felicity.preferences.AlbumArtistPreferences
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.sort.AlbumArtistSort.setCurrentSortOrder
import app.simple.felicity.repository.sort.AlbumArtistSort.setCurrentSortStyle
import app.simple.felicity.ui.pages.AlbumArtistPage
import app.simple.felicity.viewmodels.panels.AlbumArtistsViewModel

/**
 * Panel fragment displaying all album artists found in the user's library, with
 * sort, fast-scroll, and layout-style support. It mirrors the [Artists] panel but
 * groups entries by the album_artist tag rather than the artist tag.
 *
 * @author Hamza417
 */
class AlbumArtists : BasePanelFragment() {

    private lateinit var binding: FragmentAlbumArtistsBinding
    private lateinit var headerBinding: HeaderAlbumArtistsBinding

    private var adapterAlbumArtists: AdapterAlbumArtists? = null

    private val albumArtistsViewModel: AlbumArtistsViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentAlbumArtistsBinding.inflate(inflater, container, false)
        headerBinding = HeaderAlbumArtistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.header.setContentView(headerBinding.root)
        binding.header.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        binding.recyclerView.attachSlideFastScroller()
        binding.recyclerView.requireAttachedMiniPlayer()

        binding.recyclerView.setupGridLayoutManager(AlbumArtistPreferences.getGridSize().spanCount)

        setupClickListeners()

        // If we already have an adapter from before a config change, restore it immediately
        adapterAlbumArtists?.let { binding.recyclerView.adapter = it }

        albumArtistsViewModel.albumArtists.collectListWhenStarted({ adapterAlbumArtists != null }) { albumArtists ->
            updateAlbumArtistsList(albumArtists.toMutableList())
        }
    }

    override fun onDestroyView() {
        adapterAlbumArtists = null
        super.onDestroyView()
    }

    private fun setupClickListeners() {
        headerBinding.sortStyle.setOnClickListener {
            childFragmentManager.showAlbumArtistsSort()
        }

        headerBinding.sortOrder.setOnClickListener {
            childFragmentManager.showAlbumArtistsSort()
        }

        headerBinding.search.setOnClickListener {
            openSearch()
        }

        headerBinding.menu.setOnClickListener {
            childFragmentManager.showAlbumArtistsMenu()
        }
    }

    private fun updateAlbumArtistsList(albumArtists: MutableList<Artist>) {
        if (adapterAlbumArtists == null) {
            adapterAlbumArtists = AdapterAlbumArtists(albumArtists)
            adapterAlbumArtists?.setHasStableIds(true)
            adapterAlbumArtists?.setGeneralAdapterCallbacks(object : GeneralAdapterCallbacks {
                override fun onArtistClicked(artists: List<Artist>, position: Int, view: View) {
                    openFragment(AlbumArtistPage.newInstance(artists[position]), AlbumArtistPage.TAG)
                }

                override fun onArtistLongClicked(artists: List<Artist>, position: Int, imageView: ImageView?) {
                    val albumArtist = artists.getOrNull(position) ?: return
                    openArtistMenu(albumArtist, imageView)
                }
            })
            binding.recyclerView.adapter = adapterAlbumArtists
        } else {
            adapterAlbumArtists?.updateList(albumArtists)
            if (binding.recyclerView.adapter == null) {
                binding.recyclerView.adapter = adapterAlbumArtists
            }
        }

        headerBinding.count.text = getString(R.string.x_album_artists, albumArtists.size)
        binding.recyclerView.requireAttachedSectionScroller(
                sections = provideScrollPositions(albumArtists),
                header = binding.header,
                view = headerBinding.scroll)

        headerBinding.sortStyle.setCurrentSortStyle()
        headerBinding.sortOrder.setCurrentSortOrder()

        // The fast-scroll arrow only makes sense when sorting alphabetically by name
        headerBinding.scroll.hideOnUnfavorableSort(
                sorts = listOf(CommonPreferencesConstants.BY_NAME),
                preference = AlbumArtistPreferences.getAlbumArtistSort()
        )
    }

    /**
     * Builds the fast-scroller section map so letters jump correctly when the list
     * is sorted by name. Any other sort style returns an empty list (no scroll sections).
     */
    private fun provideScrollPositions(albumArtists: List<Artist>): List<SectionedFastScroller.Position> {
        when (AlbumArtistPreferences.getAlbumArtistSort()) {
            CommonPreferencesConstants.BY_NAME -> {
                val firstCharToIndex = linkedMapOf<String, Int>()
                albumArtists.forEachIndexed { index, albumArtist ->
                    val firstChar = albumArtist.name?.firstOrNull()?.uppercaseChar()
                    val key = if (firstChar != null && firstChar.isLetter()) firstChar.toString() else "#"
                    if (!firstCharToIndex.containsKey(key)) firstCharToIndex[key] = index
                }
                return firstCharToIndex.map { (char, index) -> SectionedFastScroller.Position(char, index) }
            }
        }
        return emptyList()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            AlbumArtistPreferences.GRID_SIZE_PORTRAIT, AlbumArtistPreferences.GRID_SIZE_LANDSCAPE -> {
                applyGridSizeUpdate(binding.recyclerView, AlbumArtistPreferences.getGridSize().spanCount)
            }
        }
    }

    companion object {
        fun newInstance(): AlbumArtists {
            val args = Bundle()
            val fragment = AlbumArtists()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "AlbumArtists"
    }
}