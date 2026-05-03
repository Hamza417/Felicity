package app.simple.felicity.ui.home

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.home.main.AdapterSpannedHomeTiles
import app.simple.felicity.adapters.home.main.AdapterSpannedHomeTiles.SpannedTile
import app.simple.felicity.databinding.FragmentHomeSpannedBinding
import app.simple.felicity.decorations.utils.RecyclerViewUtils.forEachViewHolderIndexed
import app.simple.felicity.extensions.fragments.BaseHomeFragment
import app.simple.felicity.preferences.UserInterfacePreferences
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.viewmodels.panels.HomeViewModel
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Home screen that renders a flat Windows Phone-style tile grid using a single
 * [GridLayoutManager] — no nesting, no inner RecyclerViews, just clean tiles.
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
class TiledHome : BaseHomeFragment() {

    private lateinit var binding: FragmentHomeSpannedBinding
    private val homeViewModel: HomeViewModel by viewModels({ requireActivity() })

    /** The adapter that drives the tile grid. Created once and never replaced. */
    private var tilesAdapter: AdapterSpannedHomeTiles? = null
    private var tileAnimators: MutableMap<Int, Pair<SpringAnimation, SpringAnimation>> = mutableMapOf()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHomeSpannedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireLightBarIcons()
        binding.recyclerView.setBackgroundColor(Color.BLACK)
        binding.recyclerView.requireAttachedMiniPlayer()

        setupRecyclerView()
        observeRecommended()
    }

    /**
     * Wires up the [GridLayoutManager] with the right column count for the current orientation
     * and a [GridLayoutManager.SpanSizeLookup] that makes hero tiles eat twice as many columns
     * as regular tiles — giving us that satisfying large-tile-small-tile contrast.
     *
     * Portrait uses 3 columns (big tiles span 2, small tiles span 1).
     * Landscape uses 6 columns (big tiles span 4, small tiles span 2) so the proportions
     * stay identical regardless of how the user is holding their phone.
     */
    private fun setupRecyclerView() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val spanCount = if (isLandscape) SPAN_COUNT_LANDSCAPE else SPAN_COUNT_PORTRAIT
        val bigTileSpan = 2
        val smallTileSpan = 1

        val layoutManager = GridLayoutManager(requireContext(), spanCount)

        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                // Hero positions get the wide treatment; everything else stays compact.
                return if (position in AdapterSpannedHomeTiles.BIG_TILE_POSITIONS) bigTileSpan else smallTileSpan
            }
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
                    // Only build the adapter on first arrival
                    if (songs.isEmpty() || tilesAdapter != null) return@collect
                    buildAndAttachAdapter(songs)
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

                // Shrink every visible tile to a random scale so the menu feels like it's
                // "pushing" the grid into the background. Each tile gets its own scale so
                // the effect looks organic rather than mechanical.
                animateTilesTo(randomScale = true, imageView)

                openSongsMenu(
                        audios = allSongs,
                        position = index,
                        imageView = imageView,
                        onDismissStart = {
                            // The dialog has started closing — restore tiles right now so they
                            // grow back in sync with the image flying home. Much smoother than
                            // waiting for the whole animation to wrap up before doing anything.
                            animateTilesTo(randomScale = false, imageView = imageView)
                        }
                )
            }

            override fun onPanelTileClicked(panelTile: SpannedTile.PanelTile) {
                when (panelTile.titleRes) {
                    R.string.server -> {
                        toggleServer()
                    }
                    else -> {
                        navigateToPanel(panelTile.getPanel())
                    }
                }
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

        tiles.add(SpannedTile.PanelTile(R.string.server, R.drawable.ic_wifi_16dp))

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

        tiles.add(SpannedTile.PanelTile(R.string.preferences, R.drawable.ic_settings_16dp))

        return tiles
    }

    private fun animateTilesTo(randomScale: Boolean, imageView: ImageView) {
        tileAnimators.values.forEach { (xAnim, yAnim) ->
            xAnim.cancel()
            yAnim.cancel()
        }

        binding.recyclerView.forEachViewHolderIndexed<AdapterSpannedHomeTiles.SongHolder> { holder, position ->
            if (holder.binding.art != imageView) {
                val targetScale = if (randomScale) Random.nextDouble(0.6, 0.9).toFloat() else 1f

                // Apply the spring animation to the item view
                holder.itemView.applySpringScale(targetScale)
            }
        }
    }

    // Extension function to handle the boilerplate of X and Y springs
    private fun View.applySpringScale(targetScale: Float) {
        // Animate Scale X
        val xAnimator = SpringAnimation(this, DynamicAnimation.SCALE_X, targetScale).apply {
            spring.stiffness = TILE_STIFFNESS
            spring.dampingRatio = TILE_DAMPING_RATIO
            start()
        }

        // Animate Scale Y
        val yAnimator = SpringAnimation(this, DynamicAnimation.SCALE_Y, targetScale).apply {
            spring.stiffness = TILE_STIFFNESS
            spring.dampingRatio = TILE_DAMPING_RATIO
            start()
        }

        tileAnimators[this.id] = Pair(xAnimator, yAnimator)
    }

    override fun onDestroyView() {
        tilesAdapter = null
        tileAnimators.values.forEach { (xAnim, yAnim) ->
            xAnim.cancel()
            yAnim.cancel()
        }
        tileAnimators.clear()
        super.onDestroyView()
    }

    companion object {
        /**
         * Creates a new instance of [TiledHome].
         *
         * @return A freshly instantiated [TiledHome] ready to show tiles.
         */
        fun newInstance(): TiledHome {
            val args = Bundle()
            val fragment = TiledHome()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "TiledHome"

        /** Portrait column count — 3 columns matches the classic Windows Phone tile layout. */
        private const val SPAN_COUNT_PORTRAIT = 3

        /**
         * Landscape column count — 6 columns so big tiles still take up the same
         * proportional space (2 out of 6 = 1 out of 3) as they do in portrait.
         */
        private const val SPAN_COUNT_LANDSCAPE = 6

        private const val TILE_STIFFNESS = 150F
        private const val TILE_DAMPING_RATIO = 1F
    }
}