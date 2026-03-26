package app.simple.felicity.engine.services

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import app.simple.felicity.engine.R
import app.simple.felicity.engine.managers.AudioPipelineManager
import app.simple.felicity.engine.managers.AudioProcessorManager
import app.simple.felicity.engine.managers.EqualizerManager
import app.simple.felicity.engine.managers.VisualizerManager
import app.simple.felicity.engine.model.AudioPipelineSnapshot
import app.simple.felicity.engine.notifications.PlaybackErrorNotifier
import app.simple.felicity.manager.SharedPreferences.initRegisterSharedPreferenceChangeListener
import app.simple.felicity.manager.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.preferences.AudioPreferences
import app.simple.felicity.preferences.EqualizerPreferences
import app.simple.felicity.preferences.PlayerPreferences
import app.simple.felicity.repository.constants.MediaConstants
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.managers.PlaybackStateManager
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.repository.repositories.SongStatRepository
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
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
import kotlin.math.roundToInt
import javax.inject.Inject

/**
 * Service responsible for managing audio playback using ExoPlayer with dynamic decoder switching support.
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class FelicityPlayerService : MediaLibraryService(), SharedPreferences.OnSharedPreferenceChangeListener {

    @Inject
    lateinit var audioRepository: AudioRepository

    @Inject
    lateinit var songStatRepository: SongStatRepository

    private var mediaSession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private var renderersFactory: DefaultRenderersFactory? = null

    /**
     * The mediaId of the media item that was playing before the most recent item transition.
     * Used in conjunction with [previousItemEndPositionMs] and [previousItemDurationMs] to
     * decide whether the previous song was skipped.
     */
    private var previousItemMediaId: String? = null

    /**
     * The playback position (ms) captured just before the most recent item transition.
     * Populated in {@link Player.Listener#onPositionDiscontinuity}.
     */
    private var previousItemEndPositionMs: Long = 0L

    /**
     * The total duration (ms) of the previous media item captured just before the transition.
     * Populated in {@link Player.Listener#onPositionDiscontinuity}.
     */
    private var previousItemDurationMs: Long = 0L

    /**
     * Manages the balance and downmix [androidx.media3.common.audio.ChannelMixingAudioProcessor]
     * instances. Extracted to keep audio processing logic out of the service.
     */
    private val audioProcessorManager = AudioProcessorManager()

    /**
     * Posts silent error notifications when a track cannot be played.
     * Initialized in [onCreate] once a valid [Context] is available.
     */
    private lateinit var playbackErrorNotifier: PlaybackErrorNotifier

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var periodicStateSaveJob: Job? = null

    /**
     * Tracks whether we are currently in a silent FFmpeg fallback retry for a failed track.
     * When true the next decoding error on the same item is treated as a final failure,
     * the original decoder is restored and the track is skipped.
     */
    private var ffmpegFallbackActive = false

    /** The [MediaItem] that triggered the decoding error we are retrying via FFmpeg. */
    private var ffmpegFallbackItem: MediaItem? = null

    /** The decoder the user had configured before a fallback attempt was started. */
    private var preFallbackDecoder: Int = AudioPreferences.LOCAL_DECODER

    /**
     * The name of the most recently initialized audio decoder, captured via [analyticsListener].
     * Defaults to "Unknown" until [AnalyticsListener.onAudioDecoderInitialized] fires.
     */
    private var currentDecoderName: String = "Unknown"

    /**
     * The compressed source [Format] most recently delivered to the audio renderer.
     * Updated via [AnalyticsListener.onAudioInputFormatChanged]; `null` before the
     * first track is decoded.
     */
    private var currentAudioInputFormat: Format? = null

    /**
     * The currently active audio output device, or `null` if detection has not yet
     * run. Updated whenever [audioDeviceCallback] fires or [detectActiveOutputDevice]
     * is called explicitly.
     */
    private var currentOutputDevice: AudioDeviceInfo? = null

    /** Coroutine job that pushes a fresh [AudioPipelineSnapshot] every 3 seconds while playing. */
    private var snapshotPulseJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        initRegisterSharedPreferenceChangeListener(applicationContext)
        playbackErrorNotifier = PlaybackErrorNotifier(applicationContext)

        // Expose the processor via VisualizerManager so the player fragment can call
        // setDirectOutput() and wire the lock-free twin-buffer path without a service bind.
        VisualizerManager.processor = audioProcessorManager.visualizerProcessor

        // Wire the native DSP processor into EqualizerManager so gain, preamp, and enable
        // changes driven by the UI are forwarded to the live audio pipeline immediately.
        EqualizerManager.attachProcessor(audioProcessorManager.nativeDspProcessor)

        // Initialize the RenderersFactory once.
        renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableOffload: Boolean): AudioSink {
                val hiresEnabled = AudioPreferences.isHiresOutputEnabled()

                // Check if the user WANTS to preserve surround sound for their USB DAC
                // You'd add this boolean to your AudioPreferences
                val forceStereoDownmix = AudioPreferences.isStereoDownmixForced()

                audioProcessorManager.applyBalance(EqualizerPreferences.getBalance())
                audioProcessorManager.applyStereoWidth(EqualizerPreferences.getStereoWidth())
                audioProcessorManager.applyTapeSaturationDrive(EqualizerPreferences.getTapeSaturationDrive())
                audioProcessorManager.applyKaraokeMode(EqualizerPreferences.isKaraokeModeEnabled())
                audioProcessorManager.applyNightMode(EqualizerPreferences.isNightModeEnabled())
                // applyEqualizerState covers 10-band EQ, bass, treble, preamp, and enabled flag.
                audioProcessorManager.applyEqualizerState()

                // Build the processor array dynamically
                val processors = mutableListOf<AudioProcessor>()

                if (AudioPreferences.isSkipSilenceEnabled()) {
                    // Trim digital silence first while the stream is uncolored.
                    processors.add(audioProcessorManager.silenceTrimmingProcessor)
                }

                if (forceStereoDownmix) {
                    processors.add(audioProcessorManager.downmixProcessor)
                }

                // Vocal removal runs before the EQ/effects chain so center-channel
                // subtraction is not colored by subsequent tonal processing.
                processors.add(audioProcessorManager.karaokeProcessor)

                // Unified native DSP: EQ → bass/treble shelves → M/S widening → balance → saturation.
                // Also feeds the processed mono downmix to the shared FFTContext.
                processors.add(audioProcessorManager.nativeDspProcessor)

                // Dynamic range compression runs after all tonal/spatial effects so it
                // can respond to the final loudness of the mix.
                processors.add(audioProcessorManager.nightModeProcessor)

                // Visualizer always goes last so the spectrum display reflects every
                // active effect in the chain.
                processors.add(audioProcessorManager.visualizerProcessor)

                val audioSink = DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(hiresEnabled)
                    // CRITICAL FOR USB DACs: Tell ExoPlayer to read the USB/HDMI capabilities
                    .setAudioCapabilities(AudioCapabilities.getCapabilities(context))
                    .setAudioProcessors(processors.toTypedArray())
                    .build()

                // If the user has a home theater / USB DAC, we MIGHT want offload for Atmos/Dolby
                val offloadMode = if (!forceStereoDownmix) {
                    if (AudioPreferences.isGaplessPlaybackEnabled()) {
                        DefaultAudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_REQUIRED
                    } else {
                        DefaultAudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_NOT_REQUIRED
                    }
                } else {
                    DefaultAudioSink.OFFLOAD_MODE_DISABLED
                }

                audioSink.setOffloadMode(offloadMode)

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

        // Set initial repeat button in the notification
        mediaSession?.setCustomLayout(listOf(buildRepeatCommandButton(PlayerPreferences.getRepeatMode())))

        // Detect the current output device and subscribe to future device changes so the
        // snapshot is refreshed whenever headphones or a BT device is connected / disconnected.
        currentOutputDevice = detectActiveOutputDevice()
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
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

        // Apply saved repeat mode
        applyRepeatMode(PlayerPreferences.getRepeatMode())

        player.addListener(playerListener)
        player.addAnalyticsListener(analyticsListener)
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

    private fun applyRepeatMode(repeatMode: Int) {
        when (repeatMode) {
            MediaConstants.REPEAT_ONE -> {
                player.repeatMode = Player.REPEAT_MODE_ONE
            }
            MediaConstants.REPEAT_QUEUE -> {
                player.repeatMode = Player.REPEAT_MODE_ALL
            }
            else -> { // REPEAT_OFF
                player.repeatMode = Player.REPEAT_MODE_OFF
            }
        }
        MediaManager.notifyRepeatMode(repeatMode)
        // Push the updated repeat button to the media notification
        mediaSession?.setCustomLayout(listOf(buildRepeatCommandButton(repeatMode)))
        Log.d(TAG, "Repeat mode applied: $repeatMode")
    }

    /** Builds a CommandButton representing the current repeat state for the notification. */
    @Suppress("DEPRECATION")
    private fun buildRepeatCommandButton(repeatMode: Int): CommandButton {
        val (iconRes, displayName) = when (repeatMode) {
            MediaConstants.REPEAT_ONE -> Pair(R.drawable.ic_repeat_one, "Repeat One")
            MediaConstants.REPEAT_QUEUE -> Pair(R.drawable.ic_repeat, "Repeat Queue")
            else -> Pair(R.drawable.ic_repeat_off, "Repeat Off")
        }

        return CommandButton.Builder(
                CommandButton.ICON_REPEAT_OFF)
            .setDisplayName(displayName)
            .setIconResId(iconRes)
            .setSessionCommand(SessionCommand(COMMAND_TOGGLE_REPEAT, Bundle.EMPTY))
            .build()
    }

    private fun setSilenceState() {
        // Skip silence is always disabled for natural audio playback
        player.skipSilenceEnabled = AudioPreferences.isSkipSilenceEnabled()
    }

    /**
     * Silently retries [failedItem] using the FFmpeg extension decoder.
     *
     * The full queue and playback position are preserved; only the renderer mode is changed.
     * The preference store is NOT modified so the user's chosen decoder is kept intact.
     * If [failedItem] is null the call is a no-op (track already gone).
     */
    private fun retryWithFfmpegFallback(failedItem: MediaItem?) {
        if (failedItem == null) {
            Log.w(TAG, "retryWithFfmpegFallback: no failed item, aborting.")
            ffmpegFallbackActive = false
            return
        }

        val mediaItems = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        val currentIndex = player.currentMediaItemIndex

        // Temporarily force the FFmpeg extension without touching user preferences.
        renderersFactory?.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        player.removeListener(playerListener)
        player.release()

        buildPlayerWithExtensionMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        if (mediaItems.isNotEmpty()) {
            player.setMediaItems(mediaItems, currentIndex, 0L)
            player.playWhenReady = true
            player.prepare()
        }

        mediaSession?.player = player
        Log.i(TAG, "FFmpeg fallback: re-trying '${failedItem.mediaMetadata.title}' from the start with FFmpeg.")
    }

    /**
     * Restores the engine to [decoderMode] without writing to shared preferences.
     * Called after a failed FFmpeg fallback so the user sees no change in settings.
     */
    private fun restoreDecoderMode(decoderMode: Int) {
        val extensionMode = if (decoderMode == AudioPreferences.FFMPEG) {
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        } else {
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
        }

        val mediaItems = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        val currentIndex = player.currentMediaItemIndex
        val currentPos = player.currentPosition
        val playWhenReady = player.playWhenReady

        player.removeListener(playerListener)
        player.release()

        buildPlayerWithExtensionMode(extensionMode)

        if (mediaItems.isNotEmpty()) {
            player.setMediaItems(mediaItems, currentIndex, currentPos)
            player.playWhenReady = playWhenReady
            player.prepare()
        }

        mediaSession?.player = player
        Log.d(TAG, "Decoder restored to mode $decoderMode (extensionMode=$extensionMode) without preference change.")
    }

    /**
     * Skips to the next track if available; otherwise restarts the current item.
     * Shared helper used by the fallback logic.
     */
    private fun skipOrRestartTrack() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            Log.i(TAG, "Skipped to next track after decoder failure.")
        } else {
            player.seekToDefaultPosition()
            Log.i(TAG, "Restarted current track (no next track available).")
        }
        player.prepare()
        player.playWhenReady = true
    }

    /**
     * Variant of [buildPlayer] that uses a specific [extensionMode] directly, bypassing the
     * shared-preference read. Used for transient fallback / restore operations.
     */
    private fun buildPlayerWithExtensionMode(extensionMode: Int) {
        renderersFactory?.setExtensionRendererMode(extensionMode)

        val hiresEnabled = AudioPreferences.isHiresOutputEnabled()
        val loadControl = if (hiresEnabled) {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(5000, 15000, 2000, 3000)
                .setPrioritizeTimeOverSizeThresholds(false)
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(2500, 10000, 1000, 2000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        }

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

        setSilenceState()
        configureGaplessPlayback()
        applyRepeatMode(PlayerPreferences.getRepeatMode())
        player.addListener(playerListener)
        player.addAnalyticsListener(analyticsListener)
    }

    /**
     * Delegates balance panning to [audioProcessorManager].
     *
     * @param pan Stereo pan value in the range [-1.0, 1.0].
     */
    private fun applyBalanceToProcessor(pan: Float) {
        audioProcessorManager.applyBalance(pan)
    }

    /**
     * Delegates stereo width to [audioProcessorManager].
     *
     * @param width Stereo width in the range [0.0, 2.0]. 1.0 = natural stereo (no change).
     */
    private fun applyStereoWidthToProcessor(width: Float) {
        audioProcessorManager.applyStereoWidth(width)
    }

    /**
     * Delegates tape saturation drive to [audioProcessorManager].
     *
     * @param drive Saturation drive in [0.0, 4.0]. 0.0 = off (clean bypass).
     */
    private fun applyTapeSaturationDriveToProcessor(drive: Float) {
        audioProcessorManager.applyTapeSaturationDrive(drive)
    }

    /**
     * Delegates karaoke mode toggle to [audioProcessorManager].
     *
     * @param enabled True to activate center-channel removal, false to bypass.
     */
    private fun applyKaraokeModeToProcessor(enabled: Boolean) {
        audioProcessorManager.applyKaraokeMode(enabled)
    }

    /**
     * Delegates night mode toggle to [audioProcessorManager].
     *
     * @param enabled True to activate the dynamic compressor, false to bypass.
     */
    private fun applyNightModeToProcessor(enabled: Boolean) {
        audioProcessorManager.applyNightMode(enabled)
    }

    /** Applies a new pan value immediately to the processor and persists it. */
    fun setBalance(pan: Float) {
        EqualizerPreferences.setBalance(pan)
        audioProcessorManager.applyBalance(pan)
    }

    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            super.onAudioSessionIdChanged(audioSessionId)
        }

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
                startSnapshotPulse()
                buildAndPushSnapshot()
            } else if (player.playbackState == Player.STATE_READY) {
                MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_PAUSED)
                stopPeriodicStateSaving()
                stopSnapshotPulse()
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
                    // Only treat as a true "ended" event in REPEAT_OFF mode.
                    // For REPEAT_ONE / REPEAT_QUEUE, ExoPlayer loops automatically and
                    // STATE_ENDED is never actually reached.
                    if (PlayerPreferences.getRepeatMode() == MediaConstants.REPEAT_OFF) {
                        MediaManager.handleQueueEnded()
                    }
                    stopPeriodicStateSaving()
                    stopSnapshotPulse()
                    savePlaybackStateToDatabase()
                }
                Player.STATE_IDLE -> {
                    MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_STOPPED)
                    stopPeriodicStateSaving()
                    stopSnapshotPulse()
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            when (error.errorCode) {
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> {
                    Log.e(TAG, "Decoding error for current track: ${error.message} (code: ${error.errorCode})")

                    val failedItem = player.currentMediaItem

                    if (ffmpegFallbackActive) {
                        // FFmpeg also failed – give up, restore original decoder and skip.
                        Log.w(TAG, "FFmpeg fallback also failed for '${failedItem?.mediaMetadata?.title}', skipping track and restoring decoder.")
                        ffmpegFallbackActive = false
                        ffmpegFallbackItem = null
                        // Notify user that the track could not be played by any available decoder.
                        playbackErrorNotifier.notifyPlaybackError(
                                failedItem?.mediaMetadata?.title?.toString(),
                                error
                        )
                        // Restore user's original decoder choice silently (no pref write – just engine mode).
                        restoreDecoderMode(preFallbackDecoder)
                        skipOrRestartTrack()
                    } else if (AudioPreferences.isFallbackToSoftwareDecoderEnabled()
                            && AudioPreferences.getAudioDecoder() != AudioPreferences.FFMPEG) {
                        // Primary decoder failed and fallback is enabled – try FFmpeg silently.
                        Log.i(TAG, "Primary decoder failed; silently retrying '${failedItem?.mediaMetadata?.title}' with FFmpeg.")
                        preFallbackDecoder = AudioPreferences.getAudioDecoder()
                        ffmpegFallbackActive = true
                        ffmpegFallbackItem = failedItem
                        retryWithFfmpegFallback(failedItem)
                    } else {
                        // Fallback disabled, or already on FFmpeg – just skip.
                        Log.w(TAG, "Skipping track (fallback disabled or already using FFmpeg).")
                        ffmpegFallbackActive = false
                        ffmpegFallbackItem = null
                        // Notify user why the track was skipped.
                        playbackErrorNotifier.notifyPlaybackError(
                                failedItem?.mediaMetadata?.title?.toString(),
                                error
                        )
                        skipOrRestartTrack()
                    }
                }
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> {
                    Log.e(TAG, "File not found: ${error.message} (code: ${error.errorCode})")
                    playbackErrorNotifier.notifyPlaybackError(
                            player.currentMediaItem?.mediaMetadata?.title?.toString(),
                            error
                    )
                    skipOrRestartTrack()
                }
                else -> {
                    Log.e(TAG, "Playback error: ${error.message} (code: ${error.errorCode})")
                    Log.e(TAG, "Player error: ${error.errorCodeName}", error)
                    playbackErrorNotifier.notifyPlaybackError(
                            player.currentMediaItem?.mediaMetadata?.title?.toString(),
                            error
                    )
                    MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_ERROR)
                    stopPeriodicStateSaving()
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // If a track transition happened naturally (not via fallback retry), clear any stale fallback state.
            if (ffmpegFallbackActive && mediaItem != ffmpegFallbackItem) {
                Log.d(TAG, "Track transitioned away from fallback item; restoring original decoder and clearing fallback state.")
                ffmpegFallbackActive = false
                ffmpegFallbackItem = null
                restoreDecoderMode(preFallbackDecoder)
            }

            // Record skip for the previous song when the user seeked away early.
            val prevMediaId = previousItemMediaId
            if (prevMediaId != null
                    && reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                    && previousItemDurationMs > 0
                    && previousItemEndPositionMs < previousItemDurationMs * SKIP_THRESHOLD) {
                serviceScope.launch(Dispatchers.IO) {
                    val audioId = prevMediaId.toLongOrNull() ?: return@launch
                    val audio = audioRepository.getAudioById(audioId) ?: return@launch
                    songStatRepository.recordSkip(audio.hash)
                    Log.d(TAG, "Skip recorded for: ${audio.title} (pos=${previousItemEndPositionMs}ms / dur=${previousItemDurationMs}ms)")
                }
            }

            // Record play event for the newly active song.
            mediaItem?.let { item ->
                previousItemMediaId = item.mediaId
                val audioId = item.mediaId.toLongOrNull() ?: return@let
                serviceScope.launch(Dispatchers.IO) {
                    val audio = audioRepository.getAudioById(audioId) ?: return@launch
                    songStatRepository.recordPlay(audio.hash)
                    Log.d(TAG, "Play recorded for: ${audio.title}")
                }
            } ?: run { previousItemMediaId = null }

            MediaManager.notifyCurrentPosition(player.currentMediaItemIndex)
            savePlaybackStateToDatabase() // Save when track changes
            buildAndPushSnapshot()
        }

        override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
        ) {
            super.onPositionDiscontinuity(oldPosition, newPosition, reason)
            // Capture position and duration of the outgoing item before ExoPlayer transitions.
            // This fires BEFORE onMediaItemTransition, so player.duration still reflects the old item.
            if (oldPosition.mediaItemIndex != newPosition.mediaItemIndex) {
                previousItemEndPositionMs = oldPosition.positionMs
                previousItemDurationMs = player.duration.coerceAtLeast(0L)
            }
        }
    }

    /**
     * Captures decoder initialization and compressed-source format changes so the
     * [AudioPipelineSnapshot] always reflects the active decoder name and track format.
     *
     * Both callbacks fire on the main thread, so accessing [player] and calling
     * [buildAndPushSnapshot] is safe without any additional dispatching.
     */
    private val analyticsListener = object : AnalyticsListener {

        override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
        ) {
            currentDecoderName = decoderName
            Log.d(TAG, "Audio decoder initialized: $decoderName")
            buildAndPushSnapshot()
        }

        override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            currentAudioInputFormat = format
            Log.d(TAG, "Audio input format changed: ${format.sampleMimeType} @ ${format.sampleRate}Hz")
            buildAndPushSnapshot()
        }
    }

    /**
     * Listens for audio output device additions and removals (e.g., plugging in wired
     * headphones or connecting a Bluetooth device). On each change the active output device
     * is re-detected and a fresh snapshot is pushed.
     */
    private val audioDeviceCallback = object : AudioDeviceCallback() {

        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            currentOutputDevice = detectActiveOutputDevice()
            Log.d(TAG, "Audio device added: ${addedDevices.firstOrNull()?.productName}")
            buildAndPushSnapshot()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            currentOutputDevice = detectActiveOutputDevice()
            Log.d(TAG, "Audio device removed: ${removedDevices.firstOrNull()?.productName}")
            buildAndPushSnapshot()
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
            AudioPreferences.IS_STEREO_DOWNMIX_FORCED -> {
                val enabled = AudioPreferences.isStereoDownmixForced()
                Log.d(TAG, "Stereo downmix preference changed to: $enabled — rebuilding audio pipeline...")
                // Rebuilding the player re-invokes buildAudioSink which re-reads the preference
                // and re-assembles the processor chain with or without the downmix processor.
                switchAudioMode()
            }
            PlayerPreferences.REPEAT_MODE -> {
                val repeatMode = PlayerPreferences.getRepeatMode()
                Log.d(TAG, "Repeat mode preference changed to: $repeatMode")
                applyRepeatMode(repeatMode)
            }
            EqualizerPreferences.BALANCE -> {
                val pan = EqualizerPreferences.getBalance()
                Log.d(TAG, "Balance preference changed to: $pan")
                applyBalanceToProcessor(pan)
            }
            EqualizerPreferences.STEREO_WIDTH -> {
                val width = EqualizerPreferences.getStereoWidth()
                Log.d(TAG, "Stereo width preference changed to: $width")
                applyStereoWidthToProcessor(width)
            }
            EqualizerPreferences.TAPE_SATURATION_DRIVE -> {
                val drive = EqualizerPreferences.getTapeSaturationDrive()
                Log.d(TAG, "Tape saturation drive preference changed to: $drive")
                applyTapeSaturationDriveToProcessor(drive)
            }
            EqualizerPreferences.KARAOKE_MODE_ENABLED -> {
                val enabled = EqualizerPreferences.isKaraokeModeEnabled()
                Log.d(TAG, "Karaoke mode preference changed to: $enabled")
                applyKaraokeModeToProcessor(enabled)
            }
            EqualizerPreferences.NIGHT_MODE_ENABLED -> {
                val enabled = EqualizerPreferences.isNightModeEnabled()
                Log.d(TAG, "Night mode preference changed to: $enabled")
                applyNightModeToProcessor(enabled)
            }
            EqualizerPreferences.EQ_ENABLED -> {
                val enabled = EqualizerPreferences.isEqEnabled()
                Log.d(TAG, "Equalizer enabled preference changed to: $enabled")
                EqualizerManager.setEnabled(enabled)
            }
            EqualizerPreferences.PREAMP_DB -> {
                Log.d(TAG, "EQ preamp preference changed")
                EqualizerManager.applyPreampFromPreference()
            }
            EqualizerPreferences.BASS_DB -> {
                val db = EqualizerPreferences.getBassDb()
                Log.d(TAG, "Bass gain preference changed to: ${db}dB")
                audioProcessorManager.applyBass(db)
            }
            EqualizerPreferences.TREBLE_DB -> {
                val db = EqualizerPreferences.getTrebleDb()
                Log.d(TAG, "Treble gain preference changed to: ${db}dB")
                audioProcessorManager.applyTreble(db)
            }
            else -> {
                // Handle each individual EQ band preference change
                if (key != null && key.startsWith(EqualizerPreferences.EQ_BAND_KEY_PREFIX)) {
                    val bandIndex = key.removePrefix(EqualizerPreferences.EQ_BAND_KEY_PREFIX).toIntOrNull()
                    if (bandIndex != null) {
                        Log.d(TAG, "EQ band $bandIndex preference changed")
                        EqualizerManager.applyBandFromPreference(bandIndex)
                    }
                }
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

        // Stop the periodic snapshot pulse before releasing resources.
        stopSnapshotPulse()

        // Unregister the audio-device-change callback so no stale reference is held after teardown.
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)

        // Clear the snapshot so observers know the pipeline is no longer active.
        AudioPipelineManager.updateSnapshot(null)

        // Detach the equalizer processor reference before releasing the player so the
        // manager does not hold a stale reference after teardown.
        EqualizerManager.detachProcessor()

        // Clear the visualizer processor reference so no stale direct-output connection
        // remains after the service has been destroyed.
        VisualizerManager.processor = null

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

    /**
     * Starts a coroutine that pushes a refreshed [AudioPipelineSnapshot] to
     * [AudioPipelineManager] every 3 seconds while playback is active.
     *
     * The coroutine runs on the main dispatcher so [player] state can be read safely.
     * If a pulse job is already active this is a no-op.
     */
    private fun startSnapshotPulse() {
        if (snapshotPulseJob?.isActive == true) return

        snapshotPulseJob = serviceScope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                delay(3_000L)
                buildAndPushSnapshot()
            }
        }

        Log.d(TAG, "Started snapshot pulse")
    }

    /**
     * Cancels the running snapshot pulse coroutine, if any.
     */
    private fun stopSnapshotPulse() {
        snapshotPulseJob?.cancel()
        snapshotPulseJob = null
        Log.d(TAG, "Stopped snapshot pulse")
    }

    /**
     * Assembles a fully-populated [AudioPipelineSnapshot] from all available sources
     * and pushes it to [AudioPipelineManager].
     *
     * Must be called from the main thread because several [ExoPlayer] API calls
     * (e.g., [ExoPlayer.audioFormat]) are not thread-safe. All call sites guarantee
     * this by using [Dispatchers.Main.immediate] or being inside main-thread callbacks.
     */
    private fun buildAndPushSnapshot() {
        if (!::player.isInitialized) return

        val inputFormat = currentAudioInputFormat
        val dspInputFormat = audioProcessorManager.nativeDspProcessor.currentInputFormat
        val hiresEnabled = AudioPreferences.isHiresOutputEnabled()

        val outputDevice = currentOutputDevice ?: detectActiveOutputDevice().also {
            currentOutputDevice = it
        }

        // Track metadata from the compressed source format
        val trackFormat = mimeTypeToFormatString(inputFormat?.sampleMimeType)
        val bitDepth = when {
            inputFormat?.pcmEncoding != null && inputFormat.pcmEncoding != Format.NO_VALUE -> {
                pcmEncodingToBitDepth(inputFormat.pcmEncoding)
            }
            else -> 16
        }
        val sampleRateHz = inputFormat?.sampleRate?.takeIf { it > 0 } ?: 0
        val bitrateKbps = (inputFormat?.bitrate?.takeIf { it != Format.NO_VALUE } ?: 0) / 1000
        val channels = inputFormat?.channelCount?.takeIf { it > 0 } ?: 0

        // Decoder info
        val decoderLabel = when {
            currentDecoderName.contains("ffmpeg", ignoreCase = true) -> "FFmpeg"
            currentDecoderName.contains("c2.", ignoreCase = true) -> currentDecoderName
            currentDecoderName != "Unknown" -> currentDecoderName
            AudioPreferences.getAudioDecoder() == AudioPreferences.FFMPEG -> "FFmpeg (pending)"
            else -> "Android Built-in (pending)"
        }

        // Resampler state: compare source sample rate with DSP (post-decode) sample rate
        val inputSampleRate = sampleRateHz
        val dspSampleRateHz = dspInputFormat.sampleRate.takeIf { it > 0 } ?: sampleRateHz
        val outputSampleRate = dspSampleRateHz
        val resamplerQuality = if (inputSampleRate == 0 || inputSampleRate == outputSampleRate) {
            "None"
        } else {
            "Android Built-in"
        }

        // DSP state
        val dspFormatStr = pcmEncodingToFormatString(dspInputFormat.encoding)
        val activeEqName = when {
            !EqualizerPreferences.isEqEnabled() -> null
            EqualizerPreferences.getAllBandGains().all { it == 0f }
                    && EqualizerPreferences.getBassDb() == 0f
                    && EqualizerPreferences.getTrebleDb() == 0f -> "Flat"
            else -> "Custom"
        }
        val stereoExpandPercent = (EqualizerPreferences.getStereoWidth() * 100).roundToInt()

        // Buffer and latency estimation from actual AudioTrack minimum buffer size
        val (buffersStr, latencyEstimateMs) = computeBufferInfo(dspInputFormat)

        // Hardware output device info
        val deviceName = outputDevice?.productName?.toString() ?: "Unknown"
        val deviceBitDepthIn = if (hiresEnabled) 32 else 16
        val deviceBitDepthOut = getDeviceBitDepth(outputDevice, deviceBitDepthIn)
        val deviceSampleRate = getDeviceSampleRate(outputDevice, sampleRateHz)

        val snapshot = AudioPipelineSnapshot(
                trackFormat = trackFormat,
                bitDepth = bitDepth,
                sampleRateHz = sampleRateHz,
                bitrateKbps = bitrateKbps,
                channels = channels,
                decoderName = decoderLabel,
                inputSampleRate = inputSampleRate,
                outputSampleRate = outputSampleRate,
                resamplerQuality = resamplerQuality,
                dspFormat = dspFormatStr,
                dspSampleRate = dspSampleRateHz,
                activeEqName = activeEqName,
                stereoExpandPercent = stereoExpandPercent,
                buffers = buffersStr,
                latencyMs = latencyEstimateMs,
                deviceName = deviceName,
                deviceBitDepthIn = deviceBitDepthIn,
                deviceBitDepthOut = deviceBitDepthOut,
                deviceSampleRate = deviceSampleRate
        )

        AudioPipelineManager.updateSnapshot(snapshot)
        Log.v(TAG, "Pipeline snapshot updated: $trackFormat @ ${sampleRateHz}Hz via $decoderLabel → $deviceName")
    }

    /**
     * Converts a MIME type string (e.g., `"audio/flac"`) to a short human-readable format
     * label (e.g., `"FLAC"`). Falls back to the subtype in uppercase for unknown types.
     *
     * @param mimeType The MIME type from [Format.sampleMimeType], or `null`.
     * @return A short uppercase label describing the audio format.
     */
    private fun mimeTypeToFormatString(mimeType: String?): String = when {
        mimeType == null -> "Unknown"
        mimeType.contains("flac", ignoreCase = true) -> "FLAC"
        mimeType.contains("mp4a") || mimeType.contains("aac") -> "AAC"
        mimeType.contains("mpeg") || mimeType.contains("mp3") -> "MP3"
        mimeType.contains("vorbis") -> "OGG"
        mimeType.contains("opus") -> "OPUS"
        mimeType.contains("wav") || mimeType.contains("wave") -> "WAV"
        mimeType.contains("alac") -> "ALAC"
        mimeType.contains("aiff") -> "AIFF"
        mimeType.contains("wma") -> "WMA"
        mimeType.contains("raw") -> "PCM"
        mimeType.contains("dsd") || mimeType.contains("dsf") -> "DSD"
        mimeType.contains("ape") -> "APE"
        else -> mimeType.substringAfterLast('/', mimeType).uppercase()
    }

    /**
     * Maps a Media3 [C.ENCODING_PCM_*] constant to a bit-depth integer.
     *
     * @param encoding A PCM encoding constant from [C].
     * @return The bit depth (8, 16, 24, or 32), defaulting to 16 for unknown encodings.
     */
    private fun pcmEncodingToBitDepth(encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_8BIT -> 8
        C.ENCODING_PCM_16BIT -> 16
        C.ENCODING_PCM_24BIT -> 24
        C.ENCODING_PCM_32BIT -> 32
        C.ENCODING_PCM_FLOAT -> 32
        else -> 16
    }

    /**
     * Maps a Media3 [C.ENCODING_PCM_*] constant to a human-readable DSP format string.
     *
     * @param encoding A PCM encoding constant from [C].
     * @return A display string such as `"PCM 16-bit"` or `"Float32"`.
     */
    private fun pcmEncodingToFormatString(encoding: Int): String = when (encoding) {
        C.ENCODING_PCM_8BIT -> "PCM 8-bit"
        C.ENCODING_PCM_16BIT -> "PCM 16-bit"
        C.ENCODING_PCM_24BIT -> "PCM 24-bit"
        C.ENCODING_PCM_32BIT -> "PCM 32-bit"
        C.ENCODING_PCM_FLOAT -> "Float32"
        else -> "Unknown"
    }

    /**
     * Estimates the AudioTrack double-buffer size and total audio chain latency for the
     * given [dspInputFormat].
     *
     * Uses [AudioTrack.getMinBufferSize] to derive the minimum frame count at the
     * DSP sample rate, then estimates end-to-end latency as twice the buffer duration
     * plus a fixed 15 ms hardware/driver overhead.
     *
     * @param dspInputFormat The [AudioProcessor.AudioFormat] currently active in [NativeDspAudioProcessor].
     * @return A pair of (human-readable buffer string, estimated latency in ms).
     */
    private fun computeBufferInfo(dspInputFormat: AudioProcessor.AudioFormat): Pair<String, Int> {
        val sr = dspInputFormat.sampleRate.takeIf { it > 0 } ?: 44100
        val ch = dspInputFormat.channelCount.takeIf { it > 0 } ?: 2

        val channelConfig = if (ch == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val afEncoding = when (dspInputFormat.encoding) {
            C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_32BIT -> AudioFormat.ENCODING_PCM_FLOAT
            else -> AudioFormat.ENCODING_PCM_16BIT
        }

        val minBufBytes = AudioTrack.getMinBufferSize(sr, channelConfig, afEncoding).coerceAtLeast(1)
        val bytesPerFrame = when (dspInputFormat.encoding) {
            C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_32BIT -> 4 * ch
            C.ENCODING_PCM_24BIT -> 3 * ch
            else -> 2 * ch
        }

        val framesInBuffer = minBufBytes / bytesPerFrame.coerceAtLeast(1)
        val bufferMs = if (sr > 0) framesInBuffer * 1000 / sr else 0
        // Double-buffer (2×) is ExoPlayer's DefaultAudioSink default; add 15 ms for hardware latency.
        val latencyEstimate = bufferMs * 2 + 15

        return Pair("2x (${bufferMs}ms, $framesInBuffer frames)", latencyEstimate)
    }

    /**
     * Selects the highest-priority active audio output device from the system device list.
     *
     * Priority order: USB headset / USB device → Bluetooth A2DP → Bluetooth SCO →
     * wired headset → wired headphones → built-in earpiece → built-in speaker → other.
     *
     * @return The best-matching [AudioDeviceInfo], or `null` if no output devices are found.
     */
    private fun detectActiveOutputDevice(): AudioDeviceInfo? {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.maxByOrNull { outputDevicePriority(it.type) }
    }

    /**
     * Returns a numeric priority for the given [AudioDeviceInfo] type so the most
     * desirable (highest fidelity) output device wins in [detectActiveOutputDevice].
     *
     * @param type An [AudioDeviceInfo.TYPE_*] constant.
     * @return Priority integer; higher means more preferred.
     */
    private fun outputDevicePriority(type: Int): Int = when (type) {
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET -> 100
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 80
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> 75
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> 60
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> 55
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> 20
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 10
        else -> 0
    }

    /**
     * Returns the maximum PCM bit depth supported by [device] by inspecting
     * [AudioDeviceInfo.getEncodings]. Falls back to [fallback] when the device
     * reports no encodings or when [device] is `null`.
     *
     * @param device   The output device to inspect, or `null`.
     * @param fallback Bit depth to return when no encoding info is available.
     * @return Maximum supported bit depth: 8, 16, 24, or 32.
     */
    private fun getDeviceBitDepth(device: AudioDeviceInfo?, fallback: Int): Int {
        device ?: return fallback
        val encodings = device.encodings
        if (encodings.isEmpty()) return fallback
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && encodings.contains(AudioFormat.ENCODING_PCM_32BIT) -> 32
            encodings.contains(AudioFormat.ENCODING_PCM_FLOAT) -> 32
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && encodings.contains(AudioFormat.ENCODING_PCM_24BIT_PACKED) -> 24
            encodings.contains(AudioFormat.ENCODING_PCM_16BIT) -> 16
            else -> fallback
        }
    }

    /**
     * Returns the best matching sample rate supported by [device] for the given [sourceSampleRate].
     *
     * Prefers the highest rate that does not exceed [sourceSampleRate] so the hardware
     * does not up-sample unnecessarily. If all device rates are above the source rate the
     * minimum device rate is returned. Returns [sourceSampleRate] when [device] is `null`
     * or its sample-rate list is empty.
     *
     * @param device           The output device to inspect, or `null`.
     * @param sourceSampleRate The source track's sample rate in Hz.
     * @return The best-matching hardware sample rate in Hz.
     */
    private fun getDeviceSampleRate(device: AudioDeviceInfo?, sourceSampleRate: Int): Int {
        device ?: return sourceSampleRate
        val rates = device.sampleRates
        if (rates.isEmpty()) return sourceSampleRate
        return rates.filter { it <= sourceSampleRate }.maxOrNull()
                ?: rates.minOrNull()
                ?: sourceSampleRate
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

        private val toggleRepeatCommand = SessionCommand(COMMAND_TOGGLE_REPEAT, Bundle.EMPTY)

        /**
         * Advertise the custom repeat command so the system notification controller can use it.
         */
        override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(toggleRepeatCommand)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        /**
         * Handle the repeat toggle command sent from the notification button.
         */
        override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == COMMAND_TOGGLE_REPEAT) {
                val current = PlayerPreferences.getRepeatMode()
                val next = when (current) {
                    MediaConstants.REPEAT_OFF -> MediaConstants.REPEAT_QUEUE
                    MediaConstants.REPEAT_QUEUE -> MediaConstants.REPEAT_ONE
                    else -> MediaConstants.REPEAT_OFF
                }
                PlayerPreferences.setRepeatMode(next)
                applyRepeatMode(next)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

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

        /**
         * Fraction of a song's duration that must have elapsed before a transition is NOT
         * counted as a skip. Songs navigated away from before this threshold increment
         * the skip counter in the song statistics database.
         */
        private const val SKIP_THRESHOLD = 0.30

        /** Custom session command sent when the user taps the repeat button in the notification. */
        const val COMMAND_TOGGLE_REPEAT = "app.simple.felicity.TOGGLE_REPEAT"
    }
}