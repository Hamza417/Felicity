package app.simple.felicity.ui.home

import android.content.SharedPreferences
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
import app.simple.felicity.adapters.home.dashboard.AdapterDashboardMixes
import app.simple.felicity.adapters.home.dashboard.AdapterDashboardMixes.Companion.AdapterDashboardMixesCallbacks
import app.simple.felicity.adapters.home.dashboard.AdapterDashboardPanels
import app.simple.felicity.adapters.home.dashboard.AdapterDashboardPanels.Companion.AdapterDashboardPanelsCallbacks
import app.simple.felicity.adapters.home.dashboard.AdapterDashboardSongs
import app.simple.felicity.adapters.home.dashboard.AdapterDashboardSongs.Companion.AdapterDashboardSongsCallbacks
import app.simple.felicity.databinding.FragmentHomeDashboardBinding
import app.simple.felicity.decorations.itemdecorations.LinearHorizontalSpacingDecoration
import app.simple.felicity.dialogs.app.AppLabel.Companion.showAppLabel
import app.simple.felicity.dialogs.player.SleepTimerDialog.Companion.openSleepTimerDialog
import app.simple.felicity.engine.managers.MediaPlaybackManager
import app.simple.felicity.extensions.fragments.BaseHomeFragment
import app.simple.felicity.preferences.MainPreferences
import app.simple.felicity.preferences.QueueLabelPreferences
import app.simple.felicity.preferences.UserInterfacePreferences
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.server.ServerModeService
import app.simple.felicity.shared.utils.ViewUtils.gone
import app.simple.felicity.shared.utils.ViewUtils.visible
import app.simple.felicity.shared.utils.WindowUtil.applyLandscapeNavBarPadding
import app.simple.felicity.ui.pages.AlbumPage
import app.simple.felicity.ui.pages.ArtistPage
import app.simple.felicity.ui.panels.MixPanel
import app.simple.felicity.ui.panels.PlayingQueue
import app.simple.felicity.viewmodels.panels.DashboardViewModel
import app.simple.felicity.viewmodels.panels.DashboardViewModel.LibraryStats
import app.simple.felicity.viewmodels.panels.MixesViewModel
import app.simple.felicity.viewmodels.panels.MixesViewModel.Mix
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel.Companion.Panel
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
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
 *  - A "Mixed" carousel with two auto-generated playlists, Discover (never played songs) and
 *    Random Picks, drawn as stacks of covers. Each opens its own panel and the refresh button
 *    generates new mixes.
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
    private val mixesViewModel: MixesViewModel by viewModels({ requireActivity() })

    private var recentlyPlayedAdapter: AdapterDashboardSongs? = null
    private var recentlyAddedAdapter: AdapterDashboardSongs? = null
    private var favoritesAdapter: AdapterDashboardSongs? = null
    private var topArtistsAdapter: AdapterDashboardArtists? = null
    private var topAlbumsAdapter: AdapterDashboardAlbums? = null
    private var mixesAdapter: AdapterDashboardMixes? = null

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



        binding.refreshMixes.setOnClickListener {
            mixesViewModel.refresh()
        }

        binding.sleepTimer.setOnClickListener {
            childFragmentManager.openSleepTimerDialog()
        }

        // Insets
        binding.header.applyLandscapeNavBarPadding()
        binding.strip.applyLandscapeNavBarPadding()
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MediaPlaybackManager.sleepTimerStateFlow.collectLatest { running ->
                    if (running) {
                        binding.sleepTimer.visible(true)
                    } else {
                        binding.sleepTimer.gone()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MediaPlaybackManager.sleepTimerRemainingFlow.collectLatest { remaining ->
                    if (MediaPlaybackManager.isSleepTimerRunning()) {
                        binding.sleepTimer.text = remaining.formatted
                    } else if (MediaPlaybackManager.pauseAfterCurrentSong) {
                        binding.sleepTimer.text = getString(R.string.till_this_song)
                    } else {
                        binding.sleepTimer.text = ""
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dashboardViewModel.activeQueue.collect { idx ->
                    binding.activeQueue.text = QueueLabelPreferences.getDisplayLabel(idx) { _ ->
                        getString(R.string.current_queue, idx + 1)
                    }

                    binding.activeQueue.setOnClickListener {
                        openFragment(PlayingQueue.newInstance(), PlayingQueue.TAG)
                    }
                }
            }
        }
    }

    private fun setupPanelsGrid() {
        val adapter = AdapterDashboardPanels(panels = dashboardViewModel.getVisiblePanelPanels())
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
        binding.mixedList.addItemDecoration(LinearHorizontalSpacingDecoration(spacing))
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

            launch {
                    mixesViewModel.mixes.collect { mixes ->
                        updateMixes(mixes)
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

    /**
     * Shows or hides the "Mixed" row and keeps its Discover / Random Picks cards in sync.
     * Tapping a card opens the panel of that mix.
     *
     * @param mixes The latest list of [Mix] items from the ViewModel.
     */
    private fun updateMixes(mixes: List<Mix>) {
        if (mixes.isEmpty()) {
            binding.mixedSection.gone()
            return
        }
        binding.mixedSection.visible(false)
        if (mixesAdapter == null) {
            mixesAdapter = AdapterDashboardMixes(mixes)
            mixesAdapter!!.setCallbacks(object : AdapterDashboardMixesCallbacks {
                override fun onMixClicked(mix: Mix) {
                    openFragment(MixPanel.newInstance(mix.type), "${MixPanel.TAG}_${mix.type.name}")
                }
            })
            binding.mixedList.adapter = mixesAdapter
        } else {
            mixesAdapter!!.updateData(mixes)
        }
    }

    private fun updateRecentlyPlayed(songs: List<Audio>) {
        if (songs.isEmpty() || !UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_RECENTLY_PLAYED)) {
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
        if (songs.isEmpty() || !UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_RECENTLY_ADDED)) {
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
        if (songs.isEmpty() || !UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_FAVORITES)) {
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
                    openFragment(ArtistPage.newInstance(artist), ArtistPage.TAG)
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
                    openFragment(AlbumPage.newInstance(album), AlbumPage.TAG)
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
        mixesAdapter = null
        super.onDestroyView()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            MainPreferences.APP_LABEL -> {
                binding.label.setAppLabel()
            }
            // When any panel visibility preference changes, rebuild the browse grid so it
            // immediately reflects what the user just toggled — no restart required.
            in UserInterfacePreferences.ALL_PANEL_VISIBILITY_KEYS -> {
                setupPanelsGrid()
                // Also re-evaluate the carousel sections that are tied to panel items.
                evaluateCarouselSections()
            }
        }
    }

    /**
     * Hides or shows the carousel sections whose visibility is controlled by panel preferences.
     * This is called once at startup via [observeData] callbacks, and again whenever the
     * user changes a panel visibility setting.
     */
    private fun evaluateCarouselSections() {
        if (!UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_RECENTLY_PLAYED)) {
            binding.recentlyPlayedSection.gone()
        } else if ((recentlyPlayedAdapter?.itemCount ?: 0) > 0) {
            binding.recentlyPlayedSection.visible(false)
        }

        if (!UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_RECENTLY_ADDED)) {
            binding.recentlyAddedSection.gone()
        } else if ((recentlyAddedAdapter?.itemCount ?: 0) > 0) {
            binding.recentlyAddedSection.visible(false)
        }

        if (!UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_FAVORITES)) {
            binding.favoritesSection.gone()
        } else if ((favoritesAdapter?.itemCount ?: 0) > 0) {
            binding.favoritesSection.visible(false)
        }
    }

    override fun onResume() {
        super.onResume()
    }

    companion object {
        /**
         * Creates a new instance of [Dashboard].
         */
        fun newInstance(): Dashboard {
            val args = Bundle()
            val fragment = Dashboard()
            fragment.arguments = args
            return fragment
        }

        /** Back-stack tag used when navigating to this fragment. */
        const val TAG = "Dashboard"
    }
}