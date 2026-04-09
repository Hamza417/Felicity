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

