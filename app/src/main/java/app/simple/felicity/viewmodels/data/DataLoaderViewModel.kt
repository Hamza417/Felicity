package app.simple.felicity.viewmodels.data

import android.app.Application
import android.os.Environment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.database.instances.AudioDatabase
import app.simple.felicity.extensions.livedata.SingleEventLiveData
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.loaders.LoaderUtils.isAudioFile
import app.simple.felicity.loaders.MediaMetadataLoader
import app.simple.felicity.models.normal.Audio
import app.simple.felicity.utils.SDCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class DataLoaderViewModel(application: Application) : WrappedViewModel(application) {

    private val dataList: ArrayList<File> = ArrayList()

    private val data: MutableLiveData<File> by lazy {
        MutableLiveData<File>().also {
            loadData()
        }
    }

    private val loaded: SingleEventLiveData<Boolean> by lazy {
        SingleEventLiveData()
    }

    fun getData(): LiveData<File> {
        return data
    }

    fun getLoaded(): SingleEventLiveData<Boolean> {
        return loaded
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val paths = arrayListOf(Environment.getExternalStorageDirectory(), SDCard.findSdCardPath(context))
            val audioDatabase = AudioDatabase.getInstance(context)

            paths.forEach { file ->
                file?.walkTopDown()?.forEach {
                    if (it.isFile) {
                        if (it.isAudioFile()) {
                            val audio = Audio()
                            val retriever = MediaMetadataLoader(it)

                            retriever.setAudioMetadata(audio)
                            audioDatabase?.audioDao()?.insert(audio)
                            data.postValue(it)
                            dataList.add(it)
                        }
                    }
                }
            }

            loaded.postValue(true)
        }
    }

    companion object {
        const val TAG = "DataLoaderViewModel"
    }
}
