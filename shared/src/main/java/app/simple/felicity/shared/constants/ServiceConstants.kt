package app.simple.felicity.shared.constants

import android.media.MediaPlayer

object ServiceConstants {

    private const val APP_PACKAGE_NAME = "app.simple.felicity"

    const val ACTION_CANCEL = "$APP_PACKAGE_NAME.cancel"

    // Audio
    const val actionPrepared = "$APP_PACKAGE_NAME.prepared"
    const val actionTogglePause = "$APP_PACKAGE_NAME.toggle_pause"
    const val actionPlay = "$APP_PACKAGE_NAME.play"
    const val actionPause = "$APP_PACKAGE_NAME.pause"
    const val actionStop = "$APP_PACKAGE_NAME.stop"
    const val actionSkip = "$APP_PACKAGE_NAME.skip"
    const val actionRewind = "$APP_PACKAGE_NAME.rewind"
    const val actionQuitMusicService = "$APP_PACKAGE_NAME.quit.music.service"
    const val actionPendingQuitService = "$APP_PACKAGE_NAME.pending_quit_service"
    const val shuffleMode = "$APP_PACKAGE_NAME.shuffle_mode"
    const val actionNext = "$APP_PACKAGE_NAME.action_next"
    const val actionPrevious = "$APP_PACKAGE_NAME.action_previous"
    const val actionOpen = "$APP_PACKAGE_NAME.action_open"
    const val actionMetaData = "$APP_PACKAGE_NAME.metadata"
    const val actionBuffering = "$APP_PACKAGE_NAME.media.buffering"
    const val actionMediaError = "$APP_PACKAGE_NAME.media.error"

    fun getMediaErrorString(extra: Int): String {
        return when (extra) {
            MediaPlayer.MEDIA_ERROR_IO -> {
                "MEDIA_ERROR_IO"
            }
            MediaPlayer.MEDIA_ERROR_MALFORMED -> {
                "MEDIA_ERROR_MALFORMED"
            }
            MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> {
                "MEDIA_ERROR_UNSUPPORTED"
            }
            MediaPlayer.MEDIA_ERROR_TIMED_OUT -> {
                "MEDIA_ERROR_TIMED_OUT"
            }
            else -> {
                "NO_ERROR"
            }
        }
    }
}
