package app.simple.felicity.managers

import android.os.Handler
import android.os.Looper
import android.util.Log
import app.simple.felicity.decorations.lrc.model.LrcData
import app.simple.felicity.decorations.lrc.parser.ILyricsParser
import app.simple.felicity.decorations.lrc.parser.LrcParser
import app.simple.felicity.decorations.lrc.parser.LyricsParseException
import app.simple.felicity.decorations.lrc.parser.TxtParser
import app.simple.felicity.decorations.lrc.parser.WordLrcParser
import app.simple.felicity.engine.managers.MediaPlaybackManager
import app.simple.felicity.managers.LyricsManager.Companion.SYNC_SAVE_DEBOUNCE_MS
import app.simple.felicity.repository.repositories.LrcRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A single place where all lyrics data lives for the currently playing song.
 *
 * Before this existed, each screen (player, lyrics panel) had its own [LyricsViewModel]
 * instance that independently loaded lyrics. When the song changed on one screen the other
 * would still show stale data. This manager fixes that — it watches the playback queue and
 * reloads lyrics automatically whenever the song changes, then all ViewModels just read from
 * the same [StateFlow] here.
 *
 * Think of it as the one lyrics DJ that everyone in the app is tuned into.
 *
 * @author Hamza417
 */
@Singleton
class LyricsManager @Inject constructor(
        private val lrcRepository: LrcRepository
) {

    private val TAG = "LyricsManager"

    /**
     * App-wide coroutine scope. A [SupervisorJob] is used so that a failed lyrics
     * fetch does not cancel the song-change observer or any other running job.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _lrcData = MutableStateFlow<LrcData?>(null)

    /**
     * The parsed lyrics for the currently playing song, or `null` while still loading.
     * An empty [LrcData] (i.e. [LrcData.isEmpty] == true) means the song has no lyrics.
     * Screens collect from this — there is no need to call load manually.
     */
    val lrcData: StateFlow<LrcData?> = _lrcData.asStateFlow()

    private val _syncOffsetMs = MutableStateFlow(0L)

    /**
     * The current sync offset in milliseconds that must be added to the playback position
     * before passing it to the lyrics view. Positive = highlight a later line (fixes lagging
     * lyrics), negative = highlight an earlier line (fixes lyrics that are ahead).
     */
    val syncOffsetMs: StateFlow<Long> = _syncOffsetMs.asStateFlow()

    /**
     * Accumulated sync offset that callers add to each [updateTime] call on the view.
     * Updated via [seekBy].
     */
    var syncOffset: Long = 0L
        private set

    /** Mirrors the LRC content currently written on disk after the last [persistSyncAdjustment] call. */
    private var bakedLrcData: LrcData? = null

    /** Pending delta that has not yet been baked into the .lrc file on disk. */
    private var pendingSyncDeltaMs: Long = 0L

    /** Debounces disk writes so rapid tap-and-hold adjustments collapse into one save. */
    private val syncSaveHandler = Handler(Looper.getMainLooper())
    private val syncSaveRunnable = Runnable { persistSyncAdjustment() }

    /** Prevents redundant network/disk fetches when the same song emits more than once. */
    @Volatile
    private var lastLoadedPath: String? = null

    /** Reference to the currently running load job so we can cancel it if the song changes mid-load. */
    private var loadingJob: Job? = null

    init {
        // Whenever the playback queue moves to a different song, automatically reload lyrics
        // so every screen that collects [lrcData] gets fresh data without needing to ask.
        scope.launch {
            MediaPlaybackManager.songPositionFlow.collect {
                loadLrcData()
            }
        }
    }

    /**
     * Picks the best parser for the raw lyrics string.
     *
     * Word-by-word LRC wins if it can handle the content, then standard LRC,
     * and plain text is the fallback so nothing ever goes unhandled.
     */
    private fun selectParser(content: String): ILyricsParser = when {
        WordLrcParser().canParse(content) -> WordLrcParser()
        LrcParser().canParse(content) -> LrcParser()
        else -> TxtParser()
    }

    /**
     * Starts (or skips) a lyrics load for the currently playing song.
     *
     * If we already have data for the same song path and a load is not already running,
     * this is a no-op — nice and cheap. Otherwise the old job is cancelled and a fresh
     * load begins.
     */
    fun loadLrcData() {
        val currentSongPath = MediaPlaybackManager.getCurrentSong()?.uri

        if (currentSongPath != null && currentSongPath == lastLoadedPath) {
            if (loadingJob?.isActive == true) {
                Log.d(TAG, "loadLrcData() skipped – still loading the same song.")
                return
            }
            if (_lrcData.value != null) {
                Log.d(TAG, "loadLrcData() skipped – lyrics already available for this song.")
                return
            }
        }

        // Before loading new lyrics, flush any unsaved sync adjustment for the previous song.
        persistSyncAdjustment()

        // Cancel any in-flight job for a different song.
        loadingJob?.cancel()
        lastLoadedPath = currentSongPath

        // Reset sync state so the new song starts with a clean slate.
        syncOffset = 0L
        pendingSyncDeltaMs = 0L
        _syncOffsetMs.value = 0L
        bakedLrcData = null
        syncSaveHandler.removeCallbacks(syncSaveRunnable)

        // Signal to collectors that lyrics are being loaded (null = loading in progress).
        _lrcData.value = null

        loadingJob = scope.launch {
            try {
                val currentSong = MediaPlaybackManager.getCurrentSong()
                if (currentSong == null) {
                    _lrcData.value = LrcData()
                    return@launch
                }

                val loadResult = withContext(Dispatchers.IO) {
                    lrcRepository.loadLrcFromFile(currentSong.uri)
                }

                loadResult.onSuccess { lrcContent ->
                    if (lrcContent != null) {
                        Log.d(TAG, "Found an existing LRC file for ${currentSong.title}.")
                        try {
                            val parsed = withContext(Dispatchers.Default) {
                                selectParser(lrcContent).parse(lrcContent)
                            }
                            _lrcData.value = parsed
                        } catch (e: LyricsParseException) {
                            e.printStackTrace()
                            _lrcData.value = LrcData()
                        }
                    } else {
                        Log.d(TAG, "No LRC found for ${currentSong.title}, checking for a TXT sidecar.")
                        val txtResult = withContext(Dispatchers.IO) {
                            lrcRepository.loadTxtFromFile(currentSong.uri)
                        }
                        val txtContent = txtResult.getOrNull()
                        if (!txtContent.isNullOrBlank()) {
                            Log.d(TAG, "TXT sidecar found for ${currentSong.title}, loading plain-text lyrics.")
                            try {
                                val parsed = withContext(Dispatchers.Default) {
                                    TxtParser().parse(txtContent)
                                }
                                _lrcData.value = parsed
                            } catch (e: LyricsParseException) {
                                e.printStackTrace()
                                _lrcData.value = LrcData()
                            }
                        } else {
                            Log.d(TAG, "Nothing local found for ${currentSong.title}, trying to fetch online.")
                            fetchAndSaveLrc(
                                    trackName = currentSong.title ?: currentSong.name,
                                    artistName = currentSong.artist ?: "",
                                    audioPath = currentSong.uri
                            )
                        }
                    }
                }.onFailure { exception ->
                    exception.printStackTrace()
                    _lrcData.value = LrcData()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _lrcData.value = LrcData()
            }
        }
    }

    /**
     * Forces a fresh load even when the same song is already loaded.
     * Call this after saving a new .lrc file so the view picks up the change.
     */
    fun reloadLrcData() {
        loadingJob?.cancel()
        loadingJob = null
        lastLoadedPath = null
        loadLrcData()
    }

    /**
     * Triggers a fresh lyrics lookup only when the current song has no lyrics yet.
     *
     * This is the right thing to call when the app comes back to the foreground — if
     * the user already has lyrics loaded, there is nothing to do. But if they stepped
     * away, dropped an .lrc file into the right folder, and came back, this will pick
     * it up without forcing a wasteful full reload every time.
     */
    fun refreshIfNoLyrics() {
        val currentLrc = _lrcData.value
        // Only do anything if we have an empty result (i.e. no lyrics were found last time).
        // null means a load is already in progress, so we leave that alone too.
        if (currentLrc != null && currentLrc.isEmpty) {
            reloadLrcData()
        }
    }

    /**
     * Tries to fetch synced lyrics from LrcLib for the given track and saves them alongside
     * the audio file so future loads are instant.
     */
    private suspend fun fetchAndSaveLrc(trackName: String, artistName: String, audioPath: String) {
        val result = lrcRepository.searchLyrics(trackName, artistName)

        result.onSuccess { results ->
            val bestMatch = results.firstOrNull()
            val syncedLyrics = bestMatch?.syncedLyrics
            if (bestMatch != null && !syncedLyrics.isNullOrBlank()) {
                try {
                    val parsed = withContext(Dispatchers.Default) {
                        LrcParser().parse(syncedLyrics)
                    }
                    withContext(Dispatchers.IO) {
                        lrcRepository.saveLrcToFile(syncedLyrics, audioPath)
                    }
                    Log.d(TAG, "Fetched and saved synced lyrics for $trackName by $artistName.")
                    _lrcData.value = parsed
                } catch (e: LyricsParseException) {
                    e.printStackTrace()
                    _lrcData.value = LrcData()
                }
            } else {
                _lrcData.value = LrcData()
            }
        }.onFailure { exception ->
            exception.printStackTrace()
            _lrcData.value = LrcData()
        }
    }

    /**
     * Shifts the lyrics sync by [deltaMs] milliseconds so the highlighted line
     * catches up to — or backs off from — the music.
     *
     * After [SYNC_SAVE_DEBOUNCE_MS] milliseconds of silence the accumulated offset is
     * baked into the .lrc file on disk and the in-memory offset resets to zero,
     * keeping the next load clean.
     *
     * Positive = later line highlights (fixes lagging lyrics).
     * Negative = earlier line highlights (fixes lyrics that run ahead).
     *
     * @param deltaMs how much to shift, in milliseconds.
     */
    fun seekBy(deltaMs: Long) {
        val current = _lrcData.value
        if (current == null || current.isEmpty) return

        pendingSyncDeltaMs += deltaMs
        syncOffset = pendingSyncDeltaMs
        _syncOffsetMs.value = pendingSyncDeltaMs

        syncSaveHandler.removeCallbacks(syncSaveRunnable)
        syncSaveHandler.postDelayed(syncSaveRunnable, SYNC_SAVE_DEBOUNCE_MS)
    }

    /**
     * Bakes the accumulated sync offset into the .lrc file on disk and updates
     * the in-memory [_lrcData] to use the corrected timestamps.
     *
     * Previously we skipped updating [_lrcData] to avoid a scroll reset, but that
     * caused the highlight to jump in the wrong direction right after to debounce
     * fired — the offset was removed from [_syncOffsetMs] while the timestamps in
     * memory were still the originals, making the lyrics appear to shift by twice
     * the intended amount. Updating [_lrcData] here keeps the view's timestamps in
     * sync with what is on disk, and the zero offset then has nothing to undo.
     */
    private fun persistSyncAdjustment() {
        val delta = pendingSyncDeltaMs
        if (delta == 0L) return

        val base = bakedLrcData ?: _lrcData.value ?: return
        val currentSong = MediaPlaybackManager.getCurrentSong() ?: return

        // A positive offset means "show a later line", which requires stored timestamps
        // to be smaller (earlier). So we subtract the delta from every timestamp.
        val baked = base.shiftTimestamps(-delta)

        scope.launch(Dispatchers.IO) {
            val result = lrcRepository.saveLrcToFile(baked.toLrcString(), currentSong.uri)
            result.onSuccess {
                Log.d(TAG, "Sync offset ${delta}ms baked and saved to ${it.absolutePath}.")
            }.onFailure {
                Log.e(TAG, "Failed to persist the sync adjustment.", it)
            }
        }

        bakedLrcData = baked
        pendingSyncDeltaMs = 0L

        // Push the corrected LrcData into the flow so every collector immediately
        // sees the right timestamps. Without this the view would lose the offset
        // correction the moment _syncOffsetMs resets to zero below.
        _lrcData.value = baked

        // Zero out the additive offset — the timestamps in _lrcData are already correct
        // so the view no longer needs to compensate.
        _syncOffsetMs.value = 0L
        syncOffset = 0L
    }

    /**
     * Deletes the .lrc sidecar file for the currently playing song and clears the lyrics view.
     *
     * @param onSuccess optional callback that runs on the main thread after deletion succeeds.
     */
    fun deleteLrc(onSuccess: (() -> Unit)? = null) {
        val currentSong = MediaPlaybackManager.getCurrentSong() ?: return

        scope.launch {
            withContext(Dispatchers.IO) {
                lrcRepository.deleteLrcFile(currentSong.uri)
            }

            // Clear in-memory state so the view goes blank immediately.
            _lrcData.value = LrcData()
            pendingSyncDeltaMs = 0L
            syncOffset = 0L
            _syncOffsetMs.value = 0L
            bakedLrcData = null
            syncSaveHandler.removeCallbacks(syncSaveRunnable)

            onSuccess?.invoke()
        }
    }

    companion object {
        private const val SYNC_SAVE_DEBOUNCE_MS = 1500L
    }
}

