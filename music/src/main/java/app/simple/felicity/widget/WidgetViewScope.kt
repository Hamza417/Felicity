package app.simple.felicity.widget

import android.app.PendingIntent
import android.content.Context
import android.widget.RemoteViews

/**
 * A little bundle of everything a widget layout needs to draw itself.
 *
 * Instead of passing a dozen separate parameters into [BaseWidgetProvider.applyStaticViews],
 * we pack them all in here. Think of it as the "briefing packet" handed to each widget
 * style so it knows exactly what to show and which intents to attach to its buttons.
 *
 * @property context Android context, used for resources and string lookups.
 * @property views The [RemoteViews] instance that will be pushed to the home screen.
 * @property title The current song title, already defaulted to "Unknown" if absent.
 * @property artist The current artist name, already defaulted to "Unknown" if absent.
 * @property isPlaying Whether the player is currently playing (used to pick the right icon).
 * @property pendingIntentBuilder A factory function that turns an action string into a
 *   [PendingIntent] for the widget's control buttons. Call it with one of the constants
 *   from [WidgetActionReceiver] — for example `pendingIntentBuilder(context, ACTION_PLAY_PAUSE)`.
 *
 * @author Hamza417
 */
data class WidgetViewScope(
        val context: Context,
        val views: RemoteViews,
        val title: String,
        val artist: String,
        val isPlaying: Boolean,
        val pendingIntentBuilder: (Context, String) -> PendingIntent
)

