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
}