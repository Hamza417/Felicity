package app.simple.felicity.repository.sort

import android.widget.TextView
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.preferences.GenresPreferences
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.shared.R

object GenreSort {
    fun List<Genre>.sorted(): List<Genre> {
        return when (GenresPreferences.getSortStyle()) {
            CommonPreferencesConstants.BY_NAME -> when (GenresPreferences.getSortOrder()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.name?.lowercase() }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.name?.lowercase() }
                else -> this
            }
            else -> this
        }
    }

    fun TextView.setCurrentSortStyle() {
        text = when (GenresPreferences.getSortStyle()) {
            CommonPreferencesConstants.BY_NAME -> context.getString(R.string.name)
            else -> context.getString(R.string.unknown)
        }
    }

    fun TextView.setCurrentSortOrder() {
        text = when (GenresPreferences.getSortOrder()) {
            CommonPreferencesConstants.ASCENDING -> context.getString(R.string.normal)
            CommonPreferencesConstants.DESCENDING -> context.getString(R.string.reversed)
            else -> context.getString(R.string.unknown)
        }
    }
}
