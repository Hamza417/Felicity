package app.simple.felicity.repository.database.instances

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.simple.felicity.repository.database.dao.AudioDao
import app.simple.felicity.repository.database.dao.PlaybackStateDao
import app.simple.felicity.repository.database.dao.SongStatDao
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.AudioStat
import app.simple.felicity.repository.models.PlaybackState

/**
 * Room database holding the core audio library, playback state, and song statistics.
 *
 * <p>Version history:</p>
 * <ul>
 *   <li>3 → 4: Added {@code is_favorite} and {@code always_skip} columns to {@code audio}.</li>
 *   <li>4 → 5: Renamed the old {@code id} column to {@code hash} and introduced a proper
 *       auto-increment {@code id} primary key.</li>
 *   <li>5 → 6: Added a unique index on {@code audio.hash} and created the {@code song_stats}
 *       table linked to {@code audio.hash} via a non-cascade foreign key.</li>
 * </ul>
 *
 * @author Hamza417
 */
@Database(
        entities = [
            Audio::class,
            PlaybackState::class,
            AudioStat::class
        ],
        version = 6,
        exportSchema = true
)
abstract class AudioDatabase : RoomDatabase() {

    abstract fun audioDao(): AudioDao?
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun songStatDao(): SongStatDao

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

        /**
         * Migrates the database from version 5 to 6.
         *
         * <p>Adds a unique index on {@code audio.hash} to satisfy the foreign-key constraint
         * required by the new {@code song_stats} table. Then creates the {@code song_stats}
         * table with {@code audioHash} referencing {@code audio.hash} (no cascade delete so
         * play history is preserved even after a library rescan removes tracks).</p>
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_audio_hash` ON `audio` (`hash`)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `song_stats` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `audioHash` INTEGER NOT NULL,
                        `lastPlayed` INTEGER NOT NULL DEFAULT 0,
                        `playCount` INTEGER NOT NULL DEFAULT 0,
                        `skipCount` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`audioHash`) REFERENCES `audio`(`hash`) ON UPDATE CASCADE ON DELETE NO ACTION
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_stats_audioHash` ON `song_stats` (`audioHash`)")
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
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
        }
    }
}