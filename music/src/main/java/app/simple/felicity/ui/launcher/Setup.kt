package app.simple.felicity.ui.launcher

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.fragment.app.activityViewModels
import app.simple.felicity.R
import app.simple.felicity.activities.MainActivity
import app.simple.felicity.databinding.FragmentSetupBinding
import app.simple.felicity.decorations.utils.PermissionUtils.isManageExternalStoragePermissionGranted
import app.simple.felicity.decorations.utils.PermissionUtils.isPostNotificationsPermissionGranted
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.viewmodels.launcher.PermissionViewModel

class Setup : MediaFragment() {

    private lateinit var binding: FragmentSetupBinding

    private val permissionViewModel by activityViewModels<PermissionViewModel>()

    private val notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
    ) { _ ->
        updateNotificationPermissionStatus()
    }

    private val manageExternalStorageLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        updateManageAllFilesPermissionStatus()
    }

    private val writeStoragePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
    ) { _ ->
        updateManageAllFilesPermissionStatus()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSetupBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireHiddenMiniPlayer()

        updateNotificationPermissionStatus()
        updateManageAllFilesPermissionStatus()

        binding.grantPostNotifications.setOnClickListener {
            requestNotificationPermission()
        }

        binding.grantManageAllFiles.setOnClickListener {
            requestManageAllFilesPermission()
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
        updateManageAllFilesPermissionStatus()
        updateStartButtonState()
    }

    private fun areRequiredPermissionsGranted(): Boolean {
        return isPostNotificationsPermissionGranted() && isManageExternalStoragePermissionGranted()
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
        if (isPostNotificationsPermissionGranted()) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun updateNotificationPermissionStatus() {
        if (isPostNotificationsPermissionGranted()) {
            binding.statusPostNotifications.setText(R.string.granted)
        } else {
            binding.statusPostNotifications.setText(R.string.not_granted)
        }
        updateStartButtonState()
    }

    private fun requestManageAllFilesPermission() {
        if (isManageExternalStoragePermissionGranted()) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = "package:${requireContext().packageName}".toUri()
            }
            manageExternalStorageLauncher.launch(intent)
        } else {
            writeStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun updateManageAllFilesPermissionStatus() {
        if (isManageExternalStoragePermissionGranted()) {
            binding.statusManageAllFiles.setText(R.string.granted)
        } else {
            binding.statusManageAllFiles.setText(R.string.not_granted)
        }

        permissionViewModel.setManageFilesPermissionState(isManageExternalStoragePermissionGranted())
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