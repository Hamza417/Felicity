package app.simple.felicity.engine.managers

import android.media.audiofx.Equalizer
import android.util.Log
import app.simple.felicity.engine.managers.EqualizerManager.bandGainsFlow
import app.simple.felicity.engine.managers.EqualizerManager.initialize
import app.simple.felicity.preferences.EqualizerPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton manager for the Android hardware [Equalizer] audio effect.
 *
 * Responsibilities:
 *  - Opens and holds a single [Equalizer] instance tied to the player's audio session.
 *  - Loads all 10-band gains from [EqualizerPreferences] on initialization so the EQ
 *    state is always restored from the last-saved settings after a cold boot.
 *  - Converts between the UI's dB float range ([-15 .. +15]) and the hardware's
 *    millibel integer range ([-1500 .. +1500]).
 *  - Exposes [bandGainsFlow] as a [StateFlow] so the equalizer panel can observe live
 *    updates regardless of where a change originates (UI, preset load, or remote command).
 *  - Gracefully handles devices where the audio effect is unavailable.
 *
 * Usage:
 * ```kotlin
 * // In the player service, after the ExoPlayer instance is ready:
 * EqualizerManager.initialize(audioSessionId)
 *
 * // In the UI fragment:
 * lifecycleScope.launch {
 *     EqualizerManager.bandGainsFlow.collect { gains -> sliders.setAllGains(gains) }
 * }
 * ```
 *
 * @author Hamza417
 */
object EqualizerManager {

    private const val TAG = "EqualizerManager"

    /** Conversion factor: 1 dB = 100 millibels. */
    private const val MILLIBELS_PER_DB = 100

    /** The underlying Android [Equalizer] effect. Null when not initialized or unavailable. */
    private var equalizer: Equalizer? = null

    /** True after [initialize] succeeds on the current audio session. */
    var isInitialized: Boolean = false
        private set

    /**
     * Backing mutable state flow. Holds the latest 10-element array of band gains in dB.
     * Initialized from persisted preferences so that [bandGainsFlow] collectors immediately
     * see the correct state without waiting for the next user interaction.
     */
    private val _bandGainsFlow = MutableStateFlow(EqualizerPreferences.getAllBandGains())

    /**
     * Read-only [StateFlow] of the current 10-band gain array (dB, [-15 .. +15]).
     * The UI should collect this to stay in sync with any externally driven changes
     * such as preset loading or a remote-control reset.
     */
    val bandGainsFlow: StateFlow<FloatArray> = _bandGainsFlow.asStateFlow()

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Opens the Android [Equalizer] effect on [audioSessionId] and immediately applies
     * all band gains and the enabled state that were last saved in [EqualizerPreferences].
     *
     * Safe to call multiple times — each call releases the previous instance first.
     * If the hardware effect is unavailable the method logs the error and returns cleanly;
     * preference reads and [bandGainsFlow] emissions still work normally so the UI is unaffected.
     *
     * @param audioSessionId The ExoPlayer audio session ID, or 0 for the global output mix.
     */
    fun initialize(audioSessionId: Int) {
        release()
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = EqualizerPreferences.isEqEnabled()
                applyAllBandsInternal(this)
            }
            isInitialized = true
            Log.i(TAG, "Equalizer initialized on audio session $audioSessionId, " +
                    "enabled=${EqualizerPreferences.isEqEnabled()}")
        } catch (e: Exception) {
            isInitialized = false
            Log.e(TAG, "Failed to open Equalizer effect (session=$audioSessionId): ${e.message}")
        }

        // Always sync the flow from preferences so the UI gets the correct state
        // even if the hardware effect failed to open.
        _bandGainsFlow.value = EqualizerPreferences.getAllBandGains()
    }

    /**
     * Releases the underlying [Equalizer] resource. Call from the player service's
     * [android.app.Service.onDestroy] to avoid leaking the audio effect handle.
     */
    fun release() {
        try {
            equalizer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Exception while releasing Equalizer: ${e.message}")
        } finally {
            equalizer = null
            isInitialized = false
        }
    }

    // -------------------------------------------------------------------------
    // Band gain control
    // -------------------------------------------------------------------------

    /**
     * Sets the gain for a single EQ band, persists it to [EqualizerPreferences], applies
     * it to the hardware [Equalizer], and updates [bandGainsFlow].
     *
     * @param band    Zero-based band index in [0 .. 9] (31 Hz → 16 kHz).
     * @param gainDb  Gain in dB, clamped to [-15 .. +15].
     * @param persist Pass false to skip the SharedPreferences write when the value was
     *                already saved by the caller (e.g., on a batch preset load).
     */
    fun setBandGain(band: Int, gainDb: Float, persist: Boolean = true) {
        if (band !in 0..9) return
        val clamped = gainDb.coerceIn(-15f, 15f)

        if (persist) {
            EqualizerPreferences.setBandGain(band, clamped)
        }

        equalizer?.let { eq ->
            try {
                eq.setBandLevel(band.toShort(), dbToMillibels(clamped))
            } catch (e: Exception) {
                Log.e(TAG, "setBandLevel failed for band $band: ${e.message}")
            }
        }

        // Emit a new array with the updated value so UI state flows are coherent
        val updated = _bandGainsFlow.value.copyOf()
        updated[band] = clamped
        _bandGainsFlow.value = updated
    }

    /**
     * Loads the gain for [band] from [EqualizerPreferences] and applies it to the hardware
     * equalizer. Called by the player service's [android.content.SharedPreferences.OnSharedPreferenceChangeListener]
     * so the engine stays in sync when a preference write arrives from the UI.
     *
     * @param band Zero-based band index in [0 .. 9].
     */
    fun applyBandFromPreference(band: Int) {
        if (band !in 0..9) return
        val gainDb = EqualizerPreferences.getBandGain(band)
        // Update the hardware but do not re-persist (already written by the caller)
        setBandGain(band, gainDb, persist = false)
    }

    /**
     * Returns the current gain for [band] in dB, sourced from [bandGainsFlow].
     *
     * @param band Zero-based band index in [0 .. 9].
     */
    fun getBandGain(band: Int): Float {
        if (band !in 0..9) return 0f
        return _bandGainsFlow.value[band]
    }

    /**
     * Returns a snapshot of all 10 band gains in dB from the current [bandGainsFlow] value.
     */
    fun getAllGains(): FloatArray = _bandGainsFlow.value.copyOf()

    /**
     * Resets all 10 bands to 0 dB (flat EQ), persists the reset, and updates [bandGainsFlow].
     */
    fun resetAllBands() {
        val flat = FloatArray(10) { 0f }
        EqualizerPreferences.setAllBandGains(flat)
        equalizer?.let { eq ->
            for (i in 0..9) {
                try {
                    eq.setBandLevel(i.toShort(), 0)
                } catch (e: Exception) {
                    Log.e(TAG, "Reset failed for band $i: ${e.message}")
                }
            }
        }
        _bandGainsFlow.value = flat
    }

    // -------------------------------------------------------------------------
    // Enable / disable
    // -------------------------------------------------------------------------

    /**
     * Enables or disables the hardware [Equalizer] effect and persists the state.
     *
     * @param enabled True to activate the EQ bands, false to bypass them.
     */
    fun setEnabled(enabled: Boolean) {
        EqualizerPreferences.setEqEnabled(enabled)
        try {
            equalizer?.enabled = enabled
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set Equalizer enabled=$enabled: ${e.message}")
        }
        Log.d(TAG, "Equalizer enabled=$enabled")
    }

    /** Returns whether the [Equalizer] is currently marked as enabled in preferences. */
    fun isEnabled(): Boolean = EqualizerPreferences.isEqEnabled()

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Reads all 10 band gains from [EqualizerPreferences] and writes them to [eq].
     * Called once during [initialize] so the hardware effect matches persisted state.
     */
    private fun applyAllBandsInternal(eq: Equalizer) {
        val gains = EqualizerPreferences.getAllBandGains()
        for (i in gains.indices) {
            try {
                eq.setBandLevel(i.toShort(), dbToMillibels(gains[i]))
            } catch (e: Exception) {
                Log.e(TAG, "applyAllBands failed for band $i: ${e.message}")
            }
        }
    }

    /**
     * Converts a dB float value to the millibel [Short] expected by [Equalizer.setBandLevel].
     * 1 dB = 100 millibels. Values are clamped to the Short range for safety.
     *
     * @param gainDb Gain in dB.
     * @return Gain in millibels as a [Short].
     */
    private fun dbToMillibels(gainDb: Float): Short {
        return (gainDb * MILLIBELS_PER_DB).toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }
}