package app.simple.felicity.repository.managers

import android.animation.ValueAnimator
import android.util.Log
import androidx.annotation.MainThread
import androidx.core.net.toUri
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import app.simple.felicity.repository.constants.MediaConstants
import app.simple.felicity.repository.managers.MediaManager._songPositionFlow
import app.simple.felicity.repository.managers.MediaManager.moveQueueItemSilently
import app.simple.felicity.repository.managers.MediaManager.removeQueueItemSilently
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.ProcessUtils.ensureOnMainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

object MediaManager {

    private const val TAG = "MediaManager"

    // Single app-scoped Main dispatcher scope to avoid leaking ad-hoc scopes
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaController: MediaController? = null

    // Backing store for the queue provided by UI/db. Treat as read-only outside.
    private var songs: List<Audio> = emptyList()

    // When true the currentSongPosition setter will NOT emit _songPositionFlow.
    // Used during queue reorders so that moving the playing song's index does not
    // trigger onAudio() in every observer — the song itself hasn't changed.
    private var suppressPositionEmit: Boolean = false

    // Current queue index. Setter also emits to observers when valid and changed.
    private var currentSongPosition: Int = 0
        set(value) {
            if (value in songs.indices) {
                if (field != value) {
                    field = value
                    if (!suppressPositionEmit) {
                        scope.launch {
                            _songPositionFlow.emit(value)
                        }
                    }
                }
            } else {
                Log.i(TAG, "Invalid song position: $value. Must be between 0 and ${songs.size - 1}.")
            }
        }

    private var seekJob: Job? = null

    // Flows are mutable internally but exposed as read-only to callers.
    private val _songListFlow = MutableSharedFlow<List<Audio>>(replay = 1)
    private val _songPositionFlow = MutableSharedFlow<Int>(replay = 1)
    private val _songSeekPositionFlow = MutableSharedFlow<Long>(replay = 1)
    private val _playbackStateFlow = MutableSharedFlow<Int>(replay = 1)
    private val _repeatModeFlow = MutableSharedFlow<Int>(replay = 1)

    val songListFlow: SharedFlow<List<Audio>> = _songListFlow.asSharedFlow()
    val songPositionFlow: SharedFlow<Int> = _songPositionFlow.asSharedFlow()
    val songSeekPositionFlow: SharedFlow<Long> = _songSeekPositionFlow.asSharedFlow()
    val playbackStateFlow: SharedFlow<Int> = _playbackStateFlow.asSharedFlow()
    val repeatModeFlow: SharedFlow<Int> = _repeatModeFlow.asSharedFlow()

    fun setMediaController(controller: MediaController) {
        mediaController = controller
        // If controller is already playing when set, ensure seek updates are running
        if (controller.isPlaying) startSeekPositionUpdates() else stopSeekPositionUpdates()
    }

    fun clearMediaController() {
        stopSeekPositionUpdates()
        mediaController = null
    }

    /**
     * Provide a new queue to the controller and notify UI. Position is clamped to valid range.
     * Note: Emission order between list and position is not strictly guaranteed due to coroutines,
     * but replay=1 on both flows ensures UI will observe the latest of each.
     */
    fun setSongs(audios: List<Audio>, position: Int = 0, startPositionMs: Long = 0L) {
        Log.d(TAG, "setSongs called: count=${audios.size}, position=$position, startPositionMs=$startPositionMs")

        this.songs = audios
        val clampedPosition = if (audios.isEmpty()) 0 else position.coerceIn(0, audios.size - 1)
        // Directly update field to bypass the "no change" guard in the setter, then always emit
        // so observers are notified even when the index stays the same but the song list changed
        // (e.g. after shuffling, position 0 is a completely different song).
        currentSongPosition = clampedPosition

        // Line 83 inside setSongs
        if (audios.isNotEmpty()) {
            // Always emit position so UI reflects the new queue's first song, and reset seek to 0
            scope.launch {
                _songPositionFlow.emit(clampedPosition)
                _songSeekPositionFlow.emit(startPositionMs)
            }

            // Move heavy mapping to background thread
            scope.launch {
                val mediaItems = withContext(Dispatchers.Default) {
                    audios.map { audio ->
                        val uri = File(audio.path).toUri()
                        MediaItem.Builder()
                            .setMediaId(audio.id.toString())
                            .setUri(uri)
                            .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setArtist(audio.artist)
                                        .setTitle(audio.title)
                                        .build()
                            )
                            .build()
                    }
                }

                // Back on Main Thread to set items
                if (mediaController != null) {
                    mediaController?.setMediaItems(mediaItems, currentSongPosition, startPositionMs)
                    mediaController?.prepare()
                    // mediaController?.play() // Auto-play when setting new list?
                }
                startSeekPositionUpdates()
            }
        } else {
            // Clear controller playlist if applicable and keep UI state consistent
            mediaController?.clearMediaItems()
            mediaController?.stop()
            stopSeekPositionUpdates()
            scope.launch { _songPositionFlow.emit(0) }
        }

        scope.launch {
            _songListFlow.emit(this@MediaManager.songs)
        }
    }

    fun getSongs(): List<Audio> = songs

    fun getCurrentSong(): Audio? = songs.getOrNull(currentSongPosition)

    /**
     * Returns true if the given list has the exact same song IDs in the same order as the current queue.
     */
    fun isSameQueue(audios: List<Audio>): Boolean {
        if (audios.size != songs.size) return false
        return audios.indices.all { audios[it].id == songs[it].id }
    }

    /**
     * Updates the internal song list and emits the new list to observers WITHOUT touching
     * the media controller. Kept for compatibility; prefer [moveQueueItemSilently] or
     * [removeQueueItemSilently] for drag/swipe gestures so the ExoPlayer queue is also
     * updated surgically without any decoder reset.
     */
    fun updateQueueSilently(audios: List<Audio>, newPosition: Int) {
        this.songs = audios
        val clampedPosition = if (audios.isEmpty()) 0 else newPosition.coerceIn(0, audios.size - 1)
        currentSongPosition = clampedPosition
        scope.launch {
            _songListFlow.emit(this@MediaManager.songs)
        }
    }

    /**
     * Moves a single media item in the ExoPlayer queue from [fromIndex] to [toIndex] without
     * interrupting playback. Also updates the internal song list to stay in sync.
     * Safe to call for drag-reorder gestures — the decoder is never reset.
     *
     * Does NOT emit [_songPositionFlow]. A queue reorder means the same song is still playing —
     * just at a different index. Emitting songPositionFlow would trigger onAudio() in every
     * observer which re-highlights the wrong adapter position while a drag is in progress.
     */
    fun moveQueueItemSilently(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        if (fromIndex !in songs.indices || toIndex !in songs.indices) {
            Log.w(TAG, "moveQueueItemSilently: invalid indices from=$fromIndex to=$toIndex (size=${songs.size})")
            return
        }

        // Capture the currently playing song BEFORE mutating the list
        val currentSong = getCurrentSong()

        // Update internal list
        val newList = songs.toMutableList()
        val moved = newList.removeAt(fromIndex)
        newList.add(toIndex, moved)
        this.songs = newList

        // Re-derive where the playing song ended up, suppressing the position flow emission —
        // the song itself hasn't changed, only its index in the queue.
        val newCurrentPosition = currentSong
            ?.let { cs -> this.songs.indexOfFirst { it.id == cs.id } }
            ?.coerceAtLeast(0) ?: currentSongPosition
        suppressPositionEmit = true
        currentSongPosition = newCurrentPosition
        suppressPositionEmit = false

        // Surgically move item in ExoPlayer — no setMediaItems, no prepare, no gap
        mediaController?.moveMediaItem(fromIndex, toIndex)

        scope.launch {
            _songListFlow.emit(this@MediaManager.songs)
        }
    }

    /**
     * Removes the item at [index] from the ExoPlayer queue without interrupting playback.
     * Also updates the internal song list. Safe to call for swipe-to-remove gestures.
     * When the currently playing song is removed, ExoPlayer auto-advances to the next item;
     * we ensure internal state and UI position are kept in sync.
     */
    fun removeQueueItemSilently(index: Int) {
        if (index !in songs.indices) {
            Log.w(TAG, "removeQueueItemSilently: invalid index=$index (size=${songs.size})")
            return
        }

        val removedSong = songs[index]
        val currentSong = getCurrentSong()
        val wasPlayingRemovedSong = currentSong?.id == removedSong.id
        val newList = songs.toMutableList()
        newList.removeAt(index)
        this.songs = newList

        // Figure out where the currently playing song lands after removal
        val newCurrentPosition = if (wasPlayingRemovedSong) {
            // The playing song itself was removed — clamp to valid range
            index.coerceAtMost((newList.size - 1).coerceAtLeast(0))
        } else {
            currentSong?.let { cs -> newList.indexOfFirst { it.id == cs.id } }
                ?.coerceAtLeast(0) ?: currentSongPosition
        }
        // Update internal position before ExoPlayer removal so listeners see correct state
        currentSongPosition = newCurrentPosition

        // Surgically remove item in ExoPlayer — no setMediaItems, no prepare, no gap.
        // ExoPlayer will automatically advance to the next item when the current item is removed.
        mediaController?.removeMediaItem(index)

        if (wasPlayingRemovedSong) {
            scope.launch {
                if (newList.isEmpty()) {
                    // Queue is now empty — stop playback
                    mediaController?.stop()
                    stopSeekPositionUpdates()
                    _playbackStateFlow.emit(MediaConstants.PLAYBACK_STOPPED)
                    _songSeekPositionFlow.emit(0L)
                } else {
                    // ExoPlayer auto-advances; force seek to confirm correct item and ensure
                    // playback continues even if we were in a buffering state.
                    mediaController?.seekTo(newCurrentPosition, 0L)
                    mediaController?.play()
                }
                _songPositionFlow.emit(newCurrentPosition)
            }
        }

        scope.launch {
            _songListFlow.emit(this@MediaManager.songs)
        }
    }

    // Line 133
    fun playCurrent() {
        // Only seek if we are NOT at the correct index already
        if (mediaController?.currentMediaItemIndex != currentSongPosition) {
            mediaController?.seekTo(currentSongPosition, 0L)
        }
        // Just play. If we are already there, this resumes perfectly.
        mediaController?.play()
        startSeekPositionUpdates()
    }

    fun playSong(audio: Audio) {
        // Prefer matching by stable id to avoid issues with data class equality or different instances
        val index = songs.indexOfFirst { it.id == audio.id }
        if (index != -1) {
            currentSongPosition = index
            playCurrent()
        } else {
            Log.w(TAG, "playSong: Audio not found in current list: ${audio.id}")
        }
    }

    fun pause() {
        mediaController?.pause()
        // Stop seek updates when paused to reduce unnecessary processing
        // UI will get the final position from the playback state change
        stopSeekPositionUpdates()
    }

    fun play() {
        if (mediaController == null) {
            Log.w(TAG, "play() called but mediaController is null")
            return
        }
        if (songs.isEmpty()) {
            Log.w(TAG, "play() called but songs list is empty")
            return
        }
        Log.d(TAG, "play() called: currentPosition=$currentSongPosition, mediaItemCount=${mediaController?.mediaItemCount}")
        mediaController?.play()
        startSeekPositionUpdates()
    }

    fun stop() {
        mediaController?.stop()
        stopSeekPositionUpdates()
    }

    fun isPlaying(): Boolean {
        return mediaController?.isPlaying == true
    }

    fun flipState() {
        if (mediaController == null) {
            Log.w(TAG, "flipState() called but mediaController is null")
            return
        }
        if (songs.isEmpty()) {
            Log.w(TAG, "flipState() called but songs list is empty")
            return
        }
        Log.d(TAG, "flipState() called: isPlaying=${mediaController?.isPlaying}, mediaItemCount=${mediaController?.mediaItemCount}")
        if (mediaController?.isPlaying == true) {
            pause()
        } else {
            play()
        }
    }

    fun next() {
        if (mediaController?.hasNextMediaItem() == true) {
            mediaController?.seekToNextMediaItem()
            // Let ExoPlayer handle the transition naturally for gapless playback
            // Don't force position updates here
        }
    }

    fun previous() {
        if (mediaController?.hasPreviousMediaItem() == true) {
            mediaController?.seekToPreviousMediaItem()
            // Let ExoPlayer handle the transition naturally for gapless playback
            // Don't force position updates here
        }
    }

    @MainThread
    fun getSeekPosition(): Long {
        ensureOnMainThread {
            val position = mediaController?.currentPosition ?: 0L
            val duration = getDuration()
            // Clamp to [0, duration] when duration is known
            return if (duration > 0L) position.coerceIn(0L, duration) else max(0L, position)
        }
    }

    fun seekTo(position: Long) {
        val duration = getDuration()
        val clamped = if (duration > 0L) position.coerceIn(0L, duration) else max(0L, position)
        mediaController?.seekTo(clamped)
        // Optimistically emit the new seek position for responsive UI
        scope.launch { _songSeekPositionFlow.emit(clamped) }
    }

    fun getCurrentPosition(): Int {
        return currentSongPosition
    }

    fun getCurrentSongId(): Long? {
        return getCurrentSong()?.id
    }

    fun updatePosition(position: Int) {
        if (position != currentSongPosition) {
            if (position in songs.indices) {
                currentSongPosition = position
                // Let ExoPlayer handle the transition naturally for gapless playback
                // Only seek if the controller is not already on this track
                if (mediaController?.currentMediaItemIndex != position) {
                    mediaController?.seekTo(position, 0L)
                    // Only start playing if we were already playing, otherwise just prepare
                    if (mediaController?.isPlaying == true) {
                        startSeekPositionUpdates()
                    }
                }
            } else {
                Log.w(TAG, "Invalid song position: $position. Must be between 0 and ${songs.size - 1}.")
            }
        } else {
            Log.i(TAG, "updatePosition called with current position: $position. No action taken.")
        }
    }

    /**
     * Prefer controller duration when available; fallback to model.
     */
    fun getDuration(): Long {
        val controllerDuration = mediaController?.duration ?: C.TIME_UNSET
        return when {
            controllerDuration != C.TIME_UNSET && controllerDuration >= 0L -> controllerDuration
            else -> getCurrentSong()?.duration ?: 0L
        }
    }

    fun getSongAt(position: Int): Audio? {
        return if (position in songs.indices) {
            songs[position]
        } else {
            Log.w(TAG, "Invalid song position: $position. Must be between 0 and ${songs.size - 1}.")
            null
        }
    }

    fun notifyRepeatMode(repeatMode: Int) {
        scope.launch { _repeatModeFlow.emit(repeatMode) }
    }

    /**
     * Called by the service when the ExoPlayer signals STATE_ENDED (end of queue).
     * Applies the current repeat mode behaviour:
     *  - REPEAT_ONE / REPEAT_QUEUE: ExoPlayer handles natively, this is a no-op.
     *  - REPEAT_OFF: pause and seek back to the first song.
     */
    fun handleQueueEnded() {
        // For REPEAT_OFF, ExoPlayer has no repeat so STATE_ENDED means the queue truly finished.
        // Seek to position 0 and pause.
        mediaController?.seekTo(0, 0L)
        mediaController?.pause()
        currentSongPosition = 0
        scope.launch {
            _playbackStateFlow.emit(MediaConstants.PLAYBACK_PAUSED)
            _songPositionFlow.emit(0)
            _songSeekPositionFlow.emit(0L)
        }
        stopSeekPositionUpdates()
    }

    /**
     * Emit seek position periodically while playing to keep UI in sync.
     * Only runs when actually needed to avoid overhead.
     */
    fun startSeekPositionUpdates(intervalMs: Long = 200L) {
        // Don't start multiple jobs - check if already running
        if (seekJob?.isActive == true) {
            return
        }

        seekJob?.cancel()
        seekJob = scope.launch {
            var lastEmittedPosition: Long? = null
            while (isActive) {
                val position = getSeekPosition()
                // Only emit if position actually changed to reduce overhead
                if (position != lastEmittedPosition) {
                    _songSeekPositionFlow.emit(position)
                    lastEmittedPosition = position
                }
                delay(intervalMs)
            }
        }
    }

    fun stopSeekPositionUpdates() {
        seekJob?.cancel()
        seekJob = null
    }

    /**
     * Service can push controller state here; we also drive seek updates based on it.
     */
    fun notifyPlaybackState(state: Int) {
        scope.launch {
            _playbackStateFlow.emit(state)
        }
        // Keep seek updates running ONLY for PLAYING state
        // For paused/buffering, stop updates to avoid interfering with ExoPlayer
        when (state) {
            MediaConstants.PLAYBACK_PLAYING -> startSeekPositionUpdates()
            MediaConstants.PLAYBACK_PAUSED -> {
                stopSeekPositionUpdates()
                // Emit final position when paused
                scope.launch {
                    _songSeekPositionFlow.emit(getSeekPosition())
                }
            }
            MediaConstants.PLAYBACK_BUFFERING -> {
                // Don't stop during buffering, but also don't restart if not running
                // ExoPlayer is handling buffer state, we shouldn't interfere
            }
            MediaConstants.PLAYBACK_STOPPED,
            MediaConstants.PLAYBACK_ENDED,
            MediaConstants.PLAYBACK_ERROR -> stopSeekPositionUpdates()
            else -> {
                // No-op
            }
        }
    }

    /**
     * Appends [audio] to the end of the current queue. If the queue is empty, starts playing immediately.
     * The new item is added both to the internal list and to the MediaController.
     */
    fun addToQueue(audio: Audio) {
        val newList = songs.toMutableList()
        newList.add(audio)

        if (songs.isEmpty()) {
            // Queue was empty — start fresh
            setSongs(newList, 0)
        } else {
            songs = newList
            scope.launch {
                val uri = File(audio.path).toUri()
                val mediaItem = MediaItem.Builder()
                    .setMediaId(audio.id.toString())
                    .setUri(uri)
                    .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setArtist(audio.artist)
                                .setTitle(audio.title)
                                .build()
                    )
                    .build()
                mediaController?.addMediaItem(mediaItem)
            }
            scope.launch {
                _songListFlow.emit(songs)
            }
        }
    }

    /**
     * Inserts [audio] immediately after the currently playing song so it plays next.
     * If the song already exists in the queue, it is repositioned (no duplicate is added).
     * If the queue is empty, starts playing immediately.
     */
    fun playNext(audio: Audio) {
        val newList = songs.toMutableList()

        if (newList.isEmpty()) {
            setSongs(mutableListOf(audio), 0)
            return
        }

        val insertAt = (currentSongPosition + 1).coerceAtMost(newList.size)
        val existingIndex = newList.indexOfFirst { it.id == audio.id }

        if (existingIndex != -1) {
            // Song already in queue — move it to the desired position instead of duplicating
            if (existingIndex == currentSongPosition + 1) {
                // Already right after current song, nothing to do
                return
            }
            // When moving an item that comes before the insert point, the target index shifts by -1
            // because the removal happens first. moveQueueItemSilently handles this correctly.
            val targetIndex = if (existingIndex < insertAt) {
                (insertAt - 1).coerceAtMost((newList.size - 1).coerceAtLeast(0))
            } else {
                insertAt.coerceAtMost((newList.size - 1).coerceAtLeast(0))
            }
            moveQueueItemSilently(existingIndex, targetIndex)
        } else {
            newList.add(insertAt, audio)
            songs = newList

            scope.launch {
                val uri = File(audio.path).toUri()
                val mediaItem = MediaItem.Builder()
                    .setMediaId(audio.id.toString())
                    .setUri(uri)
                    .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setArtist(audio.artist)
                                .setTitle(audio.title)
                                .build()
                    )
                    .build()
                mediaController?.addMediaItem(insertAt, mediaItem)
            }
            scope.launch {
                _songListFlow.emit(songs)
            }
        }
    }

    // Notify UI about current media item index changes originating from the player/service without reconfiguring playback
    fun notifyCurrentPosition(position: Int) {
        if (position in songs.indices) {
            // Check if position actually changed to avoid unnecessary emissions
            if (currentSongPosition != position) {
                // Directly update without using setter to avoid potential feedback loops
                // But we need to emit manually since we're not using the setter
                currentSongPosition = position
                scope.launch { _songPositionFlow.emit(position) }
            }
        } else {
            Log.w(TAG, "notifyCurrentPosition: Invalid song position: $position. Must be between 0 and ${songs.size - 1}.")
        }
    }

    // Keep track of the animator so we can cancel it if the opposite action is triggered
    private var volumeAnimator: ValueAnimator? = null

    fun duck(durationMs: Long = 1000L) {
        fadeVolume(targetVolume = 0.2f, durationMs = durationMs)
    }

    fun unduck(durationMs: Long = 500L) {
        fadeVolume(targetVolume = 1.0f, durationMs = durationMs)
    }

    private fun fadeVolume(targetVolume: Float, durationMs: Long) {
        // Cancel any ongoing fade so they don't fight each other
        volumeAnimator?.cancel()

        // Assume current volume is 1.0f if we can't read it, though
        // ideally, your mediaController has a getter for the current volume.
        val currentVolume = mediaController?.volume ?: 1.0f

        volumeAnimator = ValueAnimator.ofFloat(currentVolume, targetVolume).apply {
            duration = durationMs
            interpolator = LinearOutSlowInInterpolator()
            addUpdateListener { animation ->
                mediaController?.volume = animation.animatedValue as Float
            }
            start()
        }
    }
}