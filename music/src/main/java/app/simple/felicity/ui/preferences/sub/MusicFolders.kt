package app.simple.felicity.ui.preferences.sub

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import app.simple.felicity.adapters.ui.lists.AdapterFolderList
import app.simple.felicity.databinding.FragmentGenericRecyclerViewBinding
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.preferences.SAFPreferences

/**
 * The "Music Folders" preference panel.
 *
 * Shows the user all the folders they've already granted access to,
 * lets them add more via the system's folder picker (SAF), and lets
 * them remove any folder they no longer want the app to scan.
 *
 * We use the Storage Access Framework here instead of asking for broad
 * storage permissions — much friendlier, and Play Store approved.
 *
 * @author Hamza417
 */
class MusicFolders : MediaFragment() {

    private lateinit var binding: FragmentGenericRecyclerViewBinding

    /**
     * Opens the system folder picker. When the user picks a folder, we lock in
     * a persistent read permission so we can still access it after a reboot.
     */
    private val folderPickerLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // Lock in the permission so it survives process restarts and reboots.
            // Without this call, the grant expires when the process dies.
            requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            SAFPreferences.addTreeUri(uri.toString())
            refreshList()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentGenericRecyclerViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        requireHiddenMiniPlayer()

        binding.recyclerView.adapter = buildAdapter()
        view.startTransitionOnPreDraw()
    }

    /**
     * Builds a fresh adapter from the current set of saved URIs.
     */
    private fun buildAdapter(): AdapterFolderList {
        return AdapterFolderList(
                uris = SAFPreferences.getTreeUris().toList(),
                onAdd = {
                    // Launch the system directory picker — the result comes back
                    // to folderPickerLauncher.
                    folderPickerLauncher.launch(null)
                },
                onRemove = { uriString ->
                    removeFolder(uriString)
                }
        )
    }

    /**
     * Revokes the persistent permission for the given URI and removes it from
     * our saved set. After this, the app can no longer read that folder.
     */
    private fun removeFolder(uriString: String) {
        val uri = uriString.toUri()

        // Release the persistable permission so the system knows we no longer need
        // that grant. It's good practice — and the right thing to do for privacy.
        try {
            requireContext().contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // The permission may have already been revoked by the system or the user,
            // so it's safe to swallow this and continue removing it from our list.
        }

        SAFPreferences.removeTreeUri(uriString)
        refreshList()
    }

    /**
     * Tells the recycler view to rebuild itself with the latest list of folders.
     */
    private fun refreshList() {
        (binding.recyclerView.adapter as? AdapterFolderList)?.submitList(
                SAFPreferences.getTreeUris().toList()
        )
    }

    override fun getTransitionType(): TransitionType {
        return TransitionType.DRIFT
    }

    override val wantsMiniPlayerVisible: Boolean
        get() = false

    companion object {
        fun newInstance(): MusicFolders {
            return MusicFolders()
        }

        const val TAG = "MusicFolders"
    }
}

