package app.simple.felicity.repository.loader

import android.content.Context
import androidx.annotation.WorkerThread
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.shared.storage.RemovableStorageDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.io.File
import javax.inject.Inject
import kotlin.math.min

@WorkerThread
class AudioDatabaseLoader @Inject constructor(private val context: Context) {

    companion object {
        private const val MIN_SEMAPHORE_PERMITS = 4

        data class IndexedFile(val lastModified: Long, val size: Long)

        private val indexedMap: HashMap<String, IndexedFile> = hashMapOf()
    }

    private val loaderJobs: MutableSet<Job> = mutableSetOf()
    private val semaphore = Semaphore(min(MIN_SEMAPHORE_PERMITS, Runtime.getRuntime().availableProcessors()))

    private val audioDatabase: AudioDatabase by lazy {
        AudioDatabase.getInstance(context)!!
    }

    fun processAudioFiles() {
        val job = CoroutineScope(Dispatchers.IO).launch {
            val storages = RemovableStorageDetector.getAllStorageVolumes(context)
            val dao = audioDatabase.audioDao()
        }

        loaderJobs.add(job)
    }

    private fun shouldProcess(file: File, index: Map<String, IndexedFile>): Boolean {
        val existing = index[file.absolutePath] ?: return true   // new file

        if (existing.size != file.length()) return true          // changed
        if (existing.lastModified != file.lastModified()) return true

        return false                                             // unchanged → skip
    }
}