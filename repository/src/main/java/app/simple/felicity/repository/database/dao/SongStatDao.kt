package app.simple.felicity.repository.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.simple.felicity.repository.models.SongStat
import kotlinx.coroutines.flow.Flow

@Dao
interface SongStatDao {

    @Query("SELECT * FROM song_stats")
    fun getAllSongStats(): Flow<List<SongStat>>

    @Query("SELECT * FROM song_stats WHERE stableId = :stableId")
    fun getSongStatByStableIdFlow(stableId: Long): Flow<SongStat?>

    @Query("SELECT * FROM song_stats WHERE stableId = :stableId")
    suspend fun getSongStatByStableId(stableId: Long): SongStat?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongStat(songStat: SongStat)

    @Update
    suspend fun updateSongStat(songStat: SongStat)

    @Delete
    suspend fun deleteSongStat(songStat: SongStat)
}