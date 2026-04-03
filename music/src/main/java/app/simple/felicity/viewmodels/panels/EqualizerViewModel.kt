package app.simple.felicity.viewmodels.panels

import android.app.Application
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.preferences.EqualizerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the [app.simple.felicity.ui.panels.Equalizer] panel.
 *
 * Batch-reads all persisted equalizer preferences on [Dispatchers.IO] during construction
 * so the main thread is never blocked by disk I/O during fragment startup. The result is
 * published via [initialState]; the fragment observes this flow once to populate all
 * controls without any synchronous [EqualizerPreferences] access in `onViewCreated`.
 *
 * Speaker-screen and reverb-screen preferences are included in the same batch so a single
 * background coroutine warms up the entire [android.content.SharedPreferences] instance,
 * making all subsequent reads instant HashMap lookups.
 *
 * @author Hamza417
 */
class EqualizerViewModel(application: Application) : WrappedViewModel(application) {

    private val _initialState = MutableStateFlow<EqualizerInitialState?>(null)

    /**
     * Emits a fully populated [EqualizerInitialState] once all preferences have been read
     * from disk on the IO thread. The value is `null` until that read completes, which
     * typically happens within a single frame on modern devices.
     */
    val initialState: StateFlow<EqualizerInitialState?> = _initialState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _initialState.value = EqualizerInitialState(
                    isEqEnabled = EqualizerPreferences.isEqEnabled(),
                    bandGains = EqualizerPreferences.getAllBandGains(),
                    preampDb = EqualizerPreferences.getPreampDb(),
                    bassDb = EqualizerPreferences.getBassDb(),
                    trebleDb = EqualizerPreferences.getTrebleDb(),
                    balance = EqualizerPreferences.getBalance(),
                    stereoWidth = EqualizerPreferences.getStereoWidth(),
                    tapeSaturationDrive = EqualizerPreferences.getTapeSaturationDrive(),
                    reverbMix = EqualizerPreferences.getReverbMix(),
                    reverbDecay = EqualizerPreferences.getReverbDecay(),
                    reverbSize = EqualizerPreferences.getReverbSize(),
                    reverbDamp = EqualizerPreferences.getReverbDamp()
            )
        }
    }

    /**
     * Immutable snapshot of all equalizer preferences loaded in one IO-thread batch from
     * [EqualizerPreferences]. Passed to the fragment once the background read completes
     * so that view initialization never blocks the main thread.
     *
     * @property isEqEnabled Whether the 10-band graphic equalizer is active.
     * @property bandGains Persisted gains for all 10 EQ bands, in dB.
     * @property preampDb Pre-amplifier gain in dB applied before all EQ band filters.
     * @property bassDb Low-shelf bass gain in dB at 250 Hz.
     * @property trebleDb High-shelf treble gain in dB at 4000 Hz.
     * @property balance Stereo pan value in [-1 .. 1].
     * @property stereoWidth Stereo widening factor in [0 .. 2].
     * @property tapeSaturationDrive Tape saturation drive level in [0 .. 4].
     * @property reverbMix Reverb wet/dry mix in [0 .. 1].
     * @property reverbDecay Reverb decay time in [0 .. 1].
     * @property reverbSize Reverb room size in [0 .. 1].
     * @property reverbDamp Reverb high-frequency damping in [0 .. 1].
     */
    data class EqualizerInitialState(
            val isEqEnabled: Boolean,
            val bandGains: FloatArray,
            val preampDb: Float,
            val bassDb: Float,
            val trebleDb: Float,
            val balance: Float,
            val stereoWidth: Float,
            val tapeSaturationDrive: Float,
            val reverbMix: Float,
            val reverbDecay: Float,
            val reverbSize: Float,
            val reverbDamp: Float
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EqualizerInitialState) return false
            return isEqEnabled == other.isEqEnabled
                    && bandGains.contentEquals(other.bandGains)
                    && preampDb == other.preampDb
                    && bassDb == other.bassDb
                    && trebleDb == other.trebleDb
                    && balance == other.balance
                    && stereoWidth == other.stereoWidth
                    && tapeSaturationDrive == other.tapeSaturationDrive
                    && reverbMix == other.reverbMix
                    && reverbDecay == other.reverbDecay
                    && reverbSize == other.reverbSize
                    && reverbDamp == other.reverbDamp
        }

        override fun hashCode(): Int {
            var result = isEqEnabled.hashCode()
            result = 31 * result + bandGains.contentHashCode()
            result = 31 * result + preampDb.hashCode()
            result = 31 * result + bassDb.hashCode()
            result = 31 * result + trebleDb.hashCode()
            result = 31 * result + balance.hashCode()
            result = 31 * result + stereoWidth.hashCode()
            result = 31 * result + tapeSaturationDrive.hashCode()
            result = 31 * result + reverbMix.hashCode()
            result = 31 * result + reverbDecay.hashCode()
            result = 31 * result + reverbSize.hashCode()
            result = 31 * result + reverbDamp.hashCode()
            return result
        }
    }
}

