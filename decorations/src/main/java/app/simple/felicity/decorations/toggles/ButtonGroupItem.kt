package app.simple.felicity.decorations.toggles

/**
 * Represents a single button item within a [FelicityButtonGroup].
 *
 * Either [textResId] or [iconResId] (or both) must be non-null. Passing null for both
 * will render an empty, invisible cell. Pass null for [iconResId] for text-only rendering,
 * or null for [textResId] for icon-only rendering.
 *
 * @property textResId String resource ID for the button label, or null for icon-only buttons.
 * @property iconResId Drawable resource ID for the button icon, or null for text-only buttons.
 */
data class ButtonGroupItem(
        val textResId: Int?,
        val iconResId: Int?,
)

