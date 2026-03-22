package app.simple.felicity.engine.managers

import app.simple.felicity.engine.managers.VisualizerManager.BAND_COUNT
import app.simple.felicity.engine.managers.VisualizerManager.emit
import app.simple.felicity.engine.managers.VisualizerManager.processor
import app.simple.felicity.engine.managers.VisualizerManager.spectrumFlow
import app.simple.felicity.engine.processors.VisualizerProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Singleton bridge that both exposes the live [VisualizerProcessor] reference for
 * direct twin-buffer wiring and relays real-time spectrum data via a [SharedFlow] for
 * any remaining legacy consumers.
 *
 * The primary path is the direct twin-buffer connection established by calling
 * [processor].[VisualizerProcessor.setDirectOutput] from the player fragment.
 * The [SharedFlow] path ([spectrumFlow] / [emit]) is retained for backward-compatibility
 * and may be used by secondary consumers that cannot participate in the direct path.
 *
 * @author Hamza417
 */
object VisualizerManager {

    /** App-scoped coroutine scope used internally for non-blocking emissions. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Number of frequency bands emitted per window. */
    const val BAND_COUNT = 40

    /**
     * Live reference to the [VisualizerProcessor] managed by the player service.
     *
     * Set by [app.simple.felicity.engine.services.FelicityPlayerService] in [onCreate]
     * and cleared on service destruction. UI components — typically the player fragment —
     * use this reference to call [VisualizerProcessor.setDirectOutput] so the audio
     * thread can write directly into the view's twin buffers without any intermediate hop.
     */
    @Volatile
    var processor: VisualizerProcessor? = null

    /**
     * Backing mutable flow. Replay = 1 ensures a cold subscriber always gets
     * the latest spectrum snapshot right away.
     */
    private val _spectrumFlow = MutableSharedFlow<FloatArray>(replay = 1)

    /**
     * Public read-only spectrum flow (legacy path).
     * Each emission is a [BAND_COUNT]-element [FloatArray] with raw FFT-derived band
     * magnitudes, ordered from bass (index 0) to treble (last index).
     */
    val spectrumFlow: SharedFlow<FloatArray> = _spectrumFlow.asSharedFlow()

    /**
     * Emits a new set of frequency band magnitudes on the legacy [SharedFlow] path.
     *
     * Safe to call from any thread. Only needed when the direct twin-buffer path is
     * not connected (e.g., no player fragment is currently active).
     *
     * @param bands [BAND_COUNT]-element array of raw FFT band magnitudes.
     */
    fun emit(bands: FloatArray) {
        scope.launch {
            _spectrumFlow.emit(bands)
        }
    }
}
