package app.simple.felicity.extensions.fragments

import app.simple.felicity.core.R
import app.simple.felicity.dialogs.songs.SongsSort.Companion.showSongsSort
import app.simple.felicity.enums.PreferenceType
import app.simple.felicity.models.Preference
import app.simple.felicity.popups.songs.PopupSongsInterfaceMenu
import app.simple.felicity.preferences.SongsPreferences
import java.util.function.Supplier

open class PreferenceFragment : ScopedFragment() {

    override fun setTransitions() {
        // No transitions for preference fragments
    }

    protected fun createAppearancePanel(): List<Preference> {
        val preferences = mutableListOf<Preference>()

        val header = Preference(type = PreferenceType.SUB_HEADER, title = R.string.appearance)

        val cornerRadius = Preference(
                title = R.string.corner_radius,
                summary = R.string.corner_radius_summary,
                icon = app.simple.felicity.decoration.R.drawable.ic_corner,
                type = PreferenceType.DIALOG,
        )

        cornerRadius.onClickAction = { view ->
            childFragmentManager.showSongsSort()
            true
        }

        preferences.add(header)
        preferences.add(cornerRadius)

        return preferences
    }

    protected fun createUserInterfacePanel(): List<Preference> {
        val preferences = mutableListOf<Preference>()

        val songsHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.songs)

        val songInterface = Preference(
                title = R.string.change_songs_interface,
                summary = R.string.change_songs_interface_summary,
                icon = app.simple.felicity.decoration.R.drawable.ic_song,
                type = PreferenceType.POPUP,
        )

        songInterface.valueProvider = Supplier {
            when (SongsPreferences.getSongsInterface()) {
                SongsPreferences.SONG_INTERFACE_FELICITY -> getString(app.simple.felicity.R.string.app_name)
                SongsPreferences.SONG_INTERFACE_FLOW -> getString(R.string.artflow)
                else -> getString(app.simple.felicity.R.string.app_name)
            }
        }

        songInterface.onClickAction = { view ->
            PopupSongsInterfaceMenu(
                    container = requireContainerView(),
                    anchorView = view,
                    menuItems = listOf(app.simple.felicity.R.string.app_name,
                                       app.simple.felicity.R.string.artflow
                    ),
                    menuIcons = listOf(
                            app.simple.felicity.R.drawable.ic_list_16dp,
                            app.simple.felicity.R.drawable.ic_flow_16dp,
                    ),
                    onMenuItemClick = {
                        when (it) {
                            app.simple.felicity.R.string.app_name -> {
                                SongsPreferences.setSongsInterface(SongsPreferences.SONG_INTERFACE_FELICITY)
                            }
                            app.simple.felicity.R.string.artflow -> {
                                SongsPreferences.setSongsInterface(SongsPreferences.SONG_INTERFACE_FLOW)
                            }
                        }
                    },
                    onDismiss = {

                    }
            ).show()

            getString(app.simple.felicity.R.string.app_name)
        }

        preferences.add(songsHeader)
        preferences.add(songInterface)

        return preferences
    }
}