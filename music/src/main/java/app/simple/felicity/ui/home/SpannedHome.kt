package app.simple.felicity.ui.home

import android.os.Bundle
import android.util.Log
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
import app.simple.felicity.adapters.home.main.AdapterSpannedHomeTiles
import app.simple.felicity.adapters.home.main.AdapterSpannedHomeTiles.SpannedTile
import app.simple.felicity.databinding.FragmentHomeSpannedBinding
import app.simple.felicity.databinding.HeaderHomeSpannedBinding
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.dialogs.app.AppLabel.Companion.showAppLabel
import app.simple.felicity.extensions.fragments.BaseHomeFragment
import app.simple.felicity.preferences.MainPreferences
import app.simple.felicity.preferences.UserInterfacePreferences
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.server.ServerModeService
import app.simple.felicity.shared.utils.ViewUtils.gone
import app.simple.felicity.shared.utils.ViewUtils.visible
import app.simple.felicity.viewmodels.panels.HomeViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.concurrent.TimeUnit

/**
 * Home screen that renders a flat Windows Phone-style tile grid using a single
 * [GridLayoutManager] with a custom [GridLayoutManager.SpanSizeLookup].
 *
 * The header is managed by [AppHeader] and hides on downward scroll — no adapter
 * trickery needed. [AppHeader] also adds a spacing item decoration so the grid
 * content starts below the header without any padding hacks.
 *
 * The tile grid (45 items, 3 columns) contains:
 *  - Song tiles: most are 1x1, five hero positions are 2-column wide (visually 2×2
 *    because item views are square by nature).
 *  - Panel tiles: fixed at every third position, spread throughout the grid.
 *    Big hero positions are always song art — panels can never land there.
 *
 * @author Hamza417
 */
class SpannedHome : BaseHomeFragment() {

    private lateinit var binding: FragmentHomeSpannedBinding
    private lateinit var headerBinding: HeaderHomeSpannedBinding
    private val homeViewModel: HomeViewModel by viewModels({ requireActivity() })

    /** The adapter that drives the tile grid. Created once and never replaced. */
    private var tilesAdapter: AdapterSpannedHomeTiles? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHomeSpannedBinding.inflate(inflater, container, false)
        headerBinding = HeaderHomeSpannedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHeader()
        setupRecyclerView()
        observeData()

        binding.recyclerView.requireAttachedMiniPlayer()
    }

    /**
     * Attaches the header content to [AppHeader] and wires up all the action buttons.
     * [AppHeader] handles the scroll-hide animation and adds a top spacing item
     * decoration to the RecyclerView automatically via [AppHeader.attachTo].
     */
    private fun setupHeader() {
        binding.appHeader.setContentView(headerBinding.root)
        binding.appHeader.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)

        headerBinding.label.setAppLabel()

        headerBinding.label.setOnClickListener {
            childFragmentManager.showAppLabel()
        }

        headerBinding.search.setOnClickListener {
            openSearch()
        }

        headerBinding.settings.setOnClickListener {
            openPreferencesPanel()
        }

        setupServerToggle(headerBinding.serverToggle)
    }

    /**
     * Creates the [GridLayoutManager] with a span size lookup that makes hero tile positions
     * span 2 columns. Because tiles are square, 2-column-wide = visually 2×2.
     */
    private fun setupRecyclerView() {
        val layoutManager = GridLayoutManager(requireContext(), SPAN_COUNT)

        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                // Hero tiles are 2 columns wide; everyone else gets one column.
                return if (position in AdapterSpannedHomeTiles.BIG_TILE_POSITIONS) 2 else 1
            }
        }

        layoutManager.spanSizeLookup.isSpanIndexCacheEnabled = true

        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.itemAnimator = null
    }

    /**
     * Launches three parallel observations:
     * - Recommended songs: builds the adapter on the very first non-empty emission.
     * - Library stats: updates the track count + hours chip in the header.
     * - Server state: shows or hides the server-active chip in the header.
     */
    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    homeViewModel.recommended.collect { songs ->
                        // Build the adapter exactly once — no shuffling or refreshing after that.
                        if (songs.isEmpty() || tilesAdapter != null) return@collect
                        Log.d(TAG, "Building tile grid with ${songs.size} songs")
                        buildAndAttachAdapter(songs)
                    }
                }
                launch {
                    homeViewModel.libraryStats.collect { stats ->
                        stats ?: return@collect
                        val (count, totalMs) = stats
                        val fmt = NumberFormat.getNumberInstance()
                        val hours = TimeUnit.MILLISECONDS.toHours(totalMs)
                        headerBinding.libraryStats.text = getString(
                                R.string.library_stats,
                                fmt.format(count),
                                fmt.format(hours))
                        headerBinding.libraryStats.visible(animate = false)
                    }
                }
                launch {
                    ServerModeService.isRunning.collect { running ->
                        if (running) headerBinding.serverActiveChip.visible(animate = true)
                        else headerBinding.serverActiveChip.gone()
                    }
                }
            }
        }
    }

    /**
     * Assembles the tile list, creates the adapter, attaches callbacks, and hooks it
     * to the RecyclerView. Runs exactly once per fragment lifetime.
     *
     * @param songs Recommended song pool from [HomeViewModel].
     */
    private fun buildAndAttachAdapter(songs: List<Audio>) {
        val tiles = AdapterSpannedHomeTiles.buildTiles(songs, buildPanelTiles())
        tilesAdapter = AdapterSpannedHomeTiles(tiles)

        tilesAdapter!!.setCallbacks(object : AdapterSpannedHomeTiles.SpannedHomeTileCallbacks {
            override fun onSongTileClicked(songs: List<Audio>, position: Int) {
                setMediaItems(songs, position)
            }

            override fun onSongTileLongClicked(audio: Audio, imageView: ImageView) {
                val allSongs = tiles
                    .filterIsInstance<SpannedTile.SongTile>()
                    .map { it.audio }
                    .toMutableList()
                val index = allSongs.indexOfFirst { it.id == audio.id }.coerceAtLeast(0)
                openSongsMenu(allSongs, index, imageView)
            }

            override fun onPanelTileClicked(panelTile: SpannedTile.PanelTile) {
                navigateToPanel(panelTile.getPanel())
            }
        })

        binding.recyclerView.adapter = tilesAdapter
        binding.recyclerView.scheduleLayoutAnimation()
    }

    /**
     * Returns all navigation panel tiles that the user currently has enabled,
     * mirroring the Dashboard panel visibility logic exactly.
     * Songs, Albums, and Artists are always included — the rest are opt-in.
     *
     * @return Ordered list of [SpannedTile.PanelTile] items.
     */
    private fun buildPanelTiles(): List<SpannedTile.PanelTile> {
        val tiles = mutableListOf<SpannedTile.PanelTile>()

        // These three are always present — no preference check needed.
        tiles.add(SpannedTile.PanelTile(R.string.songs, R.drawable.ic_song_16dp))
        tiles.add(SpannedTile.PanelTile(R.string.albums, R.drawable.ic_album_16dp))
        tiles.add(SpannedTile.PanelTile(R.string.artists, R.drawable.ic_people_16dp))

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_ALBUM_ARTISTS))
            tiles.add(SpannedTile.PanelTile(R.string.album_artists, R.drawable.ic_artist_16dp))

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_GENRES))
            tiles.add(SpannedTile.PanelTile(R.string.genres, R.drawable.ic_piano_16dp))

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_YEAR))
            tiles.add(SpannedTile.PanelTile(R.string.year, R.drawable.ic_date_range_16dp))

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_PLAYLISTS))
            tiles.add(SpannedTile.PanelTile(R.string.playlists, R.drawable.ic_list_16dp))

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_PLAYING_QUEUE))
            tiles.add(SpannedTile.PanelTile(R.string.playing_queue, R.drawable.ic_queue_16dp))

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_RECENTLY_ADDED))
            tiles.add(SpannedTile.PanelTile(R.string.recently_added, R.drawable.ic_recently_added_16dp))

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_RECENTLY_PLAYED))
            tiles.add(SpannedTile.PanelTile(R.string.recently_played, R.drawable.ic_history_16dp))

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_MOST_PLAYED))
            tiles.add(SpannedTile.PanelTile(R.string.most_played, R.drawable.ic_equalizer_16dp))

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_FAVORITES)) {
            val iconRes = if (UserInterfacePreferences.isLikeIconInsteadOfThumb())
                R.drawable.ic_thumb_up_16dp else R.drawable.ic_favorite_filled_16dp
            tiles.add(SpannedTile.PanelTile(R.string.favorites, iconRes))
        }

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_FOLDERS))
            tiles.add(SpannedTile.PanelTile(R.string.folders, R.drawable.ic_folder_16dp))

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_FOLDERS_HIERARCHY))
            tiles.add(SpannedTile.PanelTile(R.string.folders_hierarchy, R.drawable.ic_tree_16dp))

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_ALWAYS_SKIPPED))
            tiles.add(SpannedTile.PanelTile(R.string.always_skipped, R.drawable.ic_skip_16dp))

        return tiles
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        // Reset the header scroll state so it's always visible when the fragment is
        // revisited — the grid starts at position 0, so a hidden header would be stuck.
        binding.appHeader.post { binding.appHeader.resetScrollingState() }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: android.content.SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        if (key == MainPreferences.APP_LABEL) {
            headerBinding.label.setAppLabel()
        }
    }

    override fun onDestroyView() {
        tilesAdapter = null
        super.onDestroyView()
    }

    companion object {
        /**
         * Creates a new instance of [SpannedHome].
         *
         * @return A freshly instantiated [SpannedHome] ready to show tiles.
         */
        fun newInstance(): SpannedHome {
            val args = Bundle()
            val fragment = SpannedHome()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "SpannedHome"

        /** How many columns the tile grid uses — 3 matches the classic Windows Phone look. */
        private const val SPAN_COUNT = 3
    }
}
