package app.simple.felicity.ui.launcher

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.activityViewModels
import app.simple.felicity.R
import app.simple.felicity.activities.MainActivity
import app.simple.felicity.databinding.FragmentSetupBinding
import app.simple.felicity.decorations.utils.PermissionUtils.isPostNotificationsPermissionGranted
import app.simple.felicity.decorations.utils.PermissionUtils.isReadMediaAudioPermissionGranted
import app.simple.felicity.decorations.utils.PermissionUtils.isSAFAccessGranted
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.preferences.SAFPreferences
import app.simple.felicity.viewmodels.setup.PermissionViewModel

/**
 * The first screen the user sees after installing the app.
 *
 * Instead of asking for the nuclear "Manage All Files" permission,
 * we now ask the user to pick a music folder using the system's
 * Storage Access Framework folder picker. Much friendlier and Play Store approved.
 *
 * @author Hamza417
 */
class Setup : MediaFragment() {

    private lateinit var binding: FragmentSetupBinding

    private val permissionViewModel by activityViewModels<PermissionViewModel>()

    private val notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
    ) { _ ->
        updateNotificationPermissionStatus()
    }

    private val readMediaAudioPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
    ) { _ ->
        updateReadMediaAudioPermissionStatus()
    }

    /**
     * Opens the system folder picker and waits for the user to choose a directory.
     * Once they pick one, we lock in the persistent read permission and save the URI.
     */
    private val safFolderPickerLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // Lock in a persistent read permission so we can still access the
            // folder after the app restarts (SAF permissions survive reboots only
            // when you call this — without it, the grant expires with the process).
            requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            SAFPreferences.addTreeUri(uri.toString())
        }

        updateSAFPermissionStatus()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireHiddenMiniPlayer()

        updateNotificationPermissionStatus()
        updateSAFPermissionStatus()
        updateReadMediaAudioPermissionStatus()

        binding.grantPostNotifications.setOnClickListener {
            requestNotificationPermission()
        }

        // The second row now opens the SAF folder picker instead of the All Files settings page.
        binding.grantManageAllFiles.setOnClickListener {
            safFolderPickerLauncher.launch(null)
        }

        binding.grantReadMediaAudio.setOnClickListener {
            requestReadMediaAudioPermission()
        }

        binding.startAppNow.setOnClickListener {
            if (areRequiredPermissionsGranted()) {
                proceedToHome()
            }
        }

        updateStartButtonState()
    }

    override fun onResume() {
        super.onResume()
        updateNotificationPermissionStatus()
        updateSAFPermissionStatus()
        updateReadMediaAudioPermissionStatus()
        updateStartButtonState()
    }

    /**
     * The app is ready to go when the user has granted notifications, folder access,
     * and read media audio (needed for the folder hierarchy to work on Android 13+).
     */
    private fun areRequiredPermissionsGranted(): Boolean {
        return isPostNotificationsPermissionGranted() && isSAFAccessGranted() && isReadMediaAudioPermissionGranted()
    }

    private fun updateStartButtonState() {
        val enabled = areRequiredPermissionsGranted()
        binding.startAppNow.isClickable = enabled
        binding.startAppNow.alpha = if (enabled) 1.0f else 0.5f
    }

    private fun proceedToHome() {
        (requireActivity() as? MainActivity)?.showHome()
    }

    private fun requestNotificationPermission() {
        if (isPostNotificationsPermissionGranted()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestReadMediaAudioPermission() {
        if (isReadMediaAudioPermissionGranted()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            readMediaAudioPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        }
    }

    private fun updateReadMediaAudioPermissionStatus() {
        if (isReadMediaAudioPermissionGranted()) {
            binding.statusReadMediaAudio.setText(R.string.granted)
        } else {
            binding.statusReadMediaAudio.setText(R.string.not_granted)
        }
        updateStartButtonState()
    }

    private fun updateNotificationPermissionStatus() {
        if (isPostNotificationsPermissionGranted()) {
            binding.statusPostNotifications.setText(R.string.granted)
        } else {
            binding.statusPostNotifications.setText(R.string.not_granted)
        }
        updateStartButtonState()
    }

    private fun setGrantedFoldersText() {
        val uris = requireContentResolver().persistedUriPermissions.map { it.uri }

        Log.d(TAG, "Persisted URI permissions: $uris")

        binding.folders.text = if (uris.isEmpty()) {
            getString(R.string.no_folders_granted)
        } else {
            buildString {
                uris.forEach { uri ->
                    if (isNotEmpty()) {
                        append("\n")
                    }

                    append("• ")
                    append(DocumentFile.fromTreeUri(requireContext(), uri)?.name ?: "Unknown")
                }
            }
        }
    }

    private fun updateSAFPermissionStatus() {
        val granted = isSAFAccessGranted()
        if (granted) {
            binding.statusManageAllFiles.setText(R.string.granted)
        } else {
            binding.statusManageAllFiles.setText(R.string.not_granted)
        }
        // Keep the PermissionViewModel in sync so other screens that observe it still work.
        permissionViewModel.setManageFilesPermissionState(granted)
        updateStartButtonState()
        setGrantedFoldersText()
    }

    override val wantsMiniPlayerVisible: Boolean
        get() = false

    companion object {
        fun newInstance(): Setup {
            val args = Bundle()
            val fragment = Setup()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "Setup"
    }
}