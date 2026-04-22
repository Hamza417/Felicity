package app.simple.felicity.application;

import android.app.Application;
import android.content.SharedPreferences;

import dagger.hilt.android.HiltAndroidApp;

/**
 * The entry point for the whole app. Kicks off any one-time startup work
 * before any Activity or Service gets a chance to run.
 *
 * @author Hamza417
 */
@HiltAndroidApp
public class FelicityApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        resetAudioDatabaseForSAFMigration();
    }
    
    /**
     * One-time database wipe required for the Storage Access Framework migration.
     * <p>
     * Before SAF, the library stored plain POSIX file paths like
     * "/storage/emulated/0/Music/song.mp3". After SAF, every path is a
     * content:// URI like "content://com.android.externalstorage...". The two
     * formats are completely incompatible, so an old database would just show
     * the user a library full of missing tracks on every launch — not great.
     * <p>
     * We solve this by deleting the whole database once, right here on first
     * launch of the new build. The scanner will repopulate everything using
     * the SAF URIs the user has already granted. After this one-time wipe the
     * flag is set and the database is never touched here again.
     */
    private void resetAudioDatabaseForSAFMigration() {
        SharedPreferences prefs = getSharedPreferences("migration_flags", MODE_PRIVATE);
        boolean alreadyReset = prefs.getBoolean("saf_db_reset_v1", false);
        
        if (!alreadyReset) {
            deleteDatabase("audio.db");
            prefs.edit().putBoolean("saf_db_reset_v1", true).apply();
        }
    }
}
