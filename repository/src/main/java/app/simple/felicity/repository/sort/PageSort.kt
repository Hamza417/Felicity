package app.simple.felicity.repository.sort

import androidx.appcompat.widget.AppCompatTextView
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.preferences.PagePreferences
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.sort.PageSort.PAGE_TYPE
import app.simple.felicity.shared.R

/**
 * Sorting utilities for per-page song lists. Each page type (Album, Artist, Genre, Folder,
 * Year, Playlist) stores its own sort field and sort order in [PagePreferences], so sorting
 * one page never affects another.
 *
 * Extension functions on [List]<[Audio]> apply the stored preference for the given page, and
 * extension functions on [AppCompatTextView] set the chip label text accordingly.
 *
 * @author Hamza417
 */
object PageSort {

    /** Keys passed as the [PAGE_TYPE] bundle argument to [app.simple.felicity.dialogs.pages.PageSortDialog]. */
    const val PAGE_TYPE_ALBUM = "album"
    const val PAGE_TYPE_ARTIST = "artist"
    const val PAGE_TYPE_GENRE = "genre"
    const val PAGE_TYPE_FOLDER = "folder"
    const val PAGE_TYPE_YEAR = "year"
    const val PAGE_TYPE_PLAYLIST = "playlist"

    /** Bundle key for the page-type string. */
    const val PAGE_TYPE = "page_type"

    fun List<Audio>.sortedForAlbumPage(): List<Audio> {
        return applySort(PagePreferences.getAlbumSort(), PagePreferences.getAlbumOrder())
    }

    fun List<Audio>.sortedForArtistPage(): List<Audio> {
        return applySort(PagePreferences.getArtistSort(), PagePreferences.getArtistOrder())
    }

    fun List<Audio>.sortedForGenrePage(): List<Audio> {
        return applySort(PagePreferences.getGenreSort(), PagePreferences.getGenreOrder())
    }

    fun List<Audio>.sortedForFolderPage(): List<Audio> {
        return applySort(PagePreferences.getFolderSort(), PagePreferences.getFolderOrder())
    }

    fun List<Audio>.sortedForYearPage(): List<Audio> {
        return applySort(PagePreferences.getYearSort(), PagePreferences.getYearOrder())
    }

    fun List<Audio>.sortedForPlaylistPage(): List<Audio> {
        return applySort(PagePreferences.getPlaylistSort(), PagePreferences.getPlaylistOrder())
    }

    /** Applies [sortField] and [order] to this [List]<[Audio]>. */
    private fun List<Audio>.applySort(sortField: Int, order: Int): List<Audio> {
        val ascending = order == CommonPreferencesConstants.ASCENDING
        return when (sortField) {
            CommonPreferencesConstants.BY_TITLE -> if (ascending) sortedBy { it.title } else sortedByDescending { it.title }
            CommonPreferencesConstants.BY_ARTIST -> if (ascending) sortedBy { it.artist } else sortedByDescending { it.artist }
            CommonPreferencesConstants.BY_ALBUM -> if (ascending) sortedBy { it.album } else sortedByDescending { it.album }
            CommonPreferencesConstants.BY_PATH -> if (ascending) sortedBy { it.uri } else sortedByDescending { it.uri }
            CommonPreferencesConstants.BY_DATE_ADDED -> if (ascending) sortedBy { it.dateAdded } else sortedByDescending { it.dateAdded }
            CommonPreferencesConstants.BY_DATE_MODIFIED -> if (ascending) sortedBy { it.dateModified } else sortedByDescending { it.dateModified }
            CommonPreferencesConstants.BY_DURATION -> if (ascending) sortedBy { it.duration } else sortedByDescending { it.duration }
            CommonPreferencesConstants.BY_YEAR -> if (ascending) sortedBy { it.year } else sortedByDescending { it.year }
            CommonPreferencesConstants.BY_TRACK_NUMBER -> {
                // trackNumber may be stored as "N/total" — extract the leading integer so
                // the sort is strictly numeric (1, 2 … 12) rather than lexicographic.
                val trackInt: (Audio) -> Int = { audio ->
                    audio.trackNumber?.trim()?.split("/")?.firstOrNull()?.trim()?.toIntOrNull() ?: Int.MAX_VALUE
                }
                if (ascending) sortedBy(trackInt) else sortedByDescending(trackInt)
            }
            CommonPreferencesConstants.BY_COMPOSER -> if (ascending) sortedBy { it.composer } else sortedByDescending { it.composer }
            else -> this
        }
    }

    fun AppCompatTextView.setAlbumPageSort() = setSortLabel(PagePreferences.getAlbumSort())
    fun AppCompatTextView.setAlbumPageOrder() = setOrderLabel(PagePreferences.getAlbumOrder())

    fun AppCompatTextView.setArtistPageSort() = setSortLabel(PagePreferences.getArtistSort())
    fun AppCompatTextView.setArtistPageOrder() = setOrderLabel(PagePreferences.getArtistOrder())

    fun AppCompatTextView.setGenrePageSort() = setSortLabel(PagePreferences.getGenreSort())
    fun AppCompatTextView.setGenrePageOrder() = setOrderLabel(PagePreferences.getGenreOrder())

    fun AppCompatTextView.setFolderPageSort() = setSortLabel(PagePreferences.getFolderSort())
    fun AppCompatTextView.setFolderPageOrder() = setOrderLabel(PagePreferences.getFolderOrder())

    fun AppCompatTextView.setYearPageSort() = setSortLabel(PagePreferences.getYearSort())
    fun AppCompatTextView.setYearPageOrder() = setOrderLabel(PagePreferences.getYearOrder())

    fun AppCompatTextView.setPlaylistPageSort() = setSortLabel(PagePreferences.getPlaylistSort())
    fun AppCompatTextView.setPlaylistPageOrder() = setOrderLabel(PagePreferences.getPlaylistOrder())

    private fun AppCompatTextView.setSortLabel(sortField: Int) {
        text = when (sortField) {
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

    private fun AppCompatTextView.setOrderLabel(order: Int) {
        text = when (order) {
            CommonPreferencesConstants.ASCENDING -> context.getString(R.string.normal)
            CommonPreferencesConstants.DESCENDING -> context.getString(R.string.reversed)
            else -> context.getString(R.string.unknown)
        }
    }
}

