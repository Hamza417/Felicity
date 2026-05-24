package app.simple.felicity.viewmodels.player

import android.app.Application
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.managers.BookmarksManager
import app.simple.felicity.repository.models.AudioBookmark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A thin ViewModel window into [BookmarksManager].
 *
 * The heavy lifting — loading from Room, enforcing the 1-second rule, cancelling
 * stale observers on song changes — all happens inside the singleton manager.
 * This class simply exposes the manager's flows and forwards mutating calls so
 * the UI doesn't need to hold a direct reference to the manager.
 *
 * @author Hamza417
 */
@HiltViewModel
class BookmarksViewModel @Inject constructor(
        application: Application,
        private val bookmarksManager: BookmarksManager
) : WrappedViewModel(application) {

    /**
     * The live list of bookmarks for the currently playing song.
     * Collect this in the player UI to keep the seekbar dots in sync with the database.
     */
    val bookmarks: StateFlow<List<AudioBookmark>> = bookmarksManager.bookmarks

    /**
     * A convenience snapshot of current bookmark timestamps as a [Set], ready to
     * pass directly to [app.simple.felicity.decorations.seekbars.WaveformSeekbar.bookmarks].
     */
    fun currentTimestamps(): Set<Long> = bookmarksManager.currentTimestamps()

    /**
     * Adds a bookmark at [timestampMs] for the currently playing song.
     * The call does nothing when another bookmark is already within 1 second of this position.
     *
     * @param timestampMs playback position in milliseconds
     * @param onResult optional callback that fires on the main thread with `true` when the
     *                 bookmark was saved and `false` when it was rejected
     */
    fun addBookmark(timestampMs: Long, onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val added = bookmarksManager.addBookmark(timestampMs)
            onResult?.invoke(added)
        }
    }

    /**
     * Removes the bookmark closest to [timestampMs] (within 1 second) for the current song.
     * Does nothing when no matching bookmark exists.
     */
    fun removeBookmarkNear(timestampMs: Long) {
        viewModelScope.launch {
            bookmarksManager.removeBookmarkNear(timestampMs)
        }
    }

    /**
     * Removes a specific bookmark by its database row object.
     * Prefer [removeBookmarkNear] when you only have a timestamp.
     */
    fun removeBookmark(bookmark: AudioBookmark) {
        viewModelScope.launch {
            bookmarksManager.removeBookmark(bookmark)
        }
    }

    /**
     * Deletes every bookmark for the currently playing song.
     */
    fun clearBookmarks() {
        viewModelScope.launch {
            bookmarksManager.clearBookmarks()
        }
    }

    /** Asks the manager to (re)load bookmarks for the current song. */
    fun loadBookmarks() = bookmarksManager.loadBookmarks()
}


