package app.simple.felicity.extensions.activities

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import android.view.WindowManager
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.simple.felicity.core.constants.ThemeConstants
import app.simple.felicity.core.singletons.AppOrientation
import app.simple.felicity.engine.services.FelicityPlayerService
import app.simple.felicity.manager.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.manager.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.preferences.BehaviourPreferences
import app.simple.felicity.repository.covers.AudioCover
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.managers.PlaybackStateManager
import app.simple.felicity.shared.utils.BarHeight
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

open class BaseActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener, ThemeChangedListener {

    protected var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private lateinit var content: FrameLayout

    private var predictiveBackCallback: OnBackInvokedCallback? = null

    /** Active palette-extraction job. Cancelled when a new song fires before the old one finishes. */
    private var paletteJob: Job? = null

    /** ID of the song whose palette is already applied — skip re-extraction for the same track. */
    private var lastPaletteSongId: Long = -1L

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

        AppOrientation.setOrientation(BarHeight.isLandscape(this))
        content = findViewById(android.R.id.content)
        ThemeManager.addListener(this)
        ThemeUtils.setAppTheme(resources)
        content.setBackgroundColor(ThemeManager.theme.viewGroupTheme.backgroundColor)

        initMediaController()
        setStrictModePolicy()
        enableNotchArea()
        makeAppFullScreen()
        initTheme()
        applyPredictiveBackGesture()
        observeSongChangesForPalette()
    }

    /**
     * Observes [MediaManager.songPositionFlow] so the album-art accent palette is refreshed
     * every time the track changes, without blocking the main thread or the media controller.
     */
    private fun observeSongChangesForPalette() {
        lifecycleScope.launch {
            MediaManager.songPositionFlow.collect {
                // Only regenerate when the AlbumArt accent is actually active.
                if (AppearancePreferences.getAccentColorName() == AlbumArt.IDENTIFIER) {
                    generateAlbumArtPalette()
                }
            }
        }
    }

    private fun initMediaController() {
        val sessionToken =
            SessionToken(this,
                         ComponentName(this, FelicityPlayerService::class.java))

        controllerFuture =
            MediaController.Builder(this, sessionToken).buildAsync()

        val listener = Runnable {
            Log.d(TAG, "MediaController created successfully")
            mediaController = controllerFuture?.get()
            MediaManager.setMediaController(mediaController!!)
            restoreLastSongStateFromDatabase()
            generateAlbumArtPalette()
        }

        controllerFuture?.addListener(listener, MoreExecutors.directExecutor())
    }

    private fun restoreLastSongStateFromDatabase() {
        // If the controller already has media items loaded (e.g. after a rotation where the
        // service kept running), skip the DB restore entirely to avoid re-preparing the player
        // which causes the brief audio freeze.
        if ((mediaController?.mediaItemCount ?: 0) > 0) {
            Log.d(TAG, "MediaController already has items, skipping DB restore (likely a rotation)")
            // Re-sync MediaManager's in-memory queue with what the service has
            val currentIndex = mediaController?.currentMediaItemIndex ?: 0
            MediaManager.notifyCurrentPosition(currentIndex)
            return
        }

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val audioDatabase = AudioDatabase.getInstance(applicationContext)
                val playbackState = PlaybackStateManager.fetchPlaybackState(audioDatabase)
                val lastSongs = PlaybackStateManager.getAudiosFromQueueIDs(audioDatabase)?.toList()

                Log.d(TAG, "Restoring playback state: index=${playbackState?.index}, position=${playbackState?.position}, queue size=${lastSongs?.size}")

                if (!lastSongs.isNullOrEmpty() && playbackState != null) {
                    withContext(Dispatchers.Main) {
                        // Set songs with the saved position and seek position in one call
                        // This avoids the need for a separate seekTo call
                        MediaManager.setSongs(
                                audios = lastSongs,
                                position = playbackState.index.coerceIn(0, lastSongs.size - 1),
                                startPositionMs = playbackState.position.coerceAtLeast(0L),
                        )
                        // Don't call play() automatically - let user initiate playback
                        Log.d(TAG, "Playback state restored successfully")
                    }
                } else {
                    // No saved queue (e.g. first-ever launch) – the DB may be empty because the
                    // scan hasn't finished yet. Observe the Flow and set the queue as soon as the
                    // first non-empty batch of songs arrives (works for both instant & delayed scans).
                    Log.d(TAG, "No valid playback state found – waiting for first songs from DB scan")
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val dao = audioDatabase.audioDao() ?: return@launch
                            // first { } suspends until Room emits a non-empty list, then cancels.
                            val firstSongs = dao.getAllAudio().first { it.isNotEmpty() }
                            withContext(Dispatchers.Main) {
                                if (MediaManager.getSongs().isEmpty()) {
                                    MediaManager.setSongs(
                                            audios = firstSongs,
                                            position = 0,
                                            startPositionMs = 0L,
                                    )
                                    Log.d(TAG, "Default queue loaded on first launch: ${firstSongs.size} songs")
                                } else {
                                    Log.d(TAG, "Queue already populated by the time scan finished – skipping default load")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error waiting for first songs from DB", e)
                        }
                    }
                }
            } catch (e: NullPointerException) {
                Log.e(TAG, "Error restoring last song state: ${e.message}")
                e.printStackTrace()
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error restoring playback state", e)
                e.printStackTrace()
            }
        }
    }

    protected fun generateAlbumArtPalette() {
        if (AppearancePreferences.getAccentColorName() != AlbumArt.IDENTIFIER) return

        val audio = MediaManager.getCurrentSong() ?: return

        // Skip re-extraction when the same track is already applied.
        if (audio.id == lastPaletteSongId) return

        // Cancel any in-flight extraction for a previous song (e.g. rapid skipping).
        paletteJob?.cancel()

        paletteJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Load raw bitmap on the IO dispatcher (file / MediaMetadataRetriever).
                val rawBitmap: Bitmap = AudioCover.load(audio) ?: return@launch

                // Downscale to a small thumbnail before palette math to keep CPU cost low.
                // 128×128 gives MonetPalette's 64-sample grid more than enough data.
                val thumb: Bitmap = withContext(Dispatchers.Default) {
                    val size = 128
                    if (rawBitmap.width > size || rawBitmap.height > size) {
                        rawBitmap.scale(size, size, filter = false).also {
                            if (it !== rawBitmap) rawBitmap.recycle()
                        }
                    } else {
                        rawBitmap
                    }
                }

                // Run palette extraction on the Default (CPU) dispatcher.
                val (primary, secondary) = withContext(Dispatchers.Default) {
                    val palette = MonetPalette(thumb)
                    thumb.recycle()
                    Pair(palette.accent1_500, palette.accent1_300)
                }

                withContext(Dispatchers.Main) {
                    // Guard: accent may have been changed while we were running.
                    if (AppearancePreferences.getAccentColorName() != AlbumArt.IDENTIFIER) return@withContext
                    val albumArtAccent = AlbumArt().apply {
                        primaryAccentColor = primary
                        secondaryAccentColor = secondary
                    }
                    lastPaletteSongId = audio.id
                    ThemeManager.accent = albumArtAccent
                    Log.d(TAG, "Album art palette applied for song ${audio.id}: ${albumArtAccent.hexes}")
                }
            } catch (e: FileNotFoundException) {
                Log.w(TAG, "Album art not found for song ${audio.id}", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error generating album art palette for song ${audio.id}", e)
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
        window.navigationBarDividerColor = Color.TRANSPARENT
    }

    private fun enableNotchArea() {
        window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    private fun setStrictModePolicy() {
        StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .build())
    }

    private fun applyPredictiveBackGesture() {
        if (BehaviourPreferences.isPredictiveBackEnabled()) {
            enablePredictiveBack(this)
        } else {
            disablePredictiveBack(this) {
                // Handle back press manually
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    private fun disablePredictiveBack(activity: Activity, onBack: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && predictiveBackCallback == null) {
            predictiveBackCallback = OnBackInvokedCallback {
                onBack()
            }
            activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                    predictiveBackCallback!!
            )
        }
    }

    private fun enablePredictiveBack(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && predictiveBackCallback != null) {
            activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(predictiveBackCallback!!)
            predictiveBackCallback = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterSharedPreferenceChangeListener()
        ThemeManager.removeListener(this)

        // Skip releasing the MediaController on configuration changes (e.g. rotation).
        // The service keeps playing; tearing down and rebuilding the controller causes
        // a brief audio freeze because setMediaItems + prepare() is called again on reconnect.
        if (!isChangingConfigurations) {
            MediaManager.stopSeekPositionUpdates()
            try {
                mediaController?.let {
                    MediaManager.clearMediaController()
                    it.release()
                }
                MediaController.releaseFuture(controllerFuture!!)
            } catch (e: NullPointerException) {
                e.printStackTrace()
            }
        }
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
            BehaviourPreferences.PREDICTIVE_BACK -> {
                applyPredictiveBackGesture()
            }
        }
    }

    companion object {
        const val TAG = "BaseActivity"
    }
}
