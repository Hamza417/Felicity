package app.simple.felicity.core.constants

/**
 * Small shared contract between the playback engine and the floating status bar
 * lyrics host.
 *
 * The playback service lives in the `engine` module and cannot reference the app's
 * service class directly, so it asks the app to bring the overlay back by sending
 * this package-scoped broadcast whenever playback starts. This is what makes the
 * lyrics reappear after the system has stopped the idle overlay service.
 *
 * @author Hamza417
 */
object StatusBarLyricsConstants {

    const val ACTION_START = "app.simple.felicity.action.START_STATUS_BAR_LYRICS"
}
