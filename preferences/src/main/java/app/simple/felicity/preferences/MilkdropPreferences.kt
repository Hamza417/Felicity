package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

/**
 * Persisted preferences for the Milkdrop visualizer module.
 *
 * Stores the asset path of the last preset selected by the user so it can be
 * restored automatically when the [Milkdrop][app.simple.felicity.ui.panels.Milkdrop]
 * fragment is shown again.
 *
 * @author Hamza417
 */
object MilkdropPreferences {

    /** SharedPreferences key for the currently selected preset asset path. */
    const val LAST_PRESET = "milkdrop_last_preset"

    /**
     * Persists the asset path of the most recently selected preset.
     *
     * @param path Asset-relative path, e.g. `"presets/points/martin - charming tiles.milk"`.
     */
    fun setLastPreset(path: String) {
        SharedPreferences.getSharedPreferences().edit {
            putString(LAST_PRESET, path)
        }
    }

    /**
     * Returns the asset path of the last selected preset, or an empty string if none
     * has been selected yet (first launch).
     */
    fun getLastPreset(): String {
        return SharedPreferences.getSharedPreferences()
            .getString(LAST_PRESET, "") ?: ""
    }
}

