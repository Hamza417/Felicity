package app.simple.felicity.repository.database.instances

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.simple.felicity.repository.database.coverters.UriTypeConverter
import app.simple.felicity.repository.database.dao.SongDao
import app.simple.felicity.repository.models.Song

@Database(entities = [Song::class], version = 1, exportSchema = false)
@TypeConverters(UriTypeConverter::class)
abstract class LastSongDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    companion object {
        @Volatile
        private var INSTANCE: LastSongDatabase? = null

        private const val TABLE_NAME = "last_songs"

        fun getInstance(context: Context): LastSongDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                        context.applicationContext, LastSongDatabase::class.java, TABLE_NAME)
                    .build().also {
                        INSTANCE = it
                    }
            }
        }
    }
}
