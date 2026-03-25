package app.simple.felicity.milkdrop

import android.content.res.AssetManager
import app.simple.felicity.milkdrop.PresetManager.PRESETS_ROOT
import app.simple.felicity.milkdrop.PresetManager.clearCache
import app.simple.felicity.milkdrop.PresetManager.listAll
import app.simple.felicity.milkdrop.models.MilkdropPreset

/**
 * Central manager for all bundled Milkdrop preset assets.
 *
 * Presets are stored under `assets/presets/` and may span any number of
 * subdirectories (currently `points/` and `spectrum/`).  New directories added
 * to the assets tree are picked up automatically without any code change — the
 * only root that is hard-coded here is [PRESETS_ROOT].
 *
 * The full preset list is computed once and cached in memory so that repeated calls
 * from [MilkdropViewModel][app.simple.felicity.viewmodels.panels.MilkdropViewModel]
 * and [MilkdropPresetsViewModel][app.simple.felicity.viewmodels.dialogs.MilkdropPresetsViewModel]
 * both hit the same result without rescanning the asset filesystem.
 *
 * To invalidate the cache (e.g., in tests), call [clearCache].
 *
 * @author Hamza417
 */
object PresetManager {

    /** Root asset directory that contains all preset subdirectories. */
    const val PRESETS_ROOT = "presets"

    @Volatile
    private var cache: List<MilkdropPreset>? = null

    /**
     * Returns a flat, case-insensitively sorted list of every `.milk` preset found
     * under [PRESETS_ROOT] in the given [AssetManager].
     *
     * The result is cached after the first call and reused on subsequent calls,
     * so this is safe to call repeatedly from multiple ViewModels.
     *
     * @param assets The application [AssetManager] used to traverse the asset tree.
     * @return Sorted flat list of all presets across every subdirectory.
     */
    fun listAll(assets: AssetManager): List<MilkdropPreset> {
        cache?.let { return it }
        return synchronized(this) {
            cache ?: scanDirectory(assets, PRESETS_ROOT)
                .sortedWith(compareBy { it.name.lowercase() })
                .also { cache = it }
        }
    }

    /**
     * Reads the full text content of a single preset file from assets.
     *
     * @param assets The application [AssetManager].
     * @param path   Asset-relative path as stored in [MilkdropPreset.path],
     *               e.g. `"presets/points/martin - charming tiles.milk"`.
     * @return The raw `.milk` file text, or `null` if the file cannot be opened.
     */
    fun loadContent(assets: AssetManager, path: String): String? {
        return try {
            assets.open(path).bufferedReader().readText()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Convenience wrapper: returns the [MilkdropPreset.path] of the alphabetically
     * first preset found, or `null` if the assets are empty.
     *
     * @param assets The application [AssetManager].
     */
    fun firstPresetPath(assets: AssetManager): String? {
        return listAll(assets).firstOrNull()?.path
    }

    /**
     * Clears the in-memory preset cache.
     *
     * Subsequent calls to [listAll] will rescan the asset tree.  Useful in tests
     * or if the asset set can change at runtime.
     */
    fun clearCache() {
        synchronized(this) { cache = null }
    }

    /**
     * Recursively walks [dir] in the asset tree, collecting every `.milk` file into
     * a [MilkdropPreset] and descending into any entry that does not end with `.milk`
     * (treating it as a subdirectory).
     *
     * @param assets The application [AssetManager].
     * @param dir    Current directory path relative to the asset root.
     * @return Unsorted list of all presets found in [dir] and its descendants.
     */
    private fun scanDirectory(assets: AssetManager, dir: String): List<MilkdropPreset> {
        val entries = assets.list(dir) ?: return emptyList()
        val result = mutableListOf<MilkdropPreset>()
        for (entry in entries) {
            val fullPath = "$dir/$entry"
            if (entry.endsWith(".milk")) {
                result += MilkdropPreset(
                        path = fullPath,
                        name = entry.removeSuffix(".milk")
                )
            } else {
                // No extension or non-.milk entry — treat as a subdirectory.
                result += scanDirectory(assets, fullPath)
            }
        }
        return result
    }
}

