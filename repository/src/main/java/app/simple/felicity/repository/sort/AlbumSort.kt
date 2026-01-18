package app.simple.felicity.repository.sort

import android.widget.TextView
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.preferences.AlbumPreferences
import app.simple.felicity.repository.models.Album
import app.simple.felicity.shared.R

object AlbumSort {

    fun List<Album>.sorted(): List<Album> {
        return when (AlbumPreferences.getAlbumSort()) {
            CommonPreferencesConstants.BY_ALBUM_NAME -> when (AlbumPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.name }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.name }
                else -> this
            }
            CommonPreferencesConstants.BY_ARTIST -> when (AlbumPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.artist }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.artist }
                else -> this
            }
            CommonPreferencesConstants.BY_NUMBER_OF_SONGS -> when (AlbumPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.songCount }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.songCount }
                else -> this
            }
            CommonPreferencesConstants.BY_FIRST_YEAR -> when (AlbumPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.firstYear }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.firstYear }
                else -> this
            }
            CommonPreferencesConstants.BY_LAST_YEAR -> when (AlbumPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.lastYear }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.lastYear }
                else -> this
            }
            else -> this
        }
    }

    fun TextView.setCurrentSortStyle() {
        text = when (AlbumPreferences.getAlbumSort()) {
            CommonPreferencesConstants.BY_ALBUM_NAME -> context.getString(R.string.name)
            CommonPreferencesConstants.BY_ARTIST -> context.getString(R.string.artist)
            CommonPreferencesConstants.BY_NUMBER_OF_SONGS -> context.getString(R.string.number_of_songs)
            CommonPreferencesConstants.BY_YEAR -> context.getString(R.string.year)
            CommonPreferencesConstants.BY_FIRST_YEAR -> context.getString(R.string.first_year)
            CommonPreferencesConstants.BY_LAST_YEAR -> context.getString(R.string.last_year)
            else -> context.getString(R.string.unknown)
        }
    }

    fun TextView.setCurrentSortOrder() {
        text = when (AlbumPreferences.getSortingStyle()) {
            CommonPreferencesConstants.ASCENDING -> context.getString(R.string.normal)
            CommonPreferencesConstants.DESCENDING -> context.getString(R.string.reversed)
            else -> context.getString(R.string.unknown)
        }
    }
}