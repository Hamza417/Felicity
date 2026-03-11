package app.simple.felicity.repository.database.instances

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.simple.felicity.repository.database.dao.SongStatDao
import app.simple.felicity.repository.models.SongStat

@Database(entities = [SongStat::class], version = 2, exportSchema = false)
abstract class SongStatDatabase : RoomDatabase() {

    abstract fun songStatDao(): SongStatDao

    companion object {
        @Volatile
        private var INSTANCE: SongStatDatabase? = null

        private const val TABLE_NAME = "song_stats_db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE song_stats ADD COLUMN alwaysSkip INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): SongStatDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                        context.applicationContext,
                        SongStatDatabase::class.java,
                        TABLE_NAME
                ).addMigrations(MIGRATION_1_2)
                    .build().also {
                        INSTANCE = it
                    }
            }
        }
    }
}