package app.simple.felicity.repository.database.instances

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.simple.felicity.repository.database.dao.AlbumInfoCacheDao
import app.simple.felicity.repository.database.dao.ArtistInfoCacheDao
import app.simple.felicity.repository.database.dao.AudioDao
import app.simple.felicity.repository.database.dao.BookmarkDao
import app.simple.felicity.repository.database.dao.PlaybackQueueDao
import app.simple.felicity.repository.database.dao.PlaybackStateDao
import app.simple.felicity.repository.database.dao.PlaylistDao
import app.simple.felicity.repository.database.dao.SavedQueueDao
import app.simple.felicity.repository.database.dao.SongStatDao
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.AudioBookmark
import app.simple.felicity.repository.models.AudioStat
import app.simple.felicity.repository.models.MusicBrainzAlbumInfo
import app.simple.felicity.repository.models.MusicBrainzArtistInfo
import app.simple.felicity.repository.models.PlaybackQueueEntry
import app.simple.felicity.repository.models.PlaybackState
import app.simple.felicity.repository.models.Playlist
import app.simple.felicity.repository.models.PlaylistSongCrossRef
import app.simple.felicity.repository.models.SavedQueueEntry

/**
 * Room database that holds the entire audio library, playback state, song statistics,
 * and playlists.
 *
 * Version changes:
 *   11 → 12: Renamed the {@code path} column to {@code uri} in the {@code audio} table.
 *   12 → 13: Added a new nullable {@code path} column to store the real filesystem path
 *   resolved from MediaStore. This gives the folder browser proper "/" segments to split
 *   on instead of the opaque SAF content URI.
 *   13 → 14: Added {@code replayCount} column to {@code song_stats} to track how many
 *   times the user navigated backward to replay a song.
 *   14 → 15: Added four nullable ReplayGain columns to the {@code audio} table:
 *   {@code replay_gain_track_gain}, {@code replay_gain_track_peak},
 *   {@code replay_gain_album_gain}, and {@code replay_gain_album_peak}.
 *   These are populated from the embedded REPLAYGAIN_* tags during library scan.
 *   Existing rows default to NULL and are updated on the next rescan.
 *   17 → 18: Created the {@code audio_bookmarks} table for per-track playback bookmarks.
 *   Bookmarks are intentionally not foreign-keyed to {@code audio} so they survive
 *   library deletions and are restored automatically when a track is re-added.
 *   18 → 19: Added {@code active_queue_id} column to {@code playback_state} so the
 *   app remembers which of the five queues was active on last launch. Created the
 *   {@code saved_queue} table to persist all five queues independently — each row
 *   holds a single queue slot identified by {@code queue_id} (0–4), {@code queue_pos},
 *   and the {@code audio_hash} of the track at that position. The active queue is
 *   still mirrored in {@code playback_queue} for backward compatibility with every
 *   other part of the app that reads the queue from there.
 *   19 → 20: Added {@code last_position} and {@code last_seek} columns to
 *   {@code saved_queue} so that switching between queues restores the exact song
 *   index and playback position the user left off at, instead of resetting to the
 *   first song every time.
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
            PlaylistSongCrossRef::class,
            MusicBrainzArtistInfo::class,
            MusicBrainzAlbumInfo::class,
            AudioBookmark::class,
            SavedQueueEntry::class
        ],
        version = 20,
        exportSchema = true
)
abstract class AudioDatabase : RoomDatabase() {

    abstract fun audioDao(): AudioDao?
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun playbackQueueDao(): PlaybackQueueDao
    abstract fun savedQueueDao(): SavedQueueDao
    abstract fun songStatDao(): SongStatDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun artistInfoCacheDao(): ArtistInfoCacheDao
    abstract fun albumInfoCacheDao(): AlbumInfoCacheDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        private const val DB_NAME = "audio.db"

        @Volatile
        private var instance: AudioDatabase? = null

        /**
         * Renames the {@code path} column to {@code uri} in the {@code audio} table.
         *
         * SQLite 3.25+ (available since Android 9, API 28) supports ALTER TABLE ... RENAME COLUMN
         * directly, so we can do this without the usual create-copy-drop dance. The unique index
         * that was on the old {@code path} column needs to be recreated under the new column name
         * because SQLite ties index definitions to column names.
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `audio` RENAME COLUMN `path` TO `uri`")

                // Drop the old index that still references the old column name and recreate it.
                db.execSQL("DROP INDEX IF EXISTS `index_audio_path`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_audio_uri` ON `audio` (`uri`)")
            }
        }

        /**
         * Adds the new nullable [path] column that holds the real filesystem path resolved
         * from MediaStore (e.g. "/storage/emulated/0/Music/song.mp3"). Existing rows get
         * NULL here and will be filled in on the next library scan — no data is lost.
         */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `audio` ADD COLUMN `path` TEXT")
            }
        }

        /**
         * Adds the new {@code replayCount} column to {@code song_stats}. Existing rows default
         * to 0, which is perfectly accurate — we have no historical backward-navigation data.
         */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `song_stats` ADD COLUMN `replayCount` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Adds four nullable ReplayGain columns to the [audio] table. All existing rows
         * will have NULL for these columns and will be populated the next time the library
         * is rescanned and the JNI layer reads the REPLAYGAIN_* tags from each file.
         */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `audio` ADD COLUMN `replay_gain_track_gain` TEXT")
                db.execSQL("ALTER TABLE `audio` ADD COLUMN `replay_gain_track_peak` TEXT")
                db.execSQL("ALTER TABLE `audio` ADD COLUMN `replay_gain_album_gain` TEXT")
                db.execSQL("ALTER TABLE `audio` ADD COLUMN `replay_gain_album_peak` TEXT")
            }
        }

        /**
         * Creates the artist_info_cache table that stores MusicBrainz artist profiles
         * locally so repeated page opens don't trigger unnecessary network calls.
         */
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `artist_info_cache` (
                        `artist_name` TEXT NOT NULL PRIMARY KEY,
                        `mbid` TEXT,
                        `disambiguation` TEXT,
                        `type` TEXT,
                        `country` TEXT,
                        `begin_year` TEXT,
                        `end_year` TEXT,
                        `ended` INTEGER NOT NULL DEFAULT 0,
                        `tags` TEXT NOT NULL DEFAULT '',
                        `bio` TEXT,
                        `wikipedia_url` TEXT,
                        `fetched_at` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        /**
         * Creates the album_info_cache table that stores MusicBrainz release profiles
         * locally, keyed by a composite of album name and artist name.
         */
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `album_info_cache` (
                        `album_key` TEXT NOT NULL PRIMARY KEY,
                        `mbid` TEXT,
                        `disambiguation` TEXT,
                        `release_date` TEXT,
                        `country` TEXT,
                        `status` TEXT,
                        `tags` TEXT NOT NULL DEFAULT '',
                        `labels` TEXT NOT NULL DEFAULT '',
                        `bio` TEXT,
                        `wikipedia_url` TEXT,
                        `fetched_at` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        /**
         * Creates the audio_bookmarks table. Each row ties a playback position (in
         * milliseconds) to an audio track via its content hash. No foreign key is used so
         * that bookmarks outlive library deletions and are automatically restored when the
         * same file is re-scanned.
         */
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `audio_bookmarks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `audioHash` INTEGER NOT NULL,
                        `timestampMs` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_bookmarks_audioHash` ON `audio_bookmarks` (`audioHash`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_audio_bookmarks_audioHash_timestampMs` ON `audio_bookmarks` (`audioHash`, `timestampMs`)")
            }
        }

        /**
         * Adds multi-queue support so the user can maintain up to five independent
         * playback queues and switch between them instantly.
         *
         * <p>Two changes are applied:</p>
         * <ol>
         *   <li>A new {@code active_queue_id} column is added to {@code playback_state}
         *       (defaults to 0) so the app remembers which queue was active on the
         *       last launch.</li>
         *   <li>The {@code saved_queue} table is created to persist all five queues.
         *       Each row holds one queue slot: the queue it belongs to
         *       ({@code queue_id}), its position in that queue ({@code queue_pos}),
         *       and the audio track hash at that position ({@code audio_hash}).
         *       The composite primary key on ({@code queue_id}, {@code queue_pos})
         *       ensures each queue slot is unique.</li>
         * </ol>
         *
         * <p>Existing users start with queue 0 as the default — the current
         * {@code playback_queue} contents are treated as queue 0. When the user
         * first switches to a different queue, the current queue 0 state is
         * automatically archived into {@code saved_queue} before the switch.</p>
         */
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `playback_state` ADD COLUMN `active_queue_id` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `saved_queue` (
                        `queue_id` INTEGER NOT NULL,
                        `queue_pos` INTEGER NOT NULL,
                        `audio_hash` INTEGER NOT NULL,
                        PRIMARY KEY(`queue_id`, `queue_pos`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_queue_queue_id` ON `saved_queue` (`queue_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_queue_audio_hash` ON `saved_queue` (`audio_hash`)")
            }
        }

        /**
         * Adds per-queue playback position tracking so that switching between queues
         * restores the exact song and seek offset the user was at when they last left
         * that queue.
         *
         * <p>Two columns are added to {@code saved_queue}:</p>
         * <ol>
         *   <li>{@code last_position} — the song index within the queue that was
         *       playing when the queue was archived (default 0).</li>
         *   <li>{@code last_seek} — the seek offset in milliseconds within that
         *       song (default 0).</li>
         * </ol>
         *
         * <p>Both values are stored on every row belonging to the same queue so
         * the restore position survives even when individual songs are cascade-
         * deleted — the first surviving row still carries the correct values.</p>
         */
        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `saved_queue` ADD COLUMN `last_position` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `saved_queue` ADD COLUMN `last_seek` INTEGER NOT NULL DEFAULT 0")
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

        /**
         * Bumps Android's default [android.database.CursorWindow] size from the factory default
         * of 2 MB up to 20 MB. Room loads an entire query result into a single cursor window, so
         * when a library has thousands of tracks and each row has many text columns (uri, path,
         * title, artist, album, …) the 2 MB ceiling fills up and Android throws an
         * [OutOfMemoryError] deep inside [android.database.CursorWindow.nativeGetString].
         *
         * The window size is a private static field, so we reach it via reflection. If a future
         * Android release renames or removes the field, we silently carry on with the default
         * size rather than crashing during database initialization.
         *
         * Source: https://github.com/andpor/react-native-sqlite-storage/issues/364#issuecomment-526423153
         */
        @SuppressLint("DiscouragedPrivateApi")
        private fun expandCursorWindowSize() {
            try {
                val field = android.database.CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
                field.isAccessible = true
                val expected = 20 * 1024 * 1024
                field.set(null, expected) // 20 MB
                val size = field.getInt(null)
                Log.i("AudioDatabase", "Expanded CursorWindow size to $size bytes, expected: $expected bytes")
            } catch (_: Exception) {
                // Nothing we can do if the field is gone; carry on with the default.
            }
        }

        private fun buildDatabase(context: Context): AudioDatabase {
            expandCursorWindowSize()
            return Room.databaseBuilder(context, AudioDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
