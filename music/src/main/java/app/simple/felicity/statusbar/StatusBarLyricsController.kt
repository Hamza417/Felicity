package app.simple.felicity.statusbar

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tiny process-wide flag that tells the status bar lyrics overlay whether a full
 * player screen is currently on screen.
 *
 * The player fragments own this flag through their lifecycle, and the overlay
 * service reads it to honour the "hide on player screen" preference without
 * having to know anything about fragments or navigation.
 *
 * @author Hamza417
 */
object StatusBarLyricsController {

    private val _playerScreenVisible = MutableStateFlow(false)

    /** `true` while a [app.simple.felicity.extensions.fragments.BasePlayerFragment] is started. */
    val playerScreenVisible: StateFlow<Boolean> = _playerScreenVisible.asStateFlow()

    fun setPlayerScreenVisible(visible: Boolean) {
        _playerScreenVisible.value = visible
    }

    // Both the full lyrics panel and the modal lyrics dialog can be on screen (and
    // they can be stacked). Count them so that stopping one never clears the flag
    // while the other is still visible.
    private var lyricsScreenCount = 0

    private val _lyricsScreenVisible = MutableStateFlow(false)

    /** `true` while any lyrics screen (full panel or modal dialog) is started. */
    val lyricsScreenVisible: StateFlow<Boolean> = _lyricsScreenVisible.asStateFlow()

    @Synchronized
    fun setLyricsScreenVisible(visible: Boolean) {
        lyricsScreenCount = (lyricsScreenCount + if (visible) 1 else -1).coerceAtLeast(0)
        _lyricsScreenVisible.value = lyricsScreenCount > 0
    }
}
