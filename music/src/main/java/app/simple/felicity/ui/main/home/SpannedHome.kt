package app.simple.felicity.ui.main.home

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.fragment.app.viewModels
import app.simple.felicity.adapters.home.main.AdapterGridHome
import app.simple.felicity.adapters.home.main.AdapterGridHome.Companion.AdapterSpannedHomeCallbacks
import app.simple.felicity.adapters.home.sub.AdapterGridArt
import app.simple.felicity.core.R
import app.simple.felicity.databinding.FragmentHomeSpannedBinding
import app.simple.felicity.decorations.utils.RecyclerViewUtils.randomViewHolder
import app.simple.felicity.dialogs.home.HomeMenu.Companion.showHomeMenu
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Song
import app.simple.felicity.ui.main.albums.AlbumPage
import app.simple.felicity.ui.main.albums.DefaultAlbums
import app.simple.felicity.ui.main.artists.ArtistPage
import app.simple.felicity.ui.main.artists.PeristyleArtists
import app.simple.felicity.ui.main.genres.Genres
import app.simple.felicity.ui.main.songs.DefaultSongs
import app.simple.felicity.viewmodels.main.home.HomeViewModel

class SpannedHome : MediaFragment() {

    private lateinit var binding: FragmentHomeSpannedBinding
    private val homeViewModel: HomeViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentHomeSpannedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        requireLightBarIcons()
        binding.recyclerView.setBackgroundColor(Color.BLACK)

        homeViewModel.getData().observe(viewLifecycleOwner) { it ->
            val adapter = AdapterGridHome(it)
            binding.recyclerView.setHasFixedSize(false)
            binding.recyclerView.adapter = adapter
            binding.recyclerView.scheduleLayoutAnimation()
            binding.recyclerView.itemAnimator = null

            adapter.setAdapterSpannedHomeCallbacks(object : AdapterSpannedHomeCallbacks {
                override fun onMenuClicked(view: View) {
                    parentFragmentManager.showHomeMenu()
                }

                override fun onItemClicked(items: List<Any>, position: Int) {
                    when (items.first()) {
                        is Song -> {
                            setMediaItems(items.filterIsInstance<Song>(), position)
                        }
                        is Artist -> {
                            openFragment(ArtistPage.newInstance(items.filterIsInstance<Artist>()[position]), ArtistPage.TAG)
                        }
                        is Album -> {
                            openFragment(AlbumPage.newInstance(items.filterIsInstance<Album>()[position]), AlbumPage.TAG)
                        }
                        else -> {
                            Log.w(TAG, "onItemClicked: Unsupported item type: ${items.first()::class.java.simpleName}")
                        }
                    }
                }

                override fun onItemLongClicked(item: Any) {

                }

                override fun onButtonClicked(title: Int) {
                    when (title) {
                        R.string.songs -> {
                            openFragment(DefaultSongs.newInstance(), DefaultSongs.TAG)
                        }
                        R.string.albums -> {
                            openFragment(DefaultAlbums.newInstance(), DefaultAlbums.TAG)
                        }
                        R.string.artists -> {
                            openFragment(PeristyleArtists.newInstance(), ArtistPage.TAG)
                        }
                        R.string.genres -> {
                            openFragment(Genres.newInstance(), Genres.TAG)
                        }
                        else -> {
                            Log.w(TAG, "onButtonClicked: Unsupported button title: $title")
                        }
                    }
                }
            })

            binding.recyclerView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    binding.recyclerView.viewTreeObserver.removeOnPreDrawListener(this)
                    startPostponedEnterTransition()
                    return true
                }
            })
        }
    }

    private val randomizer: Runnable = object : Runnable {
        override fun run() {
            try {
                binding.recyclerView.randomViewHolder<AdapterGridHome.Holder> { holder ->
                    holder.binding.artGrid.animate()!!
                        .alpha(0F)
                        .setDuration(resources.getInteger(android.R.integer.config_longAnimTime).toLong())
                        .withEndAction {
                            (holder.binding.artGrid.adapter as AdapterGridArt).randomize()
                            holder.binding.artGrid.scheduleLayoutAnimation()
                            holder.binding.artGrid.animate()!!
                                .alpha(1F)
                                .setDuration(resources.getInteger(android.R.integer.config_shortAnimTime).toLong())
                                .start()
                        }
                        .start()
                }
            } catch (e: NoSuchElementException) {
                Log.e(TAG, "run: No such element", e)
            } catch (e: Exception) {
                Log.e(TAG, "run: Exception", e)
            }

            handler.postDelayed(this, DELAY)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(randomizer)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(randomizer) // Just to be sure
        handler.postDelayed(randomizer, DELAY)
    }

    companion object {
        fun newInstance(): SpannedHome {
            val args = Bundle()
            val fragment = SpannedHome()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "SpannedHome"
        private const val DELAY = 5_000L
        private const val BASIC_DURATION = 1_500L
        private const val SMALL_DURATION = 250L
    }
}
