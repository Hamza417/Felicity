package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.R
import app.simple.felicity.preferences.UserInterfacePreferences
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.repository.repositories.SongStatRepository
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel.Companion.Panel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for the Dashboard home screen.
 *
 * Provides data for the recommended grid section, the recently played section,
 * the recently added songs section, the favorites section, the top artists section,
 * the top albums section, and the fixed lists of panel navigation items displayed
 * in the browse grid.
 *
 * @author Hamza417
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
        application: Application,
        audioRepository: AudioRepository,
        songStatRepository: SongStatRepository
) : BaseHomeViewModel(application, audioRepository) {

    /** Recently played songs flow ordered by last-played timestamp descending. */
    val recentlyPlayed: StateFlow<List<Audio>> = songStatRepository.getRecentlyPlayed()
        .catch { exception ->
            Log.e(TAG, "Error loading recently played songs", exception)
            emit(emptyList())
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Recently added songs flow, ordered by date added descending. */
    val recentlyAdded: StateFlow<List<Audio>> = audioRepository.getRecentAudio()
        .catch { exception ->
            Log.e(TAG, "Error loading recently added songs", exception)
            emit(mutableListOf())
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Favorite songs flow. */
    val favorites: StateFlow<List<Audio>> = audioRepository.getFavoriteAudio()
        .catch { exception ->
            Log.e(TAG, "Error loading favorites", exception)
            emit(emptyList())
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val mostPlayedFlow = songStatRepository.getMostPlayed()
        .catch { e -> Log.e(TAG, "Error loading most played", e); emit(emptyList()) }
        .flowOn(Dispatchers.IO)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    /**
     * The top 10 artists ranked by how often their songs have been played.
     * If nobody has played anything yet, this will be empty — get listening!
     */
    val topArtists: StateFlow<List<Artist>> = combine(mostPlayedFlow, allAudioFlow) { mostPlayed, allAudio ->
        computeTopArtists(mostPlayed, allAudio)
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The top 10 albums ranked by how often their songs have been played.
     * Your most-loved albums, right there on the dashboard.
     */
    val topAlbums: StateFlow<List<Album>> = combine(mostPlayedFlow, allAudioFlow) { mostPlayed, allAudio ->
        computeTopAlbums(mostPlayed, allAudio)
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Builds the list of panel navigation icons that should actually appear in the browse grid,
     * respecting the user's visibility preferences. Songs, Albums, and Artists are sacred — they
     * always show up no matter what. Everything else is opt-out.
     *
     * Call this every time you set up (or refresh) the panels grid so the list stays in sync
     * with the latest preferences.
     */
    fun getVisiblePanelPanels(): List<Panel> {
        val panels = mutableListOf<Panel>()

        // These three are always present — the holy trinity of the browse grid.
        panels.add(Panel(R.string.songs, R.drawable.ic_song_16dp))
        panels.add(Panel(R.string.albums, R.drawable.ic_album_16dp))
        panels.add(Panel(R.string.artists, R.drawable.ic_people_16dp))

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_ALBUM_ARTISTS))
            panels.add(Panel(R.string.album_artists, R.drawable.ic_artist_16dp))

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_COMPOSERS))
            panels.add(Panel(R.string.composers, R.drawable.ic_composer_16dp))

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_GENRES))
            panels.add(Panel(R.string.genres, R.drawable.ic_piano_16dp))

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_YEAR))
            panels.add(Panel(R.string.year, R.drawable.ic_date_range_16dp))

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_PLAYLISTS))
            panels.add(Panel(R.string.playlists, R.drawable.ic_list_16dp))

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_PLAYING_QUEUE))
            panels.add(Panel(R.string.playing_queue, R.drawable.ic_queue_16dp))

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_RECENTLY_ADDED))
            panels.add(Panel(R.string.recently_added, R.drawable.ic_recently_added_16dp))

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_RECENTLY_PLAYED))
            panels.add(Panel(R.string.recently_played, R.drawable.ic_history_16dp))

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_MOST_PLAYED))
            panels.add(Panel(R.string.most_played, R.drawable.ic_equalizer_16dp))

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_BOOKMARKS))
            panels.add(Panel(R.string.bookmarks, R.drawable.ic_bookmark_16dp))

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_FAVORITES)) {
            panels.add(
                    if (UserInterfacePreferences.isLikeIconInsteadOfThumb()) {
                        Panel(R.string.favorites, R.drawable.ic_thumb_up_16dp)
                    } else {
                        Panel(R.string.favorites, R.drawable.ic_favorite_filled_16dp)
                    }
            )
        }

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_FOLDERS))
            panels.add(Panel(R.string.folders, R.drawable.ic_folder_16dp))

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_FOLDERS_HIERARCHY))
            panels.add(Panel(R.string.folders_hierarchy, R.drawable.ic_tree_16dp))

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_VISIBLE_ALWAYS_SKIPPED))
            panels.add(Panel(R.string.always_skipped, R.drawable.ic_skip_16dp))

        if (UserInterfacePreferences.isPanelVisible(UserInterfacePreferences.PANEL_MOST_SKIPPED))
            panels.add(Panel(R.string.most_skipped, R.drawable.ic_skip_16dp))

        return panels
    }

    /**
     * Computes the top 10 artists by grouping most-played songs by artist
     * and counting how many times each artist appears.
     *
     * @param mostPlayed The ordered list of most-played [Audio] objects.
     * @param allAudio The full library snapshot used to build complete [Artist] records.
     * @return Up to 10 [Artist] objects sorted by play frequency.
     */
    private fun computeTopArtists(mostPlayed: List<Audio>, allAudio: List<Audio>): List<Artist> {
        // Count how many most-played entries each artist has — that's their score.
        val artistPlayCounts = mostPlayed
            .mapNotNull { it.artist }
            .groupingBy { it }
            .eachCount()

        return artistPlayCounts.entries
            .sortedByDescending { it.value }
            .take(TOP_LIST_COUNT)
            .mapNotNull { (artistName, _) ->
                val artistSongs = allAudio.filter {
                    it.artist?.equals(artistName, ignoreCase = true) == true
                }
                if (artistSongs.isEmpty()) return@mapNotNull null
                Artist(
                        id = artistName.hashCode().toLong(),
                        name = artistName,
                        albumCount = artistSongs.mapNotNull { it.album }.distinct().size,
                        trackCount = artistSongs.size,
                        songPaths = artistSongs.map { it.uri }
                )
            }
    }

    /**
     * Computes the top 10 albums by grouping most-played songs by album name.
     *
     * @param mostPlayed The ordered list of most-played [Audio] objects.
     * @param allAudio The full library snapshot used to build complete [Album] records.
     * @return Up to 10 [Album] objects sorted by play frequency.
     */
    private fun computeTopAlbums(mostPlayed: List<Audio>, allAudio: List<Audio>): List<Album> {
        val albumPlayCounts = mostPlayed
            .mapNotNull { it.album }
            .groupingBy { it }
            .eachCount()

        return albumPlayCounts.entries
            .sortedByDescending { it.value }
            .take(TOP_LIST_COUNT)
            .mapNotNull { (albumName, _) ->
                val albumSongs = allAudio.filter {
                    it.album?.equals(albumName, ignoreCase = true) == true
                }
                if (albumSongs.isEmpty()) return@mapNotNull null
                val firstSong = albumSongs.first()
                Album(
                        id = albumName.hashCode().toLong(),
                        name = albumName,
                        artist = firstSong.artist,
                        artistId = firstSong.artist?.hashCode()?.toLong() ?: 0L,
                        songCount = albumSongs.size,
                        songPaths = albumSongs.map { it.uri }
                )
            }
    }

    companion object {
        private const val TAG = "DashboardViewModel"
        private const val TOP_LIST_COUNT = 10
    }
}
