package app.simple.felicity.managers

import android.util.Log
import app.simple.felicity.engine.managers.MediaPlaybackManager
import app.simple.felicity.repository.models.AudioBookmark
import app.simple.felicity.repository.repositories.BookmarkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The central home for all bookmark data related to the currently playing song.
 *
 * Any screen that cares about bookmarks — the player, a dedicated bookmarks panel,
 * etc. — simply collects [bookmarks] and always sees the live list without doing
 * any loading on their own.
 *
 * The manager watches [MediaPlaybackManager.songPositionFlow] so it reloads
 * automatically whenever the song changes, just like [LyricsManager] does for lyrics.
 *
 * @author Hamza417
 */
@Singleton
class BookmarksManager @Inject constructor(
        private val bookmarkRepository: BookmarkRepository
) {

    private val TAG = "BookmarksManager"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _bookmarks = MutableStateFlow<List<AudioBookmark>>(emptyList())

    /**
     * The live list of bookmarks for the currently playing song, sorted by timestamp.
     * Emits an empty list when there is no song playing or when the song has no bookmarks.
     */
    val bookmarks: StateFlow<List<AudioBookmark>> = _bookmarks.asStateFlow()

    /**
     * The audio hash of the song whose bookmarks are currently loaded.
     * Used to guard against stale results arriving after a song change.
     */
    @Volatile
    private var currentHash: Long? = null

    /** Active flow-collection job; cancelled and replaced on each song change. */
    private var observeJob: Job? = null

    init {
        // Every time the queue position advances, reload bookmarks for the new song.
        scope.launch {
            MediaPlaybackManager.songPositionFlow.collect {
                loadBookmarks()
            }
        }
    }

    /**
     * Starts (or restarts) a live collection of bookmarks for the currently playing song.
     * The previous song's flow is canceled first so there is never more than one
     * active database observer.
     */
    fun loadBookmarks() {
        val currentSong = MediaPlaybackManager.getCurrentSong()

        if (currentSong == null) {
            currentHash = null
            _bookmarks.value = emptyList()
            return
        }

        val hash = currentSong.hash

        // Already watching this song — nothing to do.
        if (hash == currentHash && observeJob?.isActive == true) {
            Log.d(TAG, "loadBookmarks() skipped – already watching hash $hash.")
            return
        }

        currentHash = hash
        observeJob?.cancel()

        observeJob = scope.launch(Dispatchers.IO) {
            Log.d(TAG, "Observing bookmarks for hash $hash (${currentSong.title}).")
            bookmarkRepository.getBookmarksFlow(hash).collect { list ->
                if (hash == currentHash) {
                    _bookmarks.value = list
                    Log.d(TAG, "Bookmarks updated: ${list.size} entries for hash $hash.")
                }
            }
        }
    }

    /**
     * Adds a bookmark at [timestampMs] for the currently playing song.
     *
     * The call is silently ignored when:
     *  - No song is currently playing.
     *  - Another bookmark already sits within 1 000 ms of [timestampMs].
     *
     * @param timestampMs playback position in milliseconds where the bookmark should be placed
     * @return `true` if the bookmark was saved, `false` if it was rejected
     */
    suspend fun addBookmark(timestampMs: Long): Boolean {
        val hash = currentHash ?: return false
        val added = bookmarkRepository.addBookmark(hash, timestampMs)
        if (!added) {
            Log.d(TAG, "Bookmark at ${timestampMs}ms rejected — too close to an existing one.")
        }
        return added
    }

    /**
     * Removes the bookmark closest to [timestampMs] (within 1 000 ms) for the
     * currently playing song. Does nothing when no such bookmark exists.
     *
     * @param timestampMs the approximate timestamp of the bookmark to remove
     */
    suspend fun removeBookmarkNear(timestampMs: Long) {
        val hash = currentHash ?: return
        bookmarkRepository.removeBookmarkNear(hash, timestampMs)
    }

    /**
     * Removes an exact bookmark row. Prefer [removeBookmarkNear] when you only
     * have a timestamp rather than the full model object.
     */
    suspend fun removeBookmark(bookmark: AudioBookmark) {
        bookmarkRepository.removeBookmark(bookmark)
    }

    /**
     * Deletes every bookmark for the currently playing song.
     */
    suspend fun clearBookmarks() {
        val hash = currentHash ?: return
        bookmarkRepository.clearBookmarks(hash)
    }

    /**
     * Returns a plain [Set] of bookmark timestamps (in ms) for the current song,
     * ready to hand straight to [app.simple.felicity.decorations.seekbars.WaveformSeekbar.bookmarks].
     */
    fun currentTimestamps(): Set<Long> {
        return _bookmarks.value.map { it.timestampMs }.toSet()
    }
}

