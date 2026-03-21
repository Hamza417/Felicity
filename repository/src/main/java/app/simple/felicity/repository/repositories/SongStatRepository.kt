package app.simple.felicity.repository.repositories

import android.content.Context
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.AudioStat
import app.simple.felicity.repository.models.AudioWithStat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing per-song playback statistics.
 *
 * <p>All stat mutations are safe to call from any coroutine dispatcher since the underlying
 * Room calls use suspend functions or blocking DAO operations on IO threads. Stats are stored
 * in the {@code song_stats} table inside {@code AudioDatabase} so the foreign-key relationship
 * with the {@code audio} table is enforced at the database level.</p>
 *
 * @author Hamza417
 */
@Singleton
class SongStatRepository @Inject constructor(
        @param:ApplicationContext private val context: Context
) {

    private val database: AudioDatabase by lazy {
        AudioDatabase.getInstance(context)
    }

    /**
     * Records a play event for the song identified by [audioHash].
     *
     * <p>If no stat row exists for this hash a new one is inserted with a play count of 1.
     * Otherwise the existing row's play count is incremented by 1 and its last-played
     * timestamp is updated to the current wall-clock time.</p>
     *
     * @param audioHash The XXHash64 fingerprint of the audio file (matches {@code audio.hash}).
     */
    suspend fun recordPlay(audioHash: Long) {
        val dao = database.songStatDao()
        val existing = dao.getStatByHash(audioHash)
        if (existing == null) {
            dao.insertStat(
                    AudioStat(
                            audioHash = audioHash,
                            playCount = 1,
                            lastPlayed = System.currentTimeMillis()
                    )
            )
        } else {
            dao.updateStat(
                    existing.copy(
                            playCount = existing.playCount + 1,
                            lastPlayed = System.currentTimeMillis()
                    )
            )
        }
    }

    /**
     * Records a skip event for the song identified by [audioHash].
     *
     * <p>A skip is counted whenever the user navigates away from a song before it has played
     * past the early-skip threshold (typically 30% of its duration). If no stat row exists,
     * a new one is inserted with a skip count of 1.</p>
     *
     * @param audioHash The XXHash64 fingerprint of the audio file.
     */
    suspend fun recordSkip(audioHash: Long) {
        val dao = database.songStatDao()
        val existing = dao.getStatByHash(audioHash)
        if (existing == null) {
            dao.insertStat(AudioStat(audioHash = audioHash, skipCount = 1))
        } else {
            dao.updateStat(existing.copy(skipCount = existing.skipCount + 1))
        }
    }

    /**
     * Returns a reactive [Flow] of available songs ordered by their last-played timestamp,
     * most recently played first. Only songs present in the audio table are included.
     *
     * @return Flow emitting up to 50 [Audio] objects re-emitted whenever stats change.
     */
    fun getRecentlyPlayed(): Flow<List<Audio>> {
        return database.songStatDao().getRecentlyPlayedAudio()
    }

    /**
     * Returns a reactive [Flow] of available songs with stat data ordered by last-played
     * timestamp, most recently played first.
     *
     * @return Flow emitting up to 50 [AudioWithStat] objects re-emitted whenever stats change.
     */
    fun getRecentlyPlayedWithStat(): Flow<List<AudioWithStat>> {
        return database.songStatDao().getRecentlyPlayedWithStat()
    }

    /**
     * Returns a reactive [Flow] of available songs ordered by total play count, highest first.
     * Only songs present in the audio table are included.
     *
     * @return Flow emitting up to 50 [Audio] objects re-emitted whenever stats change.
     */
    fun getMostPlayed(): Flow<List<Audio>> {
        return database.songStatDao().getMostPlayedAudio()
    }

    /**
     * Returns a reactive [Flow] of available songs with stat data ordered by total play count,
     * highest first.
     *
     * @return Flow emitting up to 50 [AudioWithStat] objects re-emitted whenever stats change.
     */
    fun getMostPlayedWithStat(): Flow<List<AudioWithStat>> {
        return database.songStatDao().getMostPlayedWithStat()
    }
}

