package app.simple.felicity.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import app.simple.felicity.R
import app.simple.felicity.glide.util.AudioCoverUtils.getArtCoverForWidget
import app.simple.felicity.manager.SharedPreferences
import app.simple.felicity.preferences.AlbumArtPreferences
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.managers.ThemeUtils
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.models.Theme
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The 1×4 home screen widget for Felicity.
 *
 * Everything visual — background shape, colors, icons, album art — is applied
 * programmatically so the widget always matches the user's active app theme without
 * any hard-coded colors or drawable XML resources involved.
 *
 * How theme colors get here:
 *  We implement [ThemeChangedListener] and temporarily register with [ThemeManager]
 *  inside [pushUpdate]. When [ThemeUtils.setAppTheme] sets the new theme on ThemeManager,
 *  it fires [onThemeChanged] and [onAccentChanged] synchronously, giving us the latest
 *  colors before we build the RemoteViews. We unregister immediately after.
 *
 * How the background is drawn:
 *  A [GradientDrawable] is built in code with the theme's background color and the
 *  corner radius from [AppearancePreferences.getCornerRadius]. It is rasterized onto
 *  a [Bitmap] at the widget's actual pixel size (read from [AppWidgetManager.getAppWidgetOptions])
 *  and set on the background [android.widget.ImageView] via [RemoteViews.setImageViewBitmap].
 *  No XML drawable, no color filter tricks — just a Canvas draw call.
 *
 * How album art is loaded:
 *  Glide loads the [app.simple.felicity.repository.models.Audio] model directly using
 *  the project's registered [app.simple.felicity.glide.audiocover.AudioCoverLoader].
 *  That loader calls [app.simple.felicity.repository.covers.AudioCover.load] which
 *  honors all user preferences (MediaStore cache, folder art, embedded tags).
 *  The default BlurShadow+Padding transforms from the Glide module are replaced with
 *  a plain [CenterCrop] so the bitmap is a clean square.
 *
 * @author Hamza417
 */
class FelicityWidgetProvider : AppWidgetProvider() {

    /**
     * Holds the most recently received [Theme] from [ThemeManager].
     * Updated synchronously inside [onThemeChanged] while we are registered as a listener.
     */
    private var currentTheme: Theme = ThemeManager.theme

    /**
     * Holds the most recently received [Accent] from [ThemeManager].
     * Updated synchronously inside [onAccentChanged] while we are registered as a listener.
     */
    private var currentAccent: Accent = ThemeManager.accent

    /**
     * Short-lived coroutine scope for async album-art loading.
     * [SupervisorJob] ensures a failed Glide request doesn't cancel the rest of the work.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        // goAsync() keeps the broadcast alive long enough for our coroutine (and Glide) to finish.
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
     * Dispatches each incoming broadcast to the right handler.
     * Runs inside the coroutine scope, so it is safe to call suspend functions here.
     */
    private suspend fun handleReceive(context: Context, intent: Intent) {
        val manager = AppWidgetManager.getInstance(context)

        when (intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                    ?: manager.getAppWidgetIds(ComponentName(context, FelicityWidgetProvider::class.java))
                ids.forEach { id -> pushUpdate(context, manager, id) }
            }

            ACTION_WIDGET_UPDATE -> {
                // New song or play/pause state arrived from the service — persist it.
                WidgetStatePrefs.save(
                        context,
                        title = intent.getStringExtra(EXTRA_TITLE) ?: "",
                        artist = intent.getStringExtra(EXTRA_ARTIST) ?: "",
                        isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false),
                        songId = intent.getLongExtra(EXTRA_SONG_ID, -1L)
                )
                val ids = manager.getAppWidgetIds(ComponentName(context, FelicityWidgetProvider::class.java))
                ids.forEach { id -> pushUpdate(context, manager, id) }
            }

            else -> {
                // Hand off APPWIDGET_ENABLED, APPWIDGET_DISABLED, APPWIDGET_DELETED etc.
                // to AppWidgetProvider's own onReceive on the main thread.
                withContext(Dispatchers.Main) { super.onReceive(context, intent) }
            }
        }
    }

    /**
     * Assembles and pushes a full widget update for one instance.
     *
     * Phase 1 (fast): Theme colors, text, icons — pushed immediately so the user
     * sees something the instant the widget wakes up.
     *
     * Phase 2 (slow): Album art loaded via Glide on the IO dispatcher — pushed as
     * a second update once the bitmap is ready.
     */
    private suspend fun pushUpdate(context: Context, manager: AppWidgetManager, widgetId: Int) {
        // Boot the shared-preferences singleton if the widget was drawn before the app opened.
        SharedPreferences.init(context)

        // Read colors from the freshly loaded theme.
        val primaryTextColor = Color.WHITE
        val secondaryTextColor = Color.LTGRAY
        val iconColor = Color.WHITE

        // Read the last-saved song metadata.
        val title = WidgetStatePrefs.getTitle(context).ifEmpty { context.getString(R.string.unknown) }
        val artist = WidgetStatePrefs.getArtist(context).ifEmpty { context.getString(R.string.unknown) }
        val isPlaying = WidgetStatePrefs.isPlaying(context)
        val songId = WidgetStatePrefs.getSongId(context)

        val views = RemoteViews(context.packageName, R.layout.widget_blurred)

        // Build the themed background bitmap programmatically — no XML drawable involved.
        val bgBitmap = loadBGArtWithGlide(context, manager, widgetId, songId)
        views.setImageViewBitmap(R.id.widget_background_view, bgBitmap)

        // Text.
        views.setTextViewText(R.id.widget_title, title)
        views.setTextViewText(R.id.widget_artist, artist)
        views.setTextColor(R.id.widget_title, primaryTextColor)
        views.setTextColor(R.id.widget_artist, secondaryTextColor)

        // Play/pause icon swap + icon tints on all three buttons.
        val playPauseRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        views.setImageViewResource(R.id.widget_play_pause, playPauseRes)
        views.setInt(R.id.widget_prev, "setColorFilter", iconColor)
        views.setInt(R.id.widget_play_pause, "setColorFilter", iconColor)
        views.setInt(R.id.widget_next, "setColorFilter", iconColor)

        // Belt AND suspenders: nuke the ImageButton backgrounds in code too.
        views.setInt(R.id.widget_prev, "setBackgroundColor", Color.TRANSPARENT)
        views.setInt(R.id.widget_play_pause, "setBackgroundColor", Color.TRANSPARENT)
        views.setInt(R.id.widget_next, "setBackgroundColor", Color.TRANSPARENT)

        // Button click intents.
        views.setOnClickPendingIntent(R.id.widget_play_pause, buildPendingIntent(context, WidgetActionReceiver.ACTION_PLAY_PAUSE))
        views.setOnClickPendingIntent(R.id.widget_prev, buildPendingIntent(context, WidgetActionReceiver.ACTION_PREV))
        views.setOnClickPendingIntent(R.id.widget_next, buildPendingIntent(context, WidgetActionReceiver.ACTION_NEXT))

        // Phase 1 push — user sees text and icons immediately.
        manager.updateAppWidget(widgetId, views)

        // Phase 2 — load album art via Glide, then push a second update.
        val artBitmap = loadArtWithGlide(context, songId)
        if (artBitmap != null) {
            views.setImageViewBitmap(R.id.widget_album_art, artBitmap)
        } else {
            views.setImageViewResource(R.id.widget_album_art, R.mipmap.ic_launcher)
        }
        manager.updateAppWidget(widgetId, views)
    }

    /**
     * Creates the themed background as a [Bitmap] by rasterizing a [GradientDrawable]
     * built entirely in code — no drawable XML file involved at any point.
     *
     * The drawable uses:
     *  - Color from [ThemeManager.theme.viewGroupTheme.backgroundColor] (with a slight
     *    transparency so the wallpaper softly bleeds through).
     *  - Corner radius from [AppearancePreferences.getCornerRadius], converted to pixels,
     *    so it matches the same rounding the rest of the app uses.
     *
     * The bitmap is drawn at the widget's actual pixel size (obtained via
     * [AppWidgetManager.getAppWidgetOptions]) so corners stay perfectly round
     * when [android.widget.ImageView.ScaleType.FIT_XY] stretches the image to fill the view.
     */
    private fun buildBackgroundBitmap(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            backgroundColor: Int
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val options = manager.getAppWidgetOptions(widgetId)

        // Use the minimum width and maximum height since that is the size the launcher
        // actually allocates for this widget in portrait orientation.
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 294)
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 50)
        val widthPx = (widthDp * density).toInt().coerceAtLeast(8)
        val heightPx = (heightDp * density).toInt().coerceAtLeast(8)

        // Corner radius in pixels — mirror exactly what AppearancePreferences drives elsewhere.
        val cornerRadiusPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_PX,
                AppearancePreferences.getCornerRadius(),
                context.resources.displayMetrics
        )

        // Slight alpha so the wallpaper softly shows through the edges.
        val bgColor = Color.argb(
                0xE6,
                Color.red(backgroundColor),
                Color.green(backgroundColor),
                Color.blue(backgroundColor)
        )

        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(bgColor)
            cornerRadius = cornerRadiusPx
            setBounds(0, 0, widthPx, heightPx)
        }

        val bitmap = createBitmap(widthPx, heightPx)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    /**
     * Loads the cover art for the song with [songId] via Glide.
     *
     * Loading through Glide with the [app.simple.felicity.repository.models.Audio] model
     * means the registered [app.simple.felicity.glide.audiocover.AudioCoverLoader] is used,
     * which delegates to [app.simple.felicity.repository.covers.AudioCover.load] — so all
     * user preferences (MediaStore cache, embedded tags, folder art) are automatically honored.
     *
     * We pass [RequestOptions.transform(CenterCrop())] to replace the Glide module's default
     * BlurShadow+Padding chain with a plain square crop suitable for the widget's art view.
     *
     * Returns null on any failure; the caller shows the app icon as a placeholder.
     */
    private suspend fun loadArtWithGlide(context: Context, songId: Long): Bitmap? =
        withContext(Dispatchers.IO) {
            if (songId == -1L) return@withContext null
            try {
                val audio = AudioDatabase.getInstance(context.applicationContext)
                    .audioDao()
                    ?.getAudioById(songId)
                    ?: return@withContext null

                val size = context.resources.getDimensionPixelSize(R.dimen.album_art_dimen)

                context.getArtCoverForWidget(
                        audio,
                        height = size,
                        width = size,
                        shadow = false,
                        blur = false,
                        greyscale = AlbumArtPreferences.isGreyscaleEnabled(),
                        darken = false,
                        crop = true,
                        roundedCorners = AlbumArtPreferences.isRoundedCornersEnabled())
            } catch (_: Exception) {
                null
            }
        }

    private suspend fun loadBGArtWithGlide(context: Context, manager: AppWidgetManager, widgetId: Int, songId: Long): Bitmap? =
        withContext(Dispatchers.IO) {
            if (songId == -1L) return@withContext null

            try {
                val audio = AudioDatabase.getInstance(context.applicationContext)
                    .audioDao()
                    ?.getAudioById(songId)
                    ?: return@withContext null

                val density = context.resources.displayMetrics.density
                val options = manager.getAppWidgetOptions(widgetId)

                // Use the minimum width and maximum height since that is the size the launcher
                // actually allocates for this widget in portrait orientation.
                val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 294)
                val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 50)
                val widthPx = (widthDp * density).toInt().coerceAtLeast(8)
                val heightPx = (heightDp * density).toInt().coerceAtLeast(8)

                context.getArtCoverForWidget(
                        audio,
                        height = heightPx,
                        width = widthPx,
                        shadow = false,
                        blur = true,
                        greyscale = AlbumArtPreferences.isGreyscaleEnabled(),
                        darken = true,
                        crop = true,
                        roundedCorners = AlbumArtPreferences.isRoundedCornersEnabled())
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Wraps a widget button action string in a [PendingIntent] targeting
     * [WidgetActionReceiver]. Each action gets a unique request code from its hash so
     * the three buttons don't silently overwrite each other's intents.
     */
    private fun buildPendingIntent(context: Context, action: String): PendingIntent =
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
