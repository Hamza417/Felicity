package app.simple.felicity.repository.sort

import androidx.appcompat.widget.AppCompatTextView
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.preferences.FavoritesPreferences
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.R

/**
 * Sorting utilities for the Favorites panel.
 * Uses [FavoritesPreferences] so that sort settings are independent of the Songs panel.
 *
 * @author Hamza417
 */
object FavoritesSort {

    /**
     * Returns a new list sorted according to the current [FavoritesPreferences] sort field
     * and direction.
     */
    fun List<Audio>.sortedFavorites(): List<Audio> {
        return when (FavoritesPreferences.getSongSort()) {
            CommonPreferencesConstants.BY_TITLE -> when (FavoritesPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.title }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.title }
                else -> this
            }
            CommonPreferencesConstants.BY_ARTIST -> when (FavoritesPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.artist }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.artist }
                else -> this
            }
            CommonPreferencesConstants.BY_ALBUM -> when (FavoritesPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.album }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.album }
                else -> this
            }
            CommonPreferencesConstants.BY_PATH -> when (FavoritesPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.path }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.path }
                else -> this
            }
            CommonPreferencesConstants.BY_DATE_ADDED -> when (FavoritesPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.dateAdded }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.dateAdded }
                else -> this
            }
            CommonPreferencesConstants.BY_DATE_MODIFIED -> when (FavoritesPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.dateModified }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.dateModified }
                else -> this
            }
            CommonPreferencesConstants.BY_DURATION -> when (FavoritesPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.duration }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.duration }
                else -> this
            }
            CommonPreferencesConstants.BY_YEAR -> when (FavoritesPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.year }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.year }
                else -> this
            }
            CommonPreferencesConstants.BY_TRACK_NUMBER -> when (FavoritesPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.trackNumber }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.trackNumber }
                else -> this
            }
            CommonPreferencesConstants.BY_COMPOSER -> when (FavoritesPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.composer }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.composer }
                else -> this
            }
            else -> this
        }
    }

    /**
     * Sets this [AppCompatTextView]'s text to the human-readable name of the current
     * Favorites sort field.
     */
    fun AppCompatTextView.setFavoritesSort() {
        text = when (FavoritesPreferences.getSongSort()) {
            CommonPreferencesConstants.BY_TITLE -> context.getString(R.string.title)
            CommonPreferencesConstants.BY_ARTIST -> context.getString(R.string.artist)
            CommonPreferencesConstants.BY_ALBUM -> context.getString(R.string.album)
            CommonPreferencesConstants.BY_PATH -> context.getString(R.string.path)
            CommonPreferencesConstants.BY_DATE_ADDED -> context.getString(R.string.date_added)
            CommonPreferencesConstants.BY_DATE_MODIFIED -> context.getString(R.string.date_added)
            CommonPreferencesConstants.BY_DURATION -> context.getString(R.string.duration)
            CommonPreferencesConstants.BY_YEAR -> context.getString(R.string.year)
            CommonPreferencesConstants.BY_TRACK_NUMBER -> context.getString(R.string.track_number)
            CommonPreferencesConstants.BY_COMPOSER -> context.getString(R.string.composer)
            else -> context.getString(R.string.unknown)
        }
    }

    /**
     * Sets this [AppCompatTextView]'s text to the human-readable name of the current
     * Favorites sort direction.
     */
    fun AppCompatTextView.setFavoritesOrder() {
        text = when (FavoritesPreferences.getSortingStyle()) {
            CommonPreferencesConstants.ASCENDING -> context.getString(R.string.normal)
            CommonPreferencesConstants.DESCENDING -> context.getString(R.string.reversed)
            else -> context.getString(R.string.unknown)
        }
    }
}

