package app.simple.felicity.repository.sort

import android.widget.TextView
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.preferences.FoldersPreferences
import app.simple.felicity.repository.models.Folder
import app.simple.felicity.shared.R

object FolderSort {
    fun List<Folder>.sorted(): List<Folder> {
        return when (FoldersPreferences.getSortStyle()) {
            CommonPreferencesConstants.BY_NAME -> when (FoldersPreferences.getSortOrder()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.name.lowercase() }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.name.lowercase() }
                else -> this
            }
            CommonPreferencesConstants.BY_NUMBER_OF_SONGS -> when (FoldersPreferences.getSortOrder()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.songCount }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.songCount }
                else -> this
            }
            CommonPreferencesConstants.BY_PATH -> when (FoldersPreferences.getSortOrder()) {
                CommonPreferencesConstants.ASCENDING -> sortedBy { it.path.lowercase() }
                CommonPreferencesConstants.DESCENDING -> sortedByDescending { it.path.lowercase() }
                else -> this
            }
            else -> this
        }
    }

    fun TextView.setCurrentSortStyle() {
        text = when (FoldersPreferences.getSortStyle()) {
            CommonPreferencesConstants.BY_NAME -> context.getString(R.string.name)
            CommonPreferencesConstants.BY_NUMBER_OF_SONGS -> context.getString(R.string.number_of_songs)
            CommonPreferencesConstants.BY_PATH -> context.getString(R.string.path)
            else -> context.getString(R.string.unknown)
        }
    }

    fun TextView.setCurrentSortOrder() {
        text = when (FoldersPreferences.getSortOrder()) {
            CommonPreferencesConstants.ASCENDING -> context.getString(R.string.normal)
            CommonPreferencesConstants.DESCENDING -> context.getString(R.string.reversed)
            else -> context.getString(R.string.unknown)
        }
    }
}

