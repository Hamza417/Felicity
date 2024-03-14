package app.simple.felicity.viewmodels.misc

import android.annotation.SuppressLint
import android.app.Application
import android.database.Cursor
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

        cursor = context.contentResolver.query(
                externalContentUri,
                audioProjection,
                selection,
                null,
                "LOWER (" + MediaStore.Audio.Media.TITLE + ") ASC")

        if (cursor != null && cursor!!.moveToFirst()) {
            val audioDatabase = AudioDatabase.getInstance(context)
            do {
                val audioModel = Audio()
                audioModel.setFromCursor(cursor!!)
                allAudioModel.add(audioModel)
                // audioDatabase?.audioDao()?.insert(audioModel)
            } while (cursor!!.moveToNext())

            cursor!!.close()
        }

        return allAudioModel
    }
}
