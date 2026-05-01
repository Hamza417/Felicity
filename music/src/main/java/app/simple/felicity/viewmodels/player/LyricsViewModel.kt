package app.simple.felicity.viewmodels.player

import android.app.Application
import app.simple.felicity.decorations.lrc.model.LrcData
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.managers.LyricsLoadingStatus
import app.simple.felicity.managers.LyricsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * A lightweight ViewModel that exposes lyrics data from [LyricsManager] to the UI.
 *
 * All the heavy lifting — loading LRC files, fetching from the network, persisting sync
 * adjustments — now lives in [LyricsManager], which is a singleton shared across every
 * screen. This means the Lyrics panel and the player screen always see the same data,
 * even when the song changes while one of them is in the back stack.
 *
 * Think of this ViewModel as a window into the manager rather than the manager itself.
 *
 * @author Hamza417
 */
@HiltViewModel
class LyricsViewModel @Inject constructor(
        application: Application,
        private val lyricsManager: LyricsManager
) : WrappedViewModel(application) {

    /**
     * The parsed lyrics for the currently playing song.
     * `null` while loading; an empty [LrcData] when no lyrics are available.
     * Collect this in the UI — it updates automatically on every song change.
     */
    val lrcData: StateFlow<LrcData?> = lyricsManager.lrcData

    /**
     * The current sync offset (ms) to add to the playback position before passing it
     * to the lyrics view. Collect this alongside [lrcData].
     */
    val syncOffsetMs: StateFlow<Long> = lyricsManager.syncOffsetMs

    /**
     * Tells the UI what the lyrics loader is doing right now — searching the internet,
     * downloading, or just sitting quietly idle. Use this to update the lrc view's empty
     * text so users aren't left wondering why nothing is showing up.
     */
    val loadingStatus: StateFlow<LyricsLoadingStatus> = lyricsManager.loadingStatus

    /**
     * The accumulated sync offset as a plain Long so the seek listener on the view
     * can read it without needing a coroutine collector.
     */
    val syncOffset: Long get() = lyricsManager.syncOffset

    /** Asks the manager to load lyrics for the current song (skipped if already loaded). */
    fun loadLrcData() = lyricsManager.loadLrcData()

    /** Forces a fresh load even when the same song is already showing. */
    fun reloadLrcData() = lyricsManager.reloadLrcData()

    /**
     * Shifts the lyrics highlight by [deltaMs] milliseconds.
     * Positive values fix lagging lyrics; negative values fix lyrics that run ahead.
     */
    fun seekBy(deltaMs: Long) = lyricsManager.seekBy(deltaMs)

    /** Deletes the .lrc file for the current song. Calls [onSuccess] after deletion. */
    fun deleteLrc(onSuccess: (() -> Unit)? = null) = lyricsManager.deleteLrc(onSuccess)
}
