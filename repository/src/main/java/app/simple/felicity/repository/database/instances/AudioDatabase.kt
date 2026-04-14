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
import app.simple.felicity.repository.database.dao.PlaylistDao
import app.simple.felicity.repository.database.dao.SongStatDao
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.AudioStat
import app.simple.felicity.repository.models.PlaybackQueueEntry
import app.simple.felicity.repository.models.PlaybackState
import app.simple.felicity.repository.models.Playlist
import app.simple.felicity.repository.models.PlaylistSongCrossRef

/**
 * Room database holding the core audio library, playback state, song statistics, and playlists.
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
 *   <li>7 → 8: Removed the database-level foreign key from {@code song_stats}. The hard FK
 *       ({@code ON DELETE NO ACTION}) was blocking deletion of {@code audio} rows that had
 *       statistics, which contradicts the design intent that stats survive track removal.
 *       The {@code audioHash} column now acts as a logical (unconstrained) reference to
 *       {@code audio.hash}; stats are re-associated automatically when a removed file is
 *       re-added because the XXHash64 fingerprint is deterministic.</li>
 *   <li>8 → 9: Added the {@code playlists} table and the {@code playlist_song_cross_ref}
 *       junction table. Each cross-ref row carries cascade-delete foreign keys on both
 *       {@code playlist_id} and {@code audio_hash} so that deleting a playlist or removing
 *       a track from the library automatically cleans up all related membership rows.</li>
 *   <li>9 → 10: Added {@code is_m3u_playlist} and {@code m3u_file_path} columns to the
 *       {@code playlists} table so the scanner can automatically create and maintain
 *       playlists from M3U files found on the device.</li>
 *   <li>10 → 11: Replaced the unique constraint on {@code audio.hash} with a unique
 *       constraint on {@code audio.path}. This lets two identical audio files that live in
 *       different folders each have their own library row and show up independently in the
 *       UI, while the pure content hash is still used as a logical link so that song
 *       statistics survive a file being moved or temporarily removed from the device.</li>
 * </ul>
 *
 * @author Hamza417
 */
@Database(
        entities = [
            Audio::class,
            PlaybackState::class,
            PlaybackQueueEntry::class,
            AudioStat::class,
            Playlist::class,
            PlaylistSongCrossRef::class
        ],
        version = 11,
        exportSchema = true
)
abstract class AudioDatabase : RoomDatabase() {

    abstract fun audioDao(): AudioDao?
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun playbackQueueDao(): PlaybackQueueDao
    abstract fun songStatDao(): SongStatDao
    abstract fun playlistDao(): PlaylistDao

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

        /**
         * Migrates the database from version 7 to 8.
         *
         * <p>Recreates the {@code song_stats} table without a database-level foreign key on
         * {@code audioHash}. The previous schema used {@code ON DELETE NO ACTION}, which
         * SQLite interprets as a hard constraint: it raises {@code SQLITE_CONSTRAINT_FOREIGNKEY}
         * whenever an {@code audio} row is deleted while child {@code song_stats} rows still
         * reference it. This blocked the library reconcile pass from cleaning up tracks that no
         * longer exist on disk. All existing statistics are preserved by copying every row into
         * the new table before dropping the old one.</p>
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create the replacement table without any FK declaration.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `song_stats_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `audioHash` INTEGER NOT NULL,
                        `lastPlayed` INTEGER NOT NULL DEFAULT 0,
                        `playCount` INTEGER NOT NULL DEFAULT 0,
                        `skipCount` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                // Preserve all existing statistics.
                db.execSQL("""
                    INSERT INTO `song_stats_new` (`id`, `audioHash`, `lastPlayed`, `playCount`, `skipCount`)
                    SELECT `id`, `audioHash`, `lastPlayed`, `playCount`, `skipCount` FROM `song_stats`
                """.trimIndent())
                db.execSQL("DROP TABLE `song_stats`")
                db.execSQL("ALTER TABLE `song_stats_new` RENAME TO `song_stats`")
                // Restore the lookup index on audioHash.
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

        /**
         * Migrates the database from version 8 to 9.
         *
         * <p>Introduces two new tables to support user-created playlists:</p>
         * <ul>
         *   <li>{@code playlists} — stores playlist metadata (name, timestamps, sort
         *       preferences, artwork path, shuffle flag, and pin flag).</li>
         *   <li>{@code playlist_song_cross_ref} — junction table that maps playlist rows
         *       to audio tracks via {@code audio.hash}. Both foreign keys use
         *       {@code ON DELETE CASCADE} so that deleting a playlist or removing a track
         *       from the library automatically cleans up all membership rows.</li>
         * </ul>
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `playlists` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT,
                        `date_created` INTEGER NOT NULL DEFAULT 0,
                        `date_modified` INTEGER NOT NULL DEFAULT 0,
                        `last_accessed` INTEGER NOT NULL DEFAULT 0,
                        `artwork_path` TEXT,
                        `sort_order` INTEGER NOT NULL DEFAULT -1,
                        `sort_style` INTEGER NOT NULL DEFAULT 0,
                        `is_shuffled` INTEGER NOT NULL DEFAULT 0,
                        `is_pinned` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `playlist_song_cross_ref` (
                        `playlist_id` INTEGER NOT NULL,
                        `audio_hash` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`playlist_id`, `audio_hash`),
                        FOREIGN KEY(`playlist_id`) REFERENCES `playlists`(`id`)
                            ON UPDATE CASCADE ON DELETE CASCADE,
                        FOREIGN KEY(`audio_hash`) REFERENCES `audio`(`hash`)
                            ON UPDATE CASCADE ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_song_cross_ref_playlist_id` ON `playlist_song_cross_ref` (`playlist_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_song_cross_ref_audio_hash` ON `playlist_song_cross_ref` (`audio_hash`)")
            }
        }

        fun getInstance(): AudioDatabase? = instance

        /**
         * Migrates the database from version 9 to 10.
         *
         * <p>Adds two columns to the {@code playlists} table to support automatic playlist
         * generation from M3U files found on the device's storage:</p>
         * <ul>
         *   <li>{@code is_m3u_playlist} — a boolean flag that tells the app "hey, a file
         *       on disk owns this playlist, don't let the user accidentally delete it
         *       thinking it's just a regular one."</li>
         *   <li>{@code m3u_file_path} — the absolute path to the M3U source file, so the
         *       scanner can find and re-read it when something changes.</li>
         * </ul>
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN is_m3u_playlist INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE playlists ADD COLUMN m3u_file_path TEXT")
            }
        }

        /**
         * Migrates the database from version 10 to 11.
         *
         * <p>The unique index on {@code audio.hash} is replaced with a unique index on
         * {@code audio.path}. This change allows two identical audio files living in
         * different folders to each have their own row in the library and appear
         * independently in the UI — no more mysterious flip-flopping between which copy
         * shows up on each scan.</p>
         *
         * <p>The pure XXHash64 content fingerprint in {@code hash} is kept unchanged and
         * still serves as the logical key for song statistics, so play counts and history
         * survive a file being moved or temporarily removed from the device.</p>
         *
         * <p>Before adding the path unique index we clean up any rows that somehow ended up
         * sharing the same path (keeping the one with the highest id, i.e. the most recently
         * scanned version), so the {@code CREATE UNIQUE INDEX} never hits a duplicate-key
         * error on an existing dirty database.</p>
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Clean up any path-duplicate rows before the unique index lands.
                // We keep the row with the highest id (most recently scanned) for each path.
                db.execSQL("""
                    DELETE FROM audio
                    WHERE id NOT IN (
                        SELECT MAX(id) FROM audio GROUP BY path
                    )
                """.trimIndent())

                // Drop the old unique index on hash — the content fingerprint should not
                // enforce row uniqueness on its own anymore.
                db.execSQL("DROP INDEX IF EXISTS `index_audio_hash`")

                // Restore a non-unique index on hash so stats lookups (JOIN by hash) and
                // the reconcile pass stay fast even though uniqueness is no longer required.
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_hash` ON `audio` (`hash`)")

                // The file path is now the true uniqueness key for library rows.
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_audio_path` ON `audio` (`path`)")

                // Recreate playback_queue without the audio-hash foreign key.
                // Room's schema validator checks that the table schema matches the entity
                // annotations exactly — since PlaybackQueueEntry no longer declares a FK,
                // the table must not have one either. FK enforcement was already disabled
                // database-wide, so this has zero runtime impact on queue behavior.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `playback_queue_new` (
                        `queuePos` INTEGER PRIMARY KEY NOT NULL,
                        `audioHash` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO `playback_queue_new` SELECT * FROM `playback_queue`")
                db.execSQL("DROP TABLE `playback_queue`")
                db.execSQL("ALTER TABLE `playback_queue_new` RENAME TO `playback_queue`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_queue_audioHash` ON `playback_queue` (`audioHash`)")

                // Recreate playlist_song_cross_ref keeping only the playlist FK (still valid
                // because playlists.id is a primary key). The audio-hash FK is dropped for the
                // same reason as the one on playback_queue — hash is no longer unique in audio.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `playlist_song_cross_ref_new` (
                        `playlist_id` INTEGER NOT NULL,
                        `audio_hash` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`playlist_id`, `audio_hash`),
                        FOREIGN KEY(`playlist_id`) REFERENCES `playlists`(`id`)
                            ON UPDATE CASCADE ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO `playlist_song_cross_ref_new` SELECT * FROM `playlist_song_cross_ref`")
                db.execSQL("DROP TABLE `playlist_song_cross_ref`")
                db.execSQL("ALTER TABLE `playlist_song_cross_ref_new` RENAME TO `playlist_song_cross_ref`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_song_cross_ref_playlist_id` ON `playlist_song_cross_ref` (`playlist_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_song_cross_ref_audio_hash` ON `playlist_song_cross_ref` (`audio_hash`)")
            }
        }

        private fun buildDatabase(context: Context): AudioDatabase {
            return Room.databaseBuilder(context, AudioDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .build()
        }
    }
}

