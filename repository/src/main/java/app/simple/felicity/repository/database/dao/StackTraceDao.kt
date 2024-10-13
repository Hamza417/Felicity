package app.simple.felicity.repository.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.simple.felicity.repository.models.normal.StackTrace

@Dao
interface StackTraceDao {
    @Query("SELECT * FROM stacktrace ORDER BY timestamp DESC")
    suspend fun getStackTraces(): MutableList<StackTrace>

    /**
     * Delete a [StackTrace] item
     * from the table
     */
    @Delete
    suspend fun deleteStackTrace(stackTrace: StackTrace)

    /**
     * Insert [StackTrace] item
     * into the table
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTrace(stackTrace: StackTrace)

    /**
     * Delete the entire table
     */
    @Query("DELETE FROM stacktrace")
    fun nukeTable()
}
