package app.simple.felicity.extensions.activities

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.simple.felicity.core.constants.ThemeConstants
import app.simple.felicity.engine.services.ExoPlayerService
import app.simple.felicity.glide.songcover.SongCoverUtils.fetchBitmap
import app.simple.felicity.manager.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.manager.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.preferences.PlayerPreferences
import app.simple.felicity.repository.database.instances.LastSongDatabase
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.theme.accents.AlbumArt
import app.simple.felicity.theme.accents.Felicity
import app.simple.felicity.theme.data.MaterialYou.presetMaterialYouDynamicColors
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.managers.ThemeUtils
import app.simple.felicity.theme.themes.Theme
import app.simple.felicity.theme.tools.MonetPalette
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class BaseActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener, ThemeChangedListener {

    protected var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private lateinit var content: FrameLayout

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

        content = findViewById(android.R.id.content)
        ThemeManager.addListener(this)
        ThemeUtils.setAppTheme(resources)
        content.setBackgroundColor(ThemeManager.theme.viewGroupTheme.backgroundColor)

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

    protected fun generateAlbumArtPalette() {
        lifecycleScope.launch(Dispatchers.Default) {
            if (AppearancePreferences.getAccentColorName() == AlbumArt.IDENTIFIER) {
                val song = MediaManager.getCurrentSong() ?: return@launch
                val bitmap = song.fetchBitmap(applicationContext)!!
                val albumArtAccent = AlbumArt()
                val monetAccents = MonetPalette(bitmap)

                albumArtAccent.primaryAccentColor = monetAccents.accent1_500
                albumArtAccent.secondaryAccentColor = monetAccents.accent1_300

                withContext(Dispatchers.Main) {
                    ThemeManager.accent = albumArtAccent
                    Log.d(TAG, "Album art palette generated: ${albumArtAccent.hexes}")
                }
            }
        }
    }

    private fun initTheme() {
        ThemeUtils.setAppTheme(resources)
        ThemeUtils.updateNavAndStatusColors(resources, window)

        ThemeManager.accent = when (val accentName = AppearancePreferences.getAccentColorName()) {
            null -> Felicity().also {
                AppearancePreferences.setAccentColorName(it.identifier)
            }
            else -> {
                ThemeManager.getAccentByName(accentName)
            }
        }
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
        ThemeManager.removeListener(this)

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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (AppearancePreferences.getTheme() == ThemeConstants.MATERIAL_YOU_DARK ||
                    AppearancePreferences.getTheme() == ThemeConstants.MATERIAL_YOU_LIGHT) {
                recreate()
            }
        }
        ThemeUtils.setAppTheme(resources)
        ThemeUtils.setBarColors(resources, window)
    }

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        ThemeUtils.setBarColors(resources, window)
        content.setBackgroundColor(ThemeManager.theme.viewGroupTheme.backgroundColor)
        window.setBackgroundDrawable(ThemeManager.theme.viewGroupTheme.backgroundColor.toDrawable())
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            AppearancePreferences.ACCENT_COLOR -> {
                initTheme()
            }
        }
    }

    companion object {
        const val TAG = "BaseActivity"
    }
}
