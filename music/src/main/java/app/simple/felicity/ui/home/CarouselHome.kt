package app.simple.felicity.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.adapters.home.main.AdapterCarouselHome
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.FragmentHomeCarouselBinding
import app.simple.felicity.dialogs.home.HomeMenu.Companion.showHomeMenu
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.Song
import app.simple.felicity.ui.pages.AlbumPage
import app.simple.felicity.ui.pages.ArtistPage
import app.simple.felicity.ui.pages.GenrePage
import app.simple.felicity.ui.panels.Albums
import app.simple.felicity.ui.panels.ArtFlow
import app.simple.felicity.ui.panels.Artists
import app.simple.felicity.ui.panels.Genres
import app.simple.felicity.viewmodels.main.home.HomeViewModel

class CarouselHome : MediaFragment() {

    private var binding: FragmentHomeCarouselBinding? = null
    private val homeViewModel: HomeViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentHomeCarouselBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

        homeViewModel.getData().observe(viewLifecycleOwner) { data ->
            val adapter = AdapterCarouselHome(data)
            binding?.recyclerView?.adapter = adapter
            binding?.recyclerView?.setHasFixedSize(true)
            binding?.recyclerView?.requireAttachedMiniPlayer()

            view.startTransitionOnPreDraw()

            adapter.setAdapterCarouselHomeCallbacks(object : AdapterCarouselHome.Companion.AdapterCarouselCallbacks {
                override fun onSubItemClicked(view: View, position: Int, itemPosition: Int) {
                    Log.d(TAG, "Item clicked at position: $position")
                    if (data[position].items.isNotEmpty()) {
                        when (data[position].items[0]) {
                            is Song -> {
                                // setMediaItems(data[position].items.filterIsInstance<Song>(), itemPosition)
                            }
                            is Genre -> {
                                val genre = data[position].items.filterIsInstance<Genre>()[itemPosition]
                                openFragment(GenrePage.newInstance(genre), GenrePage.TAG)
                            }
                            is Album -> {
                                val album = data[position].items.filterIsInstance<Album>()[itemPosition]
                                openFragment(AlbumPage.newInstance(album), AlbumPage.TAG)
                            }
                            is Artist -> {
                                val artist = data[position].items.filterIsInstance<Artist>()[itemPosition]
                                openFragment(ArtistPage.newInstance(artist), ArtistPage.TAG)
                            }
                            else -> {
                                Log.w(TAG, "Unsupported item type clicked at position: $position")
                            }
                        }
                    }
                }

                override fun onClicked(view: View, position: Int) {
                    Log.d(TAG, "Carousel clicked at position: $position")
                    if (data[position].items.isNotEmpty()) {
                        when (data[position].items[0]) {
                            is Song -> {
                                // openFragment(ArtFlowRv.newInstance(), ArtFlowRv.TAG)
                                openFragment(ArtFlow.newInstance(), ArtFlow.TAG)
                            }
                            is Genre -> {
                                openFragment(Genres.newInstance(), Genres.TAG)
                            }
                            is Artist -> {
                                openFragment(Artists.newInstance(), Artists.TAG)
                            }
                            is Album -> {
                                openFragment(Albums.newInstance(), Albums.TAG)
                            }
                            else -> {
                                Log.w(TAG, "Unsupported item type clicked at position: $position")
                            }
                        }
                    }
                }
            })

            adapter.setGeneralAdapterCallbacks(object : GeneralAdapterCallbacks {
                override fun onMenuClicked(view: View) {
                    childFragmentManager.showHomeMenu()
                }

                override fun onSearchClicked(view: View) {
                    Log.d(TAG, "Search clicked")
                    // Handle search click if needed
                }
            })
        }
    }

    companion object {
        fun newInstance(): CarouselHome {
            val args = Bundle()
            val fragment = CarouselHome()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "CarouselHome"
    }
}