package app.simple.felicity.ui.home

import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.simple.felicity.R
import app.simple.felicity.adapters.home.dashboard.AdapterDashboardAlbums
import app.simple.felicity.adapters.home.dashboard.AdapterDashboardAlbums.Companion.AdapterDashboardAlbumsCallbacks
import app.simple.felicity.adapters.home.dashboard.AdapterDashboardArtists
import app.simple.felicity.adapters.home.dashboard.AdapterDashboardArtists.Companion.AdapterDashboardArtistsCallbacks
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
import app.simple.felicity.dialogs.app.AppLabel.Companion.showAppLabel
import app.simple.felicity.extensions.fragments.BaseHomeFragment
import app.simple.felicity.preferences.MainPreferences
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.server.ServerModeService
import app.simple.felicity.shared.utils.ViewUtils.gone
import app.simple.felicity.shared.utils.ViewUtils.visible
import app.simple.felicity.ui.panels.Albums
import app.simple.felicity.ui.panels.Artists
import app.simple.felicity.viewmodels.panels.DashboardViewModel
import app.simple.felicity.viewmodels.panels.DashboardViewModel.LibraryStats
import app.simple.felicity.viewmodels.panels.DashboardViewModel.RecommendedSpanConfig
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel.Companion.Panel
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat

/**
 * Dashboard home screen fragment.
 *
 * Displays a scrollable dashboard composed of:
 *  - A header with the app name, search, Wi-Fi server, and settings buttons.
 *  - A chip strip showing library stats (track count + total hours) and a server-active
 *    indicator that only appears while the Wi-Fi server is running.
 *  - A spanned art grid of recommended songs loaded once per app session. The user can
 *    request a new random selection at any time via the shuffle button next to the section title.
 *    Portrait uses a 3-column spanned layout; landscape uses a flat 5-column grid.
 *  - A horizontal carousel of recently played songs.
 *  - A horizontal carousel of top artists ranked by play count.
 *  - A horizontal carousel of top albums ranked by play count.
 *  - A four-column browse grid of every available panel navigation shortcut.
 *  - A horizontal carousel of recently added songs.
 *  - A horizontal carousel of favorite songs.
 *
 * All song items across every section support tap to play and long-press to open the
 * song context menu. Carousel and recommended data is loaded once and remains stable for
 * the duration of the app's lifecycle; only the recommended section can be manually refreshed.
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class Dashboard : BaseHomeFragment() {

    private lateinit var binding: FragmentHomeDashboardBinding

    private val dashboardViewModel: DashboardViewModel by viewModels()

    private var recentlyPlayedAdapter: AdapterDashboardSongs? = null
    private var recentlyAddedAdapter: AdapterDashboardSongs? = null
    private var favoritesAdapter: AdapterDashboardSongs? = null
    private var topArtistsAdapter: AdapterDashboardArtists? = null
    private var topAlbumsAdapter: AdapterDashboardAlbums? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHomeDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.label.setAppLabel()

        binding.label.setOnClickListener {
            childFragmentManager.showAppLabel()
        }

        setupPanelsGrid()
        showMiniPlayer()
        setupHeader()
        setupCarouselSpacing()
        observeData()

        binding.scrollView.requireAttachedMiniPlayer()

        binding.settings.setOnClickListener {
            openPreferencesPanel()
        }

        binding.refreshRecommended.setOnClickListener {
            dashboardViewModel.refreshRecommended()
        }
    }

    private fun setupHeader() {
        binding.search.setOnClickListener {
            openSearch()
        }

        setupServerToggle(binding.serverToggle)

        // Watch the server state to show or hide the "Server Active" chip in the strip.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServerModeService.isRunning.collect { running ->
                    if (running) binding.serverActiveChip.visible(true)
                    else binding.serverActiveChip.gone()
                }
            }
        }
    }

    private fun setupPanelsGrid() {
        val adapter = AdapterDashboardPanels(panels = dashboardViewModel.allPanelPanels)
        binding.panelsRecyclerView.layoutManager = FlexboxLayoutManager(requireContext()).apply {
            flexDirection = FlexDirection.ROW
            justifyContent = JustifyContent.FLEX_START
            alignItems = AlignItems.FLEX_START
        }
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
        binding.topArtistsList.addItemDecoration(LinearHorizontalSpacingDecoration(spacing))
        binding.topAlbumsList.addItemDecoration(LinearHorizontalSpacingDecoration(spacing))
    }

    /**
     * Configures the recommended spanned art grid using a [RecommendedSpanConfig] from
     * the ViewModel. Portrait uses 3 columns with two large 2x2 cells; landscape uses
     * 5 flat columns so the grid doesn't take up the entire screen height.
     *
     * The grid is fully replaced on every call so each ViewModel-driven cycle shows a fresh
     * selection with a new random layout pattern.
     *
     * @param data The fresh list of [Audio] items emitted by [DashboardViewModel.recommended].
     */
    private fun setupRecommendedGrid(data: List<Audio>) {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val spanConfig = dashboardViewModel.getSpanConfigForOrientation(resources.configuration.orientation)

        val layoutManager = SpannedGridLayoutManager(SpannedGridLayoutManager.Orientation.VERTICAL, spanConfig.spanCount)
        layoutManager.spanSizeLookup = SpannedGridLayoutManager.SpanSizeLookup { position ->
            if (position in spanConfig.bigCellPositions) SpanSize(2, 2) else SpanSize(1, 1)
        }

        if (binding.recommendedGrid.adapter != null) {
            binding.recommendedGrid.animate()
                .alpha(0f)
                .scaleX(0.9F)
                .scaleY(0.9F)
                .setDuration(300)
                .withEndAction {
                    applyRecommendedAdapter(data, layoutManager)
                    binding.recommendedGrid.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(300)
                        .start()
                }.start()
        } else {
            applyRecommendedAdapter(data, layoutManager)
        }

        binding.recommendedGrid.post {
            try {
                binding.recommendedGrid.layoutParams.height =
                    layoutManager.getTotalHeight() +
                            binding.recommendedGrid.paddingTop +
                            binding.recommendedGrid.paddingBottom
                binding.recommendedGrid.requestLayout()
            } catch (_: UninitializedPropertyAccessException) {
                // View was destroyed before the post ran — no worries, nothing to update.
            }
        }

        binding.recommendedSection.visible(false)
    }

    /**
     * Swaps in a fresh [AdapterRecommended] and attaches callbacks.
     * Extracted to avoid duplicating the adapter setup code for both the
     * first-time and animation-end code paths.
     *
     * @param data          The recommended [Audio] items to show.
     * @param layoutManager The freshly-built [SpannedGridLayoutManager] to attach.
     */
    private fun applyRecommendedAdapter(data: List<Audio>, layoutManager: SpannedGridLayoutManager) {
        binding.recommendedGrid.adapter = null
        binding.recommendedGrid.layoutManager = layoutManager
        binding.recommendedGrid.setHasFixedSize(false)
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
                launch {
                    dashboardViewModel.libraryStats.collect { stats ->
                        updateLibraryStats(stats)
                    }
                }
                launch {
                    dashboardViewModel.topArtists.collect { artists ->
                        updateTopArtists(artists)
                    }
                }
                launch {
                    dashboardViewModel.topAlbums.collect { albums ->
                        updateTopAlbums(albums)
                    }
                }
            }
        }
    }

    /**
     * Updates the library stats chip with a nicely formatted "12,403 Tracks • 840 Hours" string.
     * Uses [NumberFormat] so large numbers get the commas they deserve.
     *
     * @param stats The latest [LibraryStats] from the ViewModel, or null if not yet loaded.
     */
    private fun updateLibraryStats(stats: LibraryStats?) {
        if (stats == null) return
        val formatter = NumberFormat.getNumberInstance()
        binding.libraryStats.text = getString(
                R.string.library_stats,
                formatter.format(stats.trackCount),
                formatter.format(stats.totalHours)
        )
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
        } else {
            recentlyPlayedAdapter!!.updateData(songs)
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
        } else {
            recentlyAddedAdapter!!.updateData(songs)
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
        } else {
            favoritesAdapter!!.updateData(songs)
        }
    }

    /**
     * Shows or hides the top artists section and keeps the carousel in sync
     * with the latest data. Empty list? The section hides itself and waits.
     *
     * @param artists The latest list of top [Artist] items from the ViewModel.
     */
    private fun updateTopArtists(artists: List<Artist>) {
        if (artists.isEmpty()) {
            binding.topArtistsSection.gone()
            return
        }
        binding.topArtistsSection.visible(false)
        if (topArtistsAdapter == null) {
            topArtistsAdapter = AdapterDashboardArtists(artists)
            topArtistsAdapter!!.setCallbacks(object : AdapterDashboardArtistsCallbacks {
                override fun onArtistClicked(artist: Artist) {
                    openFragment(
                            Artists.newInstance(),
                            Artists.TAG
                    )
                }

                override fun onArtistLongClicked(artist: Artist, imageView: ImageView) {
                    openArtistMenu(artist, imageView)
                }
            })
            binding.topArtistsList.adapter = topArtistsAdapter
        } else {
            topArtistsAdapter!!.updateData(artists)
        }
    }

    /**
     * Shows or hides the top albums section and keeps the carousel in sync
     * with the latest data. Empty list? The section disappears quietly.
     *
     * @param albums The latest list of top [Album] items from the ViewModel.
     */
    private fun updateTopAlbums(albums: List<Album>) {
        if (albums.isEmpty()) {
            binding.topAlbumsSection.gone()
            return
        }
        binding.topAlbumsSection.visible(false)
        if (topAlbumsAdapter == null) {
            topAlbumsAdapter = AdapterDashboardAlbums(albums)
            topAlbumsAdapter!!.setCallbacks(object : AdapterDashboardAlbumsCallbacks {
                override fun onAlbumClicked(album: Album) {
                    openFragment(Albums.newInstance(), Albums.TAG)
                }

                override fun onAlbumLongClicked(album: Album, imageView: ImageView) {
                    openAlbumMenu(album, imageView)
                }
            })
            binding.topAlbumsList.adapter = topAlbumsAdapter
        } else {
            topAlbumsAdapter!!.updateData(albums)
        }
    }

    override fun onDestroyView() {
        recentlyPlayedAdapter = null
        recentlyAddedAdapter = null
        favoritesAdapter = null
        topArtistsAdapter = null
        topAlbumsAdapter = null
        super.onDestroyView()
    }

    override fun onAudio(audio: Audio) {
        super.onAudio(audio)
        // Nothing extra to do here — the library stats chip replaced the "currently playing" display.
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            MainPreferences.APP_LABEL -> {
                binding.label.setAppLabel()
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

    companion object {
        /**
         * Creates a new instance of [Dashboard].
         *
         * @return A fresh [Dashboard] fragment ready to show your music library.
         */
        fun newInstance(): Dashboard = Dashboard()

        /** Back-stack tag used when navigating to this fragment. */
        const val TAG = "Dashboard"

        private const val PANEL_SPAN_COUNT = 3
    }
}