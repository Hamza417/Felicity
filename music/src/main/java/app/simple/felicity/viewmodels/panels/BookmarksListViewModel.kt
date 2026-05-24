package app.simple.felicity.viewmodels.panels

import android.app.Application
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.models.AudioWithBookmarks
import app.simple.felicity.repository.repositories.BookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Bookmarks panel.
 *
 * Keeps a live [StateFlow] of every audio track that has at least one bookmark,
 * with each track's full bookmark list attached. The list updates automatically
 * whenever a bookmark is added or removed anywhere in the app.
 *
 * @author Hamza417
 */
@HiltViewModel
class BookmarksListViewModel @Inject constructor(
        application: Application,
        private val bookmarkRepository: BookmarkRepository
) : WrappedViewModel(application) {

    private val _bookmarks = MutableStateFlow<List<AudioWithBookmarks>>(emptyList())

    /**
     * Live list of all audio tracks that have at least one bookmark, sorted by title.
     * Each item pairs an [app.simple.felicity.repository.models.Audio] with its ordered
     * bookmark list so the adapter can render both levels without extra queries.
     */
    val bookmarks: StateFlow<List<AudioWithBookmarks>> = _bookmarks.asStateFlow()

    init {
        observe()
    }

    private fun observe() {
        viewModelScope.launch {
            bookmarkRepository.getAllWithAudio()
                .flowOn(Dispatchers.IO)
                .catch { /* silently ignore errors rather than crashing the panel */ }
                .collect { list -> _bookmarks.value = list }
        }
    }

    /** Removes all bookmarks for the track identified by [audioHash]. */
    fun clearBookmarks(audioHash: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            bookmarkRepository.clearBookmarks(audioHash)
        }
    }

    /** Removes a single bookmark entry. */
    fun deleteBookmark(bookmark: app.simple.felicity.repository.models.AudioBookmark) {
        viewModelScope.launch(Dispatchers.IO) {
            bookmarkRepository.removeBookmark(bookmark)
        }
    }
}

