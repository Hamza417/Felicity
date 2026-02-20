package app.simple.felicity.repository.sort

import androidx.appcompat.widget.AppCompatTextView
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.preferences.SearchPreferences
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.R

object SearchSort {

    fun List<Audio>.searchSorted(): List<Audio> {
        return when (SearchPreferences.getSongSort()) {
            CommonPreferencesConstants.BY_TITLE -> when (SearchPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.title }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.title }
                else -> this
            }
            CommonPreferencesConstants.BY_ARTIST -> when (SearchPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.artist }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.artist }
                else -> this
            }
            CommonPreferencesConstants.BY_ALBUM -> when (SearchPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.album }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.album }
                else -> this
            }
            CommonPreferencesConstants.BY_PATH -> when (SearchPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.path }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.path }
                else -> this
            }
            CommonPreferencesConstants.BY_DATE_ADDED -> when (SearchPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.dateAdded }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.dateAdded }
                else -> this
            }
            CommonPreferencesConstants.BY_DATE_MODIFIED -> when (SearchPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.dateModified }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.dateModified }
                else -> this
            }
            CommonPreferencesConstants.BY_DURATION -> when (SearchPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.duration }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.duration }
                else -> this
            }
            CommonPreferencesConstants.BY_YEAR -> when (SearchPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.year }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.year }
                else -> this
            }
            CommonPreferencesConstants.BY_TRACK_NUMBER -> when (SearchPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.trackNumber }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.trackNumber }
                else -> this
            }
            CommonPreferencesConstants.BY_COMPOSER -> when (SearchPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.composer }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.composer }
                else -> this
            }
            else -> this
        }
    }

    fun AppCompatTextView.setSearchSort() {
        text = when (SearchPreferences.getSongSort()) {
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

    fun AppCompatTextView.setSearchOrder() {
        text = when (SearchPreferences.getSortingStyle()) {
            CommonPreferencesConstants.ASCENDING -> context.getString(R.string.normal)
            CommonPreferencesConstants.DESCENDING -> context.getString(R.string.reversed)
            else -> context.getString(R.string.unknown)
        }
    }
}

