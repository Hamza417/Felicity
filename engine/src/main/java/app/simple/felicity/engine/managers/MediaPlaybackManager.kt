package app.simple.felicity.engine.managers

import android.animation.ValueAnimator
import android.net.Uri
import android.util.Log
import androidx.annotation.MainThread
import androidx.core.net.toUri
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import app.simple.felicity.engine.managers.MediaPlaybackManager._songPositionFlow
import app.simple.felicity.engine.managers.MediaPlaybackManager.currentSongPosition
import app.simple.felicity.engine.managers.MediaPlaybackManager.mediaController
import app.simple.felicity.engine.managers.MediaPlaybackManager.moveQueueItemSilently
import app.simple.felicity.engine.managers.MediaPlaybackManager.next
import app.simple.felicity.engine.managers.MediaPlaybackManager.notifyCurrentPosition
import app.simple.felicity.engine.managers.MediaPlaybackManager.notifyPlaybackState
import app.simple.felicity.engine.managers.MediaPlaybackManager.pendingSeekPositions
import app.simple.felicity.engine.managers.MediaPlaybackManager.playNext
import app.simple.felicity.engine.managers.MediaPlaybackManager.previous
import app.simple.felicity.engine.managers.MediaPlaybackManager.removeQueueItemSilently
import app.simple.felicity.engine.managers.MediaPlaybackManager.setSongs
import app.simple.felicity.engine.managers.MediaPlaybackManager.updatePosition
import app.simple.felicity.repository.constants.MediaConstants
import app.simple.felicity.repository.listeners.MediaStateListener
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

/**
 * Converts an audio path string to a URI that ExoPlayer can open for playback.
 *
 * When the path is already a content:// URI (from a SAF-scanned file), we parse
 * it directly. Otherwise we wrap it as a file:// URI the old-fashioned way.
 */
private fun String.toPlaybackUri(): Uri {
    return if (this.startsWith("content://")) {
        Uri.parse(this)
    } else {
        File(this).toUri()
    }
}

// TODO - move to engine module
object MediaPlaybackManager {

    private const val TAG = "MediaPlaybackManager"


    // Single app-scoped Main dispatcher scope to avoid leaking ad-hoc scopes
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaController: MediaController? = null

    // Backing store for the queue provided by UI/db. Treat as read-only outside.
    private var songs: List<Audio> = emptyList()

    // When true the currentSongPosition setter will NOT emit _songPositionFlow.
    // Used during queue reorders so that moving the playing song's index does not
    // trigger onAudio() in every observer — the song itself hasn't changed.
    private var suppressPositionEmit: Boolean = false

    /**
     * Tracks positions for which a user-initiated [mediaController.seekTo] has been issued
     * but the corresponding [notifyCurrentPosition] callback has not yet arrived.
     *
     * When the user swipes songs rapidly, ExoPlayer fires [Player.Listener.onMediaItemTransition]
     * for every intermediate seek — including ones the user has already moved past.
     * By recording every seekTo target here and consuming entries in [notifyCurrentPosition],
     * stale callbacks that would otherwise revert [currentSongPosition] and fight the user
     * are silently discarded.
     *
     * All access happens on the main thread, so a plain [MutableSet] is safe.
     *
     * @author Hamza417
     */
    private val pendingSeekPositions = mutableSetOf<Int>()

    /**
     * Set to `true` for the entire window between [setSongs] being called and the new
     * media items actually being handed to the [MediaController].
     *
     * During that window the heavy song-to-[MediaItem] mapping runs on a background thread
     * while ExoPlayer still holds the old queue. Any [notifyCurrentPosition] callback that
     * arrives during this window reflects the old queue's state and must be discarded;
     * otherwise ExoPlayer's stale [Player.Listener.onMediaItemTransition] for the old
     * current index (e.g. position 10) would overwrite the freshly set [currentSongPosition]
     * and briefly flash the wrong song in the UI before the correct emit arrives.
     *
     * Reset to `false` immediately after [MediaController.setMediaItems] returns so that
     * the first real [notifyCurrentPosition] for the new queue is processed normally.
     *
     * All access is on the main thread.
     *
     * @author Hamza417
     */
    private var isQueueBeingReplaced: Boolean = false

    /**
     * The most recently emitted playback state constant from [notifyPlaybackState].
     * Initialized to [MediaConstants.PLAYBACK_STOPPED] so callers can safely query it
     * before the service has started.
     */
    private var lastKnownPlaybackState: Int = MediaConstants.PLAYBACK_STOPPED

    /**
     * Returns `true` when the player has reached [MediaConstants.PLAYBACK_READY],
     * [MediaConstants.PLAYBACK_PLAYING], or [MediaConstants.PLAYBACK_PAUSED] — i.e., whenever
     * the decoder has finished its initial buffering phase and it is safe to open the audio
     * file concurrently (for example for waveform extraction).
     */
    fun isPlayerReady(): Boolean {
        return lastKnownPlaybackState == MediaConstants.PLAYBACK_READY
                || lastKnownPlaybackState == MediaConstants.PLAYBACK_PLAYING
                || lastKnownPlaybackState == MediaConstants.PLAYBACK_PAUSED
    }

    private val listeners = mutableSetOf<MediaStateListener>()

    // Current queue index. Setter also emits to observers when valid and changed.
    private var currentSongPosition: Int = 0
        set(value) {
            if (value in songs.indices) {
                if (field != value) {
                    field = value
                    if (!suppressPositionEmit) {
                        scope.launch {
                            _songPositionFlow.emit(value)

                            /**
                             * Notify listeners on the main thread after the position flow emits, so that any
                             * MediaFragment observing the flow will have updated its current song before we call onAudioChange.
                             */
                            withContext(Dispatchers.Main) {
                                listeners.forEach { it.onAudioChange(getCurrentSong()) }
                            }
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

    /**
     * Indicates the direction of the most recent song transition.
     * `true` means forward (next song); `false` means backward (previous song).
     * Updated by [next], [previous], and [updatePosition].
     */
    var lastNavigationDirection: Boolean = true
        private set

    fun setMediaController(controller: MediaController) {
        mediaController = controller
        // If controller is already playing when set, ensure seek updates are running
        if (controller.isPlaying) startSeekPositionUpdates() else stopSeekPositionUpdates()
    }

    fun clearMediaController() {
        stopSeekPositionUpdates()
        pendingSeekPositions.clear()
        mediaController = null
    }

    /**
     * Provide a new queue to the controller and notify UI. Position is clamped to valid range.
     * Note: Emission order between list and position is not strictly guaranteed due to coroutines,
     * but replay=1 on both flows ensures UI will observe the latest of each.
     */
    fun setSongs(audios: List<Audio>, position: Int = 0, startPositionMs: Long = 0L, autoPlay: Boolean = false) {
        Log.d(TAG, "setSongs called: count=${audios.size}, position=$position, startPositionMs=$startPositionMs, autoPlay=$autoPlay")

        // Block notifyCurrentPosition for the old queue until the new items are set.
        isQueueBeingReplaced = true

        // Discard any seeks queued for the previous queue.
        pendingSeekPositions.clear()

        this.songs = audios
        val clampedPosition = if (audios.isEmpty()) 0 else position.coerceIn(0, audios.size - 1)
        // Capture position BEFORE the setter runs so we know whether the setter's own
        // field-change guard (field != value) will suppress its internal emit.
        val positionBeforeUpdate = currentSongPosition
        // Directly update field to bypass the "no change" guard in the setter, then always emit
        // so observers are notified even when the index stays the same but the song list changed
        // (e.g. after shuffling, position 0 is a completely different song).
        currentSongPosition = clampedPosition

        // Line 83 inside setSongs
        if (audios.isNotEmpty()) {
            // Only emit explicitly when the setter's field-change guard prevented it — i.e.
            // when position is unchanged but the queue itself is new (e.g. after a shuffle).
            // When the position DID change, the setter already emitted; a second emit here
            // would cause every subscriber to receive the same position value twice.
            scope.launch {
                if (positionBeforeUpdate == clampedPosition) {
                    _songPositionFlow.emit(clampedPosition)
                }
                _songSeekPositionFlow.emit(startPositionMs)
            }

            // Mark the user-chosen start position as pending so the first onMediaItemTransition
            // callback from setMediaItems is treated as user-initiated and always-skip is
            // never applied to an explicitly chosen song.
            pendingSeekPositions.add(clampedPosition)

            // Move heavy mapping to background thread
            scope.launch {
                val mediaItems = withContext(Dispatchers.Default) {
                    audios.map { audio ->
                        val uri = audio.uri.toPlaybackUri()
                        MediaItem.Builder()
                            .setMediaId(audio.id.toString())
                            .setUri(uri)
                            .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setArtist(audio.artist)
                                        .setTitle(audio.title)
                                        .build())
                            .build()

                    }
                }

                // Back on Main Thread to set items
                if (mediaController != null) {
                    mediaController?.setMediaItems(mediaItems, currentSongPosition, startPositionMs)
                    mediaController?.prepare()
                    if (autoPlay) {
                        mediaController?.play()
                    }
                    // Do NOT reset isQueueBeingReplaced here. setMediaItems is asynchronous —
                    // it posts the command to the main-thread handler and returns immediately,
                    // meaning any pending onMediaItemTransition callbacks from the OLD queue
                    // that are already in the handler queue would be processed while the flag
                    // is false. The flag is instead cleared inside notifyCurrentPosition once
                    // ExoPlayer confirms a position that belongs to the new queue (i.e. it is
                    // present in pendingSeekPositions).
                } else {
                    // No controller to confirm — lift the guard immediately so future
                    // notifyCurrentPosition calls are not permanently suppressed.
                    isQueueBeingReplaced = false
                }
                startSeekPositionUpdates()
            }
        } else {
            // Clear controller playlist if applicable and keep UI state consistent
            isQueueBeingReplaced = false
            mediaController?.clearMediaItems()
            mediaController?.stop()
            stopSeekPositionUpdates()
            scope.launch { _songPositionFlow.emit(0) }
        }

        scope.launch {
            _songListFlow.emit(this@MediaPlaybackManager.songs)
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
            _songListFlow.emit(this@MediaPlaybackManager.songs)
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
            _songListFlow.emit(this@MediaPlaybackManager.songs)
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
            _songListFlow.emit(this@MediaPlaybackManager.songs)
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

    fun startPlayingIfPaused() {
        if (mediaController?.isPlaying == false) {
            play()
        }
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
        lastNavigationDirection = true
        val nextPos = findNextNonSkippedPosition(currentSongPosition)
        if (nextPos != null) {
            mediaController?.seekTo(nextPos, 0L)
        } else if (mediaController?.hasNextMediaItem() == true) {
            mediaController?.seekToNextMediaItem()
        }
    }

    fun previous() {
        lastNavigationDirection = false
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

    fun seekRelative(offsetMs: Long) {
        val current = getSeekPosition()
        seekTo(current + offsetMs)
    }

    fun getCurrentSongPosition(): Int {
        return currentSongPosition
    }

    fun getCurrentSongId(): Long? {
        return getCurrentSong()?.id
    }

    fun updatePosition(position: Int, forcePlay: Boolean = false) {
        if (position != currentSongPosition) {
            if (position in songs.indices) {
                lastNavigationDirection = position > currentSongPosition
                currentSongPosition = position
                if (mediaController?.currentMediaItemIndex != position) {
                    pendingSeekPositions.add(position)
                    mediaController?.seekTo(position, 0L)
                }
                if (forcePlay) {
                    // Explicit user tap: always start playback regardless of prior pause state.
                    mediaController?.play()
                    startSeekPositionUpdates()
                } else if (mediaController?.isPlaying == true) {
                    // Passive navigation (pager swipe, mini player): keep running if already playing.
                    startSeekPositionUpdates()
                }
            } else {
                Log.w(TAG, "Invalid song position: $position. Must be between 0 and ${songs.size - 1}.")
            }
        } else {
            // Same position: resume only when the user explicitly tapped and player is paused.
            if (forcePlay && mediaController?.isPlaying == false) {
                mediaController?.play()
                startSeekPositionUpdates()
            }
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
     * Applies the current repeat mode behavior:
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
    fun startSeekPositionUpdates(intervalMs: Long = 100L) {
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
        lastKnownPlaybackState = state
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
            // Guard the appended position against spurious ExoPlayer transition callbacks
            // that fire when addMediaItem triggers a state change (e.g. from STATE_ENDED).
            val addedAt = newList.size - 1
            pendingSeekPositions.add(addedAt)
            scope.launch {
                val uri = audio.uri.toPlaybackUri()
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
            // Guard the inserted position against spurious ExoPlayer transition callbacks
            // that fire when addMediaItem triggers a state change (e.g. from STATE_ENDED).
            pendingSeekPositions.add(insertAt)

            scope.launch {
                val uri = audio.uri.toPlaybackUri()
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

    /**
     * Inserts [audio] immediately after the currently playing song, seeks to it, and starts
     * playback. If the song already exists in the queue it is moved to that position instead
     * of being duplicated. If the queue is empty the song becomes the only item and plays
     * from the start.
     *
     * Unlike [playNext], this function immediately seeks and plays the inserted song.
     *
     * @param audio The [Audio] track to insert and play.
     * @author Hamza417
     */
    fun insertAndPlay(audio: Audio) {
        val newList = songs.toMutableList()

        if (newList.isEmpty()) {
            setSongs(mutableListOf(audio), 0, autoPlay = true)
            return
        }

        val insertAt = (currentSongPosition + 1).coerceAtMost(newList.size)
        val existingIndex = newList.indexOfFirst { it.id == audio.id }

        if (existingIndex != -1) {
            // Song already exists in the queue — move it, then seek and play.
            if (existingIndex == currentSongPosition + 1) {
                // Already right after the current song; just seek and play.
                updatePosition(existingIndex, forcePlay = true)
                return
            }
            val targetIndex = if (existingIndex < insertAt) {
                (insertAt - 1).coerceAtMost((newList.size - 1).coerceAtLeast(0))
            } else {
                insertAt.coerceAtMost((newList.size - 1).coerceAtLeast(0))
            }
            moveQueueItemSilently(existingIndex, targetIndex)
            updatePosition(targetIndex, forcePlay = true)
        } else {
            // New song — insert, seek, and play.
            newList.add(insertAt, audio)
            songs = newList
            lastNavigationDirection = true
            currentSongPosition = insertAt
            pendingSeekPositions.add(insertAt)

            scope.launch {
                val uri = audio.uri.toPlaybackUri()
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
                mediaController?.seekTo(insertAt, 0L)
                mediaController?.play()
                startSeekPositionUpdates()
            }
            scope.launch {
                _songListFlow.emit(songs)
            }
        }
    }

    /**
     * Notify the manager that ExoPlayer has moved to [position].
     *
     * User-initiated seeks (via [updatePosition] or [setSongs]) are registered in
     * [pendingSeekPositions]. When the callback arrives for such a position it is
     * treated as a confirmed user action and the always-skip flag is intentionally
     * ignored — the user explicitly chose to play that song.
     *
     * The always-skip check only fires for natural, automatic advances (end-of-track,
     * gapless play, etc.) so that "Always Skip" only applies to the auto-queue, never
     * to deliberate user interaction.
     */
    fun notifyCurrentPosition(position: Int) {
        if (isQueueBeingReplaced) {
            if (pendingSeekPositions.contains(position)) {
                // ExoPlayer just confirmed the first item of the new queue.
                // It is now safe to lift the guard and process this position normally.
                isQueueBeingReplaced = false
            } else {
                Log.d(TAG, "notifyCurrentPosition: discarding stale ExoPlayer callback (position=$position) — queue replacement in progress")
                return
            }
        }
        if (position in songs.indices) {
            if (pendingSeekPositions.remove(position)) {
                // ExoPlayer just confirmed a position that was pre-registered as a pending seek.
                // This covers two cases:
                //  1. A real user-initiated seek (updatePosition / setSongs) — the position was
                //     already written to currentSongPosition before the seekTo call, so the setter
                //     won't fire again. No harm done.
                //  2. A guard added by addToQueue / playNext to absorb the spurious STATE_ENDED
                //     callback that ExoPlayer emits when a new item is added to a finished queue.
                //     In this case currentSongPosition was NOT updated yet, so we must sync it now
                //     so the UI correctly shows the newly-playing song.
                if (currentSongPosition != position) {
                    currentSongPosition = position
                }
            } else {
                // Natural ExoPlayer advance (end of track, auto-next, gapless, etc.).
                // Honor the always-skip flag only here, in the auto-queue path.
                val song = songs[position]
                if (song.isAlwaysSkip) {
                    val nextPos = findNextNonSkippedPosition(position)
                    if (nextPos != null) {
                        mediaController?.seekTo(nextPos, 0L)
                        return
                    }
                    // Every song is marked always-skip → play anyway to avoid an infinite loop.
                }
                if (currentSongPosition != position) {
                    currentSongPosition = position
                    scope.launch { _songPositionFlow.emit(position) }
                }
            }
        } else {
            Log.w(TAG, "notifyCurrentPosition: Invalid song position: $position. Must be between 0 and ${songs.size - 1}.")
        }
    }

    /**
     * Returns the index of the next song in the queue that does NOT have [Audio.isAlwaysSkip] set,
     * starting from the position after [from]. Returns null when every song in the queue is
     * marked as always-skip (caller should fall back to normal behavior).
     */
    private fun findNextNonSkippedPosition(from: Int): Int? {
        if (songs.isEmpty()) return null
        if (songs.all { it.isAlwaysSkip }) return null
        var pos = (from + 1) % songs.size
        var attempts = 0
        while (attempts < songs.size) {
            if (!songs[pos].isAlwaysSkip) return pos
            pos = (pos + 1) % songs.size
            attempts++
        }
        return null
    }

    /**
     * Re-emits the current position so that all [MediaFragment] observers receive an updated
     * [onAudio] callback. Call this after mutating an [Audio] object in the queue in-place
     * (e.g. after toggling [Audio.isFavorite]).
     */
    fun notifyCurrentSongUpdated() {
        scope.launch { _songPositionFlow.emit(currentSongPosition) }
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

    fun registerListener(listener: MediaStateListener) {
        listeners.add(listener)
    }

    fun unregisterListener(listener: MediaStateListener) {
        listeners.remove(listener)
    }
}