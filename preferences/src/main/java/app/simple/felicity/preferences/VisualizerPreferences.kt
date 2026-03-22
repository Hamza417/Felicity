package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

object VisualizerPreferences {

    const val VISUALIZER_TYPE = "visualizer_type"
    const val VISUALIZER_LATENCY = "visualizer_latency"
    const val PARTICLES_ENABLED = "visualizer_particles_enabled"

    const val TYPE_BARS = 0
    const val TYPE_WAVE = 1

    // ------------------------------------------------------------------------------------------ //

    fun setVisualizerType(value: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(VISUALIZER_TYPE, value)
        }
    }

    fun getVisualizerType(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(VISUALIZER_TYPE, TYPE_BARS)
    }

    // -------------------------------------------------------------------------- //

    // Latency in milliseconds to delay the visualizer updates, allowing it to sync better with the audio.
    fun setVisualizerLatency(value: Long) {
        SharedPreferences.getSharedPreferences().edit {
            putLong(VISUALIZER_LATENCY, value)
        }
    }

    fun getVisualizerLatency(): Long {
        return SharedPreferences.getSharedPreferences()
            .getLong(VISUALIZER_LATENCY, 0)
    }

    // -------------------------------------------------------------------------- //

    fun setParticlesEnabled(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit {
            putBoolean(PARTICLES_ENABLED, enabled)
        }
    }

    fun areParticlesEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences()
            .getBoolean(PARTICLES_ENABLED, true)
    }
}