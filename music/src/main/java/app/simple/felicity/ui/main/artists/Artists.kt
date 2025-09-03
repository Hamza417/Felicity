package app.simple.felicity.ui.main.artists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.adapters.ui.lists.artists.AdapterArtists
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.core.R
import app.simple.felicity.databinding.FragmentArtistsBinding
import app.simple.felicity.databinding.HeaderArtistsBinding
import app.simple.felicity.decorations.fastscroll.SectionedFastScroller
import app.simple.felicity.decorations.fastscroll.SlideFastScroller
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.dialogs.songs.ArtistsSort.Companion.showArtistsSort
import app.simple.felicity.extensions.fragments.PanelFragment
import app.simple.felicity.preferences.ArtistPreferences
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.sort.ArtistSort.setCurrentSortOrder
import app.simple.felicity.repository.sort.ArtistSort.setCurrentSortStyle
import app.simple.felicity.viewmodels.main.artists.ArtistsViewModel

class Artists : PanelFragment() {

    private lateinit var binding: FragmentArtistsBinding
    private lateinit var headerBinding: HeaderArtistsBinding

    private lateinit var adapterArtists: AdapterArtists

    private val artistViewModel: ArtistsViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentArtistsBinding.inflate(inflater, container, false)
        headerBinding = HeaderArtistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.setContentView(headerBinding.root)
        binding.header.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        SlideFastScroller.attach(binding.recyclerView)
        binding.recyclerView.requireAttachedMiniPlayer()
        postponeEnterTransition()

        artistViewModel.getArtists().observe(viewLifecycleOwner) {
            binding.recyclerView.requireAttachedSectionScroller(
                    sections = provideScrollPositionDataBasedOnSortStyle(artists = it),
                    header = binding.header,
                    view = headerBinding.scroll)

            adapterArtists = AdapterArtists(it)
            binding.recyclerView.adapter = adapterArtists

            adapterArtists.setGeneralAdapterCallbacks(object : GeneralAdapterCallbacks {
                override fun onArtistClicked(artist: Artist, position: Int, view: View) {
                    openFragment(ArtistPage.newInstance(artist), ArtistPage.TAG)
                }
            })

            headerBinding.count.text = getString(R.string.x_artists, it.size)
            headerBinding.sortOrder.setCurrentSortOrder()
            headerBinding.sortStyle.setCurrentSortStyle()

            headerBinding.scroll.hideOnUnfavorableSort(
                    listOf(CommonPreferencesConstants.BY_NUMBER_OF_SONGS, CommonPreferencesConstants.BY_NUMBER_OF_ALBUMS),
                    ArtistPreferences.getArtistSort()
            )

            headerBinding.sortStyle.setOnClickListener {
                childFragmentManager.showArtistsSort()
            }

            headerBinding.sortOrder.setOnClickListener {
                childFragmentManager.showArtistsSort()
            }

            view.startTransitionOnPreDraw()
        }
    }

    private fun provideScrollPositionDataBasedOnSortStyle(artists: List<Artist>): List<SectionedFastScroller.Position> {
        when (ArtistPreferences.getArtistSort()) {
            CommonPreferencesConstants.BY_NAME -> {
                val firstAlphabetToIndex = linkedMapOf<String, Int>()
                artists.forEachIndexed { index, artist ->
                    val firstChar = artist.name?.firstOrNull()?.uppercaseChar()
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
        }

        return emptyList()
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