package app.simple.felicity.engine.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.PlaybackException
import app.simple.felicity.engine.R
import app.simple.felicity.engine.notifications.PlaybackErrorNotifier.Companion.CHANNEL_ID

/**
 * Posts silent system notifications to inform the user when a track fails to play,
 * along with a human-readable description of the underlying error.
 *
 * A dedicated notification channel ([CHANNEL_ID]) is created automatically on Android O+.
 * All notifications posted by this class are silent (no sound, no vibration) so they
 * do not interrupt the user's experience while the player auto-advances to the next track.
 *
 * @param context Application context used to access the [NotificationManager] and resources.
 *
 * @author Hamza417
 */
class PlaybackErrorNotifier(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    /**
     * Posts a silent notification explaining why a specific track could not be played.
     *
     * The notification body includes the track title, the symbolic error code name, and
     * the numeric error code for easy diagnosis.
     *
     * @param trackTitle Display title of the track that failed, or null/empty if unknown.
     * @param error      The [PlaybackException] that caused the failure.
     */
    fun notifyPlaybackError(trackTitle: String?, error: PlaybackException) {
        val resolvedTitle = if (trackTitle.isNullOrEmpty()) "Unknown Track" else trackTitle
        val errorName = mapErrorCodeToName(error.errorCode)
        val bigText = "\"$resolvedTitle\" could not be played.\n\nReason: $errorName\nCode: ${error.errorCode}"

        Log.d(TAG, "Posting playback error notification: $errorName (${error.errorCode}) for \"$resolvedTitle\"")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_error)
            .setContentTitle("Playback Error")
            .setContentText("\"$resolvedTitle\" could not be played")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setAutoCancel(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // Use a stable ID derived from the error code so identical errors collapse into one.
        notificationManager.notify(NOTIFICATION_ID_BASE + (error.errorCode % MAX_NOTIFICATION_OFFSET), notification)
    }

    /**
     * Creates the silent notification channel required on Android O and above.
     * Safe to call repeatedly; the system is a no-op if the channel already exists.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback Errors",
                NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Silent notifications for tracks that could not be played"
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Converts a [PlaybackException] error code into its symbolic constant name.
     * Covers all public error code families: unspecified, I/O, parsing, decoding,
     * audio-track, and DRM errors.
     *
     * @param errorCode Integer error code from [PlaybackException.errorCode].
     * @return A human-readable constant name such as "ERROR_CODE_DECODING_FAILED",
     *         or "UNKNOWN_ERROR_CODE(n)" for codes not yet mapped.
     */
    private fun mapErrorCodeToName(errorCode: Int): String = when (errorCode) {
        PlaybackException.ERROR_CODE_UNSPECIFIED -> "ERROR_CODE_UNSPECIFIED"
        PlaybackException.ERROR_CODE_REMOTE_ERROR -> "ERROR_CODE_REMOTE_ERROR"
        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> "ERROR_CODE_BEHIND_LIVE_WINDOW"
        PlaybackException.ERROR_CODE_TIMEOUT -> "ERROR_CODE_TIMEOUT"
        PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK -> "ERROR_CODE_FAILED_RUNTIME_CHECK"
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> "ERROR_CODE_IO_UNSPECIFIED"
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED"
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT"
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE"
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "ERROR_CODE_IO_BAD_HTTP_STATUS"
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "ERROR_CODE_IO_FILE_NOT_FOUND"
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "ERROR_CODE_IO_NO_PERMISSION"
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> "ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED"
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> "ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "ERROR_CODE_PARSING_CONTAINER_MALFORMED"
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "ERROR_CODE_PARSING_MANIFEST_MALFORMED"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED"
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> "ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED"
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "ERROR_CODE_DECODER_INIT_FAILED"
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> "ERROR_CODE_DECODER_QUERY_FAILED"
        PlaybackException.ERROR_CODE_DECODING_FAILED -> "ERROR_CODE_DECODING_FAILED"
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES"
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "ERROR_CODE_DECODING_FORMAT_UNSUPPORTED"
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> "ERROR_CODE_AUDIO_TRACK_INIT_FAILED"
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> "ERROR_CODE_AUDIO_TRACK_WRITE_FAILED"
        PlaybackException.ERROR_CODE_DRM_UNSPECIFIED -> "ERROR_CODE_DRM_UNSPECIFIED"
        PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED -> "ERROR_CODE_DRM_SCHEME_UNSUPPORTED"
        PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED -> "ERROR_CODE_DRM_PROVISIONING_FAILED"
        PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR -> "ERROR_CODE_DRM_CONTENT_ERROR"
        PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> "ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED"
        PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION -> "ERROR_CODE_DRM_DISALLOWED_OPERATION"
        PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR -> "ERROR_CODE_DRM_SYSTEM_ERROR"
        PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED -> "ERROR_CODE_DRM_DEVICE_REVOKED"
        PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED -> "ERROR_CODE_DRM_LICENSE_EXPIRED"
        else -> "UNKNOWN_ERROR_CODE($errorCode)"
    }

    private companion object {
        private const val TAG = "PlaybackErrorNotifier"
        private const val CHANNEL_ID = "felicity_playback_errors"

        /** Base ID for error notifications. Offset by error code to collapse duplicates. */
        private const val NOTIFICATION_ID_BASE = 9000

        /** Modulo cap so notification IDs stay within a safe bounded range. */
        private const val MAX_NOTIFICATION_OFFSET = 500
    }
}

