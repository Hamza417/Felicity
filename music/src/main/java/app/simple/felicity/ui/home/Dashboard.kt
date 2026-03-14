package app.simple.felicity.ui.home

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
import app.simple.felicity.adapters.home.dashboard.AdapterDashboardPanels
import app.simple.felicity.adapters.home.dashboard.AdapterDashboardPanels.Companion.AdapterDashboardPanelsCallbacks
import app.simple.felicity.adapters.home.dashboard.AdapterDashboardSongs
import app.simple.felicity.adapters.home.dashboard.AdapterDashboardSongs.Companion.AdapterDashboardSongsCallbacks
import app.simple.felicity.adapters.home.dashboard.AdapterRecommended
import app.simple.felicity.adapters.home.dashboard.AdapterRecommended.Companion.AdapterRecommendedCallbacks
import app.simple.felicity.adapters.home.sub.AdapterGridArt
import app.simple.felicity.databinding.FragmentHomeDahsboardBinding
import app.simple.felicity.decorations.itemdecorations.LinearHorizontalSpacingDecoration
import app.simple.felicity.decorations.layoutmanager.spanned.SpanSize
import app.simple.felicity.decorations.layoutmanager.spanned.SpannedGridLayoutManager
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.models.ArtFlowData
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.TextViewUtils.setTypeWriting
import app.simple.felicity.shared.utils.ViewUtils.gone
import app.simple.felicity.shared.utils.ViewUtils.visible
import app.simple.felicity.ui.panels.Albums
import app.simple.felicity.ui.panels.Artists
import app.simple.felicity.ui.panels.Favorites
import app.simple.felicity.ui.panels.Folders
import app.simple.felicity.ui.panels.FoldersHierarchy
import app.simple.felicity.ui.panels.Genres
import app.simple.felicity.ui.panels.PlayingQueue
import app.simple.felicity.ui.panels.Preferences
import app.simple.felicity.ui.panels.RecentlyAdded
import app.simple.felicity.ui.panels.Search
import app.simple.felicity.ui.panels.Songs
import app.simple.felicity.ui.panels.Year
import app.simple.felicity.viewmodels.panels.DashboardViewModel
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel.Companion.Element
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Dashboard home screen fragment.
 *
 * Displays a scrollable dashboard composed of:
 * - A header with the app name, search, and settings buttons.
 * - A spanned art grid of recommended songs that continuously shuffles its cells.
 * - A horizontal carousel of recently played songs.
 * - A four-column browse grid of seven panel navigation shortcuts with an inline
 *   expand/collapse button that reveals the full panel list without navigation.
 * - A horizontal carousel of recently added songs.
 * - A horizontal carousel of favorite songs.
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class Dashboard : MediaFragment() {

    private lateinit var binding: FragmentHomeDahsboardBinding

    private val dashboardViewModel: DashboardViewModel by viewModels()

    private var recentlyPlayedAdapter: AdapterDashboardSongs? = null
    private var recentlyAddedAdapter: AdapterDashboardSongs? = null
    private var favoritesAdapter: AdapterDashboardSongs? = null

    /**
     * Periodically fades the entire recommended spanned grid out, triggers
     * [AdapterGridArt.randomize] on its adapter (which re-shuffles every cell), schedules
     * the grid's layout animation, then fades back in — identical to the effect seen in
     * [SpannedHome].
     */
    private val recommendedRandomizer: Runnable = object : Runnable {
        override fun run() {
            try {
                val adapter = binding.recommendedGrid.adapter as? AdapterGridArt
                if (adapter != null) {
                    binding.recommendedGrid.animate()
                        .alpha(0F)
                        .setDuration(resources.getInteger(android.R.integer.config_longAnimTime).toLong())
                        .withEndAction {
                            adapter.randomize()
                            binding.recommendedGrid.scheduleLayoutAnimation()
                            binding.recommendedGrid.animate()
                                .alpha(1F)
                                .setDuration(resources.getInteger(android.R.integer.config_shortAnimTime).toLong())
                                .start()
                        }
                        .start()
                }
            } catch (_: Exception) {
                // Ignore transient errors during animation.
            }

            handler.postDelayed(this, RANDOMIZER_DELAY)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHomeDahsboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        showMiniPlayer()
        setupHeader()
        setupPanelsGrid()
        setupCarouselSpacing()
        observeData()

        binding.openAppSettings.setOnClickListener {
            openPreferencesPanel()
        }

        updateStates(MediaManager.getCurrentSong() ?: return)
    }

    private fun setupHeader() {
        binding.search.setOnClickListener {
            openFragment(Search.newInstance(), Search.TAG)
        }
    }

    private fun setupPanelsGrid() {
        val adapter = AdapterDashboardPanels(
                firstPanels = dashboardViewModel.firstPanelItems,
                allPanels = dashboardViewModel.allPanelItems)
        binding.panelsRecyclerView.layoutManager = GridLayoutManager(requireContext(), PANEL_SPAN_COUNT)
        binding.panelsRecyclerView.adapter = adapter
        adapter.setCallbacks(object : AdapterDashboardPanelsCallbacks {
            override fun onPanelClicked(element: Element) {
                navigateToPanel(element)
            }
        })
    }

    private fun setupCarouselSpacing() {
        val spacing = resources.getDimensionPixelSize(R.dimen.carousel_spacing)
        binding.recentlyPlayedList.addItemDecoration(LinearHorizontalSpacingDecoration(spacing))
        binding.recentlyAddedList.addItemDecoration(LinearHorizontalSpacingDecoration(spacing))
        binding.favoritesList.addItemDecoration(LinearHorizontalSpacingDecoration(spacing))
    }

    /**
     * Configures the recommended spanned art grid with [SpannedGridLayoutManager] using the
     * same random span-size pattern as [SpannedHome], then attaches an [AdapterGridArt].
     *
     * @param data The [ArtFlowData] block of songs to display in the grid.
     */
    private fun setupRecommendedGrid(data: List<Audio>) {
        val randomSpanPositions = intArrayOf(1, 2, 3, 4, 5, 7).let { arr ->
            val i = arr.indices.random()
            var j = arr.indices.random()
            while (j == i) j = arr.indices.random()
            intArrayOf(arr[i], arr[j])
        }

        val layoutManager = SpannedGridLayoutManager(SpannedGridLayoutManager.Orientation.VERTICAL, RECOMMENDED_GRID_SPANS)
        layoutManager.spanSizeLookup = SpannedGridLayoutManager.SpanSizeLookup { position ->
            if (position in randomSpanPositions) {
                SpanSize(2, 2)
            } else {
                SpanSize(1, 1)
            }
        }

        val adapter = AdapterRecommended(data)
        binding.recommendedGrid.setHasFixedSize(false)
        binding.recommendedGrid.layoutManager = layoutManager
        binding.recommendedGrid.adapter = adapter
        binding.recommendedGrid.scheduleLayoutAnimation()

        adapter.setCallbacks(object : AdapterRecommendedCallbacks {
            override fun onItemClicked(items: List<Audio>, position: Int) {
                setMediaItems(items, position)
            }
        })

        binding.recommendedGrid.post {
            binding.recommendedGrid.layoutParams.height =
                layoutManager.getTotalHeight() +
                        binding.recommendedGrid.paddingTop +
                        binding.recommendedGrid.paddingBottom
            binding.recommendedGrid.requestLayout()
        }

        binding.recommendedSection.visible(false)
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    dashboardViewModel.recommended.collect { data ->
                        if (data != null) setupRecommendedGrid(data)
                        else binding.recommendedSection.gone()
                    }
                }
                launch {
                    dashboardViewModel.recentlyPlayed.collect { songs ->
                        updateRecentlyPlayed(songs)
                    }
                }
                launch {
                    dashboardViewModel.recentlyAdded.collect { songs ->
                        updateRecentlyAdded(songs)
                    }
                }
                launch {
                    dashboardViewModel.favorites.collect { songs ->
                        updateFavorites(songs)
                    }
                }
            }
        }
    }

    private fun updateRecentlyPlayed(songs: List<Audio>) {
        if (songs.isEmpty()) {
            binding.recentlyPlayedSection.gone()
            return
        }
        binding.recentlyPlayedSection.visible(false)
        if (recentlyPlayedAdapter == null) {
            recentlyPlayedAdapter = AdapterDashboardSongs(songs)
            recentlyPlayedAdapter!!.setCallbacks(object : AdapterDashboardSongsCallbacks {
                override fun onSongClicked(songs: MutableList<Audio>, position: Int) {
                    setMediaItems(songs, position)
                }
            })
            binding.recentlyPlayedList.adapter = recentlyPlayedAdapter
        } else {
            recentlyPlayedAdapter!!.updateData(songs)
            if (binding.recentlyPlayedList.adapter == null) {
                binding.recentlyPlayedList.adapter = recentlyPlayedAdapter
            }
        }
    }

    private fun updateRecentlyAdded(songs: List<Audio>) {
        if (songs.isEmpty()) {
            binding.recentlyAddedSection.gone()
            return
        }
        binding.recentlyAddedSection.visible(false)
        if (recentlyAddedAdapter == null) {
            recentlyAddedAdapter = AdapterDashboardSongs(songs)
            recentlyAddedAdapter!!.setCallbacks(object : AdapterDashboardSongsCallbacks {
                override fun onSongClicked(songs: MutableList<Audio>, position: Int) {
                    setMediaItems(songs, position)
                }
            })
            binding.recentlyAddedList.adapter = recentlyAddedAdapter
        } else {
            recentlyAddedAdapter!!.updateData(songs)
            if (binding.recentlyAddedList.adapter == null) {
                binding.recentlyAddedList.adapter = recentlyAddedAdapter
            }
        }
    }

    private fun updateFavorites(songs: List<Audio>) {
        if (songs.isEmpty()) {
            binding.favoritesSection.gone()
            return
        }
        binding.favoritesSection.visible(false)
        if (favoritesAdapter == null) {
            favoritesAdapter = AdapterDashboardSongs(songs)
            favoritesAdapter!!.setCallbacks(object : AdapterDashboardSongsCallbacks {
                override fun onSongClicked(songs: MutableList<Audio>, position: Int) {
                    setMediaItems(songs, position)
                }
            })
            binding.favoritesList.adapter = favoritesAdapter
        } else {
            favoritesAdapter!!.updateData(songs)
            if (binding.favoritesList.adapter == null) {
                binding.favoritesList.adapter = favoritesAdapter
            }
        }
    }

    private fun navigateToPanel(element: Element) {
        when (element.titleResId) {
            R.string.songs -> openFragment(Songs.newInstance(), Songs.TAG)
            R.string.albums -> openFragment(Albums.newInstance(), Albums.TAG)
            R.string.artists -> openFragment(Artists.newInstance(), Artists.TAG)
            R.string.genres -> openFragment(Genres.newInstance(), Genres.TAG)
            R.string.favorites -> openFragment(Favorites.newInstance(), Favorites.TAG)
            R.string.playing_queue -> openFragment(PlayingQueue.newInstance(), PlayingQueue.TAG)
            R.string.recently_added -> openFragment(RecentlyAdded.newInstance(), RecentlyAdded.TAG)
            R.string.folders -> openFragment(Folders.newInstance(), Folders.TAG)
            R.string.folders_hierarchy -> openFragment(FoldersHierarchy.newInstance(), FoldersHierarchy.TAG)
            R.string.year -> openFragment(Year.newInstance(), Year.TAG)
            R.string.preferences -> openFragment(Preferences.newInstance(), Preferences.TAG)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(recommendedRandomizer)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(recommendedRandomizer)
        handler.postDelayed(recommendedRandomizer, RANDOMIZER_DELAY)
    }

    override fun onDestroyView() {
        // Null adapter references so they are cleanly re-created when the view is
        // re-inflated after returning from another fragment (mirrors Songs.kt behavior).
        recentlyPlayedAdapter = null
        recentlyAddedAdapter = null
        favoritesAdapter = null
        handler.removeCallbacks(recommendedRandomizer)
        super.onDestroyView()
    }

    override fun onAudio(audio: Audio) {
        super.onAudio(audio)
        updateStates(audio)
    }

    private fun updateStates(audio: Audio) {
        binding.currentlyPlaying.setTypeWriting(
                getString(R.string.now_playing, audio.title))
    }

    companion object {
        /**
         * Creates a new instance of [Dashboard].
         *
         * @return A fresh [Dashboard] fragment.
         */
        fun newInstance(): Dashboard = Dashboard()

        /** Back-stack tag used when navigating to this fragment. */
        const val TAG = "Dashboard"

        private const val PANEL_SPAN_COUNT = 4

        /** Number of span columns for the recommended art grid. */
        private const val RECOMMENDED_GRID_SPANS = 3

        /** Interval in milliseconds between each shuffle animation tick. */
        private const val RANDOMIZER_DELAY = 10_000L
    }
}