package app.simple.felicity.engine.managers

import app.simple.felicity.engine.managers.VisualizerManager.BAND_COUNT
import app.simple.felicity.engine.managers.VisualizerManager.emit
import app.simple.felicity.engine.managers.VisualizerManager.spectrumFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Singleton bridge that relays real-time audio spectrum data from the audio thread
 * (inside [app.simple.felicity.engine.services.FelicityPlayerService]) to any UI
 * subscriber via a [SharedFlow].
 *
 * The service feeds 40-band spectrum arrays on the audio thread by calling [emit];
 * UI components collect on the main thread via [spectrumFlow] using
 * `lifecycleScope.launch { spectrumFlow.collect { ... } }`.
 *
 * The flow replays the last value (replay = 1) so that a new collector immediately
 * receives the most recently computed spectrum rather than waiting for the next FFT window.
 *
 * @author Hamza417
 */
object VisualizerManager {

    /** App-scoped coroutine scope used internally for non-blocking emissions. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Number of frequency bands emitted per window — matches [app.simple.felicity.engine.processors.VisualizerAudioProcessor.bandCount]. */
    const val BAND_COUNT = 40

    /**
     * Backing mutable flow. Replay = 1 ensures a cold subscriber always gets
     * the latest spectrum snapshot right away.
     */
    private val _spectrumFlow = MutableSharedFlow<FloatArray>(replay = 1)

    /**
     * Public read-only spectrum flow.
     * Each emission is a [BAND_COUNT]-element [FloatArray] with magnitudes in [0..1],
     * ordered from bass (index 0) to treble (last index).
     */
    val spectrumFlow: SharedFlow<FloatArray> = _spectrumFlow.asSharedFlow()

    /**
     * Emits a new set of frequency band magnitudes.
     *
     * Safe to call from any thread. [MutableSharedFlow] is thread-safe; collectors
     * receive values on whichever dispatcher their own coroutine scope uses.
     *
     * @param bands [BAND_COUNT]-element array of magnitudes in [0..1], bass to treble.
     */
    fun emit(bands: FloatArray) {
        scope.launch {
            _spectrumFlow.emit(bands)
        }
    }
}



