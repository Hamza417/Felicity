package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences
import app.simple.felicity.preferences.VisualizerPreferences.DIRECTION_BOTTOM_TO_TOP
import app.simple.felicity.preferences.VisualizerPreferences.DIRECTION_LEFT_TO_RIGHT
import app.simple.felicity.preferences.VisualizerPreferences.DIRECTION_RIGHT_TO_LEFT
import app.simple.felicity.preferences.VisualizerPreferences.DIRECTION_TOP_TO_BOTTOM

object VisualizerPreferences {

    const val VISUALIZER_TYPE = "visualizer_type"
    const val PARTICLES_ENABLED = "visualizer_particles_enabled"

    /** SharedPreferences key for the active FelicityVisualizer bar-growth direction. */
    const val VISUALIZER_DIRECTION = "visualizer_direction"

    const val TYPE_BARS = 0
    const val TYPE_WAVE = 1

    /** Bars / wave emerge from the bottom and grow upward (default). */
    const val DIRECTION_BOTTOM_TO_TOP = 0

    /** Bars / wave emerge from the top and grow downward. */
    const val DIRECTION_TOP_TO_BOTTOM = 1

    /** Bars / wave emerge from the left edge and grow rightward. */
    const val DIRECTION_LEFT_TO_RIGHT = 2

    /** Bars / wave emerge from the right edge and grow leftward. */
    const val DIRECTION_RIGHT_TO_LEFT = 3

    fun setVisualizerType(value: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(VISUALIZER_TYPE, value)
        }
    }

    fun getVisualizerType(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(VISUALIZER_TYPE, TYPE_BARS)
    }

    fun setParticlesEnabled(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit {
            putBoolean(PARTICLES_ENABLED, enabled)
        }
    }

    fun areParticlesEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences()
            .getBoolean(PARTICLES_ENABLED, true)
    }

    /**
     * Persists the visualizer bar-growth direction.
     *
     * @param value One of [DIRECTION_BOTTOM_TO_TOP], [DIRECTION_TOP_TO_BOTTOM],
     *              [DIRECTION_LEFT_TO_RIGHT], or [DIRECTION_RIGHT_TO_LEFT].
     */
    fun setVisualizerDirection(value: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(VISUALIZER_DIRECTION, value)
        }
    }

    /**
     * Returns the persisted visualizer direction.
     * Defaults to [DIRECTION_BOTTOM_TO_TOP] if no value has been saved yet.
     */
    fun getVisualizerDirection(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(VISUALIZER_DIRECTION, DIRECTION_BOTTOM_TO_TOP)
    }
}