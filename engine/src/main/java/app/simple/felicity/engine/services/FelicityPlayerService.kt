package app.simple.felicity.engine.services

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import app.simple.felicity.manager.SharedPreferences.initRegisterSharedPreferenceChangeListener
import app.simple.felicity.manager.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.preferences.AudioPreferences
import app.simple.felicity.repository.constants.MediaConstants
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.managers.PlaybackStateManager
import app.simple.felicity.repository.repositories.AudioRepository
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Service responsible for managing audio playback using ExoPlayer with dynamic decoder switching support.
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class FelicityPlayerService : MediaLibraryService(), SharedPreferences.OnSharedPreferenceChangeListener {

    @Inject
    lateinit var audioRepository: AudioRepository

    private var mediaSession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private var renderersFactory: DefaultRenderersFactory? = null

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var periodicStateSaveJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        initRegisterSharedPreferenceChangeListener(applicationContext)

        // Initialize the RenderersFactory once.
        renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableOffload: Boolean): AudioSink {
                // Check hi-res preference to determine output format
                val hiresEnabled = AudioPreferences.isHiresOutputEnabled()

                val audioSink = DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(hiresEnabled) // 32-bit float if hi-res, 16-bit PCM otherwise
                    .build()

                // Disable offload mode for consistent processing
                audioSink.setOffloadMode(DefaultAudioSink.OFFLOAD_MODE_DISABLED)

                Log.i(TAG, "AudioSink configured: Hi-Res=${hiresEnabled} (${if (hiresEnabled) "32-bit Float" else "16-bit PCM"})")
                return audioSink
            }

            override fun buildAudioRenderers(
                    context: Context,
                    extensionRendererMode: Int,
                    mediaCodecSelector: MediaCodecSelector,
                    enableDecoderFallback: Boolean,
                    audioSink: AudioSink,
                    eventHandler: Handler,
                    eventListener: AudioRendererEventListener,
                    out: ArrayList<Renderer>
            ) {
                super.buildAudioRenderers(
                        context,
                        extensionRendererMode,
                        mediaCodecSelector,
                        enableDecoderFallback,
                        audioSink,
                        eventHandler,
                        eventListener,
                        out
                )
            }
        }

        // Build the initial player instance
        buildPlayer()

        // Initialize MediaSession
        val sessionActivityIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        mediaSession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(sessionActivityIntent!!)
            .setId("ExoPlayerServiceSession")
            .build()
    }

    /**
     * configures the RenderersFactory based on user preferences and builds a new ExoPlayer instance.
     * If a player already exists, it is released before creating the new one.
     */
    private fun buildPlayer() {
        // Configure extension mode based on preferences
        val extensionMode = if (AudioPreferences.getAudioDecoder() == AudioPreferences.FFMPEG) {
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        } else {
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
        }

        renderersFactory?.setExtensionRendererMode(extensionMode)

        // Configure LoadControl with optimized buffer settings based on hi-res mode
        val hiresEnabled = AudioPreferences.isHiresOutputEnabled()

        val loadControl = if (hiresEnabled) {
            // Hi-Res mode: 32-bit float processing requires larger buffers
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        /* minBufferMs = */ 5000,   // 5s minimum for smooth float processing
                        /* maxBufferMs = */ 15000,  // 15s maximum for hi-res content
                        /* bufferForPlaybackMs = */ 2000,   // 2s to start playback
                        /* bufferForPlaybackAfterRebufferMs = */ 3000  // 3s rebuffer threshold
                )
                .setPrioritizeTimeOverSizeThresholds(false) // Prioritize size for hi-res
                .build()
        } else {
            // Standard mode: 16-bit PCM processing uses smaller, efficient buffers
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        /* minBufferMs = */ 2500,   // 2.5s minimum for standard playback
                        /* maxBufferMs = */ 10000,  // 10s maximum for efficiency
                        /* bufferForPlaybackMs = */ 1000,   // 1s quick start
                        /* bufferForPlaybackAfterRebufferMs = */ 2000  // 2s rebuffer threshold
                )
                .setPrioritizeTimeOverSizeThresholds(true) // Prioritize time for responsiveness
                .build()
        }

        Log.i(TAG, "LoadControl configured for ${if (hiresEnabled) "Hi-Res" else "Standard"} mode")

        // Build new player instance
        player = ExoPlayer.Builder(this, renderersFactory!!)
            .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .setSpatializationBehavior(C.SPATIALIZATION_BEHAVIOR_NEVER)
                        .build(),
                    true
            )
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        // Set initial silence state based on preferences
        setSilenceState()

        // Configure gapless playback
        configureGaplessPlayback()

        player.addListener(playerListener)
    }

    /**
     * Handles the dynamic switching of the audio decoder.
     * Captures current playback state (full queue + position), rebuilds the player with new
     * decoder settings, restores the entire queue and resumes from the same track/position.
     */
    private fun switchDecoder() {
        val mediaItems = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        val currentIndex = player.currentMediaItemIndex
        val currentPos = player.currentPosition
        val playWhenReady = player.playWhenReady

        // Release the old player to free up codecs/resources
        player.removeListener(playerListener)
        player.release()

        // Build the new player with updated Factory settings
        buildPlayer()

        // Restore the full queue and position
        if (mediaItems.isNotEmpty()) {
            player.setMediaItems(mediaItems, currentIndex, currentPos)
            player.playWhenReady = playWhenReady
            player.prepare()
        }

        // Update the session to point to the new player instance
        mediaSession?.player = player
    }

    /**
     * Handles the dynamic switching between hi-res and standard audio modes.
     * Captures current playback state, rebuilds the player with new audio output settings,
     * restores the state seamlessly for real-time mode switching.
     */
    private fun switchAudioMode() {
        val mediaItems = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        val currentIndex = player.currentMediaItemIndex
        val currentPos = player.currentPosition
        val playWhenReady = player.playWhenReady
        val hiresEnabled = AudioPreferences.isHiresOutputEnabled()

        Log.i(TAG, "Switching audio mode to: ${if (hiresEnabled) "Hi-Res (32-bit Float)" else "Standard (16-bit PCM)"}")

        // Release the old player to free up audio resources
        player.removeListener(playerListener)
        player.release()

        // Build the new player with updated audio sink and buffer settings
        buildPlayer()

        // Restore the full queue and position seamlessly
        if (mediaItems.isNotEmpty()) {
            player.setMediaItems(mediaItems, currentIndex, currentPos)
            player.playWhenReady = playWhenReady
            player.prepare()
        }

        // Update the session to point to the new player instance
        mediaSession?.player = player

        Log.i(TAG, "Audio mode switch completed successfully")
    }

    /**
     * Configures gapless playback based on user preferences.
     * When enabled, the player will seamlessly transition between tracks without silence.
     */
    private fun configureGaplessPlayback() {
        val gaplessEnabled = AudioPreferences.isGaplessPlaybackEnabled()
        player.pauseAtEndOfMediaItems = !gaplessEnabled
    }

    private fun setSilenceState() {
        // Skip silence is always disabled for natural audio playback
        player.skipSilenceEnabled = AudioPreferences.isSkipSilenceEnabled()
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val format = player.audioFormat
            if (format != null && format.pcmEncoding != C.ENCODING_INVALID) {
                val encodingName = when (format.pcmEncoding) {
                    C.ENCODING_PCM_16BIT -> "16-bit"
                    C.ENCODING_PCM_FLOAT -> "32-bit Float"
                    C.ENCODING_PCM_24BIT -> "24-bit"
                    C.ENCODING_PCM_32BIT -> "32-bit"
                    else -> "Other (${format.pcmEncoding})"
                }
                Log.i(TAG, "Audio Engine: ${format.sampleRate}Hz | Output: $encodingName")
                Log.i(TAG, "Song Info: Channels: ${format.channelCount}, Encoding: ${format.pcmEncoding}, Sample Rate: ${format.sampleRate}")
            }

            if (isPlaying) {
                MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_PLAYING)
                startPeriodicStateSaving()
            } else if (player.playbackState == Player.STATE_READY) {
                MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_PAUSED)
                stopPeriodicStateSaving()
                savePlaybackStateToDatabase() // Save immediately when paused
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (AudioPreferences.isGaplessPlaybackEnabled().not()) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    // The track ended and the player paused itself automatically.
                    // Now we introduce our artificial gap.
                    serviceScope.launch(Dispatchers.Main) {
                        delay(GAP_DURATION_MS) // time of silence
                        player.play() // Move on to the next track
                    }
                }
            } else {
                // If gapless is enabled, we don't need to do anything special here.
                // The player will handle seamless transitions automatically.
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_BUFFERING)
                Player.STATE_READY -> {
                    if (player.playWhenReady) MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_PLAYING)
                    else MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_PAUSED)
                }
                Player.STATE_ENDED -> {
                    MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_ENDED)
                    stopPeriodicStateSaving()
                    savePlaybackStateToDatabase()
                }
                Player.STATE_IDLE -> {
                    MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_STOPPED)
                    stopPeriodicStateSaving()
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e(TAG, "Player error: ${error.errorCodeName}", error)
            MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_ERROR)
            stopPeriodicStateSaving()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            MediaManager.notifyCurrentPosition(player.currentMediaItemIndex)
            savePlaybackStateToDatabase() // Save when track changes
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            AudioPreferences.AUDIO_DECODER -> {
                Log.d(TAG, "Audio decoder preference changed, switching decoder...")
                switchDecoder()
            }
            AudioPreferences.HIRES_OUTPUT -> {
                val hiresEnabled = AudioPreferences.isHiresOutputEnabled()
                Log.d(TAG, "Hi-Res output preference changed to: $hiresEnabled")
                switchAudioMode()
            }
            AudioPreferences.GAPLESS_PLAYBACK -> {
                // Reconfigure gapless playback when preference changes
                configureGaplessPlayback()
                Log.d(TAG, "Gapless playback preference changed to: ${AudioPreferences.isGaplessPlaybackEnabled()}")
            }
            AudioPreferences.SKIP_SILENCE -> {
                setSilenceState()
                Log.d(TAG, "Skip silence preference changed to: ${AudioPreferences.isSkipSilenceEnabled()} (Note: Skip silence is currently disabled for all modes)")
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        savePlaybackStateToDatabase()
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        savePlaybackStateToDatabase()
        unregisterSharedPreferenceChangeListener()

        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private fun savePlaybackStateToDatabase() {
        serviceScope.launch {
            PlaybackStateManager.saveCurrentPlaybackState(applicationContext, TAG)
        }
    }

    private fun startPeriodicStateSaving() {
        if (periodicStateSaveJob?.isActive == true) return

        periodicStateSaveJob = serviceScope.launch {
            while (isActive) {
                delay(10000) // Save every 10 seconds
                savePlaybackStateToDatabase()
            }
        }
        Log.d(TAG, "Started periodic state saving")
    }

    private fun stopPeriodicStateSaving() {
        periodicStateSaveJob?.cancel()
        periodicStateSaveJob = null
        Log.d(TAG, "Stopped periodic state saving")
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        /**
         * Allow clients (Assistant, Android Auto) to connect and get the library root
         */
        override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> = serviceScope.future {
            Log.d(TAG, "onGetLibraryRoot called by: ${browser.packageName}")

            val rootItem = MediaItem.Builder()
                .setMediaId("root")
                .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setTitle("Felicity Music Library")
                            .build()
                )
                .build()

            LibraryResult.ofItem(rootItem, params)
        }

        /**
         * Allow clients to browse content (essential for "Play Music" generally)
         */
        override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future {
            Log.d(TAG, "onGetChildren called for parentId: $parentId, page: $page, pageSize: $pageSize")

            when (parentId) {
                "root" -> {
                    // Fetch all songs from AudioRepository
                    val songs = audioRepository.getAllAudioList()

                    // Convert Audio models to MediaItems
                    val mediaItems = songs.map { audio ->
                        MediaItem.Builder()
                            .setMediaId(audio.id.toString())
                            .setUri(audio.path)
                            .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(audio.title ?: "Unknown Title")
                                        .setArtist(audio.artist ?: "Unknown Artist")
                                        .setAlbumTitle(audio.album ?: "Unknown Album")
                                        .setIsBrowsable(false) // Songs are leaves, not folders
                                        .setIsPlayable(true)
                                        .build()
                            )
                            .build()
                    }

                    // Handle pagination
                    val startIndex = page * pageSize
                    val endIndex = minOf(startIndex + pageSize, mediaItems.size)
                    val paginatedItems = if (startIndex < mediaItems.size) {
                        mediaItems.subList(startIndex, endIndex)
                    } else {
                        emptyList()
                    }

                    Log.d(TAG, "Returning ${paginatedItems.size} items out of ${mediaItems.size} total")
                    LibraryResult.ofItemList(ImmutableList.copyOf(paginatedItems), params)
                }
                else -> {
                    // Unknown parent ID
                    Log.w(TAG, "Unknown parent ID: $parentId")
                    LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                }
            }
        }

        /**
         * Handle "Play [Song Name]" commands from Assistant (Search Intent)
         */
        override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> = serviceScope.future {
            Log.d(TAG, "onAddMediaItems called with ${mediaItems.size} items")

            val updatedMediaItems = mediaItems.mapNotNull { mediaItem ->
                // If the mediaItem comes from a search query, it often lacks a URI
                if (mediaItem.requestMetadata.searchQuery != null) {
                    val query = mediaItem.requestMetadata.searchQuery!!
                    Log.d(TAG, "Assistant requested search for: $query")

                    // Search for the song in the AudioRepository
                    // Try title search first, then artist search
                    val titleResults = audioRepository.searchByTitle(query)
                    val artistResults = audioRepository.searchByArtist(query)
                    val audio = titleResults.firstOrNull() ?: artistResults.firstOrNull()

                    if (audio != null) {
                        Log.d(TAG, "Found audio: ${audio.title} by ${audio.artist}")
                        // Return the fully populated MediaItem with URI
                        MediaItem.Builder()
                            .setMediaId(audio.id.toString())
                            .setUri(audio.path)
                            .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(audio.title ?: "Unknown Title")
                                        .setArtist(audio.artist ?: "Unknown Artist")
                                        .setAlbumTitle(audio.album ?: "Unknown Album")
                                        .setIsPlayable(true)
                                        .build()
                            )
                            .build()
                    } else {
                        Log.w(TAG, "No audio found for query: $query")
                        null
                    }
                } else if (mediaItem.localConfiguration != null) {
                    // Already has a URI, return as-is
                    mediaItem
                } else {
                    // Try to resolve by media ID
                    val mediaId = mediaItem.mediaId
                    if (mediaId.isNotEmpty()) {
                        val audioId = mediaId.toLongOrNull()
                        if (audioId != null) {
                            // Get audio by ID from database
                            val query = "SELECT * FROM audio WHERE id = ?"
                            val args = arrayOf<Any>(audioId)
                            val results = audioRepository.executeRawQuery(query, args)
                            val audio = results.firstOrNull()

                            if (audio != null) {
                                Log.d(TAG, "Resolved media ID $mediaId to audio: ${audio.title}")
                                MediaItem.Builder()
                                    .setMediaId(audio.id.toString())
                                    .setUri(audio.path)
                                    .setMediaMetadata(
                                            MediaMetadata.Builder()
                                                .setTitle(audio.title)
                                                .setArtist(audio.artist ?: "Unknown Artist")
                                                .setAlbumTitle(audio.album ?: "Unknown Album")
                                                .setIsPlayable(true)
                                                .build()
                                    )
                                    .build()
                            } else {
                                Log.w(TAG, "No audio found for media ID: $mediaId")
                                null
                            }
                        } else {
                            Log.w(TAG, "Invalid media ID format: $mediaId")
                            null
                        }
                    } else {
                        Log.w(TAG, "MediaItem has no URI, search query, or valid media ID")
                        null
                    }
                }
            }.toMutableList()

            Log.d(TAG, "Resolved ${updatedMediaItems.size} media items")
            updatedMediaItems
        }
    }

    companion object {
        private const val TAG = "FelicityPlayerService"
        private const val GAP_DURATION_MS = 800L // Duration of silence gap when gapless playback is disabled
    }
}