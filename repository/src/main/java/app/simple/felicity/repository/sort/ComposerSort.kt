package app.simple.felicity.repository.sort

import androidx.appcompat.widget.AppCompatTextView
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.preferences.ComposerPreferences
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.shared.R

object ComposerSort {

    fun List<Artist>.sortedComposers(): List<Artist> {
        return when (ComposerPreferences.getComposerSort()) {
            CommonPreferencesConstants.BY_NAME -> when (ComposerPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.name?.lowercase() }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.name?.lowercase() }
                else -> this
            }
            CommonPreferencesConstants.BY_NUMBER_OF_SONGS -> when (ComposerPreferences.getSortingStyle()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.trackCount }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.trackCount }
                else -> this
            }
            else -> this
        }
    }

    fun AppCompatTextView.setCurrentSortStyle() {
        text = when (ComposerPreferences.getComposerSort()) {
            CommonPreferencesConstants.BY_NAME -> context.getString(R.string.name)
            CommonPreferencesConstants.BY_NUMBER_OF_SONGS -> context.getString(R.string.number_of_songs)
            else -> context.getString(R.string.unknown)
        }
    }

    fun AppCompatTextView.setCurrentSortOrder() {
        text = when (ComposerPreferences.getSortingStyle()) {
            CommonPreferencesConstants.ASCENDING -> context.getString(R.string.normal)
            CommonPreferencesConstants.DESCENDING -> context.getString(R.string.reversed)
            else -> context.getString(R.string.unknown)
        }
    }
}

