package app.simple.felicity.engine.managers

import app.simple.felicity.engine.model.AudioPipelineSnapshot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton manager that owns and exposes the live [AudioPipelineSnapshot] state flow.
 *
 * [FelicityPlayerService] pushes an updated snapshot on every track transition,
 * output-device change, decoder initialization, or periodic 3-second pulse while
 * playback is active. The UI layer observes [snapshotFlow] as a read-only
 * [StateFlow] and never writes to it directly.
 *
 * The flow starts with `null` and is reset to `null` when the service is destroyed or
 * the queue becomes empty, so observers can distinguish "no data yet" from a real snapshot.
 *
 * @author Hamza417
 */
object AudioPipelineManager {

    private val _snapshotFlow = MutableStateFlow<AudioPipelineSnapshot?>(null)

    /**
     * Read-only view of the most-recently pushed [AudioPipelineSnapshot].
     *
     * Emits `null` when no media is loaded or before the first snapshot has been
     * computed after the service starts.
     */
    val snapshotFlow: StateFlow<AudioPipelineSnapshot?> = _snapshotFlow.asStateFlow()

    private val _refreshRequestFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Hot flow that emits a [Unit] whenever any observer calls [requestRefresh].
     *
     * [FelicityPlayerService] collects this flow and responds by calling
     * `buildAndPushSnapshot()` immediately, bypassing the 3-second pulse interval.
     */
    val refreshRequestFlow: SharedFlow<Unit> = _refreshRequestFlow.asSharedFlow()

    /**
     * Asks the service to immediately push a fresh [AudioPipelineSnapshot].
     *
     * Safe to call from any thread. Typically called by [AudioPipelineDialog] when
     * it opens so the first snapshot appears without waiting for the periodic pulse.
     */
    fun requestRefresh() {
        _refreshRequestFlow.tryEmit(Unit)
    }

    /**
     * Replaces the current snapshot value and notifies all active collectors.
     *
     * This method is `internal` so only the engine module (i.e. [FelicityPlayerService])
     * can push updates; UI modules observe [snapshotFlow] but cannot mutate it.
     *
     * @param snapshot The fully-populated snapshot to publish, or `null` to clear the
     *                 state on service teardown or when the queue is empty.
     */
    internal fun updateSnapshot(snapshot: AudioPipelineSnapshot?) {
        _snapshotFlow.value = snapshot
    }
}

