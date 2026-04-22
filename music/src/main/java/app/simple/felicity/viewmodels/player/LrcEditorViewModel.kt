package app.simple.felicity.viewmodels.player

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.simple.felicity.decorations.lrc.model.LrcData
import app.simple.felicity.decorations.lrc.model.LrcEntry
import app.simple.felicity.decorations.lrc.parser.LrcParser
import app.simple.felicity.decorations.lrc.parser.LyricsParseException
import app.simple.felicity.engine.managers.MediaPlaybackManager
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.models.LrcEntryModel
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.repositories.LrcRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the [app.simple.felicity.ui.subpanels.LrcEditor] panel.
 *
 * Manages a local [ExoPlayer] instance that plays the song being edited,
 * completely independent of the global [MediaPlaybackManager]. When this
 * ViewModel is created, the global service is paused so the user can listen
 * to the track while stamping lyric timestamps. The global service is left
 * paused when this ViewModel is cleared — the user controls resumption manually.
 *
 * The list of [LrcEntryModel] objects is loaded from the existing sidecar file
 * (if any) and exposed as [LiveData]. All mutations (stamp, delete, paste, text
 * edit) are performed in-place on the list objects so focused [EditText] views
 * in the RecyclerView are not unnecessarily invalidated.
 *
 * @author Hamza417
 */
@HiltViewModel(assistedFactory = LrcEditorViewModel.Factory::class)
class LrcEditorViewModel @AssistedInject constructor(
        application: Application,
        @Assisted val audio: Audio,
        private val lrcRepository: LrcRepository
) : WrappedViewModel(application) {

    private val _entries: MutableLiveData<MutableList<LrcEntryModel>> = MutableLiveData(mutableListOf())
    private val _seekPositionMs: MutableLiveData<Long> = MutableLiveData(0L)
    private val _durationMs: MutableLiveData<Long> = MutableLiveData(audio.duration)
    private val _isPlaying: MutableLiveData<Boolean> = MutableLiveData(false)
    private val _saved: MutableLiveData<Boolean> = MutableLiveData(false)

    private var localPlayer: ExoPlayer? = null

    /** Handler used to poll [localPlayer] position while playback is active. */
    private val seekHandler = Handler(Looper.getMainLooper())
    private val seekRunnable: Runnable = object : Runnable {
        override fun run() {
            localPlayer?.let { player ->
                _seekPositionMs.value = player.currentPosition.coerceAtLeast(0L)
            }
            seekHandler.postDelayed(this, SEEK_POLL_INTERVAL_MS)
        }
    }

    init {
        // Pause the global service so it does not compete with the local player.
        if (MediaPlaybackManager.isPlaying()) {
            MediaPlaybackManager.pause()
        }

        initLocalPlayer()
        loadLrcEntries()
    }

    /** Returns the current list of lyric entries to observe for structural changes. */
    fun getEntries(): LiveData<MutableList<LrcEntryModel>> = _entries

    /** Returns a stream of the local player's current seek position in milliseconds. */
    fun getSeekPosition(): LiveData<Long> = _seekPositionMs

    /** Returns the total duration of the audio track in milliseconds. */
    fun getDuration(): LiveData<Long> = _durationMs

    /** Returns whether the local player is currently playing. */
    fun isPlayingLiveData(): LiveData<Boolean> = _isPlaying

    /**
     * Emits [true] exactly once when a save operation completes successfully.
     * Observers should react to the value and then reset their own state.
     */
    fun getSaved(): LiveData<Boolean> = _saved

    /** The current playback position as reported by the local player. */
    val currentPositionMs: Long
        get() = localPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L

    fun play() {
        localPlayer?.play()
    }

    fun pause() {
        localPlayer?.pause()
    }

    /** Toggles between playing and paused states in the local player. */
    fun flipPlayback() {
        if (localPlayer?.isPlaying == true) pause() else play()
    }

    /**
     * Seeks the local player to [positionMs], clamped to the valid range
     * [0, duration]. Also updates the seek position [LiveData] immediately
     * so the seekbar reflects the change without waiting for the next poll tick.
     */
    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, _durationMs.value ?: 0L)
        localPlayer?.seekTo(clamped)
        _seekPositionMs.value = clamped
    }

    /**
     * Seeks the local player forward or backward by [deltaMs] milliseconds relative
     * to the current position.
     */
    fun seekRelative(deltaMs: Long) {
        seekTo(currentPositionMs + deltaMs)
    }

    /**
     * Stamps the current playback position as the timestamp for the entry at [index].
     * Posts a new list reference so the adapter can rebind that single row.
     */
    fun stampTimestamp(index: Int) {
        val list = _entries.value ?: return
        if (index !in list.indices) return
        list[index] = list[index].copy(timestampMs = currentPositionMs)
        _entries.value = list
    }

    /**
     * Deletes the entry at [index] from the list and notifies observers.
     */
    fun removeEntry(index: Int) {
        val list = _entries.value ?: return
        if (index !in list.indices) return
        list.removeAt(index)
        _entries.value = list
    }

    /**
     * Adds a single empty entry at the end of the list.
     */
    fun addEmptyEntry() {
        val list = _entries.value ?: mutableListOf()
        list.add(LrcEntryModel(timestampMs = 0L, text = ""))
        _entries.value = list
    }

    /**
     * Splits [text] by newlines and appends one [LrcEntryModel] per non-blank line
     * with a timestamp of 0 ms. Empty lines are discarded.
     */
    fun pasteLines(text: String) {
        val list = _entries.value ?: mutableListOf()
        text.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line -> list.add(LrcEntryModel(timestampMs = 0L, text = line)) }
        _entries.value = list
    }

    /**
     * Serializes the current entry list to an LRC string and saves it as a sidecar
     * file next to the audio file. Entries are sorted by timestamp before writing.
     * Emits [true] on [getSaved] when the operation succeeds.
     */
    fun saveLrc() {
        val entries = _entries.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lrcData = LrcData()
                entries.sortedBy { it.timestampMs }.forEach { model ->
                    lrcData.addEntry(LrcEntry(model.timestampMs, model.text))
                }
                val result = lrcRepository.saveLrcToFile(lrcData.toLrcString(), audio.uri)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        _saved.value = true
                        Log.d(TAG, "LRC saved to ${result.getOrNull()?.path}")
                    } else {
                        Log.e(TAG, "Failed to save LRC", result.exceptionOrNull())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while saving LRC", e)
            }
        }
    }

    private fun initLocalPlayer() {
        localPlayer = ExoPlayer.Builder(getApplication()).build().also { player ->
            player.setMediaItem(MediaItem.fromUri(audio.uri.toUri()))
            player.prepare()
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        val dur = player.duration.coerceAtLeast(audio.duration)
                        _durationMs.postValue(dur)
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.postValue(isPlaying)
                    if (isPlaying) startSeekPolling() else stopSeekPolling()
                }
            })
        }
    }

    private fun loadLrcEntries() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = lrcRepository.loadLrcFromFile(audio.uri)
            val lrcContent = result.getOrNull()
            if (!lrcContent.isNullOrBlank()) {
                try {
                    val lrcData = LrcParser().parse(lrcContent)
                    val models = lrcData.getEntries()
                        .map { entry -> LrcEntryModel(entry.timeInMillis, entry.text) }
                        .toMutableList()
                    _entries.postValue(models)
                } catch (e: LyricsParseException) {
                    Log.w(TAG, "Could not parse existing LRC file; starting with empty list.", e)
                    _entries.postValue(mutableListOf())
                }
            } else {
                _entries.postValue(mutableListOf())
            }
        }
    }

    private fun startSeekPolling() {
        seekHandler.removeCallbacks(seekRunnable)
        seekHandler.post(seekRunnable)
    }

    private fun stopSeekPolling() {
        seekHandler.removeCallbacks(seekRunnable)
    }

    override fun onCleared() {
        super.onCleared()
        stopSeekPolling()
        localPlayer?.release()
        localPlayer = null
    }

    @AssistedFactory
    interface Factory {
        /** Creates an [LrcEditorViewModel] for the given [audio] track. */
        fun create(audio: Audio): LrcEditorViewModel
    }

    companion object {
        private const val TAG = "LrcEditorViewModel"

        /** Interval between seek-position poll ticks while the local player is playing. */
        private const val SEEK_POLL_INTERVAL_MS = 100L
    }
}

