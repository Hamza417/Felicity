package app.simple.felicity.repository.repositories

import app.simple.felicity.repository.database.dao.AudioDao
import app.simple.felicity.repository.models.normal.Audio
import kotlinx.coroutines.flow.Flow
import java.io.File

class AudioRepository(private val audioDao: AudioDao) {

    suspend fun insertAudio(audio: Audio) {
        audioDao.insert(audio)
    }

    suspend fun deleteAudio(audio: Audio) {
        audioDao.delete(audio)
    }

    fun getAllAudio(): Flow<MutableList<Audio>> {
        return audioDao.getAllAudio()
    }

    fun getAllArtists(): Flow<MutableList<Audio>> {
        return audioDao.getAllArtists()
    }

    fun getAllAlbums(): Flow<MutableList<Audio>> {
        return audioDao.getAllAlbums()
    }

    fun getRecentAudio(): Flow<MutableList<Audio>> {
        return audioDao.getRecentAudio()
    }

    fun getIDByPath(path: String): Long {
        return audioDao.getAudioIdByPath(path)
    }

    fun getHashMapForIDByPath(): HashMap<String, Long> {
        val list = audioDao.getIdAndPath()
        val map = HashMap<String, Long>()

        for (idPath in list) {
            map[idPath.path] = idPath.id
        }

        return map
    }

    fun isFileScannedAndExists(path: String): Boolean {
        return isFileScannedAndExists(File(path))
    }

    fun isFileScannedAndExists(file: File): Boolean {
        return file.exists() && getIDByPath(file.absolutePath) != 0L
    }
}