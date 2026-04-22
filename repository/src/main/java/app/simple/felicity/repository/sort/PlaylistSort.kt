package app.simple.felicity.repository.sort

import androidx.appcompat.widget.AppCompatTextView
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.preferences.PlaylistPreferences
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.R

/**
 * Sorting utilities for the song list inside a playlist.
 *
 * <p>Sorting is driven by [PlaylistPreferences] so that the playlist panel remembers
 * its own sort configuration independently of the Songs and Favorites panels. When a
 * playlist's own {@code sortOrder} field equals {@code -1} the caller should skip this
 * sort altogether and preserve the raw database order (i.e., by {@code position} from
 * {@link app.simple.felicity.repository.models.PlaylistSongCrossRef}).</p>
 *
 * @author Hamza417
 */
object PlaylistSort {

    /**
     * Returns a new [Audio] list sorted according to the current [PlaylistPreferences]
     * sort field and direction.
     */
    fun List<Audio>.sortedPlaylist(): List<Audio> {
        return when (PlaylistPreferences.getSongSort()) {
            CommonPreferencesConstants.BY_TITLE -> when (PlaylistPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.title }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.title }
                else -> this
            }
            CommonPreferencesConstants.BY_ARTIST -> when (PlaylistPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.artist }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.artist }
                else -> this
            }
            CommonPreferencesConstants.BY_ALBUM -> when (PlaylistPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.album }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.album }
                else -> this
            }
            CommonPreferencesConstants.BY_PATH -> when (PlaylistPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.uri }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.uri }
                else -> this
            }
            CommonPreferencesConstants.BY_DATE_ADDED -> when (PlaylistPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.dateAdded }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.dateAdded }
                else -> this
            }
            CommonPreferencesConstants.BY_DATE_MODIFIED -> when (PlaylistPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.dateModified }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.dateModified }
                else -> this
            }
            CommonPreferencesConstants.BY_DURATION -> when (PlaylistPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.duration }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.duration }
                else -> this
            }
            CommonPreferencesConstants.BY_YEAR -> when (PlaylistPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.year }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.year }
                else -> this
            }
            CommonPreferencesConstants.BY_TRACK_NUMBER -> when (PlaylistPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.trackNumber }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.trackNumber }
                else -> this
            }
            CommonPreferencesConstants.BY_COMPOSER -> when (PlaylistPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.composer }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.composer }
                else -> this
            }
            else -> this
        }
    }

    /**
     * Sets this [AppCompatTextView]'s text to the human-readable label for whatever
     * sort field is currently active in the Playlists panel.
     *
     * The panel can be sorted by playlist-level fields (name, date, song count) chosen
     * from the playlists sort dialog, or by song-level fields (title, artist, etc.) used
     * inside a single playlist — both ultimately live in the same preference key, so
     * we handle all valid values here to avoid ever showing "Unknown" to the user.
     */
    fun AppCompatTextView.setPlaylistSort() {
        text = when (PlaylistPreferences.getSongSort()) {
            CommonPreferencesConstants.BY_NAME -> context.getString(R.string.name)
            CommonPreferencesConstants.BY_NUMBER_OF_SONGS -> context.getString(R.string.number_of_songs)
            CommonPreferencesConstants.BY_TITLE -> context.getString(R.string.title)
            CommonPreferencesConstants.BY_ARTIST -> context.getString(R.string.artist)
            CommonPreferencesConstants.BY_ALBUM -> context.getString(R.string.album)
            CommonPreferencesConstants.BY_PATH -> context.getString(R.string.path)
            CommonPreferencesConstants.BY_DATE_ADDED -> context.getString(R.string.date_added)
            CommonPreferencesConstants.BY_DATE_MODIFIED -> context.getString(R.string.date_modified)
            CommonPreferencesConstants.BY_DURATION -> context.getString(R.string.duration)
            CommonPreferencesConstants.BY_YEAR -> context.getString(R.string.year)
            CommonPreferencesConstants.BY_TRACK_NUMBER -> context.getString(R.string.track_number)
            CommonPreferencesConstants.BY_COMPOSER -> context.getString(R.string.composer)
            else -> context.getString(R.string.unknown)
        }
    }

    /**
     * Sets this [AppCompatTextView]'s text to the human-readable name of the current
     * playlist song sort direction.
     */
    fun AppCompatTextView.setPlaylistOrder() {
        text = when (PlaylistPreferences.getSortingStyle()) {
            CommonPreferencesConstants.ASCENDING -> context.getString(R.string.normal)
            CommonPreferencesConstants.DESCENDING -> context.getString(R.string.reversed)
            else -> context.getString(R.string.unknown)
        }
    }
}

