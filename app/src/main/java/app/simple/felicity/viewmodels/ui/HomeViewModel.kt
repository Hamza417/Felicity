package app.simple.felicity.viewmodels.ui

import android.annotation.SuppressLint
import android.app.Application
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.models.Audio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : WrappedViewModel(application) {

    private val selection = MediaStore.Audio.Media.IS_MUSIC + " != 0"

    private val data: MutableLiveData<ArrayList<ArrayList<Audio>>> by lazy {
        MutableLiveData<ArrayList<ArrayList<Audio>>>().also {
            loadData()
        }
    }

    fun getHomeData(): MutableLiveData<ArrayList<ArrayList<Audio>>> {
        return data
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            data.postValue(arrayListOf(getSongsData(), getArtistsData(), getAlbumsData()))
        }
    }

    // Load Songs
    @SuppressLint("Range", "InlinedApi")
    private fun getSongsData(): ArrayList<Audio> {
        val allAudioModel = ArrayList<Audio>()

        val cursor = context.contentResolver.query(
                externalContentUri,
                audioProjection,
                selection,
                null,
                "LOWER (" + MediaStore.Audio.Media.TITLE + ") ASC")

        if (cursor != null && cursor.moveToFirst()) {
            do {
                val audioModel = Audio()
                val albumId = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID))

                audioModel.name = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME))
                audioModel.title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE))
                audioModel.id = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media._ID))
                audioModel.fileUri = Uri.withAppendedPath(externalContentUri, audioModel.id.toString()).toString()
                audioModel.path = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA))
                audioModel.size = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.SIZE))
                audioModel.album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM))
                audioModel.artists = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST))
                audioModel.duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                audioModel.dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED))
                audioModel.dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED))
                audioModel.dateTaken = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_TAKEN))
                audioModel.artUri = Uri.withAppendedPath(Uri.parse("content://media/external/audio/albumart"), albumId.toString()).toString()
                audioModel.track = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.TRACK))
                audioModel.mimeType = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE))
                audioModel.year = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.YEAR))
                audioModel.bitrate = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE))

                allAudioModel.add(audioModel)
            } while (cursor.moveToNext())

            cursor.close()
        }

        return allAudioModel
    }

    // Load Artists
    @SuppressLint("Range", "InlinedApi")
    private fun getArtistsData(): ArrayList<Audio> {
        val artists = ArrayList<Audio>()

        val cursor = context.contentResolver.query(
                externalContentUri,
                audioProjection,
                // Select unique artists only
                selection,
                null,
                "LOWER (" + MediaStore.Audio.Media.TITLE + ") ASC")

        if (cursor != null && cursor.moveToFirst()) {
            do {
                val audioModel = Audio()
                val albumId = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID))

                audioModel.name = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME))
                audioModel.title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE))
                audioModel.id = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media._ID))
                audioModel.fileUri = Uri.withAppendedPath(externalContentUri, audioModel.id.toString()).toString()
                audioModel.path = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA))
                audioModel.size = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.SIZE))
                audioModel.album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM))
                audioModel.artists = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST))
                audioModel.duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                audioModel.dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED))
                audioModel.dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED))
                audioModel.dateTaken = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_TAKEN))
                audioModel.artUri = Uri.withAppendedPath(Uri.parse("content://media/external/audio/albumart"), albumId.toString()).toString()
                audioModel.track = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.TRACK))
                audioModel.mimeType = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE))
                audioModel.year = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.YEAR))
                audioModel.bitrate = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE))

                artists.add(audioModel)
            } while (cursor.moveToNext())

            cursor.close()
        }

        // Select unique artists only
        val returnArtists = ArrayList<Audio>()
        for (artist in artists) {
            if (!returnArtists.contains(artist)) {
                returnArtists.add(artist)
            }
        }

        // Randomize the list
        returnArtists.shuffle()

        return returnArtists
    }

    // Load Albums
    @SuppressLint("Range", "InlinedApi")
    private fun getAlbumsData(): ArrayList<Audio> {
        val albums = ArrayList<Audio>()

        val cursor = context.contentResolver.query(
                externalContentUri,
                audioProjection,
                // Select unique albums only
                selection,
                null,
                "LOWER (" + MediaStore.Audio.Media.TITLE + ") ASC")

        if (cursor != null && cursor.moveToFirst()) {
            do {
                val audioModel = Audio()
                val albumId = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID))

                audioModel.name = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME))
                audioModel.title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE))
                audioModel.id = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media._ID))
                audioModel.fileUri = Uri.withAppendedPath(externalContentUri, audioModel.id.toString()).toString()
                audioModel.path = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA))
                audioModel.size = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.SIZE))
                audioModel.album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM))
                audioModel.artists = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST))
                audioModel.duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                audioModel.dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED))
                audioModel.dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED))
                audioModel.dateTaken = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_TAKEN))
                audioModel.artUri = Uri.withAppendedPath(Uri.parse("content://media/external/audio/albumart"), albumId.toString()).toString()
                audioModel.track = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.TRACK))
                audioModel.mimeType = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE))
                audioModel.year = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.YEAR))
                audioModel.bitrate = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE))

                albums.add(audioModel)
            } while (cursor.moveToNext())

            cursor.close()
        }

        // Select unique albums only
        val returnAlbums = ArrayList<Audio>()
        for (album in albums) {
            if (!returnAlbums.contains(album)) {
                returnAlbums.add(album)
            }
        }

        // Randomize albums
        returnAlbums.shuffle()

        return returnAlbums
    }
}