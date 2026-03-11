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
    fun getSongStatByStableIdFlow(stableId: String): Flow<SongStat?>

    @Query("SELECT * FROM song_stats WHERE stableId = :stableId")
    suspend fun getSongStatByStableId(stableId: String): SongStat?

    @Query("SELECT * FROM song_stats WHERE isFavorite = 1")
    fun getFavorites(): Flow<List<SongStat>>

    @Query("SELECT songId FROM song_stats WHERE isFavorite = 1")
    fun getFavoriteSongIds(): Flow<List<Long>>

    @Query("UPDATE song_stats SET isFavorite = :isFavorite WHERE stableId = :stableId")
    suspend fun setFavorite(stableId: String, isFavorite: Boolean)

    @Query("UPDATE song_stats SET alwaysSkip = :alwaysSkip WHERE stableId = :stableId")
    suspend fun setAlwaysSkip(stableId: String, alwaysSkip: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongStat(songStat: SongStat)

    @Update
    suspend fun updateSongStat(songStat: SongStat)

    @Delete
    suspend fun deleteSongStat(songStat: SongStat)
}