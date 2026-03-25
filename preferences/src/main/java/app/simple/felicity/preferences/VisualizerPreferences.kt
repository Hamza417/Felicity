package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

object VisualizerPreferences {

    const val VISUALIZER_TYPE = "visualizer_type"
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