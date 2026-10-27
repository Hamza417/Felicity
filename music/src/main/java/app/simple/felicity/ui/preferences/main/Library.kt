package app.simple.felicity.ui.preferences.main

import android.content.SharedPreferences
import android.os.Bundle
import app.simple.felicity.R
import app.simple.felicity.extensions.fragments.PreferenceFragment
import app.simple.felicity.models.Preference
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.services.AudioDatabaseService
import app.simple.felicity.repository.utils.AudioUtils

class Library : PreferenceFragment() {

    override val titleRes: Int
        get() = R.string.library

    override val iconRes: Int
        get() = R.drawable.ic_library

    override fun getPreferencesList(): List<Preference> {
        return createLibraryPanel()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            LibraryPreferences.MINIMUM_AUDIO_SIZE,
            LibraryPreferences.MINIMUM_AUDIO_LENGTH,
            LibraryPreferences.SKIP_HIDDEN_FILES,
            LibraryPreferences.SKIP_HIDDEN_FOLDERS -> {
                postDelayed {
                    // Any scanner-related preference change requires a fresh scan
                    AudioDatabaseService.refreshScan(requireContext())
                }
            }
            LibraryPreferences.ALBUM_ARTIST_OVER_ARTIST -> {
                AudioUtils.albumArtistOverArtist = LibraryPreferences.isAlbumArtistOverArtist()
            }
        }
    }

    companion object {
        fun newInstance(): Library {
            val args = Bundle()
            val fragment = Library()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "Appearance"
    }
}
