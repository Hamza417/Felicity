package app.simple.felicity.repository.sort

import androidx.appcompat.widget.AppCompatTextView
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.preferences.ArtistPreferences
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.shared.R

object ArtistSort {

    fun List<Artist>.sorted(): List<Artist> {
        return when (ArtistPreferences.getArtistSort()) {
            CommonPreferencesConstants.BY_NAME -> when (ArtistPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.name }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.name }
                else -> this
            }
            CommonPreferencesConstants.BY_NUMBER_OF_ALBUMS -> when (ArtistPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.albumCount }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.albumCount }
                else -> this
            }
            CommonPreferencesConstants.BY_NUMBER_OF_SONGS -> when (ArtistPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.trackCount }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.trackCount }
                else -> this
            }
            else -> this
        }
    }

    fun AppCompatTextView.setCurrentSortStyle() {
        text = when (ArtistPreferences.getArtistSort()) {
            CommonPreferencesConstants.BY_NAME -> context.getString(R.string.name)
            CommonPreferencesConstants.BY_NUMBER_OF_ALBUMS -> context.getString(R.string.number_of_albums)
            CommonPreferencesConstants.BY_NUMBER_OF_SONGS -> context.getString(R.string.number_of_songs)
            else -> context.getString(R.string.unknown)
        }
    }

    fun AppCompatTextView.setCurrentSortOrder() {
        text = when (ArtistPreferences.getSortingStyle()) {
            CommonPreferencesConstants.ASCENDING -> context.getString(R.string.normal)
            CommonPreferencesConstants.DESCENDING -> context.getString(R.string.reversed)
            else -> context.getString(R.string.unknown)
        }
    }
}