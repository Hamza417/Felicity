package app.simple.felicity.ui.panels

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.AdapterArtists
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.FragmentArtistsBinding
import app.simple.felicity.databinding.HeaderArtistsBinding
import app.simple.felicity.decorations.fastscroll.SectionedFastScroller
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.dialogs.artists.ArtistsMenu.Companion.showArtistsMenu
import app.simple.felicity.dialogs.songs.ArtistsSort.Companion.showArtistsSort
import app.simple.felicity.extensions.fragments.BasePanelFragment
import app.simple.felicity.preferences.ArtistPreferences
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.sort.ArtistSort.setCurrentSortOrder
import app.simple.felicity.repository.sort.ArtistSort.setCurrentSortStyle
import app.simple.felicity.ui.pages.ArtistPage
import app.simple.felicity.viewmodels.panels.ArtistsViewModel

/**
 * Panel fragment displaying the user's artists with sort, grid layout, and search support.
 *
 * @author Hamza417
 */
class Artists : BasePanelFragment() {

    private lateinit var binding: FragmentArtistsBinding
    private lateinit var headerBinding: HeaderArtistsBinding

    private var adapterArtists: AdapterArtists? = null

    private val artistViewModel: ArtistsViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentArtistsBinding.inflate(inflater, container, false)
        headerBinding = HeaderArtistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.header.setContentView(headerBinding.root)
        binding.header.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        binding.recyclerView.attachSlideFastScroller()
        binding.recyclerView.requireAttachedMiniPlayer()

        binding.recyclerView.setupGridLayoutManager(ArtistPreferences.getGridSize().spanCount)

        setupClickListeners()

        adapterArtists?.let { binding.recyclerView.adapter = it }

        artistViewModel.artists.collectListWhenStarted({ adapterArtists != null }) { artists ->
            updateArtistsList(artists.toMutableList())
        }
    }

    override fun onDestroyView() {
        adapterArtists = null
        super.onDestroyView()
    }

    private fun setupClickListeners() {
        headerBinding.sortStyle.setOnClickListener {
            childFragmentManager.showArtistsSort()
        }

        headerBinding.sortOrder.setOnClickListener {
            childFragmentManager.showArtistsSort()
        }

        headerBinding.search.setOnClickListener {
            openSearch()
        }

        headerBinding.menu.setOnClickListener {
            childFragmentManager.showArtistsMenu()
        }
    }

    private fun updateArtistsList(artists: MutableList<Artist>) {
        if (adapterArtists == null) {
            adapterArtists = AdapterArtists(artists)
            adapterArtists?.setHasStableIds(true)
            adapterArtists?.setGeneralAdapterCallbacks(object : GeneralAdapterCallbacks {
                override fun onArtistClicked(artists: List<Artist>, position: Int, view: View) {
                    openFragment(ArtistPage.newInstance(artists[position]), ArtistPage.TAG)
                }
            })
            binding.recyclerView.adapter = adapterArtists
        } else {
            adapterArtists?.updateList(artists)
            if (binding.recyclerView.adapter == null) {
                binding.recyclerView.adapter = adapterArtists
            }
        }

        headerBinding.count.text = getString(R.string.x_artists, artists.size)
        binding.recyclerView.requireAttachedSectionScroller(
                sections = provideScrollPositionDataBasedOnSortStyle(artists = artists),
                header = binding.header,
                view = headerBinding.scroll)

        headerBinding.sortStyle.setCurrentSortStyle()
        headerBinding.sortOrder.setCurrentSortOrder()
        headerBinding.scroll.hideOnUnfavorableSort(
                sorts = listOf(CommonPreferencesConstants.BY_NAME),
                preference = ArtistPreferences.getArtistSort()
        )
    }

    private fun provideScrollPositionDataBasedOnSortStyle(artists: List<Artist>): List<SectionedFastScroller.Position> {
        when (ArtistPreferences.getArtistSort()) {
            CommonPreferencesConstants.BY_NAME -> {
                val firstAlphabetToIndex = linkedMapOf<String, Int>()
                artists.forEachIndexed { index, artist ->
                    val firstChar = artist.name?.firstOrNull()?.uppercaseChar()
                    val key = if (firstChar != null && firstChar.isLetter()) firstChar.toString() else "#"
                    if (!firstAlphabetToIndex.containsKey(key)) firstAlphabetToIndex[key] = index
                }
                return firstAlphabetToIndex.map { (char, index) -> SectionedFastScroller.Position(char, index) }
            }
        }

        return emptyList()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            ArtistPreferences.GRID_SIZE_PORTRAIT, ArtistPreferences.GRID_SIZE_LANDSCAPE -> {
                applyGridSizeUpdate(binding.recyclerView, ArtistPreferences.getGridSize().spanCount)
            }
        }
    }

    companion object {
        fun newInstance(): Artists {
            val args = Bundle()
            val fragment = Artists()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "Artists"
    }
}