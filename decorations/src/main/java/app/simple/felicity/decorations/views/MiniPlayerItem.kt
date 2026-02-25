package app.simple.felicity.decorations.views

/**
 * Lightweight data holder for a single mini-player page.
 *
 * @param title   Song / track title
 * @param artist  Artist name
 * @param payload Opaque object forwarded to [MiniPlayer.Callbacks.onLoadArt] so the
 *                caller (e.g. the music module) can drive Glide without creating a
 *                dependency on Glide inside the decorations' module.
 */
data class MiniPlayerItem(
        val title: String,
        val artist: String,
        val payload: Any? = null
)

