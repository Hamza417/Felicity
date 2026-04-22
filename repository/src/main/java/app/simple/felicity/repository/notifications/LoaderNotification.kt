package app.simple.felicity.repository.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import app.simple.felicity.repository.R
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages the "Scanning Library" notification that pops up while Felicity
 * indexes your music collection. Keeping this separate from the loader itself
 * means the scanning logic stays focused on scanning, not on notification plumbing.
 *
 * The notification starts life as an indeterminate spinner (we don't know how
 * many songs there are yet), then graduates to a real progress bar once we
 * have counted all the audio files on the device.
 *
 * There is also a subtle race-condition guard built in using a "generation"
 * counter. If a scan is canceled and a new one starts immediately, the old
 * scan's cleanup cannot accidentally dismiss the new scan's notification —
 * because the generation number will have already moved on.
 *
 * @author Hamza417
 */
class LoaderNotification(private val context: Context) {

    companion object {
        private const val SCAN_CHANNEL_ID = "felicity_library_scanner"
        private const val SCAN_NOTIFICATION_ID = 1001

        /**
         * How often (in files evaluated) the notification text is refreshed.
         * Updating on every single file would hammer the notification system,
         * so we settle for a nice round number that still feels responsive.
         */
        const val NOTIFICATION_UPDATE_INTERVAL = 25
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Each time a new scan begins, this number goes up by one. When a scan
     * finishes and tries to dismiss the notification, it passes the number it
     * received when it started. If a newer scan already bumped the counter,
     * the dismiss call quietly does nothing — protecting the new scan's
     * notification from being swept away by the old one's cleanup routine.
     */
    private val currentGeneration = AtomicInteger(0)

    /**
     * Tracks whether the notification is currently visible on screen. We manage
     * this ourselves because Android does not give us a reliable API to ask the
     * NotificationManager "hey, is this still showing?". Setting it to false in
     * both dismiss paths means the old scan can never accidentally nuke the new
     * scan's notification by falling through to the force-dismiss below.
     */
    private val isNotificationActive = AtomicBoolean(false)

    /**
     * Total number of audio files found on the device. Once this is set (non-zero),
     * the progress bar switches from the spinning indeterminate style to a proper
     * fill-level bar.
     */
    @Volatile
    private var totalFiles = 0

    /**
     * Call this right before a scan begins. It sets up the notification channel
     * (Android is smart enough not to create duplicate channels on repeated calls),
     * shows the initial "warming up" spinner, and returns a generation token you
     * will need to pass to [dismiss] at the end of the scan.
     *
     * @return A generation token for this scan — hold onto it!
     */
    fun begin(): Int {
        setupChannel()
        totalFiles = 0
        val generation = currentGeneration.incrementAndGet()
        isNotificationActive.set(true)
        postIndeterminate()
        return generation
    }

    /**
     * Once you know how many audio files live on the device, call this to give
     * the notification a finishing line to aim for. Before this is called the
     * bar stays as an indeterminate spinner. After this, it fills up as files
     * are scanned — much more satisfying to watch.
     *
     * @param total The total number of audio files found across all storage volumes.
     */
    fun setTotal(total: Int) {
        totalFiles = total
    }

    /**
     * Refreshes the notification to show the latest scan progress. If we know
     * the total file count (from [setTotal]), this shows a proper fill-level bar.
     * Otherwise, it keeps the indeterminate spinner going.
     *
     * @param scanned How many files have been evaluated (checked) so far.
     */
    fun updateProgress(scanned: Int) {
        val total = totalFiles
        val isIndeterminate = total <= 0

        val contentText = if (isIndeterminate) {
            "Scanning your music library…"
        } else {
            "$scanned of $total files scanned"
        }

        val notification = NotificationCompat.Builder(context, SCAN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_felicity_full)
            .setContentTitle("Scanning Library")
            .setContentText(contentText)
            .setProgress(total, scanned.coerceAtMost(total), isIndeterminate)
            .setSilent(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(SCAN_NOTIFICATION_ID, notification)
    }

    /**
     * Dismisses the scan notification — but only if [generation] still matches
     * the current one. If a newer scan already called [begin] and bumped the
     * counter, this call quietly does nothing, leaving the newer notification
     * safely in place.
     *
     * @param generation The token returned by [begin] when this scan started.
     */
    fun dismiss(generation: Int) {
        if (generation == currentGeneration.get()) {
            isNotificationActive.set(false)
            notificationManager.cancel(SCAN_NOTIFICATION_ID)
        }
    }

    /**
     * Force-dismisses the notification regardless of the current generation.
     * Use this for hard-stop situations like service shutdown, where you are
     * absolutely sure nothing is running anymore, and you just want it gone.
     */
    fun dismissForce() {
        isNotificationActive.set(false)
        notificationManager.cancel(SCAN_NOTIFICATION_ID)
    }

    /**
     * Returns true when the notification is currently visible on screen.
     * We track this ourselves with a simple boolean flag rather than asking
     * the system, since Android's NotificationManager has no reliable "is it
     * showing?" API for the app's own notifications.
     */
    fun isShowing(): Boolean = isNotificationActive.get()

    private fun setupChannel() {
        val channel = NotificationChannel(
                SCAN_CHANNEL_ID,
                "Library Scanner",
                NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress while your music library is being scanned"
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun postIndeterminate() {
        val notification = NotificationCompat.Builder(context, SCAN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_felicity_full)
            .setContentTitle("Scanning Library")
            .setContentText("Getting your music ready…")
            .setProgress(0, 0, true)
            .setSilent(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        notificationManager.notify(SCAN_NOTIFICATION_ID, notification)
    }
}