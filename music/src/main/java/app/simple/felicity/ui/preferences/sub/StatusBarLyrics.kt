package app.simple.felicity.ui.preferences.sub

import android.os.Bundle
import android.provider.Settings
import app.simple.felicity.R
import app.simple.felicity.extensions.fragments.PreferenceFragment
import app.simple.felicity.models.Preference
import app.simple.felicity.preferences.StatusBarLyricsPreferences
import app.simple.felicity.statusbar.StatusBarLyricsService

/**
 * Settings screen for the floating status bar lyrics overlay.
 *
 * Kept as a dedicated panel instead of a handful of switches inside the player
 * section because the feature has a lot of moving parts: width, size, colour,
 * outline, alignment, translation visibility and two position axes.
 *
 * @author Hamza417
 */
class StatusBarLyrics : PreferenceFragment() {

    override val titleRes: Int
        get() = R.string.status_bar_lyrics

    override val iconRes: Int
        get() = R.drawable.ic_lyrics

    override fun getPreferencesList(): List<Preference> {
        return createStatusBarLyricsPanel()
    }

    override fun onResume() {
        super.onResume()

        // The user may have revoked the permission while the app was in the background.
        // Do not keep a switch pretending the overlay is alive.
        val context = context ?: return
        if (StatusBarLyricsPreferences.isEnabled() && !Settings.canDrawOverlays(context)) {
            StatusBarLyricsPreferences.setEnabled(false)
            StatusBarLyricsService.stop(context)
            binding.recyclerView.adapter?.notifyDataSetChanged()
        }
    }

    companion object {
        fun newInstance(): StatusBarLyrics {
            val args = Bundle()
            val fragment = StatusBarLyrics()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "StatusBarLyrics"
    }
}
