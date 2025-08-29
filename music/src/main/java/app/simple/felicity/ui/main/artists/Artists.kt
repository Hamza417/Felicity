package app.simple.felicity.ui.main.artists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.adapters.ui.lists.artists.AdapterArtists
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.core.R
import app.simple.felicity.databinding.FragmentArtistsBinding
import app.simple.felicity.databinding.HeaderArtistsBinding
import app.simple.felicity.decorations.fastscroll.SectionedFastScroller
import app.simple.felicity.decorations.fastscroll.SlideFastScroller
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.viewmodels.main.artists.ArtistsViewModel

class Artists : MediaFragment() {

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
        postponeEnterTransition()

        artistViewModel.getArtists().observe(viewLifecycleOwner) {
            val nav = SectionedFastScroller.attach(binding.recyclerView)
            nav.setPositions(provideScrollPositionDataBasedOnSortStyle(it))

            nav.setOnPositionSelectedListener { position ->
                binding.recyclerView.scrollToPosition(position.index)
                if (position.index > 10) {
                    binding.header.hideHeader()
                    binding.header.resumeAutoBehavior()
                } else {
                    binding.header.showHeader()
                    binding.header.resumeAutoBehavior()
                }
            }

            adapterArtists = AdapterArtists(it)
            binding.recyclerView.adapter = adapterArtists

            adapterArtists.setGeneralAdapterCallbacks(object : GeneralAdapterCallbacks {
                override fun onArtistClicked(artist: Artist, position: Int, view: View) {
                    openFragment(ArtistPage.newInstance(artist), ArtistPage.TAG)
                }
            })

            headerBinding.count.text = getString(R.string.x_artists, it.size)

            view.startTransitionOnPreDraw()
        }
    }

    private fun provideScrollPositionDataBasedOnSortStyle(artists: List<Artist>): List<SectionedFastScroller.Position> {
        return listOf()
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