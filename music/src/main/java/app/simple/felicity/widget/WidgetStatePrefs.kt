package app.simple.felicity.widget

import android.content.Context

/**
 * A tiny wrapper around SharedPreferences that acts as the widget's long-term memory.
 *
 * The widget reads from this whenever it needs to redraw itself — even days after the
 * app was last open. The service writes to this every time the song or play-state changes.
 *
 * Think of it as a sticky note the service leaves on the fridge for the widget to read
 * in the morning.
 *
 * @author Hamza417
 */
object WidgetStatePrefs {

    private const val PREFS_NAME = "felicity_widget_state"

    private const val KEY_TITLE = "widget_title"
    private const val KEY_ARTIST = "widget_artist"
    private const val KEY_IS_PLAYING = "widget_is_playing"
    private const val KEY_SONG_ID = "widget_song_id"

    /**
     * Saves the latest song and playback info so the widget can draw something
     * meaningful the next time it wakes up (even if the service is long gone).
     */
    fun save(context: Context, title: String?, artist: String?, isPlaying: Boolean, songId: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_TITLE, title ?: "")
            putString(KEY_ARTIST, artist ?: "")
            putBoolean(KEY_IS_PLAYING, isPlaying)
            putLong(KEY_SONG_ID, songId)
            apply()
        }
    }

    /** Returns the last known song title, or an empty string if nothing was ever saved. */
    fun getTitle(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TITLE, "") ?: ""

    /** Returns the last known artist name, or an empty string if nothing was ever saved. */
    fun getArtist(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ARTIST, "") ?: ""

    /** Returns whether the player was playing when the state was last written. */
    fun isPlaying(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_PLAYING, false)

    /** Returns the database ID of the last known song, or -1 if nothing was saved. */
    fun getSongId(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_SONG_ID, -1L)
}

