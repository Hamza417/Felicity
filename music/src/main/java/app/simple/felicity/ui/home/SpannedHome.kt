package app.simple.felicity.ui.home

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.simple.felicity.R
import app.simple.felicity.adapters.home.main.AdapterGridHome
import app.simple.felicity.adapters.home.main.AdapterGridHome.Companion.AdapterSpannedHomeCallbacks
import app.simple.felicity.adapters.home.sub.AdapterGridArt
import app.simple.felicity.databinding.FragmentHomeSpannedBinding
import app.simple.felicity.decorations.utils.RecyclerViewUtils.randomViewHolder
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.models.ArtFlowData
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.ui.pages.AlbumPage
import app.simple.felicity.ui.pages.ArtistPage
import app.simple.felicity.ui.panels.Albums
import app.simple.felicity.ui.panels.Artists
import app.simple.felicity.ui.panels.Genres
import app.simple.felicity.ui.panels.PlayingQueue
import app.simple.felicity.ui.panels.Songs
import app.simple.felicity.viewmodels.panels.HomeViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Home fragment that presents curated song collections as a spanned art grid with periodic
 * randomization. The four data sources (Favorites, Recently Played, Most Played, Recently Added)
 * are collected as independent [kotlinx.coroutines.flow.StateFlow]s from [HomeViewModel] and
 * combined into a single [AdapterGridHome] data set. Reactive Room flows ensure that any
 * addition or deletion in the library is reflected immediately without a restart.
 *
 * @author Hamza417
 */
class SpannedHome : MediaFragment() {

    private lateinit var binding: FragmentHomeSpannedBinding
    private val homeViewModel: HomeViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHomeSpannedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        requireLightBarIcons()
        binding.recyclerView.setBackgroundColor(Color.BLACK)
        binding.recyclerView.requireAttachedMiniPlayer()

        var adapter: AdapterGridHome? = null

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                        homeViewModel.favorites,
                        homeViewModel.recentlyPlayed,
                        homeViewModel.mostPlayed,
                        homeViewModel.recentlyAdded
                ) { favorites, recentlyPlayed, mostPlayed, recentlyAdded ->
                    buildSections(favorites, recentlyPlayed, mostPlayed, recentlyAdded)
                }.collect { sections ->
                    Log.d(TAG, "Data received: ${sections.size} sections")
                    if (sections.isEmpty()) return@collect

                    if (adapter == null) {
                        adapter = AdapterGridHome(sections)
                        binding.recyclerView.setHasFixedSize(false)
                        binding.recyclerView.adapter = adapter
                        binding.recyclerView.scheduleLayoutAnimation()
                        binding.recyclerView.itemAnimator = null

                        adapter.setAdapterSpannedHomeCallbacks(object : AdapterSpannedHomeCallbacks {
                            override fun onMenuClicked(view: View) {
                                openPreferencesPanel()
                            }

                            override fun onItemClicked(items: List<Any>, position: Int) {
                                when (items.first()) {
                                    is Audio -> {}
                                    is Artist -> openFragment(ArtistPage.newInstance(items.filterIsInstance<Artist>()[position]), ArtistPage.TAG)
                                    is Album -> openFragment(AlbumPage.newInstance(items.filterIsInstance<Album>()[position]), AlbumPage.TAG)
                                    else -> Log.w(TAG, "onItemClicked: Unsupported item type: ${items.first()::class.java.simpleName}")
                                }
                            }

                            override fun onItemLongClicked(item: Any) {}

                            override fun onButtonClicked(title: Int) {
                                when (title) {
                                    R.string.songs -> openFragment(Songs.newInstance(), Songs.TAG)
                                    R.string.albums -> openFragment(Albums.newInstance(), Albums.TAG)
                                    R.string.artists -> openFragment(Artists.newInstance(), Artists.TAG)
                                    R.string.genres -> openFragment(Genres.newInstance(), Genres.TAG)
                                    R.string.playing_queue -> openFragment(PlayingQueue.newInstance(), PlayingQueue.TAG)
                                    else -> Log.w(TAG, "onButtonClicked: Unsupported button title: $title")
                                }
                            }
                        })

                        requireView().startTransitionOnPreDraw()
                    } else {
                        adapter.updateData(sections)
                    }
                }
            }
        }
    }

    /**
     * Assembles the ordered list of [ArtFlowData] sections passed to [AdapterGridHome].
     * Sections whose backing list is empty are omitted so the grid never shows an empty row.
     *
     * @param favorites      Latest favorites snapshot.
     * @param recentlyPlayed Latest recently-played snapshot.
     * @param mostPlayed     Latest most-played snapshot.
     * @param recentlyAdded  Latest recently-added snapshot.
     * @return Ordered list of non-empty [ArtFlowData] sections.
     */
    private fun buildSections(
            favorites: List<Audio>,
            recentlyPlayed: List<Audio>,
            mostPlayed: List<Audio>,
            recentlyAdded: List<Audio>
    ): List<ArtFlowData<Any>> {
        val sections = mutableListOf<ArtFlowData<Any>>()
        if (favorites.isNotEmpty()) sections.add(ArtFlowData(R.string.favorites, favorites))
        if (recentlyPlayed.isNotEmpty()) sections.add(ArtFlowData(R.string.recently_played, recentlyPlayed))
        if (mostPlayed.isNotEmpty()) sections.add(ArtFlowData(R.string.most_played, mostPlayed))
        if (recentlyAdded.isNotEmpty()) sections.add(ArtFlowData(R.string.recently_added, recentlyAdded))
        return sections
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
        /**
         * Creates a new instance of [SpannedHome].
         *
         * @return A freshly instantiated [SpannedHome] fragment.
         */
        fun newInstance(): SpannedHome {
            val args = Bundle()
            val fragment = SpannedHome()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "SpannedHome"
        private const val DELAY = 5_000L
    }
}
