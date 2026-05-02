package app.simple.felicity.ui.home

import android.graphics.Color
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
import app.simple.felicity.R
import app.simple.felicity.adapters.home.main.AdapterSpannedHomeTiles
import app.simple.felicity.adapters.home.main.AdapterSpannedHomeTiles.SpannedTile
import app.simple.felicity.databinding.FragmentHomeSpannedBinding
import app.simple.felicity.decorations.layoutmanager.spanned.SpanSize
import app.simple.felicity.decorations.layoutmanager.spanned.SpannedGridLayoutManager
import app.simple.felicity.extensions.fragments.BaseHomeFragment
import app.simple.felicity.preferences.UserInterfacePreferences
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.viewmodels.panels.HomeViewModel
import kotlinx.coroutines.launch

/**
 * Home screen that renders a flat Windows Phone-style tile grid using a single
 * [SpannedGridLayoutManager] — no nesting, no inner RecyclerViews, just clean tiles.
 *
 * The grid is 3 columns wide and contains 45 fixed positions:
 *
 *  - **Song tiles** — album art + title. Five of these are 2×2 hero tiles; the rest are 1×1.
 *    The song pool is a curated mix of your most-played, recently-played, and random tracks
 *    from the full library, so every session feels a little different.
 *
 *  - **Panel tiles** — every third position (15 slots total) is reserved for navigation panels.
 *    They are populated in order from the user's enabled panel list and never move, so
 *    tapping your favorite shortcut quickly becomes pure muscle memory.
 *
 * Data is loaded exactly once per fragment lifecycle. No background shuffling,
 * no flickering — the grid just sits there looking great.
 *
 * @author Hamza417
 */
class SpannedHome : BaseHomeFragment() {

    private lateinit var binding: FragmentHomeSpannedBinding
    private val homeViewModel: HomeViewModel by viewModels({ requireActivity() })

    /** The adapter that drives the tile grid. Created once and never replaced. */
    private var tilesAdapter: AdapterSpannedHomeTiles? = null

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

        setupRecyclerView()
        observeRecommended()
    }

    /**
     * Attaches the [SpannedGridLayoutManager] with a fixed span lookup so the hero tile
     * positions and the 3-column structure are locked in before any data arrives.
     */
    private fun setupRecyclerView() {
        val layoutManager = SpannedGridLayoutManager(SpannedGridLayoutManager.Orientation.VERTICAL, SPAN_COUNT)

        layoutManager.spanSizeLookup = SpannedGridLayoutManager.SpanSizeLookup { position ->
            // Five hero positions get the big 2×2 treatment; everything else stays compact 1×1.
            if (position in AdapterSpannedHomeTiles.BIG_TILE_POSITIONS) SpanSize(2, 2) else SpanSize(1, 1)
        }

        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.setHasFixedSize(false)
        binding.recyclerView.itemAnimator = null
    }

    /**
     * Waits for the first non-empty emission from [HomeViewModel.recommended] and then
     * builds the adapter. We only do this once — subsequent emissions are ignored because
     * the recommended list is stable for the duration of the session. No surprises.
     */
    private fun observeRecommended() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeViewModel.recommended.collect { songs ->
                    // Only build the adapter on first arrival — we don't shuffle or refresh after that.
                    if (songs.isEmpty() || tilesAdapter != null) return@collect
                    Log.d(TAG, "Building tile grid with ${songs.size} songs")
                    buildAndAttachAdapter(songs)
                    requireView().startTransitionOnPreDraw()
                }
            }
        }
    }

    /**
     * Assembles the full tile list, wires up the adapter, and attaches it to the RecyclerView.
     * This runs exactly once per fragment lifetime.
     *
     * @param songs The recommended song pool from the ViewModel.
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
     * Builds the full list of panel tiles that the user currently has enabled,
     * respecting their visibility preferences exactly like the dashboard does.
     *
     * Songs, Albums, and Artists are always included — the rest are opt-in.
     * The order here is the order they appear in the grid, so it's intentional.
     *
     * @return Ordered list of [SpannedTile.PanelTile] items to populate the panel slots.
     */
    private fun buildPanelTiles(): List<SpannedTile.PanelTile> {
        val tiles = mutableListOf<SpannedTile.PanelTile>()

        // These three are sacred — always present, no preference check needed.
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

        /** How many columns the tile grid uses — 3 matches the classic Windows Phone layout. */
        private const val SPAN_COUNT = 3
    }
}