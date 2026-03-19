package app.simple.felicity.repository.database.instances

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.simple.felicity.repository.database.dao.AudioDao
import app.simple.felicity.repository.database.dao.PlaybackQueueDao
import app.simple.felicity.repository.database.dao.PlaybackStateDao
import app.simple.felicity.repository.database.dao.SongStatDao
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.AudioStat
import app.simple.felicity.repository.models.PlaybackQueueEntry
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
 *   <li>6 → 7: Replaced the JSON {@code queue} blob in {@code playback_state} with a
 *       dedicated {@code playback_queue} table whose {@code audioHash} carries an
 *       {@code ON DELETE CASCADE} foreign key — stale queue entries are automatically
 *       removed whenever the corresponding audio row is deleted.</li>
 * </ul>
 *
 * @author Hamza417
 */
@Database(
        entities = [
            Audio::class,
            PlaybackState::class,
            PlaybackQueueEntry::class,
            AudioStat::class
        ],
        version = 7,
        exportSchema = true
)
abstract class AudioDatabase : RoomDatabase() {

    abstract fun audioDao(): AudioDao?
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun playbackQueueDao(): PlaybackQueueDao
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

        /**
         * Migrates the database from version 6 to 7.
         *
         * <p>Replaces the JSON queue blob in {@code playback_state} with a proper relational
         * table. The old {@code queue} TEXT column is dropped (recreate-and-copy technique since
         * SQLite does not support DROP COLUMN on older Android versions), the {@code index} and
         * {@code position} columns are renamed to {@code current_index} and {@code position_ms},
         * and a new {@code current_hash} column is added. A companion {@code playback_queue}
         * table is created with an {@code ON DELETE CASCADE} foreign key so that deleting an
         * audio track automatically removes it from any saved queue.</p>
         *
         * <p>The JSON queue data cannot be migrated in pure SQL, so {@code playback_queue} starts
         * empty. On the next cold boot the app falls back to loading the full library as the
         * default queue, which is identical to first-launch behaviour.</p>
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Recreate playback_state without the old queue JSON column.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `playback_state_new` (
                        `id` INTEGER PRIMARY KEY NOT NULL DEFAULT 1,
                        `current_index` INTEGER NOT NULL DEFAULT 0,
                        `position_ms` INTEGER NOT NULL DEFAULT 0,
                        `shuffle` INTEGER NOT NULL DEFAULT 0,
                        `repeatMode` INTEGER NOT NULL DEFAULT 0,
                        `updatedAt` INTEGER NOT NULL DEFAULT 0,
                        `current_hash` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                // Copy scalar fields; discard the JSON queue column.
                db.execSQL("""
                    INSERT OR IGNORE INTO `playback_state_new`
                        (`id`, `current_index`, `position_ms`, `shuffle`, `repeatMode`, `updatedAt`)
                    SELECT `id`, `index`, `position`, `shuffle`, `repeatMode`, `updatedAt`
                    FROM `playback_state`
                """.trimIndent())
                db.execSQL("DROP TABLE `playback_state`")
                db.execSQL("ALTER TABLE `playback_state_new` RENAME TO `playback_state`")

                // Create the per-slot queue table with cascade-delete FK.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `playback_queue` (
                        `queuePos` INTEGER PRIMARY KEY NOT NULL,
                        `audioHash` INTEGER NOT NULL,
                        FOREIGN KEY(`audioHash`) REFERENCES `audio`(`hash`)
                            ON UPDATE CASCADE ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_playback_queue_audioHash` ON `playback_queue` (`audioHash`)"
                )
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
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
        }
    }
}

