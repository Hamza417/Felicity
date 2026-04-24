package app.simple.felicity.managers

import android.os.Handler
import android.os.Looper
import android.util.Log
import app.simple.felicity.decorations.lrc.model.LrcData
import app.simple.felicity.decorations.lrc.model.LrcEntry
import app.simple.felicity.decorations.lrc.parser.ILyricsParser
import app.simple.felicity.decorations.lrc.parser.LrcParser
import app.simple.felicity.decorations.lrc.parser.LyricsParseException
import app.simple.felicity.decorations.lrc.parser.TxtParser
import app.simple.felicity.decorations.lrc.parser.WordLrcParser
import app.simple.felicity.engine.managers.MediaPlaybackManager
import app.simple.felicity.managers.LyricsManager.Companion.SYNC_SAVE_DEBOUNCE_MS
import app.simple.felicity.preferences.LyricsPreferences
import app.simple.felicity.repository.models.Audio
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
 * Every screen that needs lyrics just collects [lrcData] — no manual loading required.
 * The manager watches the playback queue and reloads automatically on song changes,
 * so the Lyrics panel and the player screen always see the same data.
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

    private val EMPTY_LRC_DATA = LrcData().apply {
        addEntry(LrcEntry(0L, "No lyrics found for this song."))
    }

    /**
     * App-wide coroutine scope. A [SupervisorJob] is used so that a failed lyrics
     * fetch does not cancel the song-change observer or any other running job.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _lrcData = MutableStateFlow<LrcData?>(null)

    /**
     * The parsed lyrics for the currently playing song.
     * - `null`      → a load is in progress (first start only; during song changes
     *                  the previous song's data stays until the new one is ready).
     * - empty [LrcData] → load finished but no lyrics were found.
     * - non-empty   → lyrics are ready to display.
     */
    val lrcData: StateFlow<LrcData?> = _lrcData.asStateFlow()

    private val _loadingStatus = MutableStateFlow<LyricsLoadingStatus>(LyricsLoadingStatus.Idle)

    /**
     * Only emits non-[LyricsLoadingStatus.Idle] values when the manager is actively
     * talking to the internet (searching or downloading). Local file reads are fast
     * enough that showing a loading indicator would just be noise.
     */
    val loadingStatus: StateFlow<LyricsLoadingStatus> = _loadingStatus.asStateFlow()

    private val _syncOffsetMs = MutableStateFlow(0L)

    /**
     * The current sync offset in milliseconds to add to the playback position before
     * passing it to the lyrics view. Positive = highlight a later line (fixes lagging
     * lyrics), negative = highlight an earlier line (fixes lyrics running ahead).
     */
    val syncOffsetMs: StateFlow<Long> = _syncOffsetMs.asStateFlow()

    /**
     * Accumulated sync offset that callers can read synchronously from the seek listener
     * without needing a coroutine collector.
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

    /**
     * Path of the song whose lyrics the current [loadingJob] was started for.
     * Used to discard stale results when the song changes mid-load.
     */
    @Volatile
    private var lastLoadedPath: String? = null

    /** Reference to the currently running load job so we can cancel it if the song changes. */
    private var loadingJob: Job? = null

    init {
        // Watch the playback queue — whenever a new song starts, reload lyrics automatically.
        scope.launch {
            MediaPlaybackManager.songPositionFlow.collect {
                loadLrcData()
            }
        }
    }

    /**
     * Picks the best parser for the given raw lyrics string.
     * Word-sync LRC wins first, then standard LRC, then plain text as a last resort.
     */
    private fun selectParser(content: String): ILyricsParser = when {
        WordLrcParser().canParse(content) -> WordLrcParser()
        LrcParser().canParse(content) -> LrcParser()
        else -> TxtParser()
    }

    /**
     * Loads lyrics for the currently playing song, or skips if they are already loaded.
     *
     * The song reference is captured at the call site so that a fast song change cannot
     * trick the coroutine into loading the wrong file. Every emission into [_lrcData] is
     * guarded by a stale-check — if the song changed before the result was ready, the
     * result is silently discarded and the next [loadLrcData] call wins.
     */
    fun loadLrcData() {
        // Capture both the song and its path NOW — do not re-read them inside the coroutine.
        val currentSong = MediaPlaybackManager.getCurrentSong()
        val currentSongPath = currentSong?.uri

        // Already loaded (or loading) this exact song — nothing to do.
        if (currentSongPath != null && currentSongPath == lastLoadedPath) {
            if (loadingJob?.isActive == true) {
                Log.d(TAG, "loadLrcData() skipped – already loading '${currentSong.title}'.")
                return
            }
            if (_lrcData.value != null) {
                Log.d(TAG, "loadLrcData() skipped – lyrics already ready for '${currentSong.title}'.")
                return
            }
        }

        // If the path is null we can't do anything meaningful — bail here so doLoad
        // never receives a null path and we don't need the non-null assertion below.

        // Bake any unsaved sync adjustment for the outgoing song before resetting state.
        persistSyncAdjustment()

        // Cancel the in-flight job for the previous song (cooperative cancellation).
        loadingJob?.cancel()

        // Plant our flag — any result that arrives after this for a different path is stale.
        lastLoadedPath = currentSongPath

        // Fresh slate for sync state on the new song.
        syncOffset = 0L
        pendingSyncDeltaMs = 0L
        _syncOffsetMs.value = 0L
        bakedLrcData = null
        syncSaveHandler.removeCallbacks(syncSaveRunnable)

        // No song is playing at all — mark empty and stop here.
        if (currentSong == null) {
            _lrcData.value = LrcData()
            return
        }

        // currentSongPath is guaranteed non-null at this point because currentSong is non-null above.
        val path = currentSongPath ?: return

        loadingJob = scope.launch {
            try {
                doLoad(currentSong, path)
            } catch (e: Exception) {
                e.printStackTrace()
                emitIfStillRelevant(path, LrcData())
            }
        }
    }

    /**
     * Runs the actual file-read / network-fetch sequence for [song].
     * [songPath] is used as the "load token" — every [_lrcData] emission is guarded
     * so that a late-arriving result for an old song never stomps on the current one.
     */
    private suspend fun doLoad(song: Audio, songPath: String) {
        // Step 1: Try the .lrc sidecar (fastest — local disk).
        val lrcContent = withContext(Dispatchers.IO) {
            lrcRepository.loadLrcFromFile(song.uri).getOrNull()
        }

        if (!isStillRelevant(songPath)) return

        if (lrcContent != null) {
            Log.d(TAG, "LRC sidecar found for '${song.title}'.")
            parseThenEmit(lrcContent, songPath, fallback = LrcData()) { content ->
                selectParser(content).parse(content)
            }
            return
        }

        // Step 2: Try the .txt sidecar (plain-text lyrics, also local).
        val txtContent = withContext(Dispatchers.IO) {
            lrcRepository.loadTxtFromFile(song.uri).getOrNull()
        }

        if (!isStillRelevant(songPath)) return

        if (!txtContent.isNullOrBlank()) {
            Log.d(TAG, "TXT sidecar found for '${song.title}'.")
            parseThenEmit(txtContent, songPath, fallback = LrcData()) { content ->
                TxtParser().parse(content)
            }
            return
        }

        // Step 3: Nothing local — try the internet if the user opted in.
        if (LyricsPreferences.isAutoDownloadLyrics()) {
            Log.d(TAG, "No local lyrics for '${song.title}', fetching online.")

            // post an empty data first
            emitIfStillRelevant(songPath, LrcData())

            fetchAndSaveLrc(
                    trackName = song.title ?: song.name,
                    artistName = song.artist ?: "",
                    audioPath = song.uri,
                    songPath = songPath
            )
        } else {
            Log.d(TAG, "No local lyrics and auto-download is off for '${song.title}'.")
            emitIfStillRelevant(songPath, LrcData())
        }
    }

    /**
     * Parses [content] on the default dispatcher, then emits the result — or [fallback]
     * if parsing blows up — provided [songPath] is still the active load target.
     */
    private suspend fun parseThenEmit(
            content: String,
            songPath: String,
            fallback: LrcData,
            parse: suspend (String) -> LrcData
    ) {
        try {
            val parsed = withContext(Dispatchers.Default) { parse(content) }
            emitIfStillRelevant(songPath, parsed)
        } catch (e: LyricsParseException) {
            e.printStackTrace()
            emitIfStillRelevant(songPath, fallback)
        }
    }

    /**
     * Emits [data] into [_lrcData] only if [songPath] still matches [lastLoadedPath].
     * Stale results from cancelled-but-not-yet-dead coroutines are quietly dropped here.
     */
    private fun emitIfStillRelevant(songPath: String?, data: LrcData) {
        if (isStillRelevant(songPath)) {
            _lrcData.value = data
        } else {
            Log.d(TAG, "Discarding stale lyrics result for path='$songPath' (current='$lastLoadedPath').")
        }
    }

    /**
     * Returns true when [songPath] is still the song we should be loading for.
     * False means the song changed while we were busy — our result is garbage now.
     */
    private fun isStillRelevant(songPath: String?): Boolean = (songPath == lastLoadedPath)

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
     * Triggers a fresh load only when the current song genuinely has no lyrics.
     * Handy when the app returns to the foreground — if lyrics are already loaded,
     * there's nothing to do; if not, we give it another shot.
     */
    fun refreshIfNoLyrics() {
        val currentLrc = _lrcData.value
        if (currentLrc != null && currentLrc.isEmpty) {
            reloadLrcData()
        }
    }

    /**
     * Fetches synced lyrics from LrcLib, saves them as a sidecar, and emits the result.
     * This is the only place that updates [loadingStatus] — local reads are too fast
     * to be worth announcing.
     *
     * [songPath] is the load token; the result is discarded if the song has changed
     * by the time the network round-trip finishes.
     */
    private suspend fun fetchAndSaveLrc(
            trackName: String,
            artistName: String,
            audioPath: String,
            songPath: String
    ) {
        _loadingStatus.value = LyricsLoadingStatus.Searching(trackName)

        val result = lrcRepository.searchLyrics(trackName, artistName)

        if (!isStillRelevant(songPath)) {
            _loadingStatus.value = LyricsLoadingStatus.Idle
            return
        }

        result.onSuccess { results ->
            val syncedLyrics = results.firstOrNull()?.syncedLyrics
            if (!syncedLyrics.isNullOrBlank()) {
                _loadingStatus.value = LyricsLoadingStatus.Downloading(trackName)
                try {
                    val parsed = withContext(Dispatchers.Default) { LrcParser().parse(syncedLyrics) }
                    withContext(Dispatchers.IO) { lrcRepository.saveLrcToFile(syncedLyrics, audioPath) }
                    Log.d(TAG, "Downloaded and saved lyrics for '$trackName'.")
                    emitIfStillRelevant(songPath, parsed)
                } catch (e: LyricsParseException) {
                    e.printStackTrace()
                    emitIfStillRelevant(songPath, LrcData())
                }
            } else {
                Log.d(TAG, "No match found online for '$trackName'.")
                emitIfStillRelevant(songPath, LrcData())
            }
        }.onFailure { exception ->
            exception.printStackTrace()
            emitIfStillRelevant(songPath, LrcData())
        }

        _loadingStatus.value = LyricsLoadingStatus.Idle
    }

    /**
     * Shifts the lyrics sync by [deltaMs] milliseconds so the highlighted line
     * catches up to — or backs off from — the music.
     *
     * After [SYNC_SAVE_DEBOUNCE_MS] ms of silence the accumulated offset is baked
     * into the .lrc file on disk and the in-memory offset resets to zero so the
     * next load starts clean.
     *
     * Positive = later line highlights (fixes lagging lyrics).
     * Negative = earlier line highlights (fixes lyrics running ahead).
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
     * Updating [_lrcData] here is intentional — without it the view would lose
     * the correction the moment [_syncOffsetMs] resets to zero, making the
     * lyrics jump by twice the intended amount.
     */
    private fun persistSyncAdjustment() {
        val delta = pendingSyncDeltaMs
        if (delta == 0L) return

        val base = bakedLrcData ?: _lrcData.value ?: return
        val currentSong = MediaPlaybackManager.getCurrentSong() ?: return

        // Positive offset means "show a later line" → timestamps must move earlier.
        val baked = base.shiftTimestamps(-delta)

        scope.launch(Dispatchers.IO) {
            val result = lrcRepository.saveLrcToFile(baked.toLrcString(), currentSong.uri)
            result.onSuccess { Log.d(TAG, "Sync offset ${delta}ms baked and saved to ${it.path}.") }
                .onFailure { Log.e(TAG, "Failed to persist sync adjustment.", it) }
        }

        bakedLrcData = baked
        pendingSyncDeltaMs = 0L
        _lrcData.value = baked
        _syncOffsetMs.value = 0L
        syncOffset = 0L
    }

    /**
     * Deletes the .lrc sidecar for the currently playing song and clears the lyrics view.
     *
     * @param onSuccess optional callback that fires on the main thread after deletion.
     */
    fun deleteLrc(onSuccess: (() -> Unit)? = null) {
        val currentSong = MediaPlaybackManager.getCurrentSong() ?: return

        scope.launch {
            withContext(Dispatchers.IO) { lrcRepository.deleteLrcFile(currentSong.uri) }

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

/**
 * Describes what the lyrics loader is doing at any given moment.
 *
 * [Idle] is the quiet default — nothing happening, nothing to show.
 * [Searching] and [Downloading] only appear when talking to the internet.
 */
sealed class LyricsLoadingStatus {

    /** Calm and quiet — no network activity in progress. */
    data object Idle : LyricsLoadingStatus()

    /**
     * Asking LrcLib whether it has lyrics for this track.
     * @param trackName the song being searched, so the UI can display it.
     */
    data class Searching(val trackName: String) : LyricsLoadingStatus()

    /**
     * A match was found and the lyrics are being written to disk.
     * @param trackName the song whose lyrics are being saved.
     */
    data class Downloading(val trackName: String) : LyricsLoadingStatus()
}
