package app.simple.felicity.repository.database.instances

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.simple.felicity.repository.database.dao.ArtistInfoCacheDao
import app.simple.felicity.repository.database.dao.AudioDao
import app.simple.felicity.repository.database.dao.PlaybackQueueDao
import app.simple.felicity.repository.database.dao.PlaybackStateDao
import app.simple.felicity.repository.database.dao.PlaylistDao
import app.simple.felicity.repository.database.dao.SongStatDao
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.AudioStat
import app.simple.felicity.repository.models.MusicBrainzArtistInfo
import app.simple.felicity.repository.models.PlaybackQueueEntry
import app.simple.felicity.repository.models.PlaybackState
import app.simple.felicity.repository.models.Playlist
import app.simple.felicity.repository.models.PlaylistSongCrossRef

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
            MusicBrainzArtistInfo::class
        ],
        version = 16,
        exportSchema = true
)
abstract class AudioDatabase : RoomDatabase() {

    abstract fun audioDao(): AudioDao?
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun playbackQueueDao(): PlaybackQueueDao
    abstract fun songStatDao(): SongStatDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun artistInfoCacheDao(): ArtistInfoCacheDao

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
                .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
