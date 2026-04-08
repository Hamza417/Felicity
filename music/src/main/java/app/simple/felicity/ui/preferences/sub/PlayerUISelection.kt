package app.simple.felicity.ui.preferences.sub

import app.simple.felicity.R
import app.simple.felicity.extensions.fragments.BasePanelThemeSelectionFragment
import app.simple.felicity.preferences.UserInterfacePreferences
import app.simple.felicity.ui.player.DefaultPlayer
import app.simple.felicity.ui.player.PlayerFaded

/**
 * Preference screen that lets the user pick the full-screen Player interface by swiping
 * through live-preview pages rendered at the device's exact aspect ratio.
 *
 * Extends [BasePanelThemeSelectionFragment] and only needs to supply the ordered
 * list of [UIEntry] objects, the current selection, and the persistence callback.
 *
 * @author Hamza417
 */
class PlayerUISelection : BasePanelThemeSelectionFragment() {

    override fun getUIEntries(): List<UIEntry> {
        return listOf(
                UIEntry(
                        factory = { DefaultPlayer.newInstance() },
                        nameResId = R.string.simple
                ),
                UIEntry(
                        factory = { PlayerFaded.newInstance() },
                        nameResId = R.string.faded
                )
        )
    }

    override fun getCurrentSelection(): Int {
        return when (UserInterfacePreferences.getPlayerInterface()) {
            UserInterfacePreferences.PLAYER_INTERFACE_DEFAULT -> 0
            UserInterfacePreferences.PLAYER_INTERFACE_FADED -> 1
            else -> 0
        }
    }

    override fun onSelectionChanged(index: Int) {
        val value = when (index) {
            0 -> UserInterfacePreferences.PLAYER_INTERFACE_DEFAULT
            1 -> UserInterfacePreferences.PLAYER_INTERFACE_FADED
            else -> UserInterfacePreferences.PLAYER_INTERFACE_DEFAULT
        }
        UserInterfacePreferences.setPlayerInterface(value)
    }

    companion object {

        /** Unique back-stack tag for this fragment. */
        const val TAG = "PlayerUISelection"

        /**
         * Creates a new instance of [PlayerUISelection].
         *
         * @return A freshly instantiated [PlayerUISelection] fragment.
         */
        fun newInstance(): PlayerUISelection = PlayerUISelection()
    }
}

