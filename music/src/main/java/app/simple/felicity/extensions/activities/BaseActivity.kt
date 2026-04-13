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
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import androidx.core.os.ConfigurationCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.simple.felicity.core.constants.ThemeConstants
import app.simple.felicity.core.singletons.AppOrientation
import app.simple.felicity.databinding.DialogSureBinding
import app.simple.felicity.decorations.popups.SimpleDialog
import app.simple.felicity.engine.managers.MediaPlaybackManager
import app.simple.felicity.engine.managers.PlaybackStateManager
import app.simple.felicity.engine.services.FelicityPlayerService
import app.simple.felicity.manager.SharedPreferences.registerEncryptedSharedPreferencesListener
import app.simple.felicity.manager.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.manager.SharedPreferences.unregisterEncryptedSharedPreferencesListener
import app.simple.felicity.manager.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.preferences.BehaviourPreferences
import app.simple.felicity.preferences.ConfigurationPreferences
import app.simple.felicity.preferences.TrialPreferences
import app.simple.felicity.repository.covers.AudioCover
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.shared.utils.BarHeight
import app.simple.felicity.shared.utils.ContextUtils
import app.simple.felicity.shared.utils.LocaleUtils
import app.simple.felicity.theme.accents.AlbumArt
import app.simple.felicity.theme.accents.Felicity
import app.simple.felicity.theme.data.AlbumArtData
import app.simple.felicity.theme.data.MaterialYou.presetMaterialYouDynamicColors
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.managers.ThemeUtils
import app.simple.felicity.theme.models.Theme
import app.simple.felicity.theme.themes.dark.AlbumArtDark
import app.simple.felicity.theme.themes.light.AlbumArtLight
import app.simple.felicity.theme.tools.MonetPalette
import app.simple.felicity.utils.DateUtils.toDate
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

    /** Active palette-extraction job. Canceled when a new song fires before the old one finishes. */
    private var paletteJob: Job? = null

    /** ID of the song whose palette is already applied — skip re-extraction for the same track. */
    private var lastPaletteSongId: Long = -1L

    override fun attachBaseContext(newBase: Context?) {
        app.simple.felicity.manager.SharedPreferences.init(newBase!!)
        app.simple.felicity.manager.SharedPreferences.initEncrypted(newBase)
        registerSharedPreferenceChangeListener()
        registerEncryptedSharedPreferencesListener()
        super.attachBaseContext(/* newBase = */ ContextUtils.updateLocale(
                baseContext = newBase,
                languageCode = ConfigurationPreferences.getAppLanguage()))
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

        if (TrialPreferences.isFirstLaunchDateSet().not()) {
            Log.d(TAG, "First launch detected — setting trial start date")
            TrialPreferences.setFirstLaunchDate(System.currentTimeMillis())
        } else {
            Log.d(TAG, "daysLeft=${TrialPreferences.getDaysLeft()}, firstLaunchDate=${TrialPreferences.getFirstLaunchDate().toDate()}")
        }

        /**
         * Keeps the instance of current locale of the app
         */
        LocaleUtils.setAppLocale(ConfigurationCompat.getLocales(resources.configuration)[0]!!)

        /**
         * Sets window flags for keeping the screen on
         */
        if (ConfigurationPreferences.isKeepScreenOn()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /**
     * Observes [MediaPlaybackManager.songPositionFlow] so the album-art accent palette is refreshed
     * every time the track changes, without blocking the main thread or the media controller.
     *
     * Uses [ThemeUtils.isEffectiveAlbumArtTheme] to correctly detect album-art mode even when
     * the active theme is resolved through Follow System or Day/Night sub-theme settings.
     */
    private fun observeSongChangesForPalette() {
        lifecycleScope.launch {
            MediaPlaybackManager.songPositionFlow.collect {
                val isAlbumArtAccent = AppearancePreferences.getAccentColorName() == AlbumArt.IDENTIFIER
                val isAlbumArtTheme = ThemeUtils.isEffectiveAlbumArtTheme(resources)
                if (isAlbumArtAccent || isAlbumArtTheme) {
                    generateAlbumArtPalette()
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun initMediaController() {
        val sessionToken =
            SessionToken(this,
                         ComponentName(this, FelicityPlayerService::class.java))

        controllerFuture =
            MediaController.Builder(this, sessionToken).buildAsync()

        val listener = Runnable {
            Log.d(TAG, "MediaController created successfully")
            mediaController = controllerFuture?.get()
            MediaPlaybackManager.setMediaController(mediaController!!)
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
            // Re-sync MediaPlaybackManager's in-memory queue with what the service has
            val currentIndex = mediaController?.currentMediaItemIndex ?: 0
            MediaPlaybackManager.notifyCurrentPosition(currentIndex)
            onStateReady()
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
                        /*
                         * Prefer a hash-based lookup so the index stays correct even when
                         * cascade deletions shifted queue positions.
                         *
                         * Three cases:
                         *  1. currentHash == 0  →  hash was never saved (old schema). Use the
                         *     raw saved index directly; it is the only information available.
                         *  2. currentHash != 0, match found  →  reliable; use the found index.
                         *  3. currentHash != 0, no match  →  the stored hash is stale, most
                         *     likely from a DB schema migration where the hash algorithm or
                         *     field semantics changed (e.g. auto-increment id previously stored
                         *     in the hash column). The raw index is equally untrustworthy in
                         *     this case, so reset to 0 rather than pointing at a random song.
                         */
                        val restoredIndex = when {
                            playbackState.currentHash == 0L -> {
                                playbackState.index.coerceIn(0, lastSongs.size - 1)
                            }
                            else -> {
                                val byHash = lastSongs.indexOfFirst { it.hash == playbackState.currentHash }
                                if (byHash >= 0) {
                                    byHash
                                } else {
                                    Log.w(TAG, "Hash lookup failed for currentHash=${playbackState.currentHash} — stale state after DB migration, resetting to position 0")
                                    0
                                }
                            }
                        }
                        MediaPlaybackManager.setSongs(
                                audios = lastSongs,
                                position = restoredIndex,
                                startPositionMs = playbackState.position.coerceAtLeast(0L),
                        )
                        Log.d(TAG, "Playback state restored successfully")
                        onStateReady()
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
                                if (MediaPlaybackManager.getSongs().isEmpty()) {
                                    MediaPlaybackManager.setSongs(
                                            audios = firstSongs,
                                            position = 0,
                                            startPositionMs = 0L,
                                    )
                                    Log.d(TAG, "Default queue loaded on first launch: ${firstSongs.size} songs")
                                    onStateReady()
                                } else {
                                    Log.d(TAG, "Queue already populated by the time scan finished – skipping default load")
                                    onStateReady()
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

    /**
     * Called on the main thread once the media queue and playback state have been fully
     * restored from the database (or determined to be empty on first launch).
     *
     * Subclasses should override this to perform any UI initialization that must wait
     * until songs and themes are ready, such as revealing the miniplayer.
     *
     * @author Hamza417
     */
    protected open fun onStateReady() = Unit

    protected fun generateAlbumArtPalette() {
        val isAlbumArtAccent = AppearancePreferences.getAccentColorName() == AlbumArt.IDENTIFIER
        val isAlbumArtTheme = ThemeUtils.isEffectiveAlbumArtTheme(resources)

        if (!isAlbumArtAccent && !isAlbumArtTheme) return

        val audio = MediaPlaybackManager.getCurrentSong() ?: return

        // Skip re-extraction when the same track is already applied.
        if (audio.id == lastPaletteSongId) return

        // Cancel any in-flight extraction for a previous song (e.g. rapid skipping).
        paletteJob?.cancel()

        paletteJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Load raw bitmap on the IO dispatcher (file / MediaMetadataRetriever).
                val rawBitmap: Bitmap = AudioCover.load(this@BaseActivity, audio) ?: return@launch

                // Downscale to a small thumbnail before palette math to keep CPU cost low.
                // 128x128 gives MonetPalette's 64-sample grid more than enough data.
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
                val palette: MonetPalette = withContext(Dispatchers.Default) {
                    val p = MonetPalette(thumb)
                    thumb.recycle()
                    p
                }

                withContext(Dispatchers.Main) {
                    // Guard: the accent setting may have changed while the coroutine was running.
                    val stillAlbumArtAccent = AppearancePreferences.getAccentColorName() == AlbumArt.IDENTIFIER
                    // Re-evaluate using the full effective-theme check (covers Follow System /
                    // Day-Night sub-theme selections, not just direct ALBUM_ART_* constants).
                    val albumArtThemeActive = ThemeUtils.isEffectiveAlbumArtTheme(resources)

                    if (!stillAlbumArtAccent && !albumArtThemeActive) return@withContext

                    lastPaletteSongId = audio.id

                    // Apply album art theme — independently of the accent.
                    // Populate AlbumArtData then push a brand-new Theme object into ThemeManager
                    // so that every registered ThemeChangedListener is notified automatically.
                    if (albumArtThemeActive) {
                        AlbumArtData.populate(palette)
                        ThemeManager.theme = if (ThemeUtils.isEffectiveAlbumArtDark(resources)) {
                            AlbumArtDark()
                        } else {
                            AlbumArtLight()
                        }
                        Log.d(TAG, "Album art theme applied for song ${audio.id}")
                    }

                    // Apply album art accent — independently of the theme.
                    // Creating a new AlbumArt accent and setting it on ThemeManager notifies
                    // every registered ThemeChangedListener via onAccentChanged.
                    if (stillAlbumArtAccent) {
                        val albumArtAccent = AlbumArt().apply {
                            primaryAccentColor = palette.accent1_500
                            secondaryAccentColor = palette.accent1_300
                        }
                        ThemeManager.accent = albumArtAccent
                        Log.d(TAG, "Album art accent applied for song ${audio.id}: ${albumArtAccent.hexes}")
                    }
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

        val isAlbumArtAccent = AppearancePreferences.getAccentColorName() == AlbumArt.IDENTIFIER
        val isAlbumArtTheme = ThemeUtils.isEffectiveAlbumArtTheme(resources)

        // Set a regular accent immediately. Album art accent is applied asynchronously via
        // generateAlbumArtPalette once the bitmap is loaded and must not be pre-empted here.
        if (!isAlbumArtAccent) {
            ThemeManager.accent = when (val accentName = AppearancePreferences.getAccentColorName()) {
                null -> Felicity().also {
                    AppearancePreferences.setAccentColorName(it.identifier)
                }
                else -> ThemeManager.getAccentByName(accentName)
            }
        }

        // Trigger palette extraction whenever either album art mode is active.
        // The extraction method handles both cases independently on the main thread.
        if (isAlbumArtAccent || isAlbumArtTheme) {
            generateAlbumArtPalette()
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

    protected fun withSureDialog(onResult: (Boolean) -> Unit) {
        SimpleDialog.Builder(
                container = content,
                inflateBinding = DialogSureBinding::inflate)
            .onViewCreated { binding ->
                /* no-op */
            }.onDialogInflated { binding, dismiss, _ ->
                binding.sure.setOnClickListener {
                    onResult(true)
                    dismiss()
                }

                binding.cancel.setOnClickListener {
                    onResult(false)
                    dismiss()
                }
            }
            .onDismiss {
                /* no-op */
            }
            .build()
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterSharedPreferenceChangeListener()
        unregisterEncryptedSharedPreferencesListener()
        ThemeManager.removeListener(this)

        // Skip releasing the MediaController on configuration changes (e.g. rotation).
        // The service keeps playing; tearing down and rebuilding the controller causes
        // a brief audio freeze because setMediaItems + prepare() is called again on reconnect.
        if (!isChangingConfigurations) {
            MediaPlaybackManager.stopSeekPositionUpdates()
            try {
                mediaController?.let {
                    MediaPlaybackManager.clearMediaController()
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
            AppearancePreferences.THEME -> {
                // Reset the cached song ID so the palette is always regenerated when switching
                // to or from an album art theme, ensuring fresh colors are applied immediately.
                lastPaletteSongId = -1L
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
