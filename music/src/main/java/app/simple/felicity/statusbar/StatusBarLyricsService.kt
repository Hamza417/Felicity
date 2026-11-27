package app.simple.felicity.statusbar

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import app.simple.felicity.decorations.lrc.model.LrcData
import app.simple.felicity.decorations.lrc.model.LrcEntry
import app.simple.felicity.engine.managers.MediaPlaybackManager
import app.simple.felicity.managers.LyricsManager
import app.simple.felicity.manager.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.preferences.StatusBarLyricsPreferences
import app.simple.felicity.repository.constants.MediaConstants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import app.simple.felicity.manager.SharedPreferences as FelicityPreferences

/**
 * Hosts the floating status bar lyrics window.
 *
 * The service is intentionally lightweight: it is **not** a foreground service and
 * never posts a notification. It simply adds one non-touchable, non-focusable
 * [WindowManager] overlay and keeps its content in sync with the player:
 *
 * - [LyricsManager] supplies the parsed lyrics for the current song;
 * - [MediaPlaybackManager.songSeekPositionFlow] supplies the clock;
 * - [MediaPlaybackManager.playbackStateFlow] decides when the overlay is visible.
 *
 * Pausing playback fades the overlay out and resuming brings it back. Turning the
 * preference off stops the service entirely.
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class StatusBarLyricsService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val TAG = "StatusBarLyricsService"

        private const val FADE_DURATION_MS = 300L
        private const val FALLBACK_STATUS_BAR_DP = 24f

        /**
         * Starts the overlay only when the user enabled it and granted the overlay
         * permission. Safe to call from [android.app.Application] — failures (for
         * example a background-start restriction on Android 12+) are swallowed.
         */
        @JvmStatic
        fun startIfEnabled(context: Context) {
            FelicityPreferences.init(context)

            if (!StatusBarLyricsPreferences.isEnabled()) return
            if (!Settings.canDrawOverlays(context)) return

            runCatching {
                context.startService(Intent(context, StatusBarLyricsService::class.java))
            }.onFailure {
                Log.w(TAG, "Unable to start status bar lyrics service: ${it.message}")
            }
        }

        @JvmStatic
        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, StatusBarLyricsService::class.java))
            }
        }
    }

    @Inject
    lateinit var lyricsManager: LyricsManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var windowManager: WindowManager

    /**
     * Window context used to inflate the overlay. Android 12+ refuses to hand a
     * `WindowManager` (or view configuration) to a plain service context, so the
     * overlay gets its own visual context tied to the application-overlay window type.
     */
    private var windowContext: Context? = null

    private var container: FrameLayout? = null
    private var lyricsView: StatusBarLyricsView? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var windowAdded = false

    private var observersStarted = false

    private var lrcData: LrcData? = null
    private var currentEntry: LrcEntry? = null
    private var syncOffsetMs: Long = 0L
    private var positionMs: Long = 0L
    private var playing: Boolean = false
    private var lastSongPath: String? = null

    override fun onCreate() {
        super.onCreate()

        FelicityPreferences.init(this)
        FelicityPreferences.registerSharedPreferencesListener(this)

        lastSongPath = MediaPlaybackManager.getCurrentSong()?.uri
        playing = MediaPlaybackManager.isPlaying()

        ensureOverlay()
        startObservers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!StatusBarLyricsPreferences.isEnabled() || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        ensureOverlay()
        applyConfiguration()
        updateContent()
        updateVisibility()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyGeometry()
    }

    override fun onDestroy() {
        observersStarted = false
        scope.cancel()

        unregisterSharedPreferenceChangeListener()

        removeOverlay()
        windowContext = null

        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            StatusBarLyricsPreferences.ENABLED -> {
                if (StatusBarLyricsPreferences.isEnabled()) {
                    ensureOverlay()
                    applyConfiguration()
                    updateContent()
                    updateVisibility()
                } else {
                    stopSelf()
                }
            }

            StatusBarLyricsPreferences.WIDTH,
            StatusBarLyricsPreferences.LINE_COUNT,
            StatusBarLyricsPreferences.TEXT_SIZE,
            StatusBarLyricsPreferences.TEXT_COLOR,
            StatusBarLyricsPreferences.ALIGNMENT,
            StatusBarLyricsPreferences.STROKE,
            StatusBarLyricsPreferences.STROKE_COLOR,
            StatusBarLyricsPreferences.STROKE_WIDTH,
            StatusBarLyricsPreferences.HIDE_TRANSLATION -> {
                applyConfiguration()
                applyGeometry()
                updateContent()
            }

            StatusBarLyricsPreferences.HORIZONTAL_BIAS,
            StatusBarLyricsPreferences.VERTICAL_OFFSET -> applyGeometry()

            StatusBarLyricsPreferences.HIDE_ON_PLAYER_SCREEN,
            StatusBarLyricsPreferences.HIDE_ON_LYRICS_SCREEN -> updateVisibility()
        }
    }

    /**
     * Creates the overlay window once. The window itself is transparent, click-through
     * and focus-less; only the text inside it is ever visible.
     */
    private fun ensureOverlay() {
        if (windowAdded) return
        if (!Settings.canDrawOverlays(this)) return

        val overlayContext = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
                val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
                if (display != null) {
                    createWindowContext(display, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
                } else {
                    this
                }
            } else {
                this
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to create overlay window context", e)
            return
        }

        val manager = overlayContext.getSystemService(WINDOW_SERVICE) as WindowManager
        val view = StatusBarLyricsView(overlayContext)
        val root = FrameLayout(overlayContext).apply {
            isClickable = false
            isFocusable = false
            // The lyric itself is positioned with translationY inside this full-screen
            // host. Without clipChildren the translated view can move below the status
            // bar even when the host's own bounds never change.
            clipChildren = false
            clipToPadding = false
            importantForAccessibility = FrameLayout.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(view, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        /*
         * Use a transparent full-screen host instead of a small window whose
         * WindowManager.LayoutParams.y carries the offset. Some OEM ROMs clamp that
         * y for APPLICATION_OVERLAY windows, which keeps the lyric pinned to the top
         * no matter what the preference says. Moving the child view inside a stable
         * full-screen window is reliable and still touch-through.
         */
        val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            /*
             * Android 12+ blocks touches that pass through an overlay whose opacity is
             * above this threshold. Staying at the maximum allowed opacity keeps the
             * status bar gestures usable.
             */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val inputManager = getSystemService(InputManager::class.java)
                alpha = inputManager?.maximumObscuringOpacityForTouch ?: 0.8f
            }
        }

        try {
            manager.addView(root, params)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to add status bar lyrics overlay", e)
            return
        }

        windowContext = overlayContext
        windowManager = manager
        container = root
        lyricsView = view
        windowParams = params
        windowAdded = true

        view.alpha = 0f
        view.applyConfig(buildConfig())
        view.setPlaying(playing)
        applyGeometry()
    }

    private fun removeOverlay() {
        if (!windowAdded) return
        runCatching { windowManager.removeViewImmediate(container) }
        windowAdded = false
        container = null
        lyricsView = null
        windowParams = null
    }

    private fun startObservers() {
        if (observersStarted) return
        observersStarted = true

        scope.launch {
            lyricsManager.lrcData.collect {
                lrcData = it
                updateContent()
            }
        }

        scope.launch {
            lyricsManager.syncOffsetMs.collect {
                syncOffsetMs = it
                updateContent()
            }
        }

        scope.launch {
            MediaPlaybackManager.songSeekPositionFlow.collect {
                positionMs = it
                updateContent()
            }
        }

        scope.launch {
            MediaPlaybackManager.playbackStateFlow.collect { state ->
                playing = state == MediaConstants.PLAYBACK_PLAYING
                        || state == MediaConstants.PLAYBACK_BUFFERING
                lyricsView?.setPlaying(playing)

                if (playing) ensureOverlay()
                updateVisibility()
            }
        }

        scope.launch {
            MediaPlaybackManager.songPositionFlow.collect {
                val path = MediaPlaybackManager.getCurrentSong()?.uri
                if (path != lastSongPath) {
                    lastSongPath = path
                    lrcData = null
                    currentEntry = null
                    lyricsView?.setLyric(null)
                    updateVisibility()
                }
            }
        }

        scope.launch {
            StatusBarLyricsController.playerScreenVisible.collect {
                updateVisibility()
            }
        }

        scope.launch {
            StatusBarLyricsController.lyricsScreenVisible.collect {
                updateVisibility()
            }
        }
    }

    /**
     * Recomputes the highlighted line from the current playback position. This is the
     * only place that decides what the overlay should say.
     */
    private fun updateContent() {
        val view = lyricsView ?: return
        val data = lrcData

        if (data == null || data.isEmpty) {
            if (currentEntry != null) {
                currentEntry = null
                view.setLyric(null)
                applyGeometry()
            }
            updateVisibility()
            return
        }

        val adjustedPosition = positionMs + syncOffsetMs + data.offset
        val index = findEntryIndex(data.entries, adjustedPosition)
        val entry = data.entries.getOrNull(index)
        val visibleEntry = entry?.takeIf { it.text.isNotBlank() }

        if (visibleEntry !== currentEntry) {
            currentEntry = visibleEntry
            view.setLyric(visibleEntry)
            applyGeometry()
        }

        view.setPosition(adjustedPosition)
        updateVisibility()
    }

    private fun updateVisibility() {
        if (!Settings.canDrawOverlays(this)) {
            removeOverlay()
            return
        }

        val view = lyricsView ?: return

        val shouldShow = StatusBarLyricsPreferences.isEnabled()
                && playing
                && currentEntry?.text?.isNotBlank() == true
                && !(StatusBarLyricsPreferences.isHideOnPlayerScreen()
                && StatusBarLyricsController.playerScreenVisible.value)
                && !(StatusBarLyricsPreferences.isHideOnLyricsScreen()
                && StatusBarLyricsController.lyricsScreenVisible.value)

        val targetAlpha = if (shouldShow) 1f else 0f
        if (abs(view.alpha - targetAlpha) < 0.01f) return

        view.animate().cancel()
        view.animate()
                .alpha(targetAlpha)
                .setDuration(FADE_DURATION_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
    }

    /**
     * Applies size, colour, outline and alignment preferences to the view.
     */
    private fun applyConfiguration() {
        lyricsView?.applyConfig(buildConfig())
    }

    /**
     * Recomputes the lyric size and its position inside the full-screen host. Called on
     * every preference change because the text height changes with size and translation.
     *
     * The window itself stays at (0, 0) and covers the screen. Horizontal bias and the
     * vertical offset are applied to the child view with translations, so the position
     * no longer depends on OEM handling of [WindowManager.LayoutParams.y].
     */
    private fun applyGeometry() {
        val view = lyricsView ?: return
        val root = container ?: return
        val params = windowParams ?: return

        val displayMetrics = windowContext?.resources?.displayMetrics ?: resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val widthPx = min(dp(StatusBarLyricsPreferences.getWidth().toFloat()).roundToInt(), screenWidth)
                .coerceAtLeast(1)

        view.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(widthPx, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        )

        val heightPx = max(view.requiredHeightPx(), view.measuredHeight)
        val statusBarHeight = statusBarHeightPx()

        val childParams = view.layoutParams as FrameLayout.LayoutParams
        if (childParams.width != widthPx || childParams.height != heightPx) {
            childParams.width = widthPx
            childParams.height = heightPx
            view.layoutParams = childParams
        }

        val x = ((screenWidth - widthPx) * StatusBarLyricsPreferences.getHorizontalBias())
                .roundToInt()
                .coerceIn(0, max(0, screenWidth - widthPx))

        /*
         * The slider is a 0..MAX scale across the whole screen: 0 puts the lyric at
         * the very top, MAX puts it at the very bottom. Keep the stored 0..2000 range
         * for compatibility, but map it onto the actual usable height so different
         * screen sizes behave the same way.
         */
        val maxY = max(0, screenHeight - heightPx)
        val offset = StatusBarLyricsPreferences.getVerticalOffset()
        val relativeY = if (StatusBarLyricsPreferences.MAX_VERTICAL_OFFSET <= 0) {
            0
        } else {
            (offset.toFloat() / StatusBarLyricsPreferences.MAX_VERTICAL_OFFSET.toFloat() * maxY)
                    .roundToInt()
                    .coerceIn(0, maxY)
        }

        /*
         * The host window itself is shifted up by statusBarHeight, so add the same
         * amount to the child translation. The two cancel out: relativeY = 0 lands
         * on the physical top, relativeY = maxY lands on the physical bottom.
         */
        val y = statusBarHeight + relativeY

        view.translationX = x.toFloat()
        view.translationY = y.toFloat()

        /*
         * The window manager may place a MATCH_PARENT overlay below the status bar.
         * Shift the whole host up by the status bar height and grow it by the same
         * amount so translationY = 0 really means the physical top of the screen,
         * while the bottom still reaches the physical bottom.
         */
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = screenHeight + statusBarHeight
        params.x = 0
        params.y = -statusBarHeight

        runCatching { windowManager.updateViewLayout(root, params) }
    }

    private fun buildConfig(): StatusBarLyricsView.Config {
        return StatusBarLyricsView.Config(
                textSizeSp = StatusBarLyricsPreferences.getTextSize(),
                textColor = StatusBarLyricsPreferences.getTextColor(),
                stroke = StatusBarLyricsPreferences.isStroke(),
                alignment = StatusBarLyricsPreferences.getAlignment(),
                strokeColor = StatusBarLyricsPreferences.getStrokeColor(),
                strokeWidthDp = StatusBarLyricsPreferences.getStrokeWidth(),
                hideTranslation = StatusBarLyricsPreferences.isHideTranslation(),
                lineCount = StatusBarLyricsPreferences.getLineCount()
        )
    }

    /**
     * Binary search for the last lyric line whose timestamp is at or before [timeMs].
     */
    private fun findEntryIndex(entries: List<LrcEntry>, timeMs: Long): Int {
        var low = 0
        var high = entries.size - 1
        var result = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (entries[mid].timeInMillis <= timeMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return result
    }

    private fun statusBarHeightPx(): Int {
        val contextResources = windowContext?.resources ?: resources
        val resourceId = contextResources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            contextResources.getDimensionPixelSize(resourceId)
        } else {
            dp(FALLBACK_STATUS_BAR_DP).roundToInt()
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
