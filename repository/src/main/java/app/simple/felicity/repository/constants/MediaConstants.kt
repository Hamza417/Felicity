package app.simple.felicity.repository.constants

import app.simple.felicity.repository.constants.MediaConstants.PLAYBACK_PAUSED
import app.simple.felicity.repository.constants.MediaConstants.PLAYBACK_PLAYING

object MediaConstants {
    const val PLAYBACK_PAUSED = 0
    const val PLAYBACK_PLAYING = 1
    const val PLAYBACK_STOPPED = 2
    const val PLAYBACK_BUFFERING = 3
    const val PLAYBACK_ENDED = 4
    const val PLAYBACK_ERROR = 5

    /**
     * Emitted once when ExoPlayer transitions to [Player.STATE_READY], immediately before
     * [PLAYBACK_PLAYING] or [PLAYBACK_PAUSED]. Observers that need to react the moment the
     * decoder is ready (e.g., waveform pre-loading) should listen for this state rather than
     * waiting for the first [PLAYBACK_PLAYING] event.
     */
    const val PLAYBACK_READY = 6

    // Repeat modes
    const val REPEAT_OFF = 0
    const val REPEAT_QUEUE = 1
    const val REPEAT_ONE = 2
}