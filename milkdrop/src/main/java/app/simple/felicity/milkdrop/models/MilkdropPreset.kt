package app.simple.felicity.milkdrop.models

/**
 * Represents a single Milkdrop preset file bundled in the app assets.
 *
 * @property path Asset-relative path used to open the file via [android.content.res.AssetManager],
 *                e.g. `"presets/points/martin - charming tiles.milk"`.
 * @property name Human-readable display name derived from the filename without the `.milk`
 *                extension, e.g. `"martin - charming tiles"`.
 *
 * @author Hamza417
 */
data class MilkdropPreset(
        val path: String,
        val name: String
)

