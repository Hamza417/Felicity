package app.simple.felicity.engine.managers

import app.simple.felicity.repository.models.normal.Audio

/**
 * The current state of the audio playback.
 *
 * @property playlist The list of songs in the current playlist.
 * @property playbackState The current playback state, such as playing, paused, stopped, etc
 * @param index The current position of the song in the playlist.
 */
data class AudioState(
        val playlist: MutableList<Audio> = arrayListOf(),
        val playbackState: PlaybackState = PlaybackState.IDLE,
        val index: Int = 0
)