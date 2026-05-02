package app.simple.felicity.adapters.home.main

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.adapters.home.main.AdapterSpannedHomeTiles.Companion.BIG_TILE_POSITIONS
import app.simple.felicity.adapters.home.main.AdapterSpannedHomeTiles.Companion.SONG_TILE_COUNT
import app.simple.felicity.adapters.home.main.AdapterSpannedHomeTiles.Companion.TOTAL_TILE_COUNT
import app.simple.felicity.adapters.home.main.AdapterSpannedHomeTiles.Companion.buildTiles
import app.simple.felicity.databinding.AdapterGridImageBinding
import app.simple.felicity.databinding.AdapterSpannedPanelTileBinding
import app.simple.felicity.decorations.overscroll.VerticalListViewHolder
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCover
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel

/**
 * Flat single-level adapter that drives the Windows Phone-inspired tile grid on the Spanned Home screen.
 *
 * Every adapter position maps one-to-one to the [tiles] list — either a song cover tile or a
 * navigation panel tile. No nesting, no header, no inner RecyclerViews.
 *
 * Panel tiles live at fixed positions and never move, giving users reliable muscle memory.
 * Big hero tiles are guaranteed to always show song art — [buildTiles] explicitly blocks
 * panels from landing at those positions.
 *
 * @param tiles The ordered tile list produced by [buildTiles].
 * @author Hamza417
 */
class AdapterSpannedHomeTiles(
        private val tiles: MutableList<SpannedTile>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var callbacks: SpannedHomeTileCallbacks? = null

    override fun getItemCount(): Int = tiles.size

    override fun getItemViewType(position: Int): Int {
        return when (tiles[position]) {
            is SpannedTile.SongTile -> TYPE_SONG
            is SpannedTile.PanelTile -> TYPE_PANEL
        }
    }

    override fun getItemId(position: Int): Long {
        return when (val tile = tiles[position]) {
            is SpannedTile.SongTile -> tile.audio.id
            is SpannedTile.PanelTile -> tile.titleRes.toLong().inv()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_SONG -> SongHolder(
                    AdapterGridImageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            TYPE_PANEL -> PanelHolder(
                    AdapterSpannedPanelTileBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SongHolder -> bindSong(holder, tiles[position] as SpannedTile.SongTile)
            is PanelHolder -> bindPanel(holder, tiles[position] as SpannedTile.PanelTile)
        }
    }

    private fun bindSong(holder: SongHolder, tile: SpannedTile.SongTile) {
        holder.binding.art.loadArtCover(item = tile.audio, shadow = false, roundedCorners = false, darken = false)
        holder.binding.title.text = tile.audio.title
        holder.binding.container.setOnClickListener {
            val songs = tiles.filterIsInstance<SpannedTile.SongTile>().map { it.audio }
            val index = songs.indexOfFirst { it.id == tile.audio.id }.coerceAtLeast(0)
            callbacks?.onSongTileClicked(songs, index)
        }
        holder.binding.container.setOnLongClickListener {
            callbacks?.onSongTileLongClicked(tile.audio, holder.binding.art)
            true
        }
    }

    private fun bindPanel(holder: PanelHolder, tile: SpannedTile.PanelTile) {
        holder.binding.icon.setImageResource(tile.iconRes)
        holder.binding.title.setText(tile.titleRes)
        holder.binding.container.setOnClickListener {
            callbacks?.onPanelTileClicked(tile)
        }
    }

    /**
     * Sets the callbacks that handle all tile interactions.
     *
     * @param callbacks Your implementation of [SpannedHomeTileCallbacks].
     */
    fun setCallbacks(callbacks: SpannedHomeTileCallbacks) {
        this.callbacks = callbacks
    }

    inner class SongHolder(val binding: AdapterGridImageBinding) :
            VerticalListViewHolder(binding.root) {
        init {
            binding.title.setTextColor(Color.LTGRAY)
        }
    }

    inner class PanelHolder(val binding: AdapterSpannedPanelTileBinding) :
            VerticalListViewHolder(binding.root)

    /**
     * Represents a single tile in the Windows Phone-style grid.
     */
    sealed class SpannedTile {
        /**
         * A tile that shows a song's album art and title.
         *
         * @param audio The song this tile represents.
         */
        data class SongTile(val audio: Audio) : SpannedTile()

        /**
         * A tile that navigates to a library panel. Its adapter position is fixed
         * so users always find it in the same spot — no guessing required.
         *
         * @param titleRes String resource ID for the panel name.
         * @param iconRes  Drawable resource ID shown as the tile icon.
         */
        data class PanelTile(val titleRes: Int, val iconRes: Int) : SpannedTile() {
            /** Converts this tile into a Panel object for the home fragment's navigation router. */
            fun getPanel(): SimpleHomeViewModel.Companion.Panel {
                return SimpleHomeViewModel.Companion.Panel(titleRes, iconRes)
            }
        }
    }

    /**
     * All user interactions — song taps, long-presses, and panel taps.
     */
    interface SpannedHomeTileCallbacks {
        /**
         * Called when the user taps a song tile.
         *
         * @param songs    All songs currently visible in the grid.
         * @param position Index of the tapped song within that list.
         */
        fun onSongTileClicked(songs: List<Audio>, position: Int)

        /**
         * Called when the user long-presses a song tile.
         *
         * @param audio     The song that was long-pressed.
         * @param imageView The album art view, useful as a shared-element source.
         */
        fun onSongTileLongClicked(audio: Audio, imageView: android.widget.ImageView)

        /**
         * Called when the user taps a panel navigation tile.
         *
         * @param panelTile The panel tile that was tapped.
         */
        fun onPanelTileClicked(panelTile: SpannedTile.PanelTile)
    }

    companion object {
        private const val TYPE_SONG = 0
        private const val TYPE_PANEL = 1

        /** Total number of tiles in the grid (songs + panel slots). */
        const val TOTAL_TILE_COUNT = 45

        /**
         * Fixed tile indices reserved for panel navigation tiles — every third position
         * starting at 2, giving 15 slots total. None of these overlap with [BIG_TILE_POSITIONS],
         * so big hero tiles are guaranteed to always show song art.
         */
        val PANEL_POSITIONS: Set<Int> = setOf(2, 5, 8, 11, 14, 17, 20, 23, 26, 29, 32, 35, 38, 41, 44)

        /**
         * Fixed tile indices that render as large 2-column hero tiles.
         * [buildTiles] explicitly prevents panels from landing here.
         */
        val BIG_TILE_POSITIONS: Set<Int> = setOf(0, 9, 19, 30, 42)

        /** How many song tiles exist in the full grid (total minus the 15 panel slots). */
        const val SONG_TILE_COUNT = TOTAL_TILE_COUNT - 15 // = 30

        /**
         * Assembles the ordered tile list used to seed the adapter.
         *
         * Big tile positions are always songs — panels are never allowed there.
         * Panel tiles fill the reserved slots in order; any leftover panel slots
         * (when fewer panels are enabled) quietly become song tiles instead.
         * Songs cycle through [songs] if the pool is smaller than the total needed.
         *
         * @param songs  Recommended song pool — aim for at least [SONG_TILE_COUNT].
         * @param panels All enabled [SpannedTile.PanelTile] items in display order.
         * @return A [MutableList] of exactly [TOTAL_TILE_COUNT] tiles.
         */
        fun buildTiles(
                songs: List<Audio>,
                panels: List<SpannedTile.PanelTile>
        ): MutableList<SpannedTile> {
            val result = ArrayList<SpannedTile>(TOTAL_TILE_COUNT)
            var songIndex = 0
            var panelIndex = 0

            for (pos in 0 until TOTAL_TILE_COUNT) {
                // Big tile positions are sacred — they always get song art, no exceptions.
                val canBePanel = pos in PANEL_POSITIONS && pos !in BIG_TILE_POSITIONS
                if (canBePanel && panelIndex < panels.size) {
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
