package app.simple.felicity.repository.repositories

import app.simple.felicity.repository.database.dao.AudioDao
import app.simple.felicity.repository.models.normal.Audio
import kotlinx.coroutines.flow.Flow

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
}