package app.simple.felicity.adapters.home.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.adapters.home.main.AdapterSpannedHomeTiles.Companion.BIG_TILE_EVERY_N
import app.simple.felicity.adapters.home.main.AdapterSpannedHomeTiles.Companion.DESIRED_SONG_COUNT
import app.simple.felicity.adapters.home.main.AdapterSpannedHomeTiles.Companion.MAX_PANEL_COUNT
import app.simple.felicity.adapters.home.main.AdapterSpannedHomeTiles.Companion.PANEL_POSITIONS
import app.simple.felicity.adapters.home.main.AdapterSpannedHomeTiles.Companion.SONG_TILE_COUNT
import app.simple.felicity.adapters.home.main.AdapterSpannedHomeTiles.Companion.SPAN_COUNT
import app.simple.felicity.adapters.home.main.AdapterSpannedHomeTiles.Companion.TOTAL_TILE_COUNT
import app.simple.felicity.adapters.home.main.AdapterSpannedHomeTiles.Companion.buildLayoutSpec
import app.simple.felicity.adapters.home.main.AdapterSpannedHomeTiles.Companion.buildTiles
import app.simple.felicity.databinding.AdapterGridImageBinding
import app.simple.felicity.databinding.AdapterSpannedPanelTileBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCover
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel.Companion.Panel

/**
 * Flat single-level adapter that drives the Windows Phone-inspired tile grid on the Spanned Home screen.
 *
 * Every adapter position maps to exactly one [SpannedTile], either a song cover tile or a
 * navigation panel tile. There is no nesting, no inner RecyclerView, no surprises.
 * Panel tiles always live at the same adapter positions so users build reliable muscle memory
 * for where to tap. Song tiles can be swapped out for fresh art without disturbing panel spots.
 *
 * The tile list is pre-assembled by the fragment using [buildTiles] so this adapter stays
 * blissfully unaware of which songs are "recommended" or what the panel list looks like.
 *
 * @param tiles The initial ordered list of [SpannedTile] items to render.
 * @author Hamza417
 */
class AdapterSpannedHomeTiles(
        private val tiles: MutableList<SpannedTile>
) : RecyclerView.Adapter<VerticalListViewHolder>() {

    private var callbacks: SpannedHomeTileCallbacks? = null

    override fun getItemViewType(position: Int): Int {
        return when (tiles[position]) {
            is SpannedTile.SongTile -> TYPE_SONG
            is SpannedTile.PanelTile -> TYPE_PANEL
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalListViewHolder {
        return when (viewType) {
            TYPE_SONG -> SongHolder(
                    AdapterGridImageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            TYPE_PANEL -> PanelHolder(
                    AdapterSpannedPanelTileBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else -> throw IllegalArgumentException("Unknown tile type at viewType=$viewType")
        }
    }

    override fun onBindViewHolder(holder: VerticalListViewHolder, position: Int) {
        when (val tile = tiles[position]) {
            is SpannedTile.SongTile -> {
                val songHolder = holder as SongHolder
                songHolder.binding.art.loadArtCover(
                        item = tile.audio,
                        shadow = false,
                        roundedCorners = false,
                        darken = false)

                songHolder.binding.art.setOnClickListener {
                    // Pass only the song pool so the caller can queue everything from the grid.
                    val songs = tiles.filterIsInstance<SpannedTile.SongTile>().map { it.audio }
                    val songIndex = songs.indexOfFirst { it.id == tile.audio.id }.coerceAtLeast(0)
                    callbacks?.onSongTileClicked(songs, songIndex)
                }
                songHolder.binding.art.setOnLongClickListener {
                    callbacks?.onSongTileLongClicked(tile.audio, songHolder.binding.art)
                    true
                }
            }

            is SpannedTile.PanelTile -> {
                val panelHolder = holder as PanelHolder
                panelHolder.binding.icon.setImageResource(tile.iconRes)
                panelHolder.binding.title.setText(tile.titleRes)
                panelHolder.binding.container.setOnClickListener {
                    callbacks?.onPanelTileClicked(tile)
                }
            }
        }
    }

    override fun getItemCount(): Int = tiles.size

    override fun getItemId(position: Int): Long {
        return when (val tile = tiles[position]) {
            is SpannedTile.SongTile -> tile.audio.id
            // Panel tiles use a stable id derived from their title resource so they never animate.
            is SpannedTile.PanelTile -> tile.titleRes.toLong().inv()
        }
    }

    /**
     * Replaces only the song tiles with a fresh set of [Audio] items while leaving
     * panel tiles completely untouched. This is the safe way to "randomize" the grid
     * without accidentally moving panel tiles to a new address.
     *
     * @param newSongs The new pool of songs, must be exactly the same count as the
     *                 current song tiles or extra songs will be silently ignored.
     */
    fun updateSongs(newSongs: List<Audio>) {
        var songIndex = 0
        tiles.forEachIndexed { position, tile ->
            if (tile is SpannedTile.SongTile && songIndex < newSongs.size) {
                tiles[position] = SpannedTile.SongTile(newSongs[songIndex++])
                notifyItemChanged(position)
            }
        }
    }

    /**
     * Sets the callbacks that handle taps and long-presses on the tiles.
     *
     * @param callbacks Your implementation of [SpannedHomeTileCallbacks].
     */
    fun setCallbacks(callbacks: SpannedHomeTileCallbacks) {
        this.callbacks = callbacks
    }

    inner class SongHolder(val binding: AdapterGridImageBinding) :
            VerticalListViewHolder(binding.root)

    inner class PanelHolder(val binding: AdapterSpannedPanelTileBinding) :
            VerticalListViewHolder(binding.root)

    /**
     * Represents a single tile in the Windows Phone-style grid. Sealed so we can
     * switch on the type without worrying about forgotten branches.
     */
    sealed class SpannedTile {
        /**
         * A tile that shows a song's album art and title, and plays the song when tapped.
         *
         * @param audio The song this tile represents.
         */
        data class SongTile(val audio: Audio) : SpannedTile()

        /**
         * A tile that navigates to a library panel (Songs, Albums, Artists, etc.).
         * Its position in the adapter is fixed so users always find it in the same spot.
         *
         * @param titleRes String resource ID for the panel name (also the navigation key).
         * @param iconRes  Drawable resource ID shown as the tile's icon.
         */
        data class PanelTile(val titleRes: Int, val iconRes: Int) : SpannedTile() {
            fun getPanel(): Panel {
                return Panel(titleRes, iconRes)
            }
        }
    }

    /**
     * Callbacks for tile interactions. Keep it small, there are only two kinds of
     * things you can do in this grid: play a song or open a panel.
     */
    interface SpannedHomeTileCallbacks {
        /**
         * Called when the user taps a song tile.
         *
         * @param songs    The full list of songs currently visible in the grid.
         * @param position The index of the tapped song within that list.
         */
        fun onSongTileClicked(songs: List<Audio>, position: Int)

        /**
         * Called when the user long-presses a song tile.
         *
         * @param audio     The song that was long-pressed.
         * @param imageView The album art view, useful as a shared-element transition source.
         */
        fun onSongTileLongClicked(audio: Audio, imageView: android.widget.ImageView)

        /**
         * Called when the user taps a panel navigation tile.
         *
         * @param panelTile The string resource ID of the panel that should be opened.
         */
        fun onPanelTileClicked(panelTile: SpannedTile.PanelTile)
    }

    companion object {
        private const val TYPE_SONG = 0
        private const val TYPE_PANEL = 1

        /**
         * The only number you need to change to get more or fewer song tiles in the grid.
         * Everything else, hero positions, panel slots, total tile count, adjusts automatically.
         */
        const val DESIRED_SONG_COUNT = 30

        /**
         * How many panel navigation slots to reserve. Keep this at least as large as the
         * total number of panels the user can possibly enable. Extra slots become song tiles
         * when the user has fewer panels enabled than slots available.
         */
        const val MAX_PANEL_COUNT = 20

        /**
         * Roughly how many songs appear between consecutive big (2-span) hero tiles.
         * Lower = more hero tiles; higher = a more uniform grid of small tiles.
         */
        private const val BIG_TILE_EVERY_N = 6

        /**
         * Grid column count, only used inside [buildLayoutSpec] to lay out rows.
         * The fragment uses its own orientation-aware span constants for the actual
         * [androidx.recyclerview.widget.GridLayoutManager].
         */
        private const val SPAN_COUNT = 3

        /**
         * A snapshot of the computed grid layout. Calculated once on first access and
         * cached forever, no repeated math, no surprises.
         */
        private val LAYOUT: LayoutSpec by lazy { buildLayoutSpec() }

        /**
         * Adapter positions that render as big 2-span hero tiles. Computed from
         * [DESIRED_SONG_COUNT] and [BIG_TILE_EVERY_N], add more songs and new hero
         * positions appear automatically.
         */
        val BIG_TILE_POSITIONS: Set<Int> get() = LAYOUT.bigTilePositions

        /**
         * Adapter positions reserved for panel navigation tiles. Every big tile's
         * row-neighbor is guaranteed to be in this set so no dead vertical space appears
         * next to a hero tile. Remaining panel slots are spread evenly through the grid.
         */
        val PANEL_POSITIONS: Set<Int> get() = LAYOUT.panelPositions

        /**
         * Total adapter item count, songs plus all panel slots. Derived automatically,
         * so you never need to update this by hand.
         */
        val TOTAL_TILE_COUNT: Int get() = LAYOUT.totalCount

        /**
         * How many of the total tiles are song tiles (big + small combined).
         * This equals [DESIRED_SONG_COUNT] as long as there are enough non-panel rows.
         */
        val SONG_TILE_COUNT: Int get() = TOTAL_TILE_COUNT - PANEL_POSITIONS.size

        /**
         * Holds the output of [buildLayoutSpec] so it can be shared across all the
         * derived properties without recomputing the layout multiple times.
         *
         * @param bigTilePositions Adapter positions for 2-span hero tiles.
         * @param panelPositions   Adapter positions for panel navigation tiles.
         * @param totalCount       Total number of tiles in the adapter.
         */
        private data class LayoutSpec(
                val bigTilePositions: Set<Int>,
                val panelPositions: Set<Int>,
                val totalCount: Int
        )

        /**
         * Computes the full grid layout from scratch using only [DESIRED_SONG_COUNT],
         * [MAX_PANEL_COUNT], and [BIG_TILE_EVERY_N].
         *
         * The grid is divided into three kinds of rows, all exactly [SPAN_COUNT] columns wide:
         *
         *   "Hero row" , [big(2), panel(1)]:  1 song + 1 panel, uses 2 positions
         *   "Panel row", [song(1), panel(1), song(1)]:  2 songs + 1 panel, uses 3 positions
         *   "Song row" , [song(1), song(1), song(1)]:  3 songs, uses 3 positions
         *
         * Hero rows are placed at even intervals first, then panel rows fill the gaps,
         * and song rows take whatever is left. The result is a grid where every big tile
         * always has a panel tile as its immediate row-neighbor, no dead vertical space.
         */
        private fun buildLayoutSpec(): LayoutSpec {
            val bigTileCount = DESIRED_SONG_COUNT / BIG_TILE_EVERY_N
            // Clamp extra panels so we never run out of songs to fill their rows.
            val extraPanelCount = minOf(
                    MAX_PANEL_COUNT - bigTileCount,
                    (DESIRED_SONG_COUNT - bigTileCount) / 2
            ).coerceAtLeast(0)

            val songsInSongRows = DESIRED_SONG_COUNT - bigTileCount - 2 * extraPanelCount
            val songRowCount = (songsInSongRows + SPAN_COUNT - 1) / SPAN_COUNT  // ceiling
            val totalRows = bigTileCount + extraPanelCount + songRowCount

            // Start with all rows as song rows, then carve out hero and panel rows
            // using evenly spaced indices so the tile types don't all clump together.
            val rowTypes = Array(totalRows) { 'S' }

            // Distribute hero rows using integer scaling so spacing is perfectly even.
            for (i in 0 until bigTileCount) {
                rowTypes[i * totalRows / bigTileCount] = 'B'
            }

            // Distribute extra panel rows into the remaining 'S' slots.
            val panelStep = if (extraPanelCount > 0) totalRows.toFloat() / extraPanelCount else Float.MAX_VALUE
            for (i in 0 until extraPanelCount) {
                // Find the first 'S' slot at or after the ideal position.
                var idx = (i * panelStep).toInt()
                while (idx < totalRows && rowTypes[idx] != 'S') idx++
                if (idx < totalRows) rowTypes[idx] = 'P'
            }

            // Walk the row list and assign adapter positions.
            val bigPositions = mutableSetOf<Int>()
            val panelPositions = mutableSetOf<Int>()
            var pos = 0

            for (type in rowTypes) {
                when (type) {
                    'B' -> {
                        // Hero row: big tile first (2 spans), then its panel neighbor (1 span).
                        bigPositions.add(pos++)
                        panelPositions.add(pos++)
                    }
                    'P' -> {
                        // Panel row: song, panel, song, panel lands in the middle of the row.
                        pos++
                        panelPositions.add(pos++)
                        pos++
                    }
                    else -> {
                        // Pure song row, advance by the full column count.
                        pos += SPAN_COUNT
                    }
                }
            }

            return LayoutSpec(bigPositions, panelPositions, pos)
        }

        /**
         * Assembles the ordered tile list used to seed the adapter.
         *
         * Panel tiles occupy [PANEL_POSITIONS] in order, first enabled panel at the
         * first panel slot, second at the second, and so on. If you have fewer panels
         * than slots, leftover slots silently become song tiles. Extras beyond the slot
         * count are not shown (raise [MAX_PANEL_COUNT] to fix that).
         *
         * Songs fill every non-panel position, cycling through [songs] if the pool
         * is smaller than the total number of song positions needed.
         *
         * @param songs  Pool of [Audio] items, aim for at least [SONG_TILE_COUNT].
         * @param panels All enabled [SpannedTile.PanelTile] items in display order.
         * @return A ready-to-use [MutableList] of exactly [TOTAL_TILE_COUNT] tiles.
         */
        fun buildTiles(
                songs: List<Audio>,
                panels: List<SpannedTile.PanelTile>
        ): MutableList<SpannedTile> {
            val result = ArrayList<SpannedTile>(TOTAL_TILE_COUNT)
            var songIndex = 0
            var panelIndex = 0

            for (pos in 0 until TOTAL_TILE_COUNT) {
                val isReservedForPanel = pos in PANEL_POSITIONS
                if (isReservedForPanel && panelIndex < panels.size) {
                    // Reserved slot, and we still have a panel to place.
                    result.add(panels[panelIndex++])
                } else if (songs.isNotEmpty()) {
                    // Either a song slot, or a panel slot whose panel didn't show up.
                    result.add(SpannedTile.SongTile(songs[songIndex % songs.size]))
                    songIndex++
                }
            }

            return result
        }
    }
}

