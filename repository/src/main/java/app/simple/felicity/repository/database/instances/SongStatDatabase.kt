package app.simple.felicity.repository.database.instances

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.simple.felicity.repository.database.dao.SongStatDao
import app.simple.felicity.repository.models.SongStat

@Database(entities = [SongStat::class], version = 3, exportSchema = false)
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

        /** Removes isFavorite and alwaysSkip columns – those fields now live in the audio table. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                        """
                    CREATE TABLE song_stats_new (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        songId INTEGER NOT NULL,
                        stableId TEXT NOT NULL,
                        lastPlayed INTEGER NOT NULL DEFAULT 0,
                        playCount INTEGER NOT NULL DEFAULT 0,
                        skipCount INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                        """
                    INSERT INTO song_stats_new (id, songId, stableId, lastPlayed, playCount, skipCount)
                    SELECT id, songId, stableId, lastPlayed, playCount, skipCount FROM song_stats
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE song_stats")
                db.execSQL("ALTER TABLE song_stats_new RENAME TO song_stats")
                db.execSQL("CREATE INDEX index_song_stats_songId ON song_stats (songId)")
                db.execSQL("CREATE INDEX index_song_stats_stableId ON song_stats (stableId)")
            }
        }

        fun getInstance(context: Context): SongStatDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                        context.applicationContext,
                        SongStatDatabase::class.java,
                        TABLE_NAME
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also {
                        INSTANCE = it
                    }
            }
        }
    }
}