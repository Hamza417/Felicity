package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

/**
 * Persisted preferences that control the music player's runtime behavior,
 * including repeat mode, visualizer visibility, and visualizer rendering mode.
 *
 * @author Hamza417
 */
object PlayerPreferences {

    const val REPEAT_MODE = "repeat_mode"

    /** SharedPreferences key for the visualizer enabled/disabled toggle. */
    const val VISUALIZER_ENABLED = "visualizer_enabled"

    /** SharedPreferences key for the waveform seekbar display mode (int, maps to WaveformSeekbar mode constants). */
    const val WAVEFORM_MODE = "waveform_mode_int"

    const val PCM_INFO_MODE = "pcm_info_mode"

    const val PCM_INFO_MODE_SAMPLING_RATE = 0
    const val PCM_INFO_MODE_BITRATE = 1
    const val PCM_INFO_MODE_QUALITY = 2

    fun setRepeatMode(value: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(REPEAT_MODE, value) }
    }

    fun getRepeatMode(): Int {
        return SharedPreferences.getSharedPreferences().getInt(REPEAT_MODE, 0)
    }

    /**
     * Persists whether the visualizer overlay should be shown in the player.
     *
     * @param value `true` to show the visualizer, `false` to hide it.
     */
    fun setVisualizerEnabled(value: Boolean) {
        SharedPreferences.getSharedPreferences().edit { putBoolean(VISUALIZER_ENABLED, value) }
    }

    /**
     * Returns whether the visualizer overlay is currently enabled.
     * Defaults to `true` if the preference has not been set yet.
     */
    fun isVisualizerEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(VISUALIZER_ENABLED, true)
    }

    /**
     * Persists the waveform seekbar display mode.
     * Use the mode constants defined in [app.simple.felicity.decorations.seekbars.WaveformSeekbar].
     *
     * @param value one of `WAVEFORM_MODE_HALF`, `WAVEFORM_MODE_FULL`, or `WAVEFORM_MODE_REFLECTION`
     */
    fun setWaveformMode(value: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(WAVEFORM_MODE, value) }
    }

    /**
     * Returns the persisted waveform seekbar display mode.
     * Defaults to `WAVEFORM_MODE_HALF` (0) if the preference has not been set yet.
     */
    fun getWaveformMode(): Int {
        return SharedPreferences.getSharedPreferences().getInt(WAVEFORM_MODE, 0)
    }

    fun setPcmInfoMode(value: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(PCM_INFO_MODE, value) }
    }

    fun getPcmInfoMode(): Int {
        return SharedPreferences.getSharedPreferences().getInt(PCM_INFO_MODE, PCM_INFO_MODE_QUALITY)
    }
}