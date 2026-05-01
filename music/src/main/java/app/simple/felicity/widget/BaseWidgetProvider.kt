package app.simple.felicity.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import app.simple.felicity.widget.WidgetViewScope
import app.simple.felicity.glide.util.AudioCoverUtils.getArtCoverForWidget
import app.simple.felicity.manager.SharedPreferences
import app.simple.felicity.preferences.AlbumArtPreferences
import app.simple.felicity.repository.database.instances.AudioDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The shared brain behind every Felicity home screen widget.
 *
 * All widgets in this app show the same song data (title, artist, art) and respond
 * to the same playback controls (previous, play/pause, next). The only thing that
 * differs between widget styles is the layout and how view IDs are wired up.
 * That per-widget work lives in the subclasses — everything else lives here.
 *
 * To create a new widget style, extend this class and implement:
 *  - [getLayoutId] — return your layout resource ID.
 *  - [applyStaticViews] — wire up text, icons, and button click intents using [WidgetViewScope].
 *  - [applyAlbumArt] — set the album art bitmap (or a placeholder) on your art view.
 *  - [applyBackgroundArt] — set the blurred background bitmap on your background view.
 *
 * @author Hamza417
 */
abstract class BaseWidgetProvider : AppWidgetProvider() {

    /**
     * A short-lived coroutine scope that keeps widget updates alive past the normal
     * broadcast deadline. [SupervisorJob] makes sure a single failed Glide request
     * doesn't cancel everything else in flight.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // -------------------------------------------------------------------------
    // Abstract contract — every subclass must fill these in
    // -------------------------------------------------------------------------

    /**
     * Returns the layout resource ID for this widget style.
     * For example: `R.layout.widget_blurred` or `R.layout.widget_compact`.
     */
    abstract fun getLayoutId(): Int

    /**
     * Called after all data is ready but before album art is loaded.
     * Wire up text, icon resources, color tints, and button click intents here
     * using the [WidgetViewScope] helper that is passed in.
     *
     * This is the fast path — whatever you do here will show up on screen immediately,
     * before the album art bitmap has finished loading.
     */
    abstract fun applyStaticViews(scope: WidgetViewScope)

    /**
     * Called after the album art bitmap has been loaded (or failed to load).
     * Set the bitmap on your art [android.widget.ImageView] here.
     * [bitmap] will be null if loading failed; show a placeholder in that case.
     */
    abstract fun applyAlbumArt(views: RemoteViews, bitmap: Bitmap?)

    /**
     * Called after the blurred background bitmap has been loaded (or failed to load).
     * Set the bitmap on your background [android.widget.ImageView] here.
     * [bitmap] will be null if loading failed — you can leave the background empty
     * or use a fallback color; totally up to the subclass.
     */
    abstract fun applyBackgroundArt(views: RemoteViews, bitmap: Bitmap?)

    // -------------------------------------------------------------------------
    // Broadcast wiring — same for every widget style
    // -------------------------------------------------------------------------

    override fun onReceive(context: Context, intent: Intent) {
        // goAsync() keeps the broadcast alive long enough for our coroutine to finish.
        // Without it Android would kill the process before Glide even starts.
        val pendingResult = goAsync()

        scope.launch {
            try {
                handleReceive(context, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Figures out what kind of broadcast just arrived and routes it to the right handler.
     * Runs inside the coroutine scope so calling suspend functions here is safe.
     */
    private suspend fun handleReceive(context: Context, intent: Intent) {
        val manager = AppWidgetManager.getInstance(context)

        when (intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                    ?: manager.getAppWidgetIds(ComponentName(context, this::class.java))
                ids.forEach { id -> pushUpdate(context, manager, id) }
            }

            ACTION_WIDGET_UPDATE -> {
                // The playback service sent us fresh song info — save it and redraw.
                WidgetStatePrefs.save(
                        context,
                        title = intent.getStringExtra(EXTRA_TITLE) ?: "",
                        artist = intent.getStringExtra(EXTRA_ARTIST) ?: "",
                        isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false),
                        songId = intent.getLongExtra(EXTRA_SONG_ID, -1L)
                )
                val ids = manager.getAppWidgetIds(ComponentName(context, this::class.java))
                ids.forEach { id -> pushUpdate(context, manager, id) }
            }

            else -> {
                // Hand off lifecycle events (ENABLED, DISABLED, DELETED …) to AppWidgetProvider
                // on the main thread where it expects to run.
                withContext(Dispatchers.Main) { super.onReceive(context, intent) }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Core update logic — orchestrates data loading and two-phase rendering
    // -------------------------------------------------------------------------

    /**
     * Builds and pushes a complete widget update for a single widget instance.
     *
     * We do this in two phases so the user sees something right away:
     *  Phase 1 — text and icons are applied instantly (no disk or network I/O).
     *  Phase 2 — album art bitmaps arrive from Glide and trigger a second update.
     *
     * Subclasses decide how each phase looks by implementing [applyStaticViews],
     * [applyAlbumArt], and [applyBackgroundArt].
     */
    protected open suspend fun pushUpdate(context: Context, manager: AppWidgetManager, widgetId: Int) {
        // Boot the shared-preferences singleton in case the widget woke up before the app.
        SharedPreferences.init(context)

        val title = WidgetStatePrefs.getTitle(context).ifEmpty { "Unknown" }
        val artist = WidgetStatePrefs.getArtist(context).ifEmpty { "Unknown" }
        val isPlaying = WidgetStatePrefs.isPlaying(context)
        val songId = WidgetStatePrefs.getSongId(context)

        val views = RemoteViews(context.packageName, getLayoutId())

        // Give the subclass everything it needs to set up text, icons, and button intents.
        val viewScope = WidgetViewScope(
                context = context,
                views = views,
                title = title,
                artist = artist,
                isPlaying = isPlaying,
                pendingIntentBuilder = ::buildPendingIntent
        )
        applyStaticViews(viewScope)

        // Phase 1: push immediately so the widget doesn't stay blank while art loads.
        manager.updateAppWidget(widgetId, views)

        // Phase 2: load bitmaps — this can take a moment, so we push a second update
        // once they arrive. Both are loaded concurrently to keep things snappy.
        val bgBitmap = loadBackgroundArt(context, manager, widgetId, songId)
        applyBackgroundArt(views, bgBitmap)
        manager.updateAppWidget(widgetId, views)

        val artBitmap = loadAlbumArt(context, songId)
        applyAlbumArt(views, artBitmap)
        manager.updateAppWidget(widgetId, views)
    }

    // -------------------------------------------------------------------------
    // Glide helpers — shared art-loading logic
    // -------------------------------------------------------------------------

    /**
     * Loads the square album art thumbnail for [songId] using the app's registered
     * Glide loader, which automatically respects all user preferences like embedded
     * tags, folder art, and greyscale mode.
     *
     * Returns null if the song is not found or anything goes wrong — callers should
     * fall back to a placeholder image in that case.
     */
    protected open suspend fun loadAlbumArt(context: Context, songId: Long): Bitmap? =
        withContext(Dispatchers.IO) {
            if (songId == -1L) return@withContext null
            runCatching {
                val audio = AudioDatabase.getInstance(context.applicationContext)
                    .audioDao()
                    ?.getAudioById(songId)
                    ?: return@withContext null

                context.getArtCoverForWidget(
                        audio,
                        height = getAlbumArtSize(context),
                        width = getAlbumArtSize(context),
                        shadow = false,
                        blur = false,
                        greyscale = AlbumArtPreferences.isGreyscaleEnabled(),
                        darken = false,
                        crop = true,
                        roundedCorners = AlbumArtPreferences.isRoundedCornersEnabled()
                )
            }.getOrNull()
        }

    /**
     * Loads a blurred, darkened version of the album art sized to fill the widget's
     * background. The dimensions are read from [AppWidgetManager.getAppWidgetOptions]
     * so the bitmap is pixel-perfect for this particular widget instance.
     *
     * Returns null if the song is not found or anything goes wrong.
     */
    protected open suspend fun loadBackgroundArt(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            songId: Long
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (songId == -1L) return@withContext null
        runCatching {
            val audio = AudioDatabase.getInstance(context.applicationContext)
                .audioDao()
                ?.getAudioById(songId)
                ?: return@withContext null

            val density = context.resources.displayMetrics.density
            val options = manager.getAppWidgetOptions(widgetId)

            // Min-width × max-height matches what the launcher actually allocates
            // for this widget in portrait orientation.
            val widthPx = (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 294) * density)
                .toInt().coerceAtLeast(8)
            val heightPx = (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 50) * density)
                .toInt().coerceAtLeast(8)

            context.getArtCoverForWidget(
                    audio,
                    height = heightPx,
                    width = widthPx,
                    shadow = false,
                    blur = true,
                    greyscale = AlbumArtPreferences.isGreyscaleEnabled(),
                    darken = true,
                    crop = true,
                    roundedCorners = AlbumArtPreferences.isRoundedCornersEnabled()
            )
        }.getOrNull()
    }

    // -------------------------------------------------------------------------
    // Utilities subclasses (and this class) can use freely
    // -------------------------------------------------------------------------

    /**
     * Returns the pixel size to use when requesting the album art thumbnail.
     * Defaults to a reasonable square size, but subclasses can override this
     * if their layout uses a larger or smaller art view.
     */
    protected open fun getAlbumArtSize(context: Context): Int =
        context.resources.getDimensionPixelSize(app.simple.felicity.R.dimen.album_art_dimen)

    /**
     * Wraps [action] in a [PendingIntent] pointing at [WidgetActionReceiver].
     * Each action gets a unique request code derived from its hash so the three
     * control buttons never accidentally overwrite each other's pending intents.
     */
    protected fun buildPendingIntent(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
                context,
                action.hashCode(),
                Intent(action, null, context, WidgetActionReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        /** Sent by FelicityPlayerService whenever the song or playback state changes. */
        const val ACTION_WIDGET_UPDATE = "app.simple.felicity.ACTION_WIDGET_UPDATE"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_SONG_ID = "extra_song_id"
    }
}


