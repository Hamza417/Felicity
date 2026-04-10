package app.simple.felicity.decorations.views

/**
 * Represents a single entry in a [SharedScrollViewPopup] or
 * [SharedScrollViewPopupNonContainer] menu.
 *
 * @property title Mandatory string resource ID used as the menu entry label.
 * @property icon Optional drawable resource ID rendered to the left of the label.
 *   Defaults to 0 (no icon). When at least one item in the list carries a non-zero
 *   icon, a transparent placeholder of equal dimensions is inserted for every
 *   icon-less item so that all labels stay horizontally aligned.
 * @property summary Optional secondary text shown below [title]. A null or blank
 *   value suppresses the summary row entirely.
 *
 * @author Hamza417
 */
data class PopupMenuItem(
        val title: Int,
        val icon: Int = 0,
        val summary: String? = null
)

