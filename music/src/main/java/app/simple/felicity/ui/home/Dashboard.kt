package app.simple.felicity.ui.home

import android.os.Bundle
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
import app.simple.felicity.adapters.home.dashboard.AdapterDashboardPanels
import app.simple.felicity.adapters.home.dashboard.AdapterDashboardPanels.Companion.AdapterDashboardPanelsCallbacks
import app.simple.felicity.adapters.home.dashboard.AdapterDashboardSongs
import app.simple.felicity.adapters.home.dashboard.AdapterDashboardSongs.Companion.AdapterDashboardSongsCallbacks
import app.simple.felicity.adapters.home.dashboard.AdapterRecommended
import app.simple.felicity.adapters.home.dashboard.AdapterRecommended.Companion.AdapterRecommendedCallbacks
import app.simple.felicity.databinding.FragmentHomeDashboardBinding
import app.simple.felicity.decorations.itemdecorations.LinearHorizontalSpacingDecoration
import app.simple.felicity.decorations.layoutmanager.spanned.SpanSize
import app.simple.felicity.decorations.layoutmanager.spanned.SpannedGridLayoutManager
import app.simple.felicity.decorations.utils.TextViewUtils.setTextWithEffect
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.ViewUtils.gone
import app.simple.felicity.shared.utils.ViewUtils.visible
import app.simple.felicity.ui.panels.Albums
import app.simple.felicity.ui.panels.Artists
import app.simple.felicity.ui.panels.Favorites
import app.simple.felicity.ui.panels.Folders
import app.simple.felicity.ui.panels.FoldersHierarchy
import app.simple.felicity.ui.panels.Genres
import app.simple.felicity.ui.panels.MostPlayed
import app.simple.felicity.ui.panels.PlayingQueue
import app.simple.felicity.ui.panels.Preferences
import app.simple.felicity.ui.panels.RecentlyAdded
import app.simple.felicity.ui.panels.RecentlyPlayed
import app.simple.felicity.ui.panels.Search
import app.simple.felicity.ui.panels.Songs
import app.simple.felicity.ui.panels.Year
import app.simple.felicity.viewmodels.panels.DashboardViewModel
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel.Companion.Panel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Dashboard home screen fragment.
 *
 * Displays a scrollable dashboard composed of:
 * - A header with the app name, search, and settings buttons.
 * - A spanned art grid of recommended songs loaded once per app session. The user can
 *   request a new random selection at any time via the shuffle button next to the section title.
 * - A horizontal carousel of recently played songs.
 * - A four-column browse grid of panel navigation shortcuts with an inline
 *   expand/collapse button that reveals the full panel list without navigation.
 * - A horizontal carousel of recently added songs.
 * - A horizontal carousel of favorite songs.
 *
 * All song items across every section support tap to play and long-press to open the
 * song context menu. Carousel and recommended data is loaded once and remains stable for
 * the duration of the app's lifecycle; only the recommended section can be manually refreshed.
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class Dashboard : MediaFragment() {

    private lateinit var binding: FragmentHomeDashboardBinding

    private val dashboardViewModel: DashboardViewModel by viewModels()

    private var recentlyPlayedAdapter: AdapterDashboardSongs? = null
    private var recentlyAddedAdapter: AdapterDashboardSongs? = null
    private var favoritesAdapter: AdapterDashboardSongs? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHomeDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        showMiniPlayer()
        setupHeader()
        setupPanelsGrid()
        setupCarouselSpacing()
        observeData()

        binding.scrollView.requireAttachedMiniPlayer()

        binding.settings.setOnClickListener {
            openPreferencesPanel()
        }

        binding.refreshRecommended.setOnClickListener {
            dashboardViewModel.refreshRecommended()
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
                firstPanels = dashboardViewModel.firstPanelPanels,
                allPanels = dashboardViewModel.allPanelPanels)
        binding.panelsRecyclerView.layoutManager = GridLayoutManager(requireContext(), PANEL_SPAN_COUNT)
        binding.panelsRecyclerView.adapter = adapter
        adapter.setCallbacks(object : AdapterDashboardPanelsCallbacks {
            override fun onPanelClicked(panel: Panel) {
                navigateToPanel(panel)
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
     * same random span-size pattern as [SpannedHome], then attaches an [AdapterRecommended].
     *
     * The grid is fully replaced on every call so each ViewModel-driven cycle shows a fresh
     * selection with a new random layout pattern.
     *
     * @param data The fresh list of [Audio] items emitted by [DashboardViewModel.recommended].
     */
    private fun setupRecommendedGrid(data: List<Audio>) {
        // Define the 4 exact patterns from your image
        val validPatterns = listOf(
                intArrayOf(0, 4), // Pattern 1: Diagonal (Top-Left, Bottom-Right)
                intArrayOf(1, 4), // Pattern 2: Stacked Right
                intArrayOf(1, 3), // Pattern 3: Diagonal (Top-Right, Bottom-Left)
                intArrayOf(0, 3)  // Pattern 4: Stacked Left
        )

        // Randomly select one pattern
        val randomSpanPositions = validPatterns.random()

        val layoutManager = SpannedGridLayoutManager(SpannedGridLayoutManager.Orientation.VERTICAL, 3) // Make sure this is 3!
        layoutManager.spanSizeLookup = SpannedGridLayoutManager.SpanSizeLookup { position ->
            if (position in randomSpanPositions) {
                SpanSize(2, 2) // The "Pink" blocks in your image
            } else {
                SpanSize(1, 1) // The "Green" blocks in your image
            }
        }

        if (binding.recommendedGrid.adapter != null) {
            binding.recommendedGrid.animate()
                .alpha(0f)
                .scaleX(0.9F)
                .scaleY(0.9F)
                .setDuration(300)
                .withEndAction {
                    binding.recommendedGrid.adapter = null
                    binding.recommendedGrid.layoutManager = layoutManager
                    val adapter = AdapterRecommended(data)
                    binding.recommendedGrid.adapter = adapter
                    binding.recommendedGrid.scheduleLayoutAnimation()

                    adapter.setCallbacks(object : AdapterRecommendedCallbacks {
                        override fun onItemClicked(items: List<Audio>, position: Int) {
                            setMediaItems(items, position)
                        }

                        override fun onItemLongClicked(items: List<Audio>, position: Int, imageView: ImageView) {
                            openSongsMenu(items, position, imageView)
                        }
                    })

                    binding.recommendedGrid.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(300)
                        .start()
                }.start()
        } else {
            val adapter = AdapterRecommended(data)
            binding.recommendedGrid.setHasFixedSize(false)
            binding.recommendedGrid.layoutManager = layoutManager
            binding.recommendedGrid.adapter = adapter
            binding.recommendedGrid.scheduleLayoutAnimation()

            adapter.setCallbacks(object : AdapterRecommendedCallbacks {
                override fun onItemClicked(items: List<Audio>, position: Int) {
                    setMediaItems(items, position)
                }

                override fun onItemLongClicked(items: List<Audio>, position: Int, imageView: ImageView) {
                    openSongsMenu(items, position, imageView)
                }
            })
        }

        // IMPORTANT: Update the grid's height to fit its content
        // (otherwise the shuffle animation will cause it to collapse to zero height and disappear).
        binding.recommendedGrid.post {
            try {
                binding.recommendedGrid.layoutParams.height =
                    layoutManager.getTotalHeight() +
                            binding.recommendedGrid.paddingTop +
                            binding.recommendedGrid.paddingBottom
                binding.recommendedGrid.requestLayout()
            } catch (_: UninitializedPropertyAccessException) {
                // This can occur if the user navigates away from the dashboard before the first
                // data emission, causing the grid to be destroyed before the posted runnable executes.
                // In this case, we can safely ignore the exception since the grid will no longer be visible.
            }
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

                override fun onSongLongClicked(songs: MutableList<Audio>, position: Int, imageView: ImageView) {
                    openSongsMenu(songs, position, imageView)
                }
            })
            binding.recentlyPlayedList.adapter = recentlyPlayedAdapter
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

                override fun onSongLongClicked(songs: MutableList<Audio>, position: Int, imageView: ImageView) {
                    openSongsMenu(songs, position, imageView)
                }
            })
            binding.recentlyAddedList.adapter = recentlyAddedAdapter
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

                override fun onSongLongClicked(songs: MutableList<Audio>, position: Int, imageView: ImageView) {
                    openSongsMenu(songs, position, imageView)
                }
            })
            binding.favoritesList.adapter = favoritesAdapter
        }
    }

    private fun navigateToPanel(panel: Panel) {
        when (panel.titleResId) {
            R.string.songs -> openFragment(Songs.newInstance(), Songs.TAG)
            R.string.albums -> openFragment(Albums.newInstance(), Albums.TAG)
            R.string.artists -> openFragment(Artists.newInstance(), Artists.TAG)
            R.string.genres -> openFragment(Genres.newInstance(), Genres.TAG)
            R.string.favorites -> openFragment(Favorites.newInstance(), Favorites.TAG)
            R.string.playing_queue -> openFragment(PlayingQueue.newInstance(), PlayingQueue.TAG)
            R.string.recently_added -> openFragment(RecentlyAdded.newInstance(), RecentlyAdded.TAG)
            R.string.recently_played -> openFragment(RecentlyPlayed.newInstance(), RecentlyPlayed.TAG)
            R.string.most_played -> openFragment(MostPlayed.newInstance(), MostPlayed.TAG)
            R.string.folders -> openFragment(Folders.newInstance(), Folders.TAG)
            R.string.folders_hierarchy -> openFragment(FoldersHierarchy.newInstance(), FoldersHierarchy.TAG)
            R.string.year -> openFragment(Year.newInstance(), Year.TAG)
            R.string.preferences -> openFragment(Preferences.newInstance(), Preferences.TAG)
        }
    }

    override fun onDestroyView() {
        // Null adapter references so they are cleanly re-created when the view is
        // re-inflated after returning from another fragment (mirrors Songs.kt behavior).
        recentlyPlayedAdapter = null
        recentlyAddedAdapter = null
        favoritesAdapter = null
        super.onDestroyView()
    }

    override fun onAudio(audio: Audio) {
        super.onAudio(audio)
        updateStates(audio)
    }

    private fun updateStates(audio: Audio) {
        binding.currentlyPlaying.setTextWithEffect(
                getString(R.string.now_playing, audio.title),
                isForward = MediaManager.lastNavigationDirection
        )
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
    }
}