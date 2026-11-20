package app.simple.felicity.engine.managers

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
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
import app.simple.felicity.preferences.AudioPreferences

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
 * was pre-rolling are swallowed and the relevant ones are synthesized at swap time.
 */
@UnstableApi
class CrossfadePlayerManager(
        private val context: Context,
        private val sharedMixer: SharedAudioMixer,
        private val audioProcessorManager: AudioProcessorManager,
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

    /**
     * The two slots are always in exactly one of these states.
     *  - [Steady]: [active] plays, [idle] is stopped and empty.
     *  - [Prerolling]: [idle] is buffering the single target item.
     *  - [Transitioning]: the roles were already swapped. [active] is the incoming track and
     */
    private sealed class State {
        object Steady : State()

        class Prerolling(
                val targetIndex: Int,
                val mediaId: String,
                val itemCount: Int,
                val manual: Boolean,
                val requestedAtMs: Long
        ) : State() {
            /** Whether [idle] was already given the target item. */
            var prepared = false

            fun matches(index: Int, id: String, count: Int) =
                targetIndex == index && mediaId == id && itemCount == count
        }

        /**
         * [outgoingSlot] (which is [idle]) is fading out inside the mixer.
         */
        class Transitioning(val token: Int, val outgoingSlot: Slot) : State()
    }

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

    private var state: State = State.Steady
    private var fadeToken = 0

    // Set while this class seeks the incoming player itself, so the resulting events do not abort the fade.
    private var ignoringInternalSeek = false
    private var released = false

    private var sessionPlayer: ForwardingPlayer? = null

    /** Length of the overlap. Set to 0 to turn crossfading off. */
    @Volatile
    var crossfadeDurationMs: Long = AudioPreferences.DEFAULT_CROSSFADE_DURATION

    var eventListener: Player.Listener? = null
    var analyticsListener: AnalyticsListener? = null
    var onActivePlayerChanged: ((Player) -> Unit)? = null

    /**
     * Resolves the linear ReplayGain for an item (1.0 when it has none). Called on the main
     * thread, so it must not block.
     */
    var replayGainResolver: ((MediaItem?) -> Float)? = null

    private fun resolveReplayGain(item: MediaItem?): Float = replayGainResolver?.invoke(item) ?: 1.0f

    /**
     * Re-evaluates the ReplayGain of the active track, and of a pre-rolled one. The outgoing
     * slot of a running crossfade is deliberately left alone so it fades with its own gain.
     */
    fun refreshReplayGain() {
        if (released) return
        val act = active
        act.sink.replayGainLinear = resolveReplayGain(act.player.currentMediaItem)
        val st = state
        if (st is State.Prerolling && st.prepared) {
            val inn = idle
            inn.sink.replayGainLinear = resolveReplayGain(inn.player.currentMediaItem)
        }
    }
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
        sharedMixer.onCrossfadeComplete = { sink, token -> mainHandler.post { onFadeFinished(sink, token) } }
        sharedMixer.start()
        mainHandler.postDelayed(tickRunnable, TICK_MS)
    }

    // ---------------------------------------------------------------------------------------------
    // Construction
    // ---------------------------------------------------------------------------------------------

    private fun buildSlot(handleAudioFocus: Boolean): Slot {
        val sink = VirtualAudioSink(sharedMixer)
        // Every call builds new processor instances. Sharing one between the slots would let the
        // pre-rolling idle player flush the state of the one that is playing.
        sink.setAudioProcessors(audioProcessorManager.getPreMixProcessors())

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
            if (state is State.Transitioning &&
                    (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED)) {
                abortCrossfade()
            }
            eventListener?.onPlaybackStateChanged(playbackState)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isActive()) eventListener?.onIsPlayingChanged(isPlaying)
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!isActive()) return
            abortCrossfade()
            eventListener?.onPlayerError(error)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!isActive()) return
            if (!playWhenReady) {
                val st = state
                if (st is State.Prerolling && st.manual) invalidatePreroll()
                abortCrossfade()
            }
            eventListener?.onPlayWhenReadyChanged(playWhenReady, reason)
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            // The queue was edited, so whatever was pre-rolled may no longer be the next item.
            val st = state
            if (isActive() && st is State.Prerolling && !st.manual) invalidatePreroll()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (!isActive()) return
            slot.sink.replayGainLinear = resolveReplayGain(mediaItem)
            if (!ignoringInternalSeek &&
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
            if (!ignoringInternalSeek &&
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
        val st = state
        if (st is State.Transitioning) return

        val out = active.player
        val inn = idle.player

        if (st is State.Prerolling && st.manual) {
            val target = st.targetIndex
            val valid = target in 0 until out.mediaItemCount
            if (valid && crossfadeDurationMs > 0 && out.playbackState == Player.STATE_READY) {
                handleManualRequest(out, inn, st)
            } else {
                invalidatePreroll()
                if (valid) out.seekToDefaultPosition(target)
            }
            return
        }

        val next = out.nextMediaItemIndex
        val crossfadeAllowed = crossfadeDurationMs > 0 &&
                out.playWhenReady &&
                out.playbackState == Player.STATE_READY &&
                !out.pauseAtEndOfMediaItems &&
                out.repeatMode != Player.REPEAT_MODE_ONE &&
                next != C.INDEX_UNSET
        if (!crossfadeAllowed) {
            invalidatePreroll()
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

        val mediaId = out.getMediaItemAt(next).mediaId
        val current = st as? State.Prerolling
        val preroll = if (current != null && current.matches(next, mediaId, out.mediaItemCount)) {
            current
        } else {
            State.Prerolling(next, mediaId, out.mediaItemCount, false, SystemClock.elapsedRealtime())
        }
        ensurePreroll(out, inn, preroll)

        if (remaining <= overlap && inn.playbackState == Player.STATE_READY) {
            startCrossfade(preroll, auto = true, durationMs = minOf(overlap, remaining.coerceAtLeast(1)))
        }
    }

    private fun handleManualRequest(out: ExoPlayer, inn: ExoPlayer, requested: State.Prerolling) {
        // The queue may have changed since the request was made.
        val target = requested.targetIndex
        val mediaId = out.getMediaItemAt(target).mediaId
        val st = if (requested.matches(target, mediaId, out.mediaItemCount)) {
            requested
        } else {
            State.Prerolling(target, mediaId, out.mediaItemCount, true, requested.requestedAtMs)
        }

        ensurePreroll(out, inn, st)
        val waited = SystemClock.elapsedRealtime() - st.requestedAtMs
        if (inn.playbackState == Player.STATE_READY) {
            val duration = out.duration
            val remaining = if (duration == C.TIME_UNSET) Long.MAX_VALUE else duration - out.currentPosition
            startCrossfade(st, auto = false, durationMs = minOf(crossfadeDurationMs, remaining.coerceAtLeast(1)))
        } else if (waited > MANUAL_WAIT_MS) {
            // Next track did not get ready in time; fall back to an ordinary skip.
            invalidatePreroll()
            out.seekToDefaultPosition(target)
        }
    }

    /** Makes [st] the current state and gives [idle] its item, unless that already happened. */
    private fun ensurePreroll(out: ExoPlayer, inn: ExoPlayer, st: State.Prerolling) {
        state = st
        if (st.prepared) return

        copySettings(out, inn)
        inn.playWhenReady = false
        val targetItem = out.getMediaItemAt(st.targetIndex)
        idle.sink.replayGainLinear = resolveReplayGain(targetItem)
        inn.setMediaItem(targetItem, 0L)
        inn.prepare()
        st.prepared = true
    }

    /** Back to [State.Steady]: stops [idle] if it was buffering something. */
    private fun invalidatePreroll() {
        val st = state as? State.Prerolling ?: return
        state = State.Steady
        if (st.prepared) {
            val inn = idle.player
            inn.stop()
            inn.clearMediaItems()
        }
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

    private fun startCrossfade(preroll: State.Prerolling, auto: Boolean, durationMs: Long) {
        val outSlot = active
        val inSlot = idle
        val out = outSlot.player
        val inn = inSlot.player

        val oldIndex = out.currentMediaItemIndex
        val oldItem = out.currentMediaItem
        val oldPosition = out.currentPosition

        val targetIndex = preroll.targetIndex

        val token = ++fadeToken
        state = State.Transitioning(token, outSlot)

        copySettings(out, inn)

        // Sync the full timeline to the incoming player.
        // It currently only holds the target item at index 0.
        if (targetIndex != C.INDEX_UNSET && targetIndex < out.mediaItemCount) {
            if (targetIndex > 0) {
                val before = (0 until targetIndex).map { out.getMediaItemAt(it) }
                inn.addMediaItems(0, before)
            }
            if (targetIndex + 1 < out.mediaItemCount) {
                val after = (targetIndex + 1 until out.mediaItemCount).map { out.getMediaItemAt(it) }
                inn.addMediaItems(inn.mediaItemCount, after)
            }
        }

        // Hand audio focus over so the incoming player does not make the outgoing one pause itself.
        out.setAudioAttributes(audioAttributes, false)
        inn.setAudioAttributes(audioAttributes, true)

        activeIsA = !activeIsA

        // The outgoing player must not advance into the next item on its own anymore.
        out.repeatMode = Player.REPEAT_MODE_OFF
        if (oldIndex + 1 < out.mediaItemCount) {
            out.removeMediaItems(oldIndex + 1, out.mediaItemCount)
        }

        // Registered before the incoming sink starts so it can never be audible at full level.
        sharedMixer.beginCrossfade(outSlot.sink, inSlot.sink, durationMs, token)

        publishSwap(oldIndex, oldItem, oldPosition, auto)
        inn.playWhenReady = true

        scheduleFadeTimeout(token, durationMs)
    }

    private val fadeTimeout = object : Runnable {
        var token = 0

        override fun run() {
            val st = state as? State.Transitioning ?: return
            if (released || st.token != token) return
            // Normally the mixer reports the end of the fade. Getting here means it did not, so
            // fade the outgoing track out quickly and force the transition shut if even that fails.
            abortCrossfade()
            mainHandler.postDelayed({ if (state === st) finishTransition(st) }, ABORT_GRACE_MS)
        }
    }

    private fun scheduleFadeTimeout(token: Int, durationMs: Long) {
        mainHandler.removeCallbacks(fadeTimeout)
        fadeTimeout.token = token
        mainHandler.postDelayed(fadeTimeout, durationMs + FADE_TIMEOUT_SLACK_MS)
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

    /** Fades the outgoing track out over a few milliseconds; the mixer reports back when done. */
    private fun abortCrossfade() {
        val st = state as? State.Transitioning ?: return
        sharedMixer.abortOutgoing(st.outgoingSlot.sink, ABORT_FADE_MS, notify = true)
    }

    private fun onFadeFinished(sink: VirtualAudioSink, token: Int) {
        if (released) return
        val st = state as? State.Transitioning ?: return
        // The incoming sink reports its own fade-in as well, which does not end the transition.
        if (st.token != token || st.outgoingSlot.sink !== sink) return
        finishTransition(st)
    }

    private fun finishTransition(st: State.Transitioning) {
        mainHandler.removeCallbacks(fadeTimeout)
        state = State.Steady
        val slot = st.outgoingSlot
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

    private inner class SessionPlayer(val internalPlayer: ExoPlayer) : ForwardingPlayer(internalPlayer) {

        override fun seekToNext() {
            if (!requestCrossfadeTo(internalPlayer.nextMediaItemIndex)) {
                super.seekToNext()
            }
        }

        override fun seekToNextMediaItem() {
            if (!requestCrossfadeTo(internalPlayer.nextMediaItemIndex)) {
                super.seekToNextMediaItem()
            }
        }

        override fun seekToPrevious() {
            if (!requestCrossfadeTo(internalPlayer.previousMediaItemIndex)) {
                super.seekToPrevious()
            }
        }

        override fun seekToPreviousMediaItem() {
            if (!requestCrossfadeTo(internalPlayer.previousMediaItemIndex)) {
                super.seekToPreviousMediaItem()
            }
        }

        override fun seekToDefaultPosition(mediaItemIndex: Int) {
            if (!requestCrossfadeTo(mediaItemIndex)) {
                super.seekToDefaultPosition(mediaItemIndex)
            }
        }

        override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
            if ((positionMs == C.TIME_UNSET || positionMs == 0L) && mediaItemIndex != currentMediaItemIndex) {
                if (requestCrossfadeTo(mediaItemIndex)) return
            }

            super.seekTo(mediaItemIndex, positionMs)
        }

        override fun getCurrentMediaItemIndex(): Int {
            val pending = pendingManualIndex()
            return if (pending != C.INDEX_UNSET) pending else super.getCurrentMediaItemIndex()
        }

        override fun getCurrentPeriodIndex(): Int {
            val pending = pendingManualIndex()
            return if (pending != C.INDEX_UNSET) {
                val timeline = currentTimeline
                if (!timeline.isEmpty && pending >= 0 && pending < timeline.windowCount) {
                    timeline.getWindow(pending, Timeline.Window()).firstPeriodIndex
                } else pending
            } else {
                super.getCurrentPeriodIndex()
            }
        }

        override fun getCurrentPosition(): Long {
            return if (pendingManualIndex() != C.INDEX_UNSET) 0L else super.getCurrentPosition()
        }

        override fun getContentPosition(): Long {
            return if (pendingManualIndex() != C.INDEX_UNSET) 0L else super.getContentPosition()
        }

        override fun getCurrentMediaItem(): MediaItem? {
            val pending = pendingManualIndex()
            return if (pending != C.INDEX_UNSET && pending >= 0 && pending < internalPlayer.mediaItemCount) {
                internalPlayer.getMediaItemAt(pending)
            } else {
                super.getCurrentMediaItem()
            }
        }
    }

    /** Target of a manual skip that is still waiting for [idle] to get ready, or [C.INDEX_UNSET]. */
    private fun pendingManualIndex(): Int {
        val st = state
        return if (st is State.Prerolling && st.manual) st.targetIndex else C.INDEX_UNSET
    }

    private fun requestCrossfadeTo(targetIndex: Int): Boolean {
        if (!AudioPreferences.isCrossfadeManualSkipEnabled()) return false
        if (released || crossfadeDurationMs <= 0) return false
        val cur = active.player
        if (!cur.playWhenReady) return false
        if (targetIndex == C.INDEX_UNSET || targetIndex < 0 || targetIndex >= cur.mediaItemCount) return false

        val st = state
        if (st is State.Transitioning) {
            if (targetIndex == cur.currentMediaItemIndex) return false
            retargetIncoming(st, cur, targetIndex)
            return true
        }

        if (cur.playbackState != Player.STATE_READY) return false

        val mediaId = cur.getMediaItemAt(targetIndex).mediaId
        val request = State.Prerolling(
                targetIndex, mediaId, cur.mediaItemCount, true, SystemClock.elapsedRealtime())
        // Keep a pre-roll that already holds this very item instead of loading it again.
        if (st is State.Prerolling && st.prepared && st.matches(targetIndex, mediaId, cur.mediaItemCount)) {
            request.prepared = true
        }
        state = request
        tick()
        return true
    }

    /**
     * Points the fade-in at another track while the outgoing one keeps fading out untouched. The
     * incoming player already holds the whole queue, so it just seeks.
     */
    private fun retargetIncoming(st: State.Transitioning, incoming: ExoPlayer, targetIndex: Int) {
        ignoringInternalSeek = true
        try {
            incoming.seekTo(targetIndex, 0L)
        } finally {
            ignoringInternalSeek = false
        }
        active.sink.replayGainLinear = resolveReplayGain(incoming.getMediaItemAt(targetIndex))
        sharedMixer.retargetIncoming(active.sink, crossfadeDurationMs)
        scheduleFadeTimeout(st.token, crossfadeDurationMs)
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
        const val TICK_MS = 200L
        const val PREROLL_LEAD_MS = 6000L
        const val MANUAL_WAIT_MS = 2500L
        const val FADE_TIMEOUT_SLACK_MS = 5000L
        const val ABORT_FADE_MS = 50L
        const val ABORT_GRACE_MS = 1000L
    }
}
