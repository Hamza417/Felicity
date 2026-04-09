package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.manager.SharedPreferences

/**
 * Stores per-page sort preferences for each viewer page independently, keeping them
 * separate from the global Songs sort and from each other.
 *
 * Each page type has its own sort-field key and sort-order key so that the AlbumPage,
 * ArtistPage, GenrePage, FolderPage, YearPage, and PlaylistPage can each remember the
 * user's last chosen sort without interfering with one another.
 *
 * @author Hamza417
 */
object PagePreferences {

    const val PAGE_SORT_ALBUM = "page_sort_album"
    const val PAGE_ORDER_ALBUM = "page_order_album"

    const val PAGE_SORT_ARTIST = "page_sort_artist"
    const val PAGE_ORDER_ARTIST = "page_order_artist"

    const val PAGE_SORT_GENRE = "page_sort_genre"
    const val PAGE_ORDER_GENRE = "page_order_genre"

    const val PAGE_SORT_FOLDER = "page_sort_folder"
    const val PAGE_ORDER_FOLDER = "page_order_folder"

    const val PAGE_SORT_YEAR = "page_sort_year"
    const val PAGE_ORDER_YEAR = "page_order_year"

    const val PAGE_SORT_PLAYLIST = "page_sort_playlist"
    const val PAGE_ORDER_PLAYLIST = "page_order_playlist"

    /** All sort-field keys as a set for quick membership checks. */
    val ALL_SORT_KEYS: Set<String> = setOf(
            PAGE_SORT_ALBUM, PAGE_SORT_ARTIST, PAGE_SORT_GENRE,
            PAGE_SORT_FOLDER, PAGE_SORT_YEAR, PAGE_SORT_PLAYLIST
    )

    /** All sort-order keys as a set for quick membership checks. */
    val ALL_ORDER_KEYS: Set<String> = setOf(
            PAGE_ORDER_ALBUM, PAGE_ORDER_ARTIST, PAGE_ORDER_GENRE,
            PAGE_ORDER_FOLDER, PAGE_ORDER_YEAR, PAGE_ORDER_PLAYLIST
    )

    /** Returns the combined set of all sort-related preference keys. */
    val ALL_PAGE_PREF_KEYS: Set<String> = ALL_SORT_KEYS + ALL_ORDER_KEYS

    fun getAlbumSort(): Int =
        SharedPreferences.getSharedPreferences().getInt(PAGE_SORT_ALBUM, CommonPreferencesConstants.BY_TITLE)

    fun setAlbumSort(value: Int) =
        SharedPreferences.getSharedPreferences().edit { putInt(PAGE_SORT_ALBUM, value) }

    fun getAlbumOrder(): Int =
        SharedPreferences.getSharedPreferences().getInt(PAGE_ORDER_ALBUM, CommonPreferencesConstants.ASCENDING)

    fun setAlbumOrder(value: Int) =
        SharedPreferences.getSharedPreferences().edit { putInt(PAGE_ORDER_ALBUM, value) }

    fun getArtistSort(): Int =
        SharedPreferences.getSharedPreferences().getInt(PAGE_SORT_ARTIST, CommonPreferencesConstants.BY_TITLE)

    fun setArtistSort(value: Int) =
        SharedPreferences.getSharedPreferences().edit { putInt(PAGE_SORT_ARTIST, value) }

    fun getArtistOrder(): Int =
        SharedPreferences.getSharedPreferences().getInt(PAGE_ORDER_ARTIST, CommonPreferencesConstants.ASCENDING)

    fun setArtistOrder(value: Int) =
        SharedPreferences.getSharedPreferences().edit { putInt(PAGE_ORDER_ARTIST, value) }

    fun getGenreSort(): Int =
        SharedPreferences.getSharedPreferences().getInt(PAGE_SORT_GENRE, CommonPreferencesConstants.BY_TITLE)

    fun setGenreSort(value: Int) =
        SharedPreferences.getSharedPreferences().edit { putInt(PAGE_SORT_GENRE, value) }

    fun getGenreOrder(): Int =
        SharedPreferences.getSharedPreferences().getInt(PAGE_ORDER_GENRE, CommonPreferencesConstants.ASCENDING)

    fun setGenreOrder(value: Int) =
        SharedPreferences.getSharedPreferences().edit { putInt(PAGE_ORDER_GENRE, value) }

    fun getFolderSort(): Int =
        SharedPreferences.getSharedPreferences().getInt(PAGE_SORT_FOLDER, CommonPreferencesConstants.BY_TITLE)

    fun setFolderSort(value: Int) =
        SharedPreferences.getSharedPreferences().edit { putInt(PAGE_SORT_FOLDER, value) }

    fun getFolderOrder(): Int =
        SharedPreferences.getSharedPreferences().getInt(PAGE_ORDER_FOLDER, CommonPreferencesConstants.ASCENDING)

    fun setFolderOrder(value: Int) =
        SharedPreferences.getSharedPreferences().edit { putInt(PAGE_ORDER_FOLDER, value) }

    fun getYearSort(): Int =
        SharedPreferences.getSharedPreferences().getInt(PAGE_SORT_YEAR, CommonPreferencesConstants.BY_TITLE)

    fun setYearSort(value: Int) =
        SharedPreferences.getSharedPreferences().edit { putInt(PAGE_SORT_YEAR, value) }

    fun getYearOrder(): Int =
        SharedPreferences.getSharedPreferences().getInt(PAGE_ORDER_YEAR, CommonPreferencesConstants.ASCENDING)

    fun setYearOrder(value: Int) =
        SharedPreferences.getSharedPreferences().edit { putInt(PAGE_ORDER_YEAR, value) }

    fun getPlaylistSort(): Int =
        SharedPreferences.getSharedPreferences().getInt(PAGE_SORT_PLAYLIST, CommonPreferencesConstants.BY_TITLE)

    fun setPlaylistSort(value: Int) =
        SharedPreferences.getSharedPreferences().edit { putInt(PAGE_SORT_PLAYLIST, value) }

    fun getPlaylistOrder(): Int =
        SharedPreferences.getSharedPreferences().getInt(PAGE_ORDER_PLAYLIST, CommonPreferencesConstants.ASCENDING)

    fun setPlaylistOrder(value: Int) =
        SharedPreferences.getSharedPreferences().edit { putInt(PAGE_ORDER_PLAYLIST, value) }
}

