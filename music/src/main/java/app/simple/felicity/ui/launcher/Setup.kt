package app.simple.felicity.ui.launcher

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import app.simple.felicity.R
import app.simple.felicity.activities.MainActivity
import app.simple.felicity.databinding.FragmentSetupBinding
import app.simple.felicity.decorations.utils.PermissionUtils.isPostNotificationsPermissionGranted
import app.simple.felicity.decorations.utils.PermissionUtils.isReadMediaAudioPermissionGranted
import app.simple.felicity.extensions.fragments.MediaFragment

class Setup : MediaFragment() {

    private lateinit var binding: FragmentSetupBinding

    private val storagePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
    ) { _ ->
        updateStoragePermissionStatus()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
    ) { _ ->
        updateNotificationPermissionStatus()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSetupBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireHiddenMiniPlayer()

        updateStoragePermissionStatus()
        updateNotificationPermissionStatus()

        binding.grantReadExternalStorage.setOnClickListener {
            requestStoragePermission()
        }

        binding.grantPostNotifications.setOnClickListener {
            requestNotificationPermission()
        }

        binding.startAppNow.setOnClickListener {
            if (areRequiredPermissionsGranted()) {
                proceedToHome()
            }
        }

        binding.grantMusicFolderAccess.setOnClickListener {
            launchDirectoryPicker()
        }

        updateStartButtonState()
    }

    override fun onResume() {
        super.onResume()
        updateStoragePermissionStatus()
        updateNotificationPermissionStatus()
        updateStartButtonState()
        updateUriPermissions()
    }

    private fun areRequiredPermissionsGranted(): Boolean {
        return isReadMediaAudioPermissionGranted() && isPostNotificationsPermissionGranted()
    }

    private fun updateStartButtonState() {
        val enabled = areRequiredPermissionsGranted()
        binding.startAppNow.isClickable = enabled
        binding.startAppNow.alpha = if (enabled) 1.0f else 0.5f
    }

    private fun proceedToHome() {
        (requireActivity() as? MainActivity)?.showHome()
    }

    private fun requestStoragePermission() {
        if (isReadMediaAudioPermissionGranted()) {
            return
        }

        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        storagePermissionLauncher.launch(permission)
    }

    private fun requestNotificationPermission() {
        if (isPostNotificationsPermissionGranted()) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun updateStoragePermissionStatus() {
        if (isReadMediaAudioPermissionGranted()) {
            binding.statusUsageAccess.setText(R.string.granted)
        } else {
            binding.statusUsageAccess.setText(R.string.not_granted)
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

    override fun onUriPermissionGranted(uri: String) {
        super.onUriPermissionGranted(uri)
        updateUriPermissions()
    }

    private fun updateUriPermissions() {
        binding.paths.text = ""
        requireContentResolver().persistedUriPermissions.forEach { permission ->
            binding.paths.text = buildString {
                append(binding.paths.text)
                append(", ")
                append(DocumentFile.fromTreeUri(requireContext(), permission.uri)?.name ?: permission.uri.toString())
            }
        }
    }

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