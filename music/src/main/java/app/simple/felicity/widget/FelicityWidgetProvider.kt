package app.simple.felicity.widget

import android.graphics.Bitmap
import android.graphics.Color
import android.widget.RemoteViews
import app.simple.felicity.R

/**
 * The classic 1×4 blurred-background widget for Felicity.
 *
 * All the heavy lifting — broadcast routing, state reading, Glide calls — lives in
 * [BaseWidgetProvider]. This class only knows about its own layout ([R.layout.widget_blurred])
 * and the view IDs inside it. If you want a new widget style, copy this pattern and
 * point it at a different layout. That's it.
 *
 * @author Hamza417
 */
class FelicityWidgetProvider : BaseWidgetProvider() {

    /** The blurred-background layout is this widget's signature look. */
    override fun getLayoutId(): Int = R.layout.widget_blurred

    /**
     * Wires up all the static views — text labels, icon tints, button click intents —
     * using the data bundled in [scope].
     *
     * Everything here runs before album art is loaded, so the user sees text and
     * control icons right away even on a slow connection.
     */
    override fun applyStaticViews(scope: WidgetViewScope) {
        val views = scope.views
        val context = scope.context

        // Song metadata.
        views.setTextViewText(R.id.widget_title, scope.title)
        views.setTextViewText(R.id.widget_artist, scope.artist)
        views.setTextColor(R.id.widget_title, Color.WHITE)
        views.setTextColor(R.id.widget_artist, Color.LTGRAY)

        // Swap the play/pause icon based on current playback state.
        val playPauseRes = if (scope.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        views.setImageViewResource(R.id.widget_play_pause, playPauseRes)

        // Tint all three control icons white so they pop against any background.
        views.setInt(R.id.widget_prev, "setColorFilter", Color.WHITE)
        views.setInt(R.id.widget_play_pause, "setColorFilter", Color.WHITE)
        views.setInt(R.id.widget_next, "setColorFilter", Color.WHITE)

        // Clear button backgrounds so ImageButtons don't show a gray pressed ripple.
        views.setInt(R.id.widget_prev, "setBackgroundColor", Color.TRANSPARENT)
        views.setInt(R.id.widget_play_pause, "setBackgroundColor", Color.TRANSPARENT)
        views.setInt(R.id.widget_next, "setBackgroundColor", Color.TRANSPARENT)

        // Attach the media control intents to each button.
        views.setOnClickPendingIntent(
                R.id.widget_play_pause,
                scope.pendingIntentBuilder(context, WidgetActionReceiver.ACTION_PLAY_PAUSE)
        )
        views.setOnClickPendingIntent(
                R.id.widget_prev,
                scope.pendingIntentBuilder(context, WidgetActionReceiver.ACTION_PREV)
        )
        views.setOnClickPendingIntent(
                R.id.widget_next,
                scope.pendingIntentBuilder(context, WidgetActionReceiver.ACTION_NEXT)
        )
    }

    /**
     * Puts the album art bitmap in the art view, or falls back to the app icon
     * if Glide came back empty-handed.
     */
    override fun applyAlbumArt(views: RemoteViews, bitmap: Bitmap?) {
        if (bitmap != null) {
            views.setImageViewBitmap(R.id.widget_album_art, bitmap)
        } else {
            views.setImageViewResource(R.id.widget_album_art, R.mipmap.ic_launcher)
        }
    }

    /**
     * Puts the blurred background bitmap in the background view.
     * If there is no bitmap (no song loaded yet) we just leave the background
     * as-is — the XML default will show through.
     */
    override fun applyBackgroundArt(views: RemoteViews, bitmap: Bitmap?) {
        if (bitmap != null) {
            views.setImageViewBitmap(R.id.widget_background_view, bitmap)
        }
    }
}
