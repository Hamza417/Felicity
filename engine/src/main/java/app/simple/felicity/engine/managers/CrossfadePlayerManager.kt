package app.simple.felicity.engine.managers

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import app.simple.felicity.engine.audio.SharedAudioMixer
import app.simple.felicity.engine.audio.VirtualAudioSink

/**
 * Owns two [ExoPlayer] instances (A and B) that both render into the same [SharedAudioMixer]
 * through their own [VirtualAudioSink], and drives crossfades between them.
 *
 * Near the end of a track the idle player is pre-rolled with the next item. When the overlap
 * window opens, the roles are swapped: the pre-rolled player becomes the active one (the one the
 * service and the UI talk to), the mixer ramps the two sinks against each other, and the old
 * player is stopped once the ramp has completed.
 *
 * Because the service only ever sees the active player, events the idle player produced while it
 * was pre-rolling are swallowed and the relevant ones are synthesised at swap time.
 */
@UnstableApi
class CrossfadePlayerManager(
        private val context: Context,
        private val sharedMixer: SharedAudioMixer,
        private val extensionRendererMode: Int,
        private val loadControl: LoadControl
) {

    private class Slot(val sink: VirtualAudioSink, val player: ExoPlayer) {
        var decoderEvent: DecoderEvent? = null
        var formatEvent: FormatEvent? = null
    }

    private class DecoderEvent(
            val eventTime: AnalyticsListener.EventTime,
            val name: String,
            val timestampMs: Long,
            val durationMs: Long
    )

    private class FormatEvent(
            val eventTime: AnalyticsListener.EventTime,
            val format: Format,
            val reuse: DecoderReuseEvaluation?
    )

    private data class PrerollKey(val index: Int, val count: Int, val mediaId: String)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .setSpatializationBehavior(C.SPATIALIZATION_BEHAVIOR_NEVER)
        .build()

    // DefaultLoadControl refuses to be shared between players on different playback threads.
    private val playbackThread = HandlerThread("Felicity-Playback", Process.THREAD_PRIORITY_AUDIO).also {
        it.start()
    }

    private val slotA: Slot
    private val slotB: Slot
    private var activeIsA = true

    private var crossfading = false
    private var fadeToken = 0
    private var prerollKey: PrerollKey? = null
    private var manualRequestedAtMs = 0L
    private var manualPending = false
    private var released = false

    private var sessionPlayer: ForwardingPlayer? = null

    /** Length of the overlap. Set to 0 to turn crossfading off. */
    @Volatile
    var crossfadeDurationMs: Long = DEFAULT_CROSSFADE_MS

    var eventListener: Player.Listener? = null
    var analyticsListener: AnalyticsListener? = null
    var onActivePlayerChanged: ((Player) -> Unit)? = null

    private val active: Slot get() = if (activeIsA) slotA else slotB
    private val idle: Slot get() = if (activeIsA) slotB else slotA

    val activePlayer: ExoPlayer?
        get() = if (released) null else active.player

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (released) return
            try {
                tick()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Crossfade monitor failed", e)
            }
            mainHandler.postDelayed(this, TICK_MS)
        }
    }

    init {
        slotA = buildSlot(handleAudioFocus = true)
        slotB = buildSlot(handleAudioFocus = false)
        sharedMixer.sinkA = slotA.sink
        sharedMixer.sinkB = slotB.sink
        sharedMixer.onCrossfadeComplete = { sink -> mainHandler.post { onFadeFinished(sink) } }
        sharedMixer.start()
        mainHandler.postDelayed(tickRunnable, TICK_MS)
    }

    // ---------------------------------------------------------------------------------------------
    // Construction
    // ---------------------------------------------------------------------------------------------

    private fun buildSlot(handleAudioFocus: Boolean): Slot {
        val sink = VirtualAudioSink(sharedMixer)
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableOffload: Boolean
            ): AudioSink = sink
        }
        renderersFactory.setExtensionRendererMode(extensionRendererMode)
        renderersFactory.setEnableDecoderFallback(true)

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(audioAttributes, handleAudioFocus)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setLoadControl(loadControl)
            .setPlaybackLooper(playbackThread.looper)
            .build()

        val slot = Slot(sink, player)
        player.addListener(PlayerEvents(slot))
        player.addAnalyticsListener(AnalyticsEvents(slot))
        return slot
    }

    private inner class PlayerEvents(private val slot: Slot) : Player.Listener {

        private fun isActive() = !released && slot === active

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (!isActive()) return
            if (crossfading && (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED)) {
                abortCrossfade()
            }
            eventListener?.onPlaybackStateChanged(playbackState)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isActive()) eventListener?.onIsPlayingChanged(isPlaying)
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!isActive()) return
            if (crossfading) abortCrossfade()
            eventListener?.onPlayerError(error)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!isActive()) return
            if (!playWhenReady) {
                manualPending = false
                if (crossfading) abortCrossfade()
            }
            eventListener?.onPlayWhenReadyChanged(playWhenReady, reason)
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            // The queue was edited, so whatever was pre-rolled may no longer be the next item.
            if (isActive() && !crossfading) invalidatePreroll()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (!isActive()) return
            if (crossfading &&
                    (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED ||
                            reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)) {
                abortCrossfade()
            }
            eventListener?.onMediaItemTransition(mediaItem, reason)
        }

        override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
        ) {
            if (!isActive()) return
            if (crossfading &&
                    (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_REMOVE)) {
                abortCrossfade()
            }
            eventListener?.onPositionDiscontinuity(oldPosition, newPosition, reason)
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (isActive()) eventListener?.onAudioSessionIdChanged(audioSessionId)
        }
    }

    private inner class AnalyticsEvents(private val slot: Slot) : AnalyticsListener {

        override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
        ) {
            slot.decoderEvent = DecoderEvent(eventTime, decoderName, initializedTimestampMs, initializationDurationMs)
            if (!released && slot === active) {
                analyticsListener?.onAudioDecoderInitialized(eventTime, decoderName, initializedTimestampMs, initializationDurationMs)
            }
        }

        override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            slot.formatEvent = FormatEvent(eventTime, format, decoderReuseEvaluation)
            if (!released && slot === active) {
                analyticsListener?.onAudioInputFormatChanged(eventTime, format, decoderReuseEvaluation)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Monitoring
    // ---------------------------------------------------------------------------------------------

    private fun tick() {
        if (crossfading) return

        val out = active.player
        val inn = idle.player
        val next = out.nextMediaItemIndex

        val crossfadeAllowed = crossfadeDurationMs > 0 &&
                out.playWhenReady &&
                out.playbackState == Player.STATE_READY &&
                !out.pauseAtEndOfMediaItems &&
                out.repeatMode != Player.REPEAT_MODE_ONE &&
                next != C.INDEX_UNSET
        if (!crossfadeAllowed) {
            manualPending = false
            invalidatePreroll()
            return
        }

        if (manualPending) {
            handleManualRequest(out, inn, next)
            return
        }

        val duration = out.duration
        if (duration == C.TIME_UNSET || duration <= 0) {
            invalidatePreroll()
            return
        }

        val remaining = duration - out.currentPosition
        val overlap = minOf(crossfadeDurationMs, duration / 2)

        if (remaining > overlap + PREROLL_LEAD_MS) {
            invalidatePreroll()
            return
        }

        ensurePreroll(out, inn, next)

        if (remaining <= overlap && inn.playbackState == Player.STATE_READY) {
            startCrossfade(auto = true, durationMs = minOf(overlap, remaining.coerceAtLeast(1)))
        }
    }

    private fun handleManualRequest(out: ExoPlayer, inn: ExoPlayer, next: Int) {
        ensurePreroll(out, inn, next)
        val waited = android.os.SystemClock.elapsedRealtime() - manualRequestedAtMs
        if (inn.playbackState == Player.STATE_READY) {
            manualPending = false
            val duration = out.duration
            val remaining = if (duration == C.TIME_UNSET) Long.MAX_VALUE else duration - out.currentPosition
            startCrossfade(auto = false, durationMs = minOf(crossfadeDurationMs, remaining.coerceAtLeast(1)))
        } else if (waited > MANUAL_WAIT_MS) {
            // Next track did not get ready in time; fall back to an ordinary skip.
            manualPending = false
            out.seekToNextMediaItem()
        }
    }

    private fun ensurePreroll(out: ExoPlayer, inn: ExoPlayer, next: Int) {
        val key = PrerollKey(next, out.mediaItemCount, out.getMediaItemAt(next).mediaId)
        if (prerollKey == key) return

        val items = (0 until out.mediaItemCount).map { out.getMediaItemAt(it) }
        copySettings(out, inn)
        inn.playWhenReady = false
        inn.setMediaItems(items, next, 0L)
        inn.prepare()
        prerollKey = key
    }

    private fun invalidatePreroll() {
        if (prerollKey == null) return
        prerollKey = null
        val inn = idle.player
        inn.stop()
        inn.clearMediaItems()
    }

    private fun copySettings(from: ExoPlayer, to: ExoPlayer) {
        // Must happen before the idle player renders anything, otherwise its sink would push its
        // own default speed/skip-silence values to the shared hardware sink.
        to.playbackParameters = from.playbackParameters
        to.skipSilenceEnabled = from.skipSilenceEnabled
        to.repeatMode = from.repeatMode
        to.pauseAtEndOfMediaItems = from.pauseAtEndOfMediaItems
        to.volume = 1f
    }

    // ---------------------------------------------------------------------------------------------
    // Crossfade
    // ---------------------------------------------------------------------------------------------

    private fun startCrossfade(auto: Boolean, durationMs: Long) {
        val outSlot = active
        val inSlot = idle
        val out = outSlot.player
        val inn = inSlot.player

        val oldIndex = out.currentMediaItemIndex
        val oldItem = out.currentMediaItem
        val oldPosition = out.currentPosition

        crossfading = true
        manualPending = false
        prerollKey = null
        val token = ++fadeToken

        copySettings(out, inn)

        // Hand audio focus over so the incoming player does not make the outgoing one pause itself.
        out.setAudioAttributes(audioAttributes, false)
        inn.setAudioAttributes(audioAttributes, true)

        activeIsA = !activeIsA

        // The outgoing player must not advance into the next item on its own any more.
        out.repeatMode = Player.REPEAT_MODE_OFF
        if (oldIndex + 1 < out.mediaItemCount) {
            out.removeMediaItems(oldIndex + 1, out.mediaItemCount)
        }

        // Registered before the incoming sink starts so it can never be audible at full level.
        sharedMixer.beginCrossfade(outSlot.sink, inSlot.sink, durationMs)

        publishSwap(oldIndex, oldItem, oldPosition, auto)
        inn.playWhenReady = true

        mainHandler.postDelayed({
                                    if (!released && crossfading && fadeToken == token) abortCrossfade()
                                }, durationMs + FADE_TIMEOUT_SLACK_MS)
    }

    private fun publishSwap(oldIndex: Int, oldItem: MediaItem?, oldPositionMs: Long, auto: Boolean) {
        val inn = active.player
        val newIndex = inn.currentMediaItemIndex
        val newItem = inn.currentMediaItem
        val newPositionMs = inn.currentPosition

        val oldInfo = Player.PositionInfo(
                null, oldIndex, oldItem, null, oldIndex, oldPositionMs, oldPositionMs,
                C.INDEX_UNSET, C.INDEX_UNSET)
        val newInfo = Player.PositionInfo(
                null, newIndex, newItem, null, newIndex, newPositionMs, newPositionMs,
                C.INDEX_UNSET, C.INDEX_UNSET)

        sessionPlayer = null
        onActivePlayerChanged?.invoke(getSessionPlayer()!!)

        eventListener?.let {
            it.onPositionDiscontinuity(
                    oldInfo, newInfo,
                    if (auto) Player.DISCONTINUITY_REASON_AUTO_TRANSITION else Player.DISCONTINUITY_REASON_SEEK)
            it.onMediaItemTransition(
                    newItem,
                    if (auto) Player.MEDIA_ITEM_TRANSITION_REASON_AUTO else Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
        }

        // Those were swallowed while this player was pre-rolling.
        val slot = active
        analyticsListener?.let { listener ->
            slot.decoderEvent?.let {
                listener.onAudioDecoderInitialized(it.eventTime, it.name, it.timestampMs, it.durationMs)
            }
            slot.formatEvent?.let { listener.onAudioInputFormatChanged(it.eventTime, it.format, it.reuse) }
        }
    }

    private fun abortCrossfade() {
        sharedMixer.finishCrossfade()
    }

    private fun onFadeFinished(sink: VirtualAudioSink) {
        if (released) return
        val slot = if (slotA.sink === sink) slotA else slotB
        crossfading = false
        fadeToken++
        if (slot !== active) {
            slot.player.stop()
            slot.player.clearMediaItems()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------------------------

    /**
     * The player to hand to a [androidx.media3.session.MediaSession]. Next-track requests made
     * through it are crossfaded; everything else is passed through to the active player.
     */
    fun getSessionPlayer(): Player? {
        if (released) return null
        return sessionPlayer ?: SessionPlayer(active.player).also { sessionPlayer = it }
    }

    private inner class SessionPlayer(player: ExoPlayer) : ForwardingPlayer(player) {

        override fun seekToNext() {
            if (!requestCrossfadeToNext()) super.seekToNext()
        }

        override fun seekToNextMediaItem() {
            if (!requestCrossfadeToNext()) super.seekToNextMediaItem()
        }
    }

    private fun requestCrossfadeToNext(): Boolean {
        val out = active.player
        if (released || crossfading || crossfadeDurationMs <= 0) return false
        if (!out.playWhenReady || out.playbackState != Player.STATE_READY) return false
        if (out.pauseAtEndOfMediaItems || out.repeatMode == Player.REPEAT_MODE_ONE) return false
        if (out.nextMediaItemIndex == C.INDEX_UNSET || out.duration == C.TIME_UNSET) return false
        manualRequestedAtMs = android.os.SystemClock.elapsedRealtime()
        manualPending = true
        tick()
        return true
    }

    fun play() {
        active.player.play()
    }

    fun pause() {
        active.player.pause()
    }

    fun release() {
        if (released) return
        released = true
        mainHandler.removeCallbacksAndMessages(null)
        sharedMixer.onCrossfadeComplete = null
        sessionPlayer = null

        // Players first, so their sinks are gone before the mixer thread and hardware sink are.
        slotA.player.release()
        slotB.player.release()
        sharedMixer.stop()
        playbackThread.quitSafely()
    }

    private companion object {
        const val TAG = "CrossfadePlayerManager"
        const val DEFAULT_CROSSFADE_MS = 3000L
        const val TICK_MS = 200L
        const val PREROLL_LEAD_MS = 6000L
        const val MANUAL_WAIT_MS = 2500L
        const val FADE_TIMEOUT_SLACK_MS = 5000L
    }
}
