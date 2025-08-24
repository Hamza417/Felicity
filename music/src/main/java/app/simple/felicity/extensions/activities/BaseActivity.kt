package app.simple.felicity.extensions.activities

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.simple.felicity.engine.services.ExoPlayerService
import app.simple.felicity.manager.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.manager.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.preferences.PlayerPreferences
import app.simple.felicity.repository.database.instances.LastSongDatabase
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.theme.accents.Felicity
import app.simple.felicity.theme.data.MaterialYou.presetMaterialYouDynamicColors
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.managers.ThemeUtils
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class BaseActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    protected var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun attachBaseContext(newBase: Context?) {
        app.simple.felicity.manager.SharedPreferences.init(newBase!!)
        registerSharedPreferenceChangeListener()
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            presetMaterialYouDynamicColors()
        }

        initMediaController()
        setStrictModePolicy()
        enableNotchArea()
        makeAppFullScreen()
        initTheme()
    }

    private fun initMediaController() {
        val sessionToken =
            SessionToken(this,
                         ComponentName(this, ExoPlayerService::class.java))

        controllerFuture =
            MediaController.Builder(this, sessionToken).buildAsync()

        val listener = Runnable {
            Log.d(TAG, "MediaController created successfully")
            mediaController = controllerFuture?.get()
            MediaManager.setMediaController(mediaController!!)
            restoreLastSongStateFromDatabase()
        }

        controllerFuture?.addListener(listener, MoreExecutors.directExecutor())
    }

    private fun restoreLastSongStateFromDatabase() {
        lifecycleScope.launch(Dispatchers.Default) {
            val lastSongDatabase = LastSongDatabase.getInstance(applicationContext)
            val songDao = lastSongDatabase.songDao()
            val lastSongs = songDao.getAllSongs()

            if (lastSongs.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    MediaManager.setSongs(
                            songs = lastSongs,
                            position = PlayerPreferences.getLastSongPosition())
                    MediaManager.seekTo(PlayerPreferences.getLastSongSeek())
                }
            } else {
                Log.d(TAG, "No last songs found in the database")
            }

        }
    }

    private fun initTheme() {
        ThemeUtils.setAppTheme(resources)
        ThemeManager.accent = Felicity()
        ThemeUtils.updateNavAndStatusColors(resources, window)
    }

    private fun makeAppFullScreen() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = Color.TRANSPARENT
        }
    }

    private fun enableNotchArea() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun setStrictModePolicy() {
        StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .build())
    }

    override fun onDestroy() {
        super.onDestroy()
        MediaManager.stopSeekPositionUpdates()
        unregisterSharedPreferenceChangeListener()

        try {
            mediaController?.let {
                MediaManager.clearMediaController()
                it.release()
            }

            MediaController.releaseFuture(controllerFuture!!)
        } catch (e: NullPointerException) {
            e.printStackTrace()
        }

        LastSongDatabase.getInstance(this.applicationContext).close()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {

    }

    companion object {
        const val TAG = "BaseActivity"
    }
}
