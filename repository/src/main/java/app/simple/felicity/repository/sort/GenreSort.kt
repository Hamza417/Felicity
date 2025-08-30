package app.simple.felicity.repository.sort

import app.simple.felicity.preferences.GenresPreferences
import app.simple.felicity.repository.models.Genre

object GenreSort {
    fun List<Genre>.sorted(): List<Genre> {
        return when (GenresPreferences.getSortStyle()) {
            GenresPreferences.BY_NAME -> when (GenresPreferences.getSortOrder()) {
                GenresPreferences.ACCENDING -> sortedBy { it.name?.lowercase() }
                GenresPreferences.DESCENDING -> sortedByDescending { it.name?.lowercase() }
                else -> this
            }
            else -> this
        }
    }
}
