package app.simple.felicity.extensions.fragments

import android.widget.TextView
import app.simple.felicity.R
import app.simple.felicity.decorations.seekbars.FelicitySeekbar
import app.simple.felicity.enums.PreferenceType
import app.simple.felicity.models.Preference
import app.simple.felicity.models.SeekbarState
import app.simple.felicity.popups.home.PopupHomeInterfaceMenu
import app.simple.felicity.popups.songs.PopupSongsInterfaceMenu
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.preferences.HomePreferences
import app.simple.felicity.preferences.SongsPreferences
import java.util.function.Supplier

open class PreferenceFragment : ScopedFragment() {

    protected fun createAppearancePanel(): List<Preference> {
        val preferences = mutableListOf<Preference>()

        val header = Preference(type = PreferenceType.SUB_HEADER, title = R.string.appearance)

        val cornerRadius = Preference(
                title = R.string.corner_radius,
                summary = R.string.corner_radius_summary,
                icon = R.drawable.ic_corner,
                type = PreferenceType.SLIDER,
        )

        cornerRadius.onPreferenceAction = { view, callback ->
            AppearancePreferences.setCornerRadius((view as FelicitySeekbar).getProgress().toFloat())
            true
        }

        cornerRadius.valueProvider = Supplier {
            SeekbarState(
                    position = AppearancePreferences.getCornerRadius(),
                    max = AppearancePreferences.MAX_CORNER_RADIUS,
                    min = 0F,
                    default = AppearancePreferences.DEFAULT_CORNER_RADIUS,
            )
        }

        val spacing = Preference(
                title = R.string.vertical_spacing,
                summary = R.string.spacing_summary,
                icon = R.drawable.ic_spacing,
                type = PreferenceType.SLIDER,
        )

        spacing.onPreferenceAction = { view, callback ->
            AppearancePreferences.setListSpacing((view as FelicitySeekbar).getProgress())
            true
        }

        spacing.valueProvider = Supplier {
            SeekbarState(
                    position = AppearancePreferences.getListSpacing(),
                    max = AppearancePreferences.MAX_SPACING,
                    min = 0F,
                    default = AppearancePreferences.DEFAULT_SPACING,
            )
        }

        preferences.add(header)
        preferences.add(cornerRadius)
        preferences.add(spacing)

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
                SongsPreferences.SONG_INTERFACE_FELICITY -> getString(R.string.app_name)
                SongsPreferences.SONG_INTERFACE_FLOW -> getString(R.string.artflow)
                else -> getString(R.string.app_name)
            }
        }

        songInterface.onPreferenceAction = { view, callback ->
            PopupSongsInterfaceMenu(
                    container = requireContainerView(),
                    anchorView = view,
                    menuItems = listOf(R.string.app_name,
                                       R.string.artflow
                    ),
                    menuIcons = listOf(
                            R.drawable.ic_list_16dp,
                            R.drawable.ic_flow_16dp,
                    ),
                    onMenuItemClick = {
                        when (it) {
                            R.string.app_name -> {
                                SongsPreferences.setSongsInterface(SongsPreferences.SONG_INTERFACE_FELICITY)
                                (view as TextView).text = getString(R.string.app_name)
                            }
                            R.string.artflow -> {
                                SongsPreferences.setSongsInterface(SongsPreferences.SONG_INTERFACE_FLOW)
                                (view as TextView).text = getString(R.string.artflow)
                            }
                        }
                    },
                    onDismiss = {

                    }
            ).show()
        }

        val homeHeader = Preference(type = PreferenceType.SUB_HEADER, title = R.string.home)

        val homeInterface = Preference(
                title = R.string.change_home_interface,
                summary = R.string.change_home_interface_summary,
                icon = app.simple.felicity.decoration.R.drawable.ic_felicity_full,
                type = PreferenceType.POPUP,
        )

        homeInterface.valueProvider = Supplier {
            when (HomePreferences.getHomeInterface()) {
                HomePreferences.HOME_INTERFACE_CAROUSEL -> getString(R.string.carousel)
                HomePreferences.HOME_INTERFACE_ARTFLOW -> getString(R.string.artflow)
                HomePreferences.HOME_INTERFACE_SPANNED -> getString(R.string.spanned)
                HomePreferences.HOME_INTERFACE_SIMPLE -> getString(R.string.simple)
                else -> getString(R.string.app_name)
            }
        }

        homeInterface.onPreferenceAction = { view, callback ->
            PopupHomeInterfaceMenu(
                    container = requireContainerView(),
                    anchorView = view,
                    menuItems = listOf(R.string.spanned,
                                       R.string.carousel,
                                       R.string.artflow,
                                       R.string.simple),
                    menuIcons = listOf(R.drawable.ic_spanned_16dp,
                                       R.drawable.ic_carousel_16dp,
                                       R.drawable.ic_flow_16dp,
                                       R.drawable.ic_list_16dp),
                    onMenuItemClick = {
                        when (it) {
                            R.string.spanned -> {
                                HomePreferences.setHomeInterface(HomePreferences.HOME_INTERFACE_SPANNED)
                            }
                            R.string.carousel -> {
                                HomePreferences.setHomeInterface(HomePreferences.HOME_INTERFACE_CAROUSEL)
                            }
                            R.string.artflow -> {
                                HomePreferences.setHomeInterface(HomePreferences.HOME_INTERFACE_ARTFLOW)
                            }
                            R.string.simple -> {
                                HomePreferences.setHomeInterface(HomePreferences.HOME_INTERFACE_SIMPLE)
                            }
                        }
                    },
                    onDismiss = {
                        callback
                    }
            ).show()
        }

        preferences.add(homeHeader)
        preferences.add(homeInterface)
        preferences.add(songsHeader)
        preferences.add(songInterface)

        return preferences
    }
}