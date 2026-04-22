package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

/**
 * Stores and retrieves the set of folder URIs that the user has granted
 * us access to via the Storage Access Framework (SAF) directory picker.
 *
 * Instead of asking for sweeping "Manage All Files" access, we ask the
 * user to pick specific folders — much friendlier to privacy and Play Store
 * policy. Each granted tree URI is persisted here as a plain string so we
 * can request its permissions again after a reboot.
 *
 * @author Hamza417
 */
object SAFPreferences {

    /** SharedPreferences key for the set of persisted SAF tree URI strings. */
    private const val SAF_TREE_URIS = "saf_granted_tree_uris"

    /**
     * Returns the full set of folder URIs the user has given us access to.
     * Returns an empty set if nothing has been granted yet — no surprises.
     */
    fun getTreeUris(): Set<String> {
        return SharedPreferences.getSharedPreferences()
            .getStringSet(SAF_TREE_URIS, emptySet()) ?: emptySet()
    }

    /**
     * Saves a new folder URI to the persisted set so we can use it on
     * the next launch without asking the user again.
     *
     * @param uriString The tree URI string returned by the SAF folder picker.
     */
    fun addTreeUri(uriString: String) {
        val current = getTreeUris().toMutableSet()
        current.add(uriString)
        SharedPreferences.getSharedPreferences().edit {
            putStringSet(SAF_TREE_URIS, current)
        }
    }

    /**
     * Removes a previously saved folder URI. Call this when the user
     * explicitly revokes access to a folder in the settings screen.
     *
     * @param uriString The tree URI string to remove from the persisted set.
     */
    fun removeTreeUri(uriString: String) {
        val current = getTreeUris().toMutableSet()
        current.remove(uriString)
        SharedPreferences.getSharedPreferences().edit {
            putStringSet(SAF_TREE_URIS, current)
        }
    }

    /**
     * Returns true when the user has granted access to at least one folder.
     * This is the go/no-go check we run before starting a library scan.
     */
    fun hasAnyTreeUri(): Boolean {
        return getTreeUris().isNotEmpty()
    }
}

