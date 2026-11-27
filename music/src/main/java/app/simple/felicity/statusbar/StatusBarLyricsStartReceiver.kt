package app.simple.felicity.statusbar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.simple.felicity.core.constants.StatusBarLyricsConstants

/**
 * Receives the "playback started" signal from the player service and makes sure the
 * floating lyrics host is alive.
 *
 * Android stops background services after the app has been idle for a while, which is
 * exactly what happens when playback stays paused. Without this receiver the overlay
 * would only come back after the user opened the app again; with it, resuming from the
 * media notification is enough.
 *
 * @author Hamza417
 */
class StatusBarLyricsStartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != StatusBarLyricsConstants.ACTION_START) return
        StatusBarLyricsService.startIfEnabled(context)
    }
}
