package app.simple.felicity.dialogs.playlists

import android.os.Bundle
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants.LayoutMode
import app.simple.felicity.extensions.dialogs.BaseLayoutMenuDialog
import app.simple.felicity.preferences.PlaylistPreferences

/**
 * Bottom-sheet menu dialog for the Playlists panel that exposes the list style selector.
 *
 * <p>Extends [BaseLayoutMenuDialog] and delegates the [LayoutMode] persistence to
 * [PlaylistPreferences], keeping the Playlists grid / list setting fully independent
 * from every other panel.</p>
 *
 * @author Hamza417
 */
class PlaylistsMenu : BaseLayoutMenuDialog() {

    override fun getLayoutMode(): LayoutMode = PlaylistPreferences.getGridSize()

    override fun setLayoutMode(mode: LayoutMode) {
        PlaylistPreferences.setGridSize(mode)
    }

    companion object {
        private const val TAG = "PlaylistsMenu"

        fun newInstance(): PlaylistsMenu {
            return PlaylistsMenu().apply { arguments = Bundle() }
        }

        /**
         * Shows the Playlists layout-style menu bottom-sheet using the given [FragmentManager].
         *
         * @return The shown [PlaylistsMenu] instance.
         */
        fun FragmentManager.showPlaylistsMenu(): PlaylistsMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }
    }
}

