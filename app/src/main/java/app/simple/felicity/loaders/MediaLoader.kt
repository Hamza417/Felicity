package app.simple.felicity.loaders

import android.content.Context
import app.simple.felicity.database.instances.AudioDatabase
import app.simple.felicity.models.Audio

object MediaLoader {

    const val MEDIA_ID_SONGS = "media_id_songs"
    const val MEDIA_ID_ALBUMS = "media_id_albums"
    const val MEDIA_ID_ARTISTS = "media_id_artists"
    const val MEDIA_ID_GENRES = "media_id_genres"
    const val MEDIA_ID_PLAYLISTS = "media_id_playlists"
    const val MEDIA_ID_FOLDERS = "media_id_folders"
    const val MEDIA_ID_FAVORITES = "media_id_favorites"
    const val MEDIA_ID_RECENTLY_ADDED = "media_id_recently_added"
    const val MEDIA_ID_RECENTLY_PLAYED = "media_id_recently_played"
    const val MEDIA_ID_TOP_TRACKS = "media_id_top_tracks"
    const val MEDIA_ID_TOP_ALBUMS = "media_id_top_albums"
    const val MEDIA_ID_TOP_ARTISTS = "media_id_top_artists"
    const val MEDIA_ID_TOP_GENRES = "media_id_top_genres"
    const val MEDIA_ID_TOP_PLAYLISTS = "media_id_top_playlists"
    const val MEDIA_ID_TOP_FOLDERS = "media_id_top_folders"
    const val MEDIA_ID_TOP_FAVORITES = "media_id_top_favorites"

    fun init(context: Context) {
        AudioDatabase.init(context)
    }

    suspend fun getCurrentMediaList(mediaId: String, context: Context): MutableList<Audio>? {
        return when (mediaId) {
            MEDIA_ID_SONGS -> getSongs(context)
            MEDIA_ID_ALBUMS -> getAlbums(context)
            MEDIA_ID_ARTISTS -> getArtists(context)
            MEDIA_ID_RECENTLY_ADDED -> getRecentlyAdded(context)
            else -> getSongs(context)
        }
    }

    suspend fun getSongs(context: Context): MutableList<Audio>? {
        return AudioDatabase.getInstance(context)?.audioDao()?.getAllAudio()
    }

    suspend fun getAlbums(context: Context): MutableList<Audio>? {
        return AudioDatabase.getInstance(context)?.audioDao()?.getAllAlbums()
    }

    suspend fun getArtists(context: Context): MutableList<Audio>? {
        return AudioDatabase.getInstance(context)?.audioDao()?.getAllArtists()
    }

    suspend fun getRecentlyAdded(context: Context): MutableList<Audio>? {
        return AudioDatabase.getInstance(context)?.audioDao()?.getRecentAudio()
    }

}