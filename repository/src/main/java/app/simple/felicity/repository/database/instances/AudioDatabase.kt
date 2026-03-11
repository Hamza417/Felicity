package app.simple.felicity.repository.database.instances

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.simple.felicity.repository.database.dao.AudioDao
import app.simple.felicity.repository.database.dao.PlaybackStateDao
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.PlaybackState

@Database(
        entities = [
            Audio::class,
            PlaybackState::class
        ],
        version = 4,
        exportSchema = true
)
abstract class AudioDatabase : RoomDatabase() {

    abstract fun audioDao(): AudioDao?
    abstract fun playbackStateDao(): PlaybackStateDao

    companion object {
        private const val DB_NAME = "audio.db"

        @Volatile
        private var instance: AudioDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audio ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE audio ADD COLUMN always_skip INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AudioDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext).also {
                    instance = it
                }
            }
        }

        fun getInstance(): AudioDatabase? = instance

        private fun buildDatabase(context: Context): AudioDatabase {
            return Room.databaseBuilder(context, AudioDatabase::class.java, DB_NAME)
                .fallbackToDestructiveMigration()
                .addMigrations(MIGRATION_3_4)
                .build()
        }
    }
}