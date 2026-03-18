package app.simple.felicity.engine.managers

import app.simple.felicity.engine.managers.VisualizerManager.BAND_COUNT
import app.simple.felicity.engine.managers.VisualizerManager.emit
import app.simple.felicity.engine.managers.VisualizerManager.spectrumFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Singleton bridge that relays real-time audio spectrum data from the audio thread
 * (inside [app.simple.felicity.engine.services.FelicityPlayerService]) to any UI
 * subscriber via a [SharedFlow].
 *
 * The service feeds 40-band spectrum arrays on the audio thread by calling [emit];
 * UI components collect on the main thread via [spectrumFlow] using
 * `lifecycleScope.launch { spectrumFlow.collect { ... } }`.
 *
 * [emit] calls `MutableSharedFlow.tryEmit` directly — no coroutine is launched and
 * no thread switch occurs — so band data reaches collectors with zero additional
 * scheduling latency. `BufferOverflow.DROP_OLDEST` ensures `tryEmit` never fails
 * even if a collector is momentarily slow.
 *
 * The flow replays the last value (replay = 1) so that a new collector immediately
 * receives the most recently computed spectrum rather than waiting for the next FFT window.
 *
 * @author Hamza417
 */
object VisualizerManager {

    /** Number of frequency bands emitted per window — matches [app.simple.felicity.engine.processors.VisualizerAudioProcessor.bandCount]. */
    const val BAND_COUNT = 40

    /**
     * Backing mutable flow. `replay = 1` ensures a cold subscriber always gets
     * the latest snapshot immediately. `extraBufferCapacity = 64` with
     * `BufferOverflow.DROP_OLDEST` guarantees that `tryEmit` from the audio thread
     * always succeeds without suspending, even when the main thread is briefly busy.
     */
    private val _spectrumFlow = MutableSharedFlow<FloatArray>(
            replay = 1,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Public read-only spectrum flow.
     * Each emission is a [BAND_COUNT]-element [FloatArray] with magnitudes in [0..1],
     * ordered from bass (index 0) to treble (last index).
     */
    val spectrumFlow: SharedFlow<FloatArray> = _spectrumFlow.asSharedFlow()

    /**
     * Emits a new set of frequency band magnitudes directly and immediately.
     *
     * Uses `MutableSharedFlow.tryEmit`, which is non-suspending and thread-safe,
     * so no coroutine is launched and no thread-pool scheduling delay is introduced.
     * This means band data is forwarded to subscribers on the exact moment it is
     * produced by the audio processor, with no additional latency.
     *
     * @param bands [BAND_COUNT]-element array of magnitudes, bass to treble.
     */
    fun emit(bands: FloatArray) {
        _spectrumFlow.tryEmit(bands)
    }
}



