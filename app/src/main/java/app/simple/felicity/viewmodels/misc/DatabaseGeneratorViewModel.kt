package app.simple.felicity.viewmodels.misc

import android.annotation.SuppressLint
import android.app.Application
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.database.instances.AudioDatabase
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.models.Audio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DatabaseGeneratorViewModel(application: Application) : WrappedViewModel(application) {

    private var cursor: Cursor? = null
    private var globalList = arrayListOf<Audio>()
    private val selection = MediaStore.Audio.Media.IS_MUSIC + " != 0"

    private val generated: MutableLiveData<ArrayList<Audio>> by lazy {
        MutableLiveData<ArrayList<Audio>>().also {
            generate()
        }
    }

    fun getGeneratedData(): MutableLiveData<ArrayList<Audio>> {
        return generated
    }

    private fun generate() {
        viewModelScope.launch(Dispatchers.IO) {
            globalList = loadSongs()
            generated.postValue(globalList)
            AudioDatabase.getInstance(context)?.close()
        }
    }

    @SuppressLint("Range", "InlinedApi")
    private fun loadSongs(): ArrayList<Audio> {
        val allAudioModel = ArrayList<Audio>()
        val audioDao = AudioDatabase.getInstance(context)?.audioDao()!!

        cursor = context.contentResolver.query(
                externalContentUri,
                audioProjection,
                selection,
                null,
                "LOWER (" + MediaStore.Audio.Media.TITLE + ") ASC")

        if (cursor != null && cursor!!.moveToFirst()) {
            do {
                val audioModel = Audio()
                val albumId = cursor!!.getLong(cursor!!.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID))

                audioModel.name = cursor!!.getString(cursor!!.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME))
                audioModel.title = cursor!!.getString(cursor!!.getColumnIndex(MediaStore.Audio.Media.TITLE))
                audioModel.id = cursor!!.getLong(cursor!!.getColumnIndex(MediaStore.Audio.Media._ID))
                audioModel.fileUri = Uri.withAppendedPath(externalContentUri, audioModel.id.toString()).toString()
                audioModel.path = cursor!!.getString(cursor!!.getColumnIndex(MediaStore.Audio.Media.DATA))
                audioModel.size = cursor!!.getInt(cursor!!.getColumnIndex(MediaStore.Audio.Media.SIZE))
                audioModel.album = cursor!!.getString(cursor!!.getColumnIndex(MediaStore.Audio.Media.ALBUM))
                audioModel.artist = cursor!!.getString(cursor!!.getColumnIndex(MediaStore.Audio.Media.ARTIST))
                audioModel.duration = cursor!!.getLong(cursor!!.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                audioModel.dateAdded = cursor!!.getLong(cursor!!.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED))
                audioModel.dateModified = cursor!!.getLong(cursor!!.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED))
                audioModel.dateTaken = cursor!!.getLong(cursor!!.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_TAKEN))
                audioModel.artUri = Uri.withAppendedPath(Uri.parse("content://media/external/audio/albumart"), albumId.toString()).toString()
                audioModel.track = cursor!!.getInt(cursor!!.getColumnIndex(MediaStore.Audio.Media.TRACK))
                audioModel.mimeType = cursor!!.getString(cursor!!.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE))
                audioModel.year = cursor!!.getInt(cursor!!.getColumnIndex(MediaStore.Audio.Media.YEAR))
                audioModel.bitrate = cursor!!.getInt(cursor!!.getColumnIndex(MediaStore.Audio.Media.BITRATE))

                //for android 10 exclusively
                /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Uri contentUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                    try {
                        AssetFileDescriptor file = audioContext.getContentResolver().openAssetFileDescriptor(contentUri, "r");
                        audioContent.setMusicPathQ(file);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }*/

                allAudioModel.add(audioModel)
                audioDao.insert(audioModel)
            } while (cursor!!.moveToNext())

            cursor!!.close()
        }

        return allAudioModel
    }
}