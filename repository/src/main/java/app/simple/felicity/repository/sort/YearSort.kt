package app.simple.felicity.repository.sort

import android.widget.TextView
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.preferences.YearPreferences
import app.simple.felicity.repository.models.YearGroup
import app.simple.felicity.shared.R

object YearSort {
    fun List<YearGroup>.sorted(): List<YearGroup> {
        return when (YearPreferences.getSortStyle()) {
            CommonPreferencesConstants.BY_YEAR -> when (YearPreferences.getSortOrder()) {
                CommonPreferencesConstants.ASCENDING -> sortedWith(compareBy { it.year.toIntOrNull() ?: Int.MIN_VALUE })
                CommonPreferencesConstants.DESCENDING -> sortedWith(compareByDescending { it.year.toIntOrNull() ?: Int.MIN_VALUE })
                else -> this
            }
            CommonPreferencesConstants.BY_NUMBER_OF_SONGS -> when (YearPreferences.getSortOrder()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.songCount }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.songCount }
                else -> this
            }
            else -> this
        }
    }

    fun TextView.setCurrentSortStyle() {
        text = when (YearPreferences.getSortStyle()) {
            CommonPreferencesConstants.BY_YEAR -> context.getString(R.string.year)
            CommonPreferencesConstants.BY_NUMBER_OF_SONGS -> context.getString(R.string.number_of_songs)
            else -> context.getString(R.string.unknown)
        }
    }

    fun TextView.setCurrentSortOrder() {
        text = when (YearPreferences.getSortOrder()) {
            CommonPreferencesConstants.ASCENDING -> context.getString(R.string.normal)
            CommonPreferencesConstants.DESCENDING -> context.getString(R.string.reversed)
            else -> context.getString(R.string.unknown)
        }
    }
}

