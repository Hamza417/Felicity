package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

object LibraryPreferences {

    private const val USE_MEDIASTORE_ARTWORK = "use_mediastore_artwork"
    const val ALBUM_ARTIST_OVER_ARTIST = "album_artist_over_artist"

    const val MINIMUM_AUDIO_LENGTH = "minimum_audio_length"
    const val MINIMUM_AUDIO_SIZE = "minimum_audio_size"

    const val SKIP_NOMEDIA = "skip_nomedia_folders"
    const val SKIP_HIDDEN_FILES = "skip_hidden_files"
    const val SKIP_HIDDEN_FOLDERS = "skip_hidden_folders"

    /** Key for the set of folder paths that should always be included in the scan. */
    private const val INCLUDED_FOLDERS = "included_folders_set"

    /** Key for the set of folder paths that should always be excluded from the scan. */
    private const val EXCLUDED_FOLDERS = "excluded_folders_set"

    fun getIncludedFolders(): Set<String> {
        return SharedPreferences.getSharedPreferences().getStringSet(INCLUDED_FOLDERS, emptySet()) ?: emptySet()
    }

    fun setIncludedFolders(folders: Set<String>) {
        SharedPreferences.getSharedPreferences().edit { putStringSet(INCLUDED_FOLDERS, folders) }
    }

    fun addIncludedFolder(path: String) {
        val current = getIncludedFolders().toMutableSet()
        current.add(path)
        setIncludedFolders(current)
    }

    fun removeIncludedFolder(path: String) {
        val current = getIncludedFolders().toMutableSet()
        current.remove(path)
        setIncludedFolders(current)
    }

    fun getExcludedFolders(): Set<String> {
        return SharedPreferences.getSharedPreferences().getStringSet(EXCLUDED_FOLDERS, emptySet()) ?: emptySet()
    }

    fun setExcludedFolders(folders: Set<String>) {
        SharedPreferences.getSharedPreferences().edit { putStringSet(EXCLUDED_FOLDERS, folders) }
    }

    fun addExcludedFolder(path: String) {
        val current = getExcludedFolders().toMutableSet()
        current.add(path)
        setExcludedFolders(current)
    }

    fun removeExcludedFolder(path: String) {
        val current = getExcludedFolders().toMutableSet()
        current.remove(path)
        setExcludedFolders(current)
    }

    fun getMinimumAudioLength(): Int {
        return SharedPreferences.getSharedPreferences().getInt(MINIMUM_AUDIO_LENGTH, 0)
    }

    fun setMinimumAudioLength(length: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(MINIMUM_AUDIO_LENGTH, length) }
    }

    // ----------------------------------------------------------------------------------------------------- //

    fun getMinimumAudioSize(): Int {
        return SharedPreferences.getSharedPreferences().getInt(MINIMUM_AUDIO_SIZE, 0)
    }

    fun setMinimumAudioSize(size: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(MINIMUM_AUDIO_SIZE, size) }
    }

    // ----------------------------------------------------------------------------------------------------- //

    fun isSkipNomedia(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(SKIP_NOMEDIA, true)
    }

    fun setSkipNomedia(skip: Boolean) {
        SharedPreferences.getSharedPreferences().edit { putBoolean(SKIP_NOMEDIA, skip) }
    }

    // ----------------------------------------------------------------------------------------------------- //

    fun isSkipHiddenFiles(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(SKIP_HIDDEN_FILES, true)
    }

    fun setSkipHiddenFiles(skip: Boolean) {
        SharedPreferences.getSharedPreferences().edit { putBoolean(SKIP_HIDDEN_FILES, skip) }
    }

    // ----------------------------------------------------------------------------------------------------- //

    fun isSkipHiddenFolders(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(SKIP_HIDDEN_FOLDERS, true)
    }

    fun setSkipHiddenFolders(skip: Boolean) {
        SharedPreferences.getSharedPreferences().edit { putBoolean(SKIP_HIDDEN_FOLDERS, skip) }
    }

    // ----------------------------------------------------------------------------------------------------- //

    fun isUseMediaStoreArtwork(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(USE_MEDIASTORE_ARTWORK, true)
    }

    fun setUseMediaStoreArtwork(use: Boolean) {
        SharedPreferences.getSharedPreferences().edit { putBoolean(USE_MEDIASTORE_ARTWORK, use) }
    }

    // ------------------------------------------------------------------------------------------------------ //

    fun isAlbumArtistOverArtist(): Boolean {
        return SharedPreferences.getSharedPreferences()
            .getBoolean(ALBUM_ARTIST_OVER_ARTIST, false)
    }

    fun setAlbumArtistOverArtist(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit {
            putBoolean(ALBUM_ARTIST_OVER_ARTIST, enabled)
        }
    }
}

