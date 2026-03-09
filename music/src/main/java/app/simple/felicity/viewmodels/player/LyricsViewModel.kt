package app.simple.felicity.viewmodels.player

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.decorations.lrc.model.LrcData
import app.simple.felicity.decorations.lrc.parser.LrcParser
import app.simple.felicity.decorations.lrc.parser.LyricsParseException
import app.simple.felicity.decorations.lrc.parser.TxtParser
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.repositories.LrcRepository
import app.simple.felicity.viewmodels.player.LyricsViewModel.Companion.SYNC_SAVE_DEBOUNCE_MS
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel(assistedFactory = LyricsViewModel.Factory::class)
class LyricsViewModel @AssistedInject constructor(
        application: Application,
        @Assisted private val audio: Audio?,
        private val lrcRepository: LrcRepository
) : WrappedViewModel(application) {

    private val lrcData: MutableLiveData<LrcData> by lazy {
        MutableLiveData<LrcData>().also {
            loadLrcData()
        }
    }

    /**
     * Mirrors what is currently written on disk. Updated silently after every
     * [persistSyncAdjustment] call so each subsequent shift builds on the
     * already-baked state rather than the original loaded data. Never posted to observers.
     */
    private var bakedLrcData: LrcData? = null

    /** Guards against concurrent duplicate load calls (e.g. lazy init + onAudio firing together). */
    @Volatile
    private var isLoading = false

    /** Path of the last song whose lyrics were successfully kicked off, to avoid redundant reloads. */
    @Volatile
    private var lastLoadedPath: String? = null

    /**
     * The running sync offset in milliseconds. Adding this to the current playback
     * position before calling updateTime shifts which line is highlighted without
     * touching the LrcData object or the view's scroll state.
     *
     * Positive → view sees a later time  → later line highlighted  (fixes lagging lyrics).
     * Negative → view sees earlier time  → earlier line highlighted (fixes ahead lyrics).
     */
    private val syncOffsetMs = MutableLiveData(0L)

    /** Tracks the current sync offset so onSeekChanged can apply it. */
    var syncOffset: Long = 0L

    /** Accumulated offset that has not yet been baked into the on-disk .lrc file. */
    private var pendingSyncDeltaMs: Long = 0L

    /** Handler + Runnable for debounced disk persistence of sync adjustments. */
    private val syncSaveHandler = Handler(Looper.getMainLooper())
    private val syncSaveRunnable = Runnable { persistSyncAdjustment() }

    fun getLrcData(): LiveData<LrcData> = lrcData

    /** Current sync offset to add to every updateTime call. */
    fun getSyncOffsetMs(): LiveData<Long> = syncOffsetMs

    fun loadLrcData() {
        // Prevent duplicate concurrent loads (e.g. lazy init racing with onAudio callback)
        if (isLoading) {
            Log.d(TAG, "loadLrcData() skipped – already in progress.")
            return
        }

        // Skip redundant reload for the same song (e.g. onAudio firing right after lazy init)
        val currentSongPath = (audio ?: MediaManager.getCurrentSong())?.path
        if (currentSongPath != null && currentSongPath == lastLoadedPath) {
            Log.d(TAG, "loadLrcData() skipped – same song already loaded.")
            return
        }

        isLoading = true
        lastLoadedPath = currentSongPath

        // Reset sync offset whenever we load fresh lyrics
        pendingSyncDeltaMs = 0L
        syncOffsetMs.value = 0L
        bakedLrcData = null
        syncSaveHandler.removeCallbacks(syncSaveRunnable)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentSong = audio ?: MediaManager.getCurrentSong()
                if (currentSong == null) {
                    lrcData.postValue(LrcData())
                    return@launch
                }

                val loadResult = lrcRepository.loadLrcFromFile(currentSong.path)

                loadResult.onSuccess { lrcContent ->
                    if (lrcContent != null) {
                        Log.d(TAG, "Existing LRC file found for ${currentSong.title}, loading lyrics.")
                        try {
                            val lrcDataLoaded = LrcParser().parse(lrcContent)
                            lrcData.postValue(lrcDataLoaded)
                        } catch (e: LyricsParseException) {
                            e.printStackTrace()
                            lrcData.postValue(LrcData())
                        }
                    } else {
                        Log.d(TAG, "No existing LRC file found for ${currentSong.title}, checking for TXT sidecar.")
                        val txtResult = lrcRepository.loadTxtFromFile(currentSong.path)
                        val txtContent = txtResult.getOrNull()
                        if (!txtContent.isNullOrBlank()) {
                            Log.d(TAG, "TXT sidecar found for ${currentSong.title}, loading plain-text lyrics.")
                            try {
                                val txtLrcData = TxtParser().parse(txtContent)
                                lrcData.postValue(txtLrcData)
                            } catch (e: LyricsParseException) {
                                e.printStackTrace()
                                lrcData.postValue(LrcData())
                            }
                        } else {
                            Log.d(TAG, "No TXT sidecar found for ${currentSong.title}, attempting to fetch automatically.")
                            fetchAndSaveLrc(
                                    trackName = currentSong.title ?: currentSong.name,
                                    artistName = currentSong.artist ?: "",
                                    audioPath = currentSong.path
                            )
                        }
                    }
                }.onFailure { exception ->
                    exception.printStackTrace()
                    lrcData.postValue(LrcData())
                }
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun fetchAndSaveLrc(trackName: String, artistName: String, audioPath: String) {
        val searchResult = lrcRepository.searchLyrics(trackName, artistName)

        searchResult.onSuccess { results ->
            val bestMatch = results.firstOrNull()
            val syncedLyrics = bestMatch?.syncedLyrics
            if (bestMatch != null && !syncedLyrics.isNullOrBlank()) {
                try {
                    val lrcDataLoaded = withContext(Dispatchers.Default) {
                        LrcParser().parse(syncedLyrics)
                    }

                    lrcRepository.saveLrcToFile(syncedLyrics, audioPath)
                    Log.d(TAG, "Fetched and saved synced lyrics for $trackName by $artistName")
                    lrcData.postValue(lrcDataLoaded)
                } catch (e: LyricsParseException) {
                    e.printStackTrace()
                    lrcData.postValue(LrcData())
                }
            } else {
                lrcData.postValue(LrcData())
            }
        }.onFailure { exception ->
            exception.printStackTrace()
            lrcData.postValue(LrcData())
        }
    }

    fun reloadLrcData() {
        lastLoadedPath = null // Force reload even for the same song
        loadLrcData()
    }

    /**
     * Nudge the lyrics sync by [deltaMs] milliseconds.
     *
     * The view simply receives an adjusted time value on the next updateTime
     * call — no LrcData object is replaced, no scroll position is touched.
     *
     * After [SYNC_SAVE_DEBOUNCE_MS] of inactivity the accumulated offset is baked
     * into the on-disk .lrc file by shifting all timestamps and the offset resets to 0.
     *
     * Positive  → view sees a later time  → later line lights up   (fixes lagging lyrics).
     * Negative  → view sees earlier time  → earlier line lights up  (fixes ahead lyrics).
     */
    fun seekBy(deltaMs: Long) {
        val current = lrcData.value
        if (current == null || current.isEmpty) return

        // Accumulate offset
        pendingSyncDeltaMs += deltaMs
        syncOffsetMs.value = pendingSyncDeltaMs

        // Debounce the disk write
        syncSaveHandler.removeCallbacks(syncSaveRunnable)
        syncSaveHandler.postDelayed(syncSaveRunnable, SYNC_SAVE_DEBOUNCE_MS)
    }

    /**
     * Bakes the accumulated offset into the .lrc file on disk — nothing else.
     * lrcData is intentionally NOT updated here to avoid triggering the observer
     * and causing a scroll reset. syncOffsetMs stays at its current value so the
     * fragment continues adding it to updateTime, keeping the highlight correct.
     * On the next loadLrcData() call (song change) the file is re-read with the
     * already-corrected timestamps and the offset resets to 0 cleanly.
     */
    private fun persistSyncAdjustment() {
        val delta = pendingSyncDeltaMs
        if (delta == 0L) return

        // Always shift from what is currently on disk, not the original loaded data.
        // This prevents each persist from overwriting previous adjustments.
        val base = bakedLrcData ?: lrcData.value ?: return
        val currentSong = audio ?: MediaManager.getCurrentSong() ?: return

        // The offset is added to the playback clock in the view (seek + offset), so
        // to bake the same correction into the timestamps we must subtract it:
        // a positive offset means "show a later line" = timestamps must be smaller.
        val baked = base.shiftTimestamps(-delta)

        viewModelScope.launch(Dispatchers.IO) {
            val result = lrcRepository.saveLrcToFile(baked.toLrcString(), currentSong.path)
            result.onSuccess {
                Log.d(TAG, "Sync ${delta}ms baked and saved to ${it.absolutePath}")
            }.onFailure {
                Log.e(TAG, "Failed to persist sync adjustment", it)
            }
        }

        // Track what is now on disk so the next persist builds on top of it.
        bakedLrcData = baked
        pendingSyncDeltaMs = 0L

        // The file now has the correct timestamps, so the fragment must stop adding
        // the offset to updateTime — otherwise it would be applied twice on reload.
        syncOffsetMs.value = 0L
    }

    override fun onCleared() {
        super.onCleared()
        // Flush immediately on ViewModel destruction so nothing is lost
        syncSaveHandler.removeCallbacks(syncSaveRunnable)
        persistSyncAdjustment()
    }

    @AssistedFactory
    interface Factory {
        fun create(audio: Audio?): LyricsViewModel
    }

    companion object {
        private const val TAG = "LyricsViewModel"
        private const val SYNC_SAVE_DEBOUNCE_MS = 1500L
    }
}
