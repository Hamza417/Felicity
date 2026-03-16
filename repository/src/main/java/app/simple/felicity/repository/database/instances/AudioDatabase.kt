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
        version = 5,
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

        /**
         * Migrates the audio table from version 4 to 5.
         *
         * <p>In version 5 the old {@code id} column (which stored the XXHash64 file fingerprint)
         * is renamed to {@code hash}, and a new auto-increment {@code id} column is introduced
         * as the proper INTEGER PRIMARY KEY so that Room can auto-assign stable row identifiers.</p>
         *
         * <p>SQLite does not support altering primary-key constraints in-place, so the migration
         * recreates the table, copies all rows (mapping old id → hash), drops the old table, and
         * renames the temporary table back to {@code audio}.</p>
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `audio_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `hash` INTEGER NOT NULL,
                        `name` TEXT,
                        `title` TEXT,
                        `artist` TEXT,
                        `path` TEXT,
                        `track` INTEGER NOT NULL,
                        `album` TEXT,
                        `size` INTEGER NOT NULL,
                        `author` TEXT,
                        `album_artist` TEXT,
                        `year` TEXT,
                        `bitrate` INTEGER NOT NULL,
                        `duration` INTEGER NOT NULL,
                        `composer` TEXT,
                        `date` TEXT,
                        `disc_number` TEXT,
                        `genre` TEXT,
                        `date_added` INTEGER NOT NULL,
                        `date_modified` INTEGER NOT NULL,
                        `date_taken` INTEGER NOT NULL,
                        `album_id` INTEGER NOT NULL,
                        `track_number` TEXT,
                        `compilation` TEXT,
                        `mimeType` TEXT,
                        `num_tracks` TEXT,
                        `sampling_rate` INTEGER NOT NULL,
                        `bit_per_sample` INTEGER NOT NULL,
                        `writer` TEXT,
                        `is_available` INTEGER NOT NULL DEFAULT 1,
                        `is_favorite` INTEGER NOT NULL DEFAULT 0,
                        `always_skip` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `audio_new` (
                        hash, name, title, artist, path, track, album, size, author,
                        album_artist, year, bitrate, duration, composer, date, disc_number,
                        genre, date_added, date_modified, date_taken, album_id, track_number,
                        compilation, mimeType, num_tracks, sampling_rate, bit_per_sample,
                        writer, is_available, is_favorite, always_skip
                    )
                    SELECT
                        id, name, title, artist, path, track, album, size, author,
                        album_artist, year, bitrate, duration, composer, date, disc_number,
                        genre, date_added, date_modified, date_taken, album_id, track_number,
                        compilation, mimeType, num_tracks, sampling_rate, bit_per_sample,
                        writer, is_available, is_favorite, always_skip
                    FROM audio
                """.trimIndent())
                db.execSQL("DROP TABLE `audio`")
                db.execSQL("ALTER TABLE `audio_new` RENAME TO `audio`")
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
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                .build()
        }
    }
}