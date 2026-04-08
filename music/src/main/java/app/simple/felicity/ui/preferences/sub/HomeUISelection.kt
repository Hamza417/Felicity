package app.simple.felicity.ui.preferences.sub

import app.simple.felicity.R
import app.simple.felicity.extensions.fragments.BasePanelThemeSelectionFragment
import app.simple.felicity.preferences.UserInterfacePreferences
import app.simple.felicity.ui.home.ArtFlowHome
import app.simple.felicity.ui.home.Dashboard
import app.simple.felicity.ui.home.SimpleHome
import app.simple.felicity.ui.home.SpannedHome

/**
 * Preference screen that lets the user pick the Home interface by swiping through
 * full live-preview pages rendered at the device's exact aspect ratio.
 *
 * Extends [BasePanelThemeSelectionFragment] and only needs to supply the ordered
 * list of [UIEntry] objects, the current selection, and the persistence callback.
 *
 * @author Hamza417
 */
class HomeUISelection : BasePanelThemeSelectionFragment() {

    override fun getUIEntries(): List<UIEntry> {
        return listOf(
                UIEntry(
                        factory = { SimpleHome.newInstance() },
                        nameResId = R.string.simple
                ),
                UIEntry(
                        factory = { Dashboard.newInstance() },
                        nameResId = R.string.dashboard
                ),
                UIEntry(
                        factory = { SpannedHome.newInstance() },
                        nameResId = R.string.spanned
                ),
                UIEntry(
                        factory = { ArtFlowHome.newInstance() },
                        nameResId = R.string.artflow
                )
        )
    }

    override fun getCurrentSelection(): Int {
        return when (UserInterfacePreferences.getHomeInterface()) {
            UserInterfacePreferences.HOME_INTERFACE_SIMPLE -> 0
            UserInterfacePreferences.HOME_INTERFACE_DASHBOARD -> 1
            UserInterfacePreferences.HOME_INTERFACE_SPANNED -> 2
            UserInterfacePreferences.HOME_INTERFACE_ARTFLOW -> 3
            else -> 0
        }
    }

    override fun onSelectionChanged(index: Int) {
        val value = when (index) {
            0 -> UserInterfacePreferences.HOME_INTERFACE_SIMPLE
            1 -> UserInterfacePreferences.HOME_INTERFACE_DASHBOARD
            2 -> UserInterfacePreferences.HOME_INTERFACE_SPANNED
            3 -> UserInterfacePreferences.HOME_INTERFACE_ARTFLOW
            else -> UserInterfacePreferences.HOME_INTERFACE_SIMPLE
        }
        UserInterfacePreferences.setHomeInterface(value)
    }

    companion object {

        /** Unique back-stack tag for this fragment. */
        const val TAG = "HomeUISelection"

        /**
         * Creates a new instance of [HomeUISelection].
         *
         * @return A freshly instantiated [HomeUISelection] fragment.
         */
        fun newInstance(): HomeUISelection = HomeUISelection()
    }
}

