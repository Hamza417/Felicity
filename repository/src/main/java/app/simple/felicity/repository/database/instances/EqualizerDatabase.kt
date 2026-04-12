package app.simple.felicity.repository.database.instances

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.simple.felicity.repository.database.dao.EqualizerPresetDao
import app.simple.felicity.repository.models.EqualizerPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Standalone Room database dedicated entirely to equalizer presets.
 *
 * Keeping EQ presets in their own database means the audio library database stays lean
 * and migrations there never accidentally break the user's saved EQ settings — the two
 * concerns are completely independent, which is exactly how it should be.
 *
 * On the very first launch, built-in presets are inserted inside the database's
 * [RoomDatabase.Callback.onCreate] hook to populate a handful of classic presets like
 * "Bass Boost", "Jazz", "Classical", and a few others. These are marked [EqualizerPreset.isBuiltIn]
 * so the UI can prevent the user from accidentally deleting them.
 *
 * @author Hamza417
 */
@Database(
        entities = [EqualizerPreset::class],
        version = 1,
        exportSchema = true
)
abstract class EqualizerDatabase : RoomDatabase() {

    abstract fun equalizerPresetDao(): EqualizerPresetDao

    companion object {

        private const val DB_NAME = "equalizer.db"

        @Volatile
        private var instance: EqualizerDatabase? = null

        /**
         * Returns the singleton [EqualizerDatabase] instance, creating it on first call.
         * Thread-safe via double-checked locking — two threads won't accidentally create
         * two separate databases and argue about whose presets are real.
         *
         * @param context Any [Context]; the application context is extracted internally.
         */
        fun getInstance(context: Context): EqualizerDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext).also {
                    instance = it
                }
            }
        }

        /** Returns the existing instance without creating a new one. May be null before first use. */
        fun getInstance(): EqualizerDatabase? = instance

        private fun buildDatabase(context: Context): EqualizerDatabase {
            return Room.databaseBuilder(context, EqualizerDatabase::class.java, DB_NAME)
                .addCallback(object : Callback() {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed the built-in presets the first time the database is created.
                        // We launch on IO so we don't block the database-open callback.
                        CoroutineScope(Dispatchers.IO).launch {
                            instance?.equalizerPresetDao()?.insertPresets(builtInPresets())
                        }
                    }
                })
                .build()
        }

        /**
         * Produces the list of factory presets shipped with the app. These cover the most
         * common listening genres and scenarios so new users can get a great sound immediately
         * without needing to know what a "parametric EQ" even is.
         *
         * Band order (low to high): 32 Hz, 64 Hz, 125 Hz, 250 Hz, 500 Hz, 1 kHz,
         * 2 kHz, 4 kHz, 8 kHz, 16 kHz.
         */
        fun builtInPresets(): List<EqualizerPreset> = listOf(

                // Dead flat — the reference point for "no coloration at all".
                EqualizerPreset.fromGains(
                        name = "Flat",
                        gains = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
                        isBuiltIn = true
                ),

                // Extra low-end punch for bass-heavy listeners. Feels like being at a concert.
                EqualizerPreset.fromGains(
                        name = "Bass Boost",
                        gains = floatArrayOf(6f, 5f, 4f, 2f, 0f, 0f, -1f, -1f, 0f, 0f),
                        isBuiltIn = true
                ),

                // Reduces the low end for a tighter, cleaner sound on smaller speakers.
                EqualizerPreset.fromGains(
                        name = "Bass Reduce",
                        gains = floatArrayOf(-4f, -3f, -2f, -1f, 0f, 0f, 0f, 0f, 0f, 0f),
                        isBuiltIn = true
                ),

                // Brightens up the high end for more sparkle and air.
                EqualizerPreset.fromGains(
                        name = "Treble Boost",
                        gains = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 1f, 2f, 4f, 6f),
                        isBuiltIn = true
                ),

                // Tames harsh highs on bright headphones or sibilant recordings.
                EqualizerPreset.fromGains(
                        name = "Treble Reduce",
                        gains = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, -1f, -2f, -3f, -4f),
                        isBuiltIn = true
                ),

                // Orchestra and chamber music shine best with a slightly scooped midrange.
                EqualizerPreset.fromGains(
                        name = "Classical",
                        gains = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, -2f, -2f, -2f, -3f),
                        isBuiltIn = true
                ),

                // Warm and intimate — great for acoustic guitars, upright bass, and brass.
                EqualizerPreset.fromGains(
                        name = "Jazz",
                        gains = floatArrayOf(2f, 2f, 0f, 2f, -2f, -2f, 0f, 1f, 2f, 3f),
                        isBuiltIn = true
                ),

                // Big guitars, punchy kick drum — classic rock tuning for guitar-driven music.
                EqualizerPreset.fromGains(
                        name = "Rock",
                        gains = floatArrayOf(4f, 3f, 0f, -1f, -1f, 0f, 2f, 3f, 4f, 4f),
                        isBuiltIn = true
                ),

                // Smiley-face curve: boosted bass and treble with a scooped midrange.
                EqualizerPreset.fromGains(
                        name = "Pop",
                        gains = floatArrayOf(-1f, 0f, 2f, 4f, 4f, 2f, 0f, 0f, -1f, -1f),
                        isBuiltIn = true
                ),

                // Deep sub-bass, boosted midrange presence — hip-hop ready.
                EqualizerPreset.fromGains(
                        name = "Hip-Hop",
                        gains = floatArrayOf(5f, 4f, 1f, 3f, -1f, -1f, 1f, -1f, 1f, 1f),
                        isBuiltIn = true
                ),

                // Punchy sub, strong upper-mid punch for kick and synth-heavy tracks.
                EqualizerPreset.fromGains(
                        name = "Electronic",
                        gains = floatArrayOf(4f, 3f, 0f, -1f, -2f, 2f, 0f, 0f, 4f, 5f),
                        isBuiltIn = true
                ),

                // Natural warmth and upper-mid detail — great for folk and singer-songwriter.
                EqualizerPreset.fromGains(
                        name = "Acoustic",
                        gains = floatArrayOf(3f, 2f, 0f, 1f, 1f, 0f, 1f, 2f, 3f, 3f),
                        isBuiltIn = true
                ),

                // Gently reduces bass so the speaker's own resonance doesn't muddy the mix.
                EqualizerPreset.fromGains(
                        name = "Small Speakers",
                        gains = floatArrayOf(-3f, -2f, 0f, 1f, 2f, 2f, 2f, 1f, 0f, 0f),
                        isBuiltIn = true
                ),

                // Boosts speech frequencies so podcasts and audiobooks are crystal clear.
                EqualizerPreset.fromGains(
                        name = "Spoken Word",
                        gains = floatArrayOf(-2f, -2f, 0f, 1f, 4f, 4f, 3f, 2f, 0f, -1f),
                        isBuiltIn = true
                ),

                // Late-night volume with a boosted mid to compensate for the Fletcher-Munson effect.
                EqualizerPreset.fromGains(
                        name = "Late Night",
                        gains = floatArrayOf(3f, 3f, 2f, 1f, 3f, 3f, 2f, 1f, 2f, 2f),
                        preampDb = -3f,
                        isBuiltIn = true
                )
        )
    }
}

