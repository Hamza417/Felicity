package app.simple.felicity.repository.sort

import androidx.appcompat.widget.AppCompatTextView
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.preferences.AlbumArtistPreferences
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.shared.R

/**
 * Sorting helpers for the Album Artists panel. Works just like [ArtistSort] but pulls
 * from [AlbumArtistPreferences] so album artist sorting never interferes with regular
 * artist sorting — two panels, two sets of preferences, zero drama.
 *
 * @author Hamza417
 */
object AlbumArtistSort {

    /**
     * Returns a sorted copy of this list based on the current [AlbumArtistPreferences] settings.
     * The original list is never mutated — we're polite like that.
     */
    fun List<Artist>.sortedAlbumArtists(): List<Artist> {
        return when (AlbumArtistPreferences.getAlbumArtistSort()) {
            CommonPreferencesConstants.BY_NAME -> when (AlbumArtistPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.name }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.name }
                else -> this
            }
            CommonPreferencesConstants.BY_NUMBER_OF_ALBUMS -> when (AlbumArtistPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.albumCount }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.albumCount }
                else -> this
            }
            CommonPreferencesConstants.BY_NUMBER_OF_SONGS -> when (AlbumArtistPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.trackCount }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.trackCount }
                else -> this
            }
            else -> this
        }
    }

    /**
     * Updates this TextView's text to reflect the current sort field label
     * so users always know what column they sorted by.
     */
    fun AppCompatTextView.setCurrentSortStyle() {
        text = when (AlbumArtistPreferences.getAlbumArtistSort()) {
            CommonPreferencesConstants.BY_NAME -> context.getString(R.string.name)
            CommonPreferencesConstants.BY_NUMBER_OF_ALBUMS -> context.getString(R.string.number_of_albums)
            CommonPreferencesConstants.BY_NUMBER_OF_SONGS -> context.getString(R.string.number_of_songs)
            else -> context.getString(R.string.unknown)
        }
    }

    /**
     * Updates this TextView's text to reflect whether the list is sorted
     * in normal (ascending) or reversed (descending) order.
     */
    fun AppCompatTextView.setCurrentSortOrder() {
        text = when (AlbumArtistPreferences.getSortingStyle()) {
            CommonPreferencesConstants.ASCENDING -> context.getString(R.string.normal)
            CommonPreferencesConstants.DESCENDING -> context.getString(R.string.reversed)
            else -> context.getString(R.string.unknown)
        }
    }
}

