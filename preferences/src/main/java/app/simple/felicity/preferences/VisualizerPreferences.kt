package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

object VisualizerPreferences {

    const val VISUALIZER_TYPE = "visualizer_type"

    /** SharedPreferences key for the active FelicityVisualizer bar-growth direction. */
    const val VISUALIZER_DIRECTION = "visualizer_direction"

    /** SharedPreferences key for the user-defined visualizer sync offset in milliseconds. */
    const val LATENCY_OFFSET_MS = "visualizer_latency_offset_ms"

    /** SharedPreferences key for the intensity-driven spectrum coloring toggle. */
    const val SPECTRUM_COLORS_ENABLED = "visualizer_spectrum_colors_enabled"

    /** Smallest allowed sync offset; negative values make the visualizer react earlier. */
    const val LATENCY_OFFSET_MIN_MS = -300

    /** Largest allowed sync offset; positive values push the visualizer later. */
    const val LATENCY_OFFSET_MAX_MS = 500

    const val TYPE_BARS = 0
    const val TYPE_WAVE = 1
    const val TYPE_BLOCKS = 2

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

    /**
     * Persists the manual visualizer sync offset, added on top of the automatically
     * estimated output latency. Clamped to [LATENCY_OFFSET_MIN_MS]..[LATENCY_OFFSET_MAX_MS].
     */
    fun setLatencyOffsetMs(value: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(LATENCY_OFFSET_MS, value.coerceIn(LATENCY_OFFSET_MIN_MS, LATENCY_OFFSET_MAX_MS))
        }
    }

    /** Returns the manual visualizer sync offset in milliseconds; 0 by default. */
    fun getLatencyOffsetMs(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(LATENCY_OFFSET_MS, 0)
            .coerceIn(LATENCY_OFFSET_MIN_MS, LATENCY_OFFSET_MAX_MS)
    }

    /** Persists whether bars take on spectrum colors as their band intensity rises. */
    fun setSpectrumColorsEnabled(value: Boolean) {
        SharedPreferences.getSharedPreferences().edit {
            putBoolean(SPECTRUM_COLORS_ENABLED, value)
        }
    }

    /** Returns whether intensity-driven spectrum coloring is enabled; off by default. */
    fun isSpectrumColorsEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences()
            .getBoolean(SPECTRUM_COLORS_ENABLED, false)
    }
}