package app.simple.felicity.adapters.home.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
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
     * panel tiles completely untouched.
     *
     * @param newSongs The new pool of songs.
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
     * Updates a single song tile at [position] with a new [Audio] item.
     * Triggers the RecyclerView's ItemAnimator to perform a live tile transition.
     *
     * @param position The adapter position of the song tile to update.
     * @param audio    The new [Audio] item to display.
     */
    fun updateSongAt(position: Int, audio: Audio) {
        if (position in tiles.indices && tiles[position] is SpannedTile.SongTile) {
            tiles[position] = SpannedTile.SongTile(audio)
            notifyItemChanged(position)
        }
    }

    /**
     * Returns all adapter positions that currently hold a [SpannedTile.SongTile].
     */
    fun getSongTilePositions(): List<Int> {
        return tiles.mapIndexedNotNull { index, tile ->
            if (tile is SpannedTile.SongTile) index else null
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
     * Callbacks for tile interactions.
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
         * @param panelTile The panel tile that was clicked.
         */
        fun onPanelTileClicked(panelTile: SpannedTile.PanelTile)
    }

    companion object {
        private const val TYPE_SONG = 0
        private const val TYPE_PANEL = 1

        /**
         * Adapter positions that render as 2x2 hero tiles in [app.simple.felicity.decorations.layoutmanager.spanned.SpannedGridLayoutManager].
         * Arranged in a mathematically perfect alternating rhythm (Left, Right, Left, Right) 
         * across rows so hero tiles are distributed evenly.
         */
        val BIG_TILE_POSITIONS: Set<Int> = setOf(0, 7, 12, 19, 24, 31, 36, 43, 48, 55, 60, 67, 72)

        /**
         * Adapter positions reserved for panel navigation tiles, interspersed
         * throughout the grid alongside song tiles in a zigzag column pattern.
         */
        val PANEL_POSITIONS: Set<Int> = setOf(1, 4, 8, 10, 14, 16, 20, 22, 26, 28, 32, 34, 38, 40, 44, 46, 50, 52, 56, 58, 62, 64, 68, 70, 74)

        /**
         * Total adapter item count across the grid.
         */
        const val TOTAL_TILE_COUNT = 74

        /**
         * Assembles the ordered tile list used to seed the adapter.
         *
         * Panel tiles occupy [PANEL_POSITIONS] in order, first enabled panel at the
         * first panel slot, second at the second, and so on. If you have fewer panels
         * than slots, leftover slots silently become song tiles.
         *
         * Songs fill every non-panel position, cycling through [songs] if the pool
         * is smaller than the total number of song positions needed.
         *
         * @param songs  Pool of [Audio] items.
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
                    result.add(panels[panelIndex++])
                } else if (songs.isNotEmpty()) {
                    result.add(SpannedTile.SongTile(songs[songIndex % songs.size]))
                    songIndex++
                }
            }

            return result
        }
    }
}

