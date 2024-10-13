package app.simple.felicity.viewmodels.data

import android.app.Application
import android.os.Environment
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.livedata.SingleEventLiveData
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.loaders.JAudioMetadataLoader
import app.simple.felicity.loaders.LoaderUtils.isAudioFile
import app.simple.felicity.loaders.MediaMetadataLoader
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.models.normal.Audio
import app.simple.felicity.tools.MovingAverage
import app.simple.felicity.utils.NumberUtils
import app.simple.felicity.utils.SDCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jaudiotagger.audio.exceptions.CannotReadException
import java.io.File
import kotlin.system.measureTimeMillis

class DataLoaderViewModel(application: Application) : WrappedViewModel(application) {

    private val dataList: ArrayList<File> = ArrayList()
    private val averageTime = MovingAverage(100)

    private val data: MutableLiveData<File> by lazy {
        MutableLiveData<File>().also {
            loadData()
        }
    }

    private val timeRemaining: MutableLiveData<Pair<Long, String>> by lazy {
        MutableLiveData<Pair<Long, String>>()
    }

    private val loaded: SingleEventLiveData<Boolean> by lazy {
        SingleEventLiveData()
    }

    fun getData(): LiveData<File> {
        return data
    }

    fun getLoaded(): LiveData<Boolean> {
        return loaded
    }

    fun getTimeRemaining(): LiveData<Pair<Long, String>> {
        return timeRemaining
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val paths = arrayListOf(Environment.getExternalStorageDirectory(), SDCard.findSdCardPath(context))
            val audioDatabase = AudioDatabase.getInstance(context)

            val files = paths.flatMap { it?.walkTopDown()?.filter { file -> file.isFile && file.isAudioFile() }?.toList() ?: listOf() }
            val fileCount = files.size
            var count = 0

            val startTime = System.currentTimeMillis()

            files.parallelStream().forEach { file ->
                count++
                val remaining = fileCount - count
                val processingTime = measureTimeMillis {
                    processFile(file, audioDatabase)
                }

                synchronized(timeRemaining) {
                    synchronized(averageTime) {
                        timeRemaining.postValue(Pair((averageTime.next(processingTime) * remaining).toLong(), "$count/$fileCount"))
                    }
                }
            }

            Log.d(TAG, "loadData: Time taken: ${NumberUtils.getFormattedTime(System.currentTimeMillis() - startTime)} s")

            loaded.postValue(true)
        }
    }

    private fun processFile(file: File, audioDatabase: AudioDatabase?) {
        try {
            val audio = Audio()
            val retriever = JAudioMetadataLoader(file)
            retriever.setAudioMetadata(audio)

            audioDatabase?.audioDao()?.insert(audio)
            dataList.add(file)
            data.postValue(file)
        } catch (e: CannotReadException) {
            e.printStackTrace()
            Log.d(TAG, "loadData: Cannot read file: ${file.absolutePath}")

            val audio = Audio()
            val retriever = MediaMetadataLoader(file)
            retriever.setAudioMetadata(audio)

            audioDatabase?.audioDao()?.insert(audio)
            dataList.add(file)
            data.postValue(file)

            Log.d(TAG, "Successfully read file using MediaMetadataLoader: ${file.absolutePath}")
        }
    }

    companion object {
        const val TAG = "DataLoaderViewModel"
        const val MAX_AVG = 100
    }
}
