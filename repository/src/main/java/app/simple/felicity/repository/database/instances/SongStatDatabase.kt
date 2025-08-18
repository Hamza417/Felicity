package app.simple.felicity.repository.database.instances

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.simple.felicity.repository.database.dao.SongStatDao
import app.simple.felicity.repository.models.SongStat

@Database(entities = [SongStat::class], version = 1, exportSchema = false)
abstract class SongStatDatabase : RoomDatabase() {

    abstract fun songStatDao(): SongStatDao

    companion object {
        @Volatile
        private var INSTANCE: SongStatDatabase? = null

        private const val TABLE_NAME = "song_stats_db"

        fun getInstance(context: Context): SongStatDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                        context.applicationContext,
                        SongStatDatabase::class.java,
                        TABLE_NAME
                ).build().also {
                    INSTANCE = it
                }
            }
        }
    }
}