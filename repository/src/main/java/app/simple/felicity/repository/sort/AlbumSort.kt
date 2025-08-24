package app.simple.felicity.repository.sort

import android.widget.TextView
import app.simple.felicity.core.R
import app.simple.felicity.preferences.AlbumPreferences

object AlbumSort {

    fun List<app.simple.felicity.repository.models.Album>.sorted(): List<app.simple.felicity.repository.models.Album> {
        return when (AlbumPreferences.getAlbumSort()) {
            AlbumPreferences.BY_ALBUM_NAME -> when (AlbumPreferences.getSortingStyle()) {
                AlbumPreferences.ACCENDING -> sortedBy { it.name }
                AlbumPreferences.DESCENDING -> sortedByDescending { it.name }
                else -> this
            }
            AlbumPreferences.BY_ARTIST -> when (AlbumPreferences.getSortingStyle()) {
                AlbumPreferences.ACCENDING -> sortedBy { it.artist }
                AlbumPreferences.DESCENDING -> sortedByDescending { it.artist }
                else -> this
            }
            AlbumPreferences.BY_NUMBER_OF_SONGS -> when (AlbumPreferences.getSortingStyle()) {
                AlbumPreferences.ACCENDING -> sortedBy { it.songCount }
                AlbumPreferences.DESCENDING -> sortedByDescending { it.songCount }
                else -> this
            }
            AlbumPreferences.BY_YEAR -> when (AlbumPreferences.getSortingStyle()) {
                AlbumPreferences.ACCENDING -> sortedBy { it.firstYear }
                AlbumPreferences.DESCENDING -> sortedByDescending { it.firstYear }
                else -> this
            }
            AlbumPreferences.BY_FIRST_YEAR -> when (AlbumPreferences.getSortingStyle()) {
                AlbumPreferences.ACCENDING -> sortedBy { it.firstYear }
                AlbumPreferences.DESCENDING -> sortedByDescending { it.firstYear }
                else -> this
            }
            AlbumPreferences.BY_LAST_YEAR -> when (AlbumPreferences.getSortingStyle()) {
                AlbumPreferences.ACCENDING -> sortedBy { it.lastYear }
                AlbumPreferences.DESCENDING -> sortedByDescending { it.lastYear }
                else -> this
            }
            else -> this
        }
    }

    fun TextView.setCurrentSortStyle() {
        text = when (AlbumPreferences.getAlbumSort()) {
            AlbumPreferences.BY_ALBUM_NAME -> context.getString(R.string.name)
            AlbumPreferences.BY_ARTIST -> context.getString(R.string.artist)
            AlbumPreferences.BY_NUMBER_OF_SONGS -> context.getString(R.string.number_of_songs)
            AlbumPreferences.BY_YEAR -> context.getString(R.string.year)
            AlbumPreferences.BY_FIRST_YEAR -> context.getString(R.string.first_year)
            AlbumPreferences.BY_LAST_YEAR -> context.getString(R.string.last_year)
            else -> context.getString(R.string.unknown)
        }
    }

    fun TextView.setCurrentSortOrder() {
        text = when (AlbumPreferences.getSortingStyle()) {
            AlbumPreferences.ACCENDING -> context.getString(R.string.normal)
            AlbumPreferences.DESCENDING -> context.getString(R.string.reversed)
            else -> context.getString(R.string.unknown)
        }
    }
}