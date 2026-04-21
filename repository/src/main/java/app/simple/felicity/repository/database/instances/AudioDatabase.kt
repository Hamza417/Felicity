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
 * Room database that holds the entire audio library, playback state, song statistics,
 * and playlists.
 *
 * Version 13 is the current baseline. Older databases below version 11 will be wiped and
 * rebuilt from scratch rather than running a long migration chain nobody needs anymore.
 *
 * Version changes:
 *   11 → 12: Renamed the {@code path} column to {@code uri} in the {@code audio} table.
 *   12 → 13: Added a new nullable {@code path} column to store the real filesystem path
 *   resolved from MediaStore. This gives the folder browser proper "/" segments to split
 *   on instead of the opaque SAF content URI.
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
        version = 13,
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
                // Rename the column — fast and safe on API 29+ which is our minimum SDK.
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
                .addMigrations(MIGRATION_11_12, MIGRATION_12_13)
                // Anything older than version 11 gets a clean slate — those migration steps
                // were removed because maintaining a decade-long chain for a pre-alpha app
                // costs more than the handful of users who might still have those old versions.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
