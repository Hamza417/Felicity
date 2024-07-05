package app.simple.felicity.preferences

object ConfigurationPreferences {

    private const val KEEP_SCREEN_ON = "keep_screen_on"
    private const val ALBUM_ART_LOADER_SOURCE = "album_art_loader_source"

    const val LANGUAGE = "language_of_app"

    const val ANDROID_API = "android_api"
    const val JAUDIO_TAG = "jAudioTag"

    fun setKeepScreenOn(value: Boolean) {
        SharedPreferences.getSharedPreferences().edit().putBoolean(KEEP_SCREEN_ON, value).apply()
    }

    fun isKeepScreenOn(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(KEEP_SCREEN_ON, false)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setAppLanguage(value: String) {
        SharedPreferences.getSharedPreferences().edit().putString(LANGUAGE, value).apply()
    }

    fun getAppLanguage(): String {
        return SharedPreferences.getSharedPreferences().getString(LANGUAGE, "default")!!
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setAlbumArtLoaderSource(value: String) {
        SharedPreferences.getSharedPreferences().edit().putString(ALBUM_ART_LOADER_SOURCE, value).apply()
    }

    fun getAlbumArtLoaderSource(): String {
        return SharedPreferences.getSharedPreferences().getString(ALBUM_ART_LOADER_SOURCE, JAUDIO_TAG)!!
    }
}
