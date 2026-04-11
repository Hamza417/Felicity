package app.simple.felicity.decorations.miniplayer

import android.graphics.drawable.Drawable

/**
 * Holds everything a single action button inside the [MiniPlayer] needs to know about itself.
 * Think of it like a Swiss Army knife blade — each instance knows its own icon,
 * an optional accessibility label, and what to do when the user actually taps it.
 *
 * Up to four of these can be shown at the top-right corner of the mini player at once.
 * Any extras beyond four are quietly ignored (we only have so much real estate on screen).
 *
 * @param icon               The drawable icon rendered inside the button zone.
 *                           Tint it before passing it in — the MiniPlayer won't touch it.
 * @param contentDescription Accessibility label used by screen readers to announce this button.
 * @param onClick            Invoked on the main thread when the user taps this button.
 *
 * @author Hamza417
 */
data class MiniPlayerButton(
        val icon: Drawable,
        val contentDescription: String = "",
        val onClick: () -> Unit
)

