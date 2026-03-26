package app.simple.felicity.models

import app.simple.felicity.decorations.toggles.FelicityButtonGroup

/**
 * Holds the state needed to initialize a [FelicityButtonGroup] preference row.
 *
 * @param buttons The ordered list of [FelicityButtonGroup.Companion.Button] items to display.
 * @param selectedIndex The zero-based index of the initially selected button.
 *
 * @author Hamza417
 */
data class ButtonGroupState(
        val buttons: List<FelicityButtonGroup.Companion.Button>,
        val selectedIndex: Int,
)

