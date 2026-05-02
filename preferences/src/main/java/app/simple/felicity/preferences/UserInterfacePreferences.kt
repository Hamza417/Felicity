package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences.getSharedPreferences
import app.simple.felicity.preferences.UserInterfacePreferences.PLAYER_INTERFACE_DEFAULT
import app.simple.felicity.preferences.UserInterfacePreferences.PLAYER_INTERFACE_FADED

/**
 * Persisted preferences that control which interface variants are shown throughout the
 * application, including the home panel, the mini player margin, and the full-screen
 * player interface.
 *
 * @author Hamza417
 */
object UserInterfacePreferences {

    const val LIKE_ICON_INSTEAD_OF_HEART = "like_icon_instead_of_heart"

    const val MARGIN_AROUND_MINIPLAYER = "margin_around_miniplayer"
    const val HOME_INTERFACE = "home_interface_"
    const val PLAYER_INTERFACE = "player_interface_"
    const val IMMERSIVE_MODE = "immersive_mode"

    const val HOME_INTERFACE_DASHBOARD = 1
    const val HOME_INTERFACE_TILED = 2
    const val HOME_INTERFACE_ARTFLOW = 3
    const val HOME_INTERFACE_SIMPLE = 0

    const val PLAYER_INTERFACE_DEFAULT = 0
    const val PLAYER_INTERFACE_FADED = 1

    fun setLikeIconInsteadOfThumb(value: Boolean) {
        getSharedPreferences().edit { putBoolean(LIKE_ICON_INSTEAD_OF_HEART, value) }
    }

    fun isLikeIconInsteadOfThumb(): Boolean {
        return getSharedPreferences().getBoolean(LIKE_ICON_INSTEAD_OF_HEART, false)
    }

    fun setMarginAroundMiniplayer(value: Boolean) {
        getSharedPreferences().edit { putBoolean(MARGIN_AROUND_MINIPLAYER, value) }
    }

    fun isMarginAroundMiniplayer(): Boolean {
        return getSharedPreferences().getBoolean(MARGIN_AROUND_MINIPLAYER, true)
    }

    fun getHomeInterface(): Int {
        return getSharedPreferences()
            .getInt(HOME_INTERFACE, HOME_INTERFACE_SIMPLE)
    }

    fun setHomeInterface(value: Int) {
        getSharedPreferences()
            .edit {
                putInt(HOME_INTERFACE, value)
            }
    }

    /**
     * Returns the currently selected full-screen player interface identifier.
     * Defaults to [PLAYER_INTERFACE_DEFAULT] if the preference has not been set yet.
     */
    fun getPlayerInterface(): Int {
        return getSharedPreferences()
            .getInt(PLAYER_INTERFACE, PLAYER_INTERFACE_DEFAULT)
    }

    /**
     * Persists the selected full-screen player interface identifier.
     *
     * @param value one of [PLAYER_INTERFACE_DEFAULT] or [PLAYER_INTERFACE_FADED]
     */
    fun setPlayerInterface(value: Int) {
        getSharedPreferences()
            .edit {
                putInt(PLAYER_INTERFACE, value)
            }
    }

    fun isImmersiveMode(): Boolean {
        return getSharedPreferences()
            .getBoolean(IMMERSIVE_MODE, false)
    }

    fun setImmersiveMode(enabled: Boolean) {
        getSharedPreferences()
            .edit {
                putBoolean(IMMERSIVE_MODE, enabled)
            }
    }

    /**
     * Checks whether a given panel should be shown in the browse grid and Simple Home list.
     * Returns true by default so nothing disappears on a fresh install — that would be rude.
     *
     * @param key one of the PANEL_VISIBLE_* constants in this object.
     */
    fun isPanelVisible(key: String): Boolean {
        return getSharedPreferences().getBoolean(key, true)
    }

    /**
     * Saves the visibility state for a given panel.
     *
     * @param key   one of the PANEL_VISIBLE_* constants in this object.
     * @param value true = show the panel, false = hide it.
     */
    fun setPanelVisible(key: String, value: Boolean) {
        getSharedPreferences().edit { putBoolean(key, value) }
    }

    /** Preference key for toggling Album Artists panel visibility. */
    const val PANEL_VISIBLE_ALBUM_ARTISTS = "panel_visible_album_artists"

    /** Preference key for toggling Genres panel visibility. */
    const val PANEL_VISIBLE_GENRES = "panel_visible_genres"

    /** Preference key for toggling Year panel visibility. */
    const val PANEL_VISIBLE_YEAR = "panel_visible_year"

    /** Preference key for toggling Playlists panel visibility. */
    const val PANEL_VISIBLE_PLAYLISTS = "panel_visible_playlists"

    /** Preference key for toggling Playing Queue panel visibility. */
    const val PANEL_VISIBLE_PLAYING_QUEUE = "panel_visible_playing_queue"

    /** Preference key for toggling Recently Added panel visibility. */
    const val PANEL_VISIBLE_RECENTLY_ADDED = "panel_visible_recently_added"

    /** Preference key for toggling Recently Played panel visibility. */
    const val PANEL_VISIBLE_RECENTLY_PLAYED = "panel_visible_recently_played"

    /** Preference key for toggling Most Played panel visibility. */
    const val PANEL_VISIBLE_MOST_PLAYED = "panel_visible_most_played"

    /** Preference key for toggling Favorites panel visibility. */
    const val PANEL_VISIBLE_FAVORITES = "panel_visible_favorites"

    /** Preference key for toggling Folders panel visibility. */
    const val PANEL_VISIBLE_FOLDERS = "panel_visible_folders"

    /** Preference key for toggling Folders Hierarchy panel visibility. */
    const val PANEL_VISIBLE_FOLDERS_HIERARCHY = "panel_visible_folders_hierarchy"

    /** Preference key for toggling Always Skipped panel visibility. */
    const val PANEL_VISIBLE_ALWAYS_SKIPPED = "panel_visible_always_skipped"

    /**
     * All panel visibility keys collected in one handy set,
     * so you can loop over them without playing whack-a-mole with constants.
     */
    val ALL_PANEL_VISIBILITY_KEYS = setOf(
            PANEL_VISIBLE_ALBUM_ARTISTS,
            PANEL_VISIBLE_GENRES,
            PANEL_VISIBLE_YEAR,
            PANEL_VISIBLE_PLAYLISTS,
            PANEL_VISIBLE_PLAYING_QUEUE,
            PANEL_VISIBLE_RECENTLY_ADDED,
            PANEL_VISIBLE_RECENTLY_PLAYED,
            PANEL_VISIBLE_MOST_PLAYED,
            PANEL_VISIBLE_FAVORITES,
            PANEL_VISIBLE_FOLDERS,
            PANEL_VISIBLE_FOLDERS_HIERARCHY,
            PANEL_VISIBLE_ALWAYS_SKIPPED
    )
}