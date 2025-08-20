package app.simple.felicity.repository.sort

import app.simple.felicity.preferences.SongsPreferences
import app.simple.felicity.repository.models.Song

object SongSort {

    fun List<Song>.sorted(): List<Song> {
        return when (SongsPreferences.getSongSort()) {
            SongsPreferences.BY_TITLE -> when (SongsPreferences.getSortingStyle()) {
                SongsPreferences.ACCENDING -> sortedBy { it.title }
                SongsPreferences.DESCENDING -> sortedByDescending { it.title }
                else -> this
            }
            SongsPreferences.BY_ARTIST -> when (SongsPreferences.getSortingStyle()) {
                SongsPreferences.ACCENDING -> sortedBy { it.artist }
                SongsPreferences.DESCENDING -> sortedByDescending { it.artist }
                else -> this
            }
            SongsPreferences.BY_ALBUM -> when (SongsPreferences.getSortingStyle()) {
                SongsPreferences.ACCENDING -> sortedBy { it.album }
                SongsPreferences.DESCENDING -> sortedByDescending { it.album }
                else -> this
            }
            SongsPreferences.PATH -> when (SongsPreferences.getSortingStyle()) {
                SongsPreferences.ACCENDING -> sortedBy { it.path }
                SongsPreferences.DESCENDING -> sortedByDescending { it.path }
                else -> this
            }
            SongsPreferences.BY_DATE_ADDED -> when (SongsPreferences.getSortingStyle()) {
                SongsPreferences.ACCENDING -> sortedBy { it.dateAdded }
                SongsPreferences.DESCENDING -> sortedByDescending { it.dateAdded }
                else -> this
            }
            SongsPreferences.BY_DATE_MODIFIED -> when (SongsPreferences.getSortingStyle()) {
                SongsPreferences.ACCENDING -> sortedBy { it.dateModified }
                SongsPreferences.DESCENDING -> sortedByDescending { it.dateModified }
                else -> this
            }
            SongsPreferences.BY_DURATION -> when (SongsPreferences.getSortingStyle()) {
                SongsPreferences.ACCENDING -> sortedBy { it.duration }
                SongsPreferences.DESCENDING -> sortedByDescending { it.duration }
                else -> this
            }
            SongsPreferences.BY_YEAR -> when (SongsPreferences.getSortingStyle()) {
                SongsPreferences.ACCENDING -> sortedBy { it.year }
                SongsPreferences.DESCENDING -> sortedByDescending { it.year }
                else -> this
            }
            SongsPreferences.BY_TRACK_NUMBER -> when (SongsPreferences.getSortingStyle()) {
                SongsPreferences.ACCENDING -> sortedBy { it.trackNumber }
                SongsPreferences.DESCENDING -> sortedByDescending { it.trackNumber }
                else -> this
            }
            SongsPreferences.BY_COMPOSER -> when (SongsPreferences.getSortingStyle()) {
                SongsPreferences.ACCENDING -> sortedBy { it.composer }
                SongsPreferences.DESCENDING -> sortedByDescending { it.composer }
                else -> this
            }
            else -> this
        }
    }

    fun List<Song>.sort(): List<Song> {
        return sorted()
    }
}